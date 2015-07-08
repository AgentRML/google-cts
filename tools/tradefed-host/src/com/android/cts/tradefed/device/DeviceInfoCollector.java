/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.cts.tradefed.device;

import com.android.compatibility.common.util.AbiUtils;
import com.android.cts.tradefed.build.ICtsBuildInfo;
import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.InstrumentationTest;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Collects info from device under test.
 * <p/>
 * This class simply serves as a conduit for grabbing info from device using the device info
 * collector apk, and forwarding that data directly to the {@link ITestInvocationListener} as run
 * metrics.
 */
public class DeviceInfoCollector {

    private static final String LOG_TAG = "DeviceInfoCollector";
    private static final String APK_NAME = "TestDeviceSetup";
    public static final String APP_PACKAGE_NAME = "android.tests.devicesetup";
    private static final String INSTRUMENTATION_NAME = "android.tests.getinfo.DeviceInfoInstrument";

    private static final String EXTENDED_APK_NAME = "CtsDeviceInfo";
    public static final String EXTENDED_APP_PACKAGE_NAME =
            "com.android.compatibility.common.deviceinfo";
    private static final String EXTENDED_INSTRUMENTATION_NAME =
            "com.android.compatibility.common.deviceinfo.DeviceInfoInstrument";
    private static final String DEVICE_RESULT_DIR = "/sdcard/device-info-files";

    public static final Set<String> IDS = new HashSet<String>();
    public static final Set<String> EXTENDED_IDS = new HashSet<String>();

    static {
        for (String abi : AbiUtils.getAbisSupportedByCompatibility()) {
            IDS.add(AbiUtils.createId(abi, APP_PACKAGE_NAME));
            EXTENDED_IDS.add(AbiUtils.createId(abi, EXTENDED_APP_PACKAGE_NAME));
        }
    }

    /**
     * Installs and runs the device info collector instrumentation, and forwards results
     * to the listener.
     *
     * @param device
     * @param listener
     * @throws DeviceNotAvailableException
     */
    public static void collectDeviceInfo(ITestDevice device, String abi, File testApkDir,
            ITestInvocationListener listener) throws DeviceNotAvailableException {
        runInstrumentation(device, abi, testApkDir, listener, APK_NAME, APP_PACKAGE_NAME,
            INSTRUMENTATION_NAME);
    }

    /**
     * Installs and runs the extended device info collector instrumentation, and forwards results
     * to the listener.
     *
     * @param device
     * @param listener
     * @throws DeviceNotAvailableException
     */
    public static void collectExtendedDeviceInfo(ITestDevice device, String abi, File testApkDir,
            ITestInvocationListener listener, IBuildInfo buildInfo)
            throws DeviceNotAvailableException {
        // Clear files in device test result directory
        device.executeShellCommand(String.format("rm -rf %s", DEVICE_RESULT_DIR));
        runInstrumentation(device, abi, testApkDir, listener, EXTENDED_APK_NAME,
            EXTENDED_APP_PACKAGE_NAME, EXTENDED_INSTRUMENTATION_NAME);
        // Copy files in remote result directory to local directory
        pullExtendedDeviceInfoResults(device, buildInfo);
    }

    private static void runInstrumentation(ITestDevice device, String abi, File testApkDir,
            ITestInvocationListener listener, String apkName, String packageName,
            String instrumentName) throws DeviceNotAvailableException {
        File apkFile = new File(testApkDir, String.format("%s.apk", apkName));
        if (!apkFile.exists()) {
            Log.e(LOG_TAG, String.format("Could not find %s", apkFile.getAbsolutePath()));
            return;
        }
        // collect the instrumentation bundle results using instrumentation test
        // should work even though no tests will actually be run
        InstrumentationTest instrTest = new InstrumentationTest();
        instrTest.setDevice(device);
        instrTest.setInstallFile(apkFile);
        // no need to collect tests and re-run
        instrTest.setRerunMode(false);
        instrTest.setPackageName(packageName);
        instrTest.setRunName(AbiUtils.createId(abi, packageName));
        instrTest.setRunnerName(instrumentName);
        instrTest.run(listener);
    }

    private static void pullExtendedDeviceInfoResults(ITestDevice device, IBuildInfo buildInfo)
            throws DeviceNotAvailableException {
        if (!(buildInfo instanceof ICtsBuildInfo)) {
            Log.e(LOG_TAG, "Invalid instance of buildInfo");
            return;
        }
        ICtsBuildInfo ctsBuildInfo = (ICtsBuildInfo) buildInfo;
        File localResultDir = ctsBuildInfo.getResultDir();
        if (localResultDir == null || !localResultDir.isDirectory()) {
            Log.e(LOG_TAG, "Local result directory is null or is not a directory");
            return;
        }
        // Pull files from device result directory to local result directory
        String command = String.format("adb -s %s pull %s %s", device.getSerialNumber(),
                DEVICE_RESULT_DIR, localResultDir.getAbsolutePath());
        if (!execute(command)) {
            Log.e(LOG_TAG, String.format("Failed to run %s", command));
        }
    }

    private static boolean execute(String command) {
        try {
            Process p = Runtime.getRuntime().exec(new String[] {"/bin/bash", "-c", command});
            return (p.waitFor() == 0);
        } catch (Exception e) {
            Log.e(LOG_TAG, e);
            return false;
        }
    }
}
