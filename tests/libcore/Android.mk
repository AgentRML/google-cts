# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := CtsLibcoreTestCases

LOCAL_STATIC_JAVA_LIBRARIES := core-tests mockito-target

LOCAL_JAVA_LIBRARIES := android-support-test android.test.runner bouncycastle conscrypt

# Don't include this package in any target
LOCAL_MODULE_TAGS := tests

# When built, explicitly put it in the data partition.
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_DEX_PREOPT := false

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_JNI_SHARED_LIBRARIES := libjavacoretests libsqlite_jni libnativehelper_compat_libc++

# Include both the 32 and 64 bit versions of libjavacoretests,
# where applicable.
LOCAL_MULTILIB := both

# Tag this module as a cts_v2 test artifact
LOCAL_COMPATIBILITY_SUITE := cts_v2

# Copy the expectation files to CTS
LOCAL_COMPATIBILITY_SUPPORT_FILES += \
    art/tools/libcore_failures.txt:$(LOCAL_PACKAGE_NAME).failures.expectations \
    libcore/expectations/brokentests.txt:$(LOCAL_PACKAGE_NAME).brokentests.expectations \
    libcore/expectations/icebox.txt:$(LOCAL_PACKAGE_NAME).icebox.expectations \
    libcore/expectations/knownfailures.txt:$(LOCAL_PACKAGE_NAME).knownfailures.expectations \
    libcore/expectations/taggedtests.txt:$(LOCAL_PACKAGE_NAME).taggedtests.expectations

LOCAL_JAVA_LANGUAGE_VERSION := 1.8

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
