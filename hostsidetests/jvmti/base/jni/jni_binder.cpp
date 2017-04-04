/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "jni_binder.h"

#include <dlfcn.h>
#include <inttypes.h>
#include <stdio.h>

#include "android-base/logging.h"
#include "android-base/stringprintf.h"
#include "common.h"
#include "jvmti_helper.h"
#include "scoped_local_ref.h"
#include "scoped_utf_chars.h"

namespace cts {
namespace jvmti {

static constexpr const char* kMainClass = "android/jvmti/cts/JniBindings";
static constexpr const char* kMainClassStartup = "startup";

size_t CountModifiedUtf8Chars(const char* utf8, size_t byte_count) {
  DCHECK_LE(byte_count, strlen(utf8));
  size_t len = 0;
  const char* end = utf8 + byte_count;
  for (; utf8 < end; ++utf8) {
    int ic = *utf8;
    len++;
    if (LIKELY((ic & 0x80) == 0)) {
      // One-byte encoding.
      continue;
    }
    // Two- or three-byte encoding.
    utf8++;
    if ((ic & 0x20) == 0) {
      // Two-byte encoding.
      continue;
    }
    utf8++;
    if ((ic & 0x10) == 0) {
      // Three-byte encoding.
      continue;
    }

    // Four-byte encoding: needs to be converted into a surrogate
    // pair.
    utf8++;
    len++;
  }
  return len;
}

static uint16_t GetTrailingUtf16Char(uint32_t maybe_pair) {
  return static_cast<uint16_t>(maybe_pair >> 16);
}

static uint16_t GetLeadingUtf16Char(uint32_t maybe_pair) {
  return static_cast<uint16_t>(maybe_pair & 0x0000FFFF);
}

static uint32_t GetUtf16FromUtf8(const char** utf8_data_in) {
  const uint8_t one = *(*utf8_data_in)++;
  if ((one & 0x80) == 0) {
    // one-byte encoding
    return one;
  }

  const uint8_t two = *(*utf8_data_in)++;
  if ((one & 0x20) == 0) {
    // two-byte encoding
    return ((one & 0x1f) << 6) | (two & 0x3f);
  }

  const uint8_t three = *(*utf8_data_in)++;
  if ((one & 0x10) == 0) {
    return ((one & 0x0f) << 12) | ((two & 0x3f) << 6) | (three & 0x3f);
  }

  // Four byte encodings need special handling. We'll have
  // to convert them into a surrogate pair.
  const uint8_t four = *(*utf8_data_in)++;

  // Since this is a 4 byte UTF-8 sequence, it will lie between
  // U+10000 and U+1FFFFF.
  //
  // TODO: What do we do about values in (U+10FFFF, U+1FFFFF) ? The
  // spec says they're invalid but nobody appears to check for them.
  const uint32_t code_point = ((one & 0x0f) << 18) | ((two & 0x3f) << 12)
      | ((three & 0x3f) << 6) | (four & 0x3f);

  uint32_t surrogate_pair = 0;
  // Step two: Write out the high (leading) surrogate to the bottom 16 bits
  // of the of the 32 bit type.
  surrogate_pair |= ((code_point >> 10) + 0xd7c0) & 0xffff;
  // Step three : Write out the low (trailing) surrogate to the top 16 bits.
  surrogate_pair |= ((code_point & 0x03ff) + 0xdc00) << 16;

  return surrogate_pair;
}

static std::string MangleForJni(const std::string& s) {
  std::string result;
  size_t char_count = CountModifiedUtf8Chars(s.c_str(), s.length());
  const char* cp = &s[0];
  for (size_t i = 0; i < char_count; ++i) {
    uint32_t ch = GetUtf16FromUtf8(&cp);
    if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
      result.push_back(ch);
    } else if (ch == '.' || ch == '/') {
      result += "_";
    } else if (ch == '_') {
      result += "_1";
    } else if (ch == ';') {
      result += "_2";
    } else if (ch == '[') {
      result += "_3";
    } else {
      const uint16_t leading = GetLeadingUtf16Char(ch);
      const uint32_t trailing = GetTrailingUtf16Char(ch);

      android::base::StringAppendF(&result, "_0%04x", leading);
      if (trailing != 0) {
        android::base::StringAppendF(&result, "_0%04x", trailing);
      }
    }
  }
  return result;
}

static std::string GetJniShortName(const std::string& class_descriptor, const std::string& method) {
  // Remove the leading 'L' and trailing ';'...
  std::string class_name(class_descriptor);
  CHECK_EQ(class_name[0], 'L') << class_name;
  CHECK_EQ(class_name[class_name.size() - 1], ';') << class_name;
  class_name.erase(0, 1);
  class_name.erase(class_name.size() - 1, 1);

  std::string short_name;
  short_name += "Java_";
  short_name += MangleForJni(class_name);
  short_name += "_";
  short_name += MangleForJni(method);
  return short_name;
}

static void BindMethod(jvmtiEnv* jvmti_env,
                       JNIEnv* env,
                       jclass klass,
                       jmethodID method) {
  std::string name;
  std::string signature;
  std::string mangled_names[2];
  {
    char* name_cstr;
    char* sig_cstr;
    jvmtiError name_result = jvmti_env->GetMethodName(method, &name_cstr, &sig_cstr, nullptr);
    CheckJvmtiError(jvmti_env, name_result);
    CHECK(name_cstr != nullptr);
    CHECK(sig_cstr != nullptr);
    name = name_cstr;
    signature = sig_cstr;

    char* klass_name;
    jvmtiError klass_result = jvmti_env->GetClassSignature(klass, &klass_name, nullptr);
    CheckJvmtiError(jvmti_env, klass_result);

    mangled_names[0] = GetJniShortName(klass_name, name);
    // TODO: Long JNI name.

    CheckJvmtiError(jvmti_env, Deallocate(jvmti_env, name_cstr));
    CheckJvmtiError(jvmti_env, Deallocate(jvmti_env, sig_cstr));
    CheckJvmtiError(jvmti_env, Deallocate(jvmti_env, klass_name));
  }

  for (const std::string& mangled_name : mangled_names) {
    if (mangled_name.empty()) {
      continue;
    }
    void* sym = dlsym(RTLD_DEFAULT, mangled_name.c_str());
    if (sym == nullptr) {
      continue;
    }

    JNINativeMethod native_method;
    native_method.fnPtr = sym;
    native_method.name = name.c_str();
    native_method.signature = signature.c_str();

    env->RegisterNatives(klass, &native_method, 1);

    return;
  }

  LOG(FATAL) << "Could not find " << mangled_names[0];
}

static std::string DescriptorToDot(const char* descriptor) {
  size_t length = strlen(descriptor);
  if (length > 1) {
    if (descriptor[0] == 'L' && descriptor[length - 1] == ';') {
      // Descriptors have the leading 'L' and trailing ';' stripped.
      std::string result(descriptor + 1, length - 2);
      std::replace(result.begin(), result.end(), '/', '.');
      return result;
    } else {
      // For arrays the 'L' and ';' remain intact.
      std::string result(descriptor);
      std::replace(result.begin(), result.end(), '/', '.');
      return result;
    }
  }
  // Do nothing for non-class/array descriptors.
  return descriptor;
}

static jobject GetSystemClassLoader(JNIEnv* env) {
  ScopedLocalRef<jclass> cl_klass(env, env->FindClass("java/lang/ClassLoader"));
  CHECK(cl_klass.get() != nullptr);
  jmethodID getsystemclassloader_method = env->GetStaticMethodID(cl_klass.get(),
                                                                 "getSystemClassLoader",
                                                                 "()Ljava/lang/ClassLoader;");
  CHECK(getsystemclassloader_method != nullptr);
  return env->CallStaticObjectMethod(cl_klass.get(), getsystemclassloader_method);
}

static jclass FindClassWithClassLoader(JNIEnv* env, const char* class_name, jobject class_loader) {
  // Create a String of the name.
  std::string descriptor = android::base::StringPrintf("L%s;", class_name);
  std::string dot_name = DescriptorToDot(descriptor.c_str());
  ScopedLocalRef<jstring> name_str(env, env->NewStringUTF(dot_name.c_str()));

  // Call Class.forName with it.
  ScopedLocalRef<jclass> c_klass(env, env->FindClass("java/lang/Class"));
  CHECK(c_klass.get() != nullptr);
  jmethodID forname_method = env->GetStaticMethodID(
      c_klass.get(),
      "forName",
      "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
  CHECK(forname_method != nullptr);

  return static_cast<jclass>(env->CallStaticObjectMethod(c_klass.get(),
                                                         forname_method,
                                                         name_str.get(),
                                                         JNI_FALSE,
                                                         class_loader));
}

// Find the given classname. First try the implied classloader, then the system classloader,
// then use JVMTI to find all classloaders.
static jclass FindClass(jvmtiEnv* jvmti_env,
                        JNIEnv* env,
                        const char* class_name,
                        jobject class_loader) {
  if (class_loader != nullptr) {
    return FindClassWithClassLoader(env, class_name, class_loader);
  }

  jclass from_implied = env->FindClass(class_name);
  if (from_implied != nullptr) {
    return from_implied;
  }
  env->ExceptionClear();

  ScopedLocalRef<jobject> system_class_loader(env, GetSystemClassLoader(env));
  CHECK(system_class_loader.get() != nullptr);
  jclass from_system = FindClassWithClassLoader(env, class_name, system_class_loader.get());
  if (from_system != nullptr) {
    return from_system;
  }
  env->ExceptionClear();

  // Look at the context classloaders of all threads.
  jint thread_count;
  jthread* threads;
  CheckJvmtiError(jvmti_env, jvmti_env->GetAllThreads(&thread_count, &threads));
  JvmtiUniquePtr threads_uptr = MakeJvmtiUniquePtr(jvmti_env, threads);

  jclass result = nullptr;
  for (jint t = 0; t != thread_count; ++t) {
    // Always loop over all elements, as we need to free the local references.
    if (result == nullptr) {
      jvmtiThreadInfo info;
      CheckJvmtiError(jvmti_env, jvmti_env->GetThreadInfo(threads[t], &info));
      CheckJvmtiError(jvmti_env, Deallocate(jvmti_env, info.name));
      if (info.thread_group != nullptr) {
        env->DeleteLocalRef(info.thread_group);
      }
      if (info.context_class_loader != nullptr) {
        result = FindClassWithClassLoader(env, class_name, info.context_class_loader);
        env->ExceptionClear();
        env->DeleteLocalRef(info.context_class_loader);
      }
    }
    env->DeleteLocalRef(threads[t]);
  }

  if (result != nullptr) {
    return result;
  }

  // TODO: Implement scanning *all* classloaders.
  LOG(FATAL) << "Unimplemented";

  return nullptr;
}

void BindFunctions(jvmtiEnv* jvmti_env, JNIEnv* env, const char* class_name, jobject class_loader) {
  // Use JNI to load the class.
  ScopedLocalRef<jclass> klass(env, FindClass(jvmti_env, env, class_name, class_loader));
  CHECK(klass.get() != nullptr) << class_name;

  // Use JVMTI to get the methods.
  jint method_count;
  jmethodID* methods;
  jvmtiError methods_result = jvmti_env->GetClassMethods(klass.get(), &method_count, &methods);
  CheckJvmtiError(jvmti_env, methods_result);

  // Check each method.
  for (jint i = 0; i < method_count; ++i) {
    jint modifiers;
    jvmtiError mod_result = jvmti_env->GetMethodModifiers(methods[i], &modifiers);
    CheckJvmtiError(jvmti_env, mod_result);
    constexpr jint kNative = static_cast<jint>(0x0100);
    if ((modifiers & kNative) != 0) {
      BindMethod(jvmti_env, env, klass.get(), methods[i]);
    }
  }

  CheckJvmtiError(jvmti_env, Deallocate(jvmti_env, methods));
}

// Inform the main instrumentation class of our successful attach.
static void InformMainAttach(jvmtiEnv* jvmti_env,
                             JNIEnv* jni_env,
                             const char* class_name,
                             const char* method_name) {
  // Use JNI to load the class.
  ScopedLocalRef<jclass> klass(jni_env, FindClass(jvmti_env, jni_env, class_name, nullptr));
  CHECK(klass.get() != nullptr) << class_name;

  jmethodID method = jni_env->GetStaticMethodID(klass.get(), method_name, "()V");
  CHECK(method != nullptr);

  jni_env->CallStaticVoidMethod(klass.get(), method);
}

// TODO: Check this. This may not work on device. The classloader containing the app's classes
//       may not have been created at this point (i.e., if it's not the system classloader).
static void JNICALL VMInitCallback(jvmtiEnv* jvmti_env,
                                   JNIEnv* jni_env,
                                   jthread thread ATTRIBUTE_UNUSED) {
  // Bind kMainClass native methods.
  BindFunctions(jvmti_env, jni_env, kMainClass);

  // And let the test know that we have started up.
  InformMainAttach(jvmti_env, jni_env, kMainClass, kMainClassStartup);

  // And delete the jvmtiEnv.
  jvmti_env->DisposeEnvironment();
}

// Install a phase callback that will bind JNI functions on VMInit.
void BindOnLoad(JavaVM* vm) {
  // Use a new jvmtiEnv. Otherwise we might collide with table changes.
  jvmtiEnv* install_env;
  if (vm->GetEnv(reinterpret_cast<void**>(&install_env), JVMTI_VERSION_1_0) != 0) {
    LOG(FATAL) << "Could not get jvmtiEnv";
  }
  SetAllCapabilities(install_env);

  {
    jvmtiEventCallbacks callbacks;
    memset(&callbacks, 0, sizeof(jvmtiEventCallbacks));
    callbacks.VMInit = VMInitCallback;

    CheckJvmtiError(install_env, install_env->SetEventCallbacks(&callbacks, sizeof(callbacks)));
  }

  {
    CheckJvmtiError(install_env, install_env->SetEventNotificationMode(JVMTI_ENABLE,
                                                                       JVMTI_EVENT_VM_INIT,
                                                                       nullptr));
  }
}

// Ensure binding of the Main class when the agent is started through OnAttach.
void BindOnAttach(JavaVM* vm) {
  // Get a JNIEnv. As the thread is attached, we must not destroy it.
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != 0) {
    LOG(FATAL) << "Could not get JNIEnv";
  }

  jvmtiEnv* jvmti_env;
  if (vm->GetEnv(reinterpret_cast<void**>(&jvmti_env), JVMTI_VERSION_1_0) != 0) {
    LOG(FATAL) << "Could not get jvmtiEnv";
  }
  SetAllCapabilities(jvmti_env);

  BindFunctions(jvmti_env, env, kMainClass);

  // And let the test know that we have started up.
  InformMainAttach(jvmti_env, env, kMainClass, kMainClassStartup);

  if (jvmti_env->DisposeEnvironment() != JVMTI_ERROR_NONE) {
    LOG(FATAL) << "Could not dispose temporary jvmtiEnv";
  }
}

extern "C" JNIEXPORT void JNICALL Java_android_jvmti_cts_JniBindings_bindAgentJNI(
    JNIEnv* env, jclass klass ATTRIBUTE_UNUSED, jstring className, jobject classLoader) {
  ScopedUtfChars name(env, className);
  BindFunctions(GetJvmtiEnv(), env, name.c_str(), classLoader);
}

}  // namespace jvmti
}  // namespace cts
