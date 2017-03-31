/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package android.jvmti.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.ZipUtil;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * Test a JVMTI device test.
 *
 * Reads the configuration (APK and package name) out of the embedded config.properties file. Runs
 * the agent (expected to be packaged with the APK) into the app's /data/data directory, starts a
 * test run and attaches the agent.
 */
public class JvmtiHostTest extends DeviceTestCase implements IBuildReceiver, IAbiReceiver {
    private static final String RUNNER = "android.support.test.runner.AndroidJUnitRunner";

    private CompatibilityBuildHelper mBuildHelper;
    private IAbi mAbi;
    private String mTestPackageName;
    private String mTestApk;

    @Override
    public void setBuild(IBuildInfo arg0) {
        mBuildHelper = new CompatibilityBuildHelper(arg0);
        mTestPackageName = arg0.getBuildAttributes().get(JvmtiPreparer.PACKAGE_NAME_ATTRIBUTE);
        mTestApk = arg0.getBuildAttributes().get(JvmtiPreparer.APK_ATTRIBUTE);
    }

    @Override
    public void setAbi(IAbi arg0) {
        mAbi = arg0;
    }

    /**
     * Tests the string was successfully logged to Logcat from the activity.
     *
     * @throws Exception
     */
    public void testJvmti() throws Exception {
        final ITestDevice device = getDevice();

        if (mTestApk == null || mTestPackageName == null) {
            throw new IllegalStateException("Incorrect configuration");
        }

        RemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(mTestPackageName, RUNNER,
                device.getIDevice());

        TestResults tr = new TestResults(new AttachAgent(device, mTestPackageName, mTestApk));

        device.runInstrumentationTests(runner, tr);

        assertTrue(tr.getErrors(), tr.hasStarted());
        assertFalse(tr.getErrors(), tr.hasFailed());
    }

    private class AttachAgent implements Runnable {
        private ITestDevice mDevice;
        private String mPkg;
        private String mApk;

        public AttachAgent(ITestDevice device, String pkg, String apk) {
            this.mDevice = device;
            this.mPkg = pkg;
            this.mApk = apk;
        }

        @Override
        public void run() {
            File tmpFile = null;
            ZipFile zf = null;
            try {
                String pwd = mDevice.executeShellCommand("run-as " + mPkg + " pwd");
                if (pwd == null) {
                    throw new RuntimeException("pwd failed");
                }
                pwd = pwd.trim();
                if (pwd.isEmpty()) {
                    throw new RuntimeException("pwd failed");
                }

                String agentInDataData = installLibToDataData(pwd, "libctsjvmtiagent.so");

                String attachReply = mDevice
                        .executeShellCommand("am attach-agent " + mPkg + " " + agentInDataData);
                // Don't try to parse the output. The test will time out anyways if this didn't
                // work.
                if (attachReply != null && !attachReply.trim().isEmpty()) {
                    CLog.e(attachReply);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed attaching", e);
            } finally {
                if (tmpFile != null) {
                    tmpFile.delete();
                }
                if (zf != null) {
                    try {
                        zf.close();
                    } catch (Exception e) {
                        throw new RuntimeException("ZipFile close failed", e);
                    }
                }
            }
        }

        String installLibToDataData(String dataData, String library) throws Exception {
            ZipFile zf = null;
            File tmpFile = null;
            try {
                String libInDataData = dataData + "/" + library;

                File apkFile = mBuildHelper.getTestFile(mApk);
                zf = new ZipFile(apkFile);

                String libPathInApk = "lib/" + mAbi.getName() + "/" + library;
                tmpFile = ZipUtil.extractFileFromZip(zf, libPathInApk);

                String libInTmp = "/data/local/tmp/" + tmpFile.getName();
                if (!mDevice.pushFile(tmpFile, libInTmp)) {
                    throw new RuntimeException("Could not push library " + library + " to device");
                }

                String runAsCp = mDevice.executeShellCommand(
                        "run-as " + mPkg + " cp " + libInTmp + " " + libInDataData);
                if (runAsCp != null && !runAsCp.trim().isEmpty()) {
                    throw new RuntimeException(runAsCp.trim());
                }

                String runAsChmod = mDevice
                        .executeShellCommand("run-as " + mPkg + " chmod a+x " + libInDataData);
                if (runAsChmod != null && !runAsChmod.trim().isEmpty()) {
                    throw new RuntimeException(runAsChmod.trim());
                }

                return libInDataData;
            } finally {
                if (tmpFile != null) {
                    tmpFile.delete();
                }
                if (zf != null) {
                    try {
                        zf.close();
                    } catch (Exception e) {
                        throw new RuntimeException("ZipFile close failed", e);
                    }
                }
            }
        }
    }

    private static class TestResults implements ITestRunListener {
        private boolean mFailed = false;
        private boolean mStarted = false;
        private final Runnable mOnStart;
        private List<String> mErrors = new LinkedList<>();

        public TestResults(Runnable onStart) {
            this.mOnStart = onStart;
        }

        public boolean hasFailed() {
            return mFailed;
        }

        public boolean hasStarted() {
            return mStarted;
        }

        public String getErrors() {
            if (mErrors.isEmpty()) {
                return "";
            }
            return mErrors.toString();
        }

        @Override
        public void testAssumptionFailure(TestIdentifier arg0, String arg1) {
            mFailed = true;
            mErrors.add(arg0.toString() + " " + arg1);
        }

        @Override
        public void testEnded(TestIdentifier arg0, Map<String, String> arg1) {}

        @Override
        public void testFailed(TestIdentifier arg0, String arg1) {
            mFailed = true;
            mErrors.add(arg0.toString() + " " + arg1);
        }

        @Override
        public void testIgnored(TestIdentifier arg0) {}

        @Override
        public void testRunEnded(long arg0, Map<String, String> arg1) {}

        @Override
        public void testRunFailed(String arg0) {
            mFailed = true;
            mErrors.add(arg0);
        }

        @Override
        public void testRunStarted(String arg0, int arg1) {
            if (mOnStart != null) {
                mOnStart.run();
            }
        }

        @Override
        public void testRunStopped(long arg0) {}

        @Override
        public void testStarted(TestIdentifier arg0) {
            mStarted = true;
        }
    }
}
