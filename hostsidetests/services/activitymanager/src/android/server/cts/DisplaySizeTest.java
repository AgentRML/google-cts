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

package android.server.cts;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;

/**
 * Ensure that compatibility dialog is shown when launching an application with
 * an unsupported smallest width.
 */
public class DisplaySizeTest extends DeviceTestCase {
    private static final String DENSITY_PROP_DEVICE = "ro.sf.lcd_density";
    private static final String DENSITY_PROP_EMULATOR = "qemu.sf.lcd_density";

    private static final String AM_START_COMMAND = "am start -n %s/%s.%s";
    private static final String AM_FORCE_STOP = "am force-stop %s";

    private static final int WINDOW_TIMEOUT_MILLIS = 1000;

    private ITestDevice mDevice;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevice = getDevice();

        // Set device to 0.85 zoom. It doesn't matter that we're zooming out
        // since the feature verifies that we're in a non-default density.
        final int stableDensity = getStableDensity();
        final int targetDensity = (int) (stableDensity * 0.85);
        mDevice.executeShellCommand("wm density " + targetDensity);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        try {
            // Restore default density.
            mDevice.executeShellCommand("wm density reset");

            // Ensure app process is stopped.
            forceStopPackage("android.displaysize.app");
        } catch (DeviceNotAvailableException e) {
            // Do nothing.
        }
    }

    public void testCompatibilityDialog() throws Exception {
        startActivity("android.displaysize.app", "SmallestWidthActivity");
        verifyWindowDisplayed("UnsupportedDisplaySizeDialog", WINDOW_TIMEOUT_MILLIS);
    }

    private int getStableDensity() {
        try {
            final String densityProp;
            if (mDevice.getSerialNumber().startsWith("emulator-")) {
                densityProp = DENSITY_PROP_EMULATOR;
            } else {
                densityProp = DENSITY_PROP_DEVICE;
            }

            return Integer.parseInt(mDevice.getProperty(densityProp));
        } catch (DeviceNotAvailableException e) {
            return 0;
        }
    }

    private void forceStopPackage(String packageName) throws DeviceNotAvailableException {
        final String forceStopCmd = String.format(AM_FORCE_STOP, packageName);
        mDevice.executeShellCommand(forceStopCmd);
    }

    private void startActivity(String packageName, String activityName)
            throws DeviceNotAvailableException {
        final String startCmd = String.format(
                AM_START_COMMAND, packageName, packageName, activityName);
        mDevice.executeShellCommand(startCmd);
    }

    private void verifyWindowDisplayed(String windowName, long timeoutMillis)
            throws DeviceNotAvailableException {
        boolean success = false;

        // Verify that compatibility dialog is shown within 1000ms.
        final long timeoutTimeMillis = System.currentTimeMillis() + timeoutMillis;
        while (!success && System.currentTimeMillis() < timeoutTimeMillis) {
            final String output = mDevice.executeShellCommand("dumpsys window");
            success = output.contains(windowName);
        }

        assertTrue(windowName + " was not displayed", success);
    }
}
