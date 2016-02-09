/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package android.host.systemui;

import com.android.compatibility.common.util.AbiUtils;
import com.android.cts.migration.MigrationHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.File;

public class BaseTileServiceTest extends DeviceTestCase implements IBuildReceiver {
    // Constants for generating commands below.
    private static final String PACKAGE = "android.systemui.cts";
    private static final String ACTION_SHOW_DIALOG = "android.sysui.testtile.action.SHOW_DIALOG";

    private static final String APK_NAME = "CtsSystemUiDeviceApp";

    // Commands used on the device.
    private static final String ADD_TILE   = "cmd statusbar add-tile ";
    private static final String REM_TILE   = "cmd statusbar remove-tile ";
    private static final String CLICK_TILE = "cmd statusbar click-tile ";

    private static final String OPEN_NOTIFICATIONS = "cmd statusbar expand-notifications";
    private static final String OPEN_SETTINGS      = "cmd statusbar expand-settings";
    private static final String COLLAPSE           = "cmd statusbar collapse";

    private static final String SHOW_DIALOG = "am broadcast -a " + ACTION_SHOW_DIALOG;

    public static final String TEST_PREFIX = "TileTest_";

    // Time between checks for logs we expect.
    private static final long CHECK_DELAY = 500;
    // Number of times to check before failing.
    private static final long CHECK_RETRIES = 15;

    private final String mService;
    private final String mComponent;

    /** A reference to the build. */
    private IBuildInfo mBuildInfo;

    public BaseTileServiceTest(String service) {
        mService = service;
        mComponent = PACKAGE + "/." + mService;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        assertNotNull(mBuildInfo);  // ensure build has been set before test is run.
        // Get the APK from the build.
        final File app = MigrationHelper.getTestFile(mBuildInfo, String.format("%s.apk", APK_NAME));

        getDevice().installPackage(app, true);

        clearLogcat();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        collapse();
        remTile();
        // Try to wait for a onTileRemoved.
        waitFor("onTileRemoved");

        ITestDevice device = getDevice();
        device.uninstallPackage(PACKAGE);
    }

    protected void showDialog() throws Exception {
        execute(SHOW_DIALOG);
    }

    protected void addTile() throws Exception {
        execute(ADD_TILE + mComponent);
    }

    protected void remTile() throws Exception {
        execute(REM_TILE + mComponent);
    }

    protected void clickTile() throws Exception {
        execute(CLICK_TILE + mComponent);
    }

    protected void openNotifications() throws Exception {
        execute(OPEN_NOTIFICATIONS);
    }

    protected void openSettings() throws Exception {
        execute(OPEN_SETTINGS);
    }

    protected void collapse() throws Exception {
        execute(COLLAPSE);
    }

    private void execute(String cmd) throws Exception {
        getDevice().executeShellCommand(cmd);
        // All of the status bar commands tend to have animations associated
        // everything seems to be happier if you give them time to finish.
        Thread.sleep(100);
    }

    protected boolean waitFor(String str) throws DeviceNotAvailableException, InterruptedException {
        final String searchStr = TEST_PREFIX + str;
        int ct = 0;
        while (!hasLog(searchStr) && (ct++ < CHECK_RETRIES)) {
            Thread.sleep(CHECK_DELAY);
        }
        return hasLog(searchStr);
    }

    protected boolean hasLog(String str) throws DeviceNotAvailableException {
        String logs = getDevice().executeAdbCommand("logcat", "-v", "brief", "-d", mService + ":I",
                "*:S");
        return logs.contains(str);
    }

    private void clearLogcat() throws DeviceNotAvailableException {
        getDevice().executeAdbCommand("logcat", "-c");
    }
}
