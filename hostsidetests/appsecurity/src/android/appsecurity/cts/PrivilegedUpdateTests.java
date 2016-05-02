/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.appsecurity.cts;

import com.android.compatibility.common.util.AbiUtils;
import com.android.cts.migration.MigrationHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

/**
 * Tests that verify intent filters.
 */
public class PrivilegedUpdateTests extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    private static final String SHIM_PKG = "com.android.cts.priv.ctsshim";
    /** Package name of the tests to be run */
    private static final String TEST_PKG = "com.android.cts.privilegedupdate";

    /** APK that contains the shim; to test upgrading */
    private static final String SHIM_UPDATE_APK = "CtsShimPrivUpgradePrebuilt.apk";
    /** APK that contains individual shim test cases */
    private static final String TEST_APK = "CtsPrivilegedUpdateTests.apk";

    private IAbi mAbi;
    private IBuildInfo mCtsBuild;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        assertNotNull(mAbi);
        assertNotNull(mCtsBuild);

        getDevice().uninstallPackage(SHIM_PKG);
        getDevice().uninstallPackage(TEST_PKG);

        assertNull(getDevice().installPackage(
                MigrationHelper.getTestFile(mCtsBuild, TEST_APK), false));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        getDevice().uninstallPackage(SHIM_PKG);
        getDevice().uninstallPackage(TEST_PKG);
    }

    public void testSystemAppPriorities() throws Exception {
        runDeviceTests(TEST_PKG, ".PrivilegedUpdateTest", "testSystemAppPriorities");
    }

    public void testPrivilegedAppPriorities() throws Exception {
        runDeviceTests(TEST_PKG, ".PrivilegedUpdateTest", "testPrivilegedAppPriorities");
    }

    public void testPrivilegedAppUpgradePriorities() throws Exception {
        getDevice().uninstallPackage(SHIM_PKG);
        
        try {
            assertNull(getDevice().installPackage(
                    MigrationHelper.getTestFile(mCtsBuild, SHIM_UPDATE_APK), true));
            runDeviceTests(TEST_PKG, ".PrivilegedUpdateTest", "testPrivilegedAppUpgradePriorities");
        } finally {
            getDevice().uninstallPackage(SHIM_PKG);
        }
    }

    private void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }
}
