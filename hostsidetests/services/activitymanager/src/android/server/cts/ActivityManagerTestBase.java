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
 * limitations under the License
 */

package android.server.cts;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;

import java.lang.Exception;
import java.lang.Integer;
import java.lang.String;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ActivityManagerTestBase extends DeviceTestCase {
    private static final boolean PRETEND_DEVICE_SUPPORTS_PIP = false;
    private static final boolean PRETEND_DEVICE_SUPPORTS_FREEFORM = false;

    // Constants copied from ActivityManager.StackId. If they are changed there, these must be
    // updated.
    /** First static stack ID. */
    public static final int FIRST_STATIC_STACK_ID = 0;

    /** Home activity stack ID. */
    public static final int HOME_STACK_ID = FIRST_STATIC_STACK_ID;

    /** ID of stack where fullscreen activities are normally launched into. */
    public static final int FULLSCREEN_WORKSPACE_STACK_ID = 1;

    /** ID of stack where freeform/resized activities are normally launched into. */
    public static final int FREEFORM_WORKSPACE_STACK_ID = FULLSCREEN_WORKSPACE_STACK_ID + 1;

    /** ID of stack that occupies a dedicated region of the screen. */
    public static final int DOCKED_STACK_ID = FREEFORM_WORKSPACE_STACK_ID + 1;

    /** ID of stack that always on top (always visible) when it exist. */
    public static final int PINNED_STACK_ID = DOCKED_STACK_ID + 1;

    private static final String TASK_ID_PREFIX = "taskId";

    private static final String AM_STACK_LIST = "am stack list";

    private static final String AM_FORCE_STOP_TEST_PACKAGE = "am force-stop android.server.app";

    private static final String AM_REMOVE_STACK = "am stack remove ";

    protected static final String AM_START_HOME_ACTIVITY_COMMAND =
            "am start -a android.intent.action.MAIN -c android.intent.category.HOME";

    protected static final String AM_MOVE_TOP_ACTIVITY_TO_PINNED_STACK_COMMAND =
            "am stack move-top-activity-to-pinned-stack 1 0 0 500 500";

    private static final String AM_RESIZE_DOCKED_STACK = "am stack resize-docked-stack ";

    private static final String AM_MOVE_TASK = "am stack movetask ";

    /** A reference to the device under test. */
    protected ITestDevice mDevice;

    private HashSet<String> mAvailableFeatures;

    protected static String getAmStartCmd(final String activityName) {
        return "am start -n " + getActivityComponentName(activityName);
    }

    protected static String getAmStartCmdOverHome(final String activityName) {
        return "am start --activity-task-on-home -n " + getActivityComponentName(activityName);
    }

    static String getActivityComponentName(final String activityName) {
        return "android.server.app/." + activityName;
    }

    static String getWindowName(final String activityName) {
        return "android.server.app/android.server.app." + activityName;
    }

    protected ActivityAndWindowManagersState mAmWmState = new ActivityAndWindowManagersState();

    private int mInitialAccelerometerRotation;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Get the device, this gives a handle to run commands and install APKs.
        mDevice = getDevice();
        mInitialAccelerometerRotation = getAccelerometerRotation();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        try {
            mDevice.executeShellCommand(AM_FORCE_STOP_TEST_PACKAGE);
            setAccelerometerRotation(mInitialAccelerometerRotation);
            // Remove special stacks.
            mDevice.executeShellCommand(AM_REMOVE_STACK + PINNED_STACK_ID);
            mDevice.executeShellCommand(AM_REMOVE_STACK + DOCKED_STACK_ID);
            mDevice.executeShellCommand(AM_REMOVE_STACK + FREEFORM_WORKSPACE_STACK_ID);
        } catch (DeviceNotAvailableException e) {
        }
    }

    protected void launchActivityInStack(String activityName, int stackId) throws Exception {
        mDevice.executeShellCommand(getAmStartCmd(activityName) + " --stack " + stackId);
    }

    protected void launchActivityInDockStack(String activityName) throws Exception {
        mDevice.executeShellCommand(getAmStartCmd(activityName));
        final int taskId = getActivityTaskId(activityName);
        final String cmd = AM_MOVE_TASK + taskId + " " + DOCKED_STACK_ID + " true";
        mDevice.executeShellCommand(cmd);
    }

    protected void resizeActivityTask(String activityName, int left, int top, int right, int bottom)
            throws Exception {
        final int taskId = getActivityTaskId(activityName);
        final String cmd = "am task resize "
                + taskId + " " + left + " " + top + " " + right + " " + bottom;
        mDevice.executeShellCommand(cmd);
    }

    protected void resizeDockedStack(
            int stackWidth, int stackHeight, int taskWidth, int taskHeight)
                    throws DeviceNotAvailableException {
        mDevice.executeShellCommand(AM_RESIZE_DOCKED_STACK
                + "0 0 " + stackWidth + " " + stackHeight
                + " 0 0 " + taskWidth + " " + taskHeight);
    }

    // Utility method for debugging, not used directly here, but useful, so kept around.
    protected void printStacksAndTasks() throws DeviceNotAvailableException {
        CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand(AM_STACK_LIST, outputReceiver);
        String output = outputReceiver.getOutput();
        for (String line : output.split("\\n")) {
            CLog.logAndDisplay(LogLevel.INFO, line);
        }
    }

    protected int getActivityTaskId(String name) throws DeviceNotAvailableException {
        CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand(AM_STACK_LIST, outputReceiver);
        final String output = outputReceiver.getOutput();
        for (String line : output.split("\\n")) {
            if (line.contains(name)) {
                for (String word : line.split("\\s+")) {
                    if (word.startsWith(TASK_ID_PREFIX)) {
                        final String withColon = word.split("=")[1];
                        return Integer.parseInt(withColon.substring(0, withColon.length() - 1));
                    }
                }
            }
        }
        return -1;
    }

    protected boolean supportsPip() throws DeviceNotAvailableException {
        return hasDeviceFeature("android.software.picture_in_picture")
                || PRETEND_DEVICE_SUPPORTS_PIP;
    }

    protected boolean supportsFreeform() throws DeviceNotAvailableException {
        return hasDeviceFeature("android.software.freeform_window_management")
                || PRETEND_DEVICE_SUPPORTS_FREEFORM;
    }

    protected boolean hasDeviceFeature(String requiredFeature) throws DeviceNotAvailableException {
        if (mAvailableFeatures == null) {
            // TODO: Move this logic to ITestDevice.
            final String output = runCommandAndPrintOutput("pm list features");

            // Extract the id of the new user.
            mAvailableFeatures = new HashSet<>();
            for (String feature: output.split("\\s+")) {
                // Each line in the output of the command has the format "feature:{FEATURE_VALUE}".
                String[] tokens = feature.split(":");
                assertTrue("\"" + feature + "\" expected to have format feature:{FEATURE_VALUE}",
                        tokens.length > 1);
                assertEquals(feature, "feature", tokens[0]);
                mAvailableFeatures.add(tokens[1]);
            }
        }
        boolean result = mAvailableFeatures.contains(requiredFeature);
        if (!result) {
            CLog.logAndDisplay(LogLevel.INFO, "Device doesn't support " + requiredFeature);
        }
        return result;
    }

    protected void lockDevice() throws DeviceNotAvailableException {
        runCommandAndPrintOutput("input keyevent 26");
    }

    protected void unlockDevice() throws DeviceNotAvailableException {
        runCommandAndPrintOutput("input keyevent 26");
        runCommandAndPrintOutput("input keyevent 82");
    }

    protected void setDeviceRotation(int rotation) throws DeviceNotAvailableException {
        setAccelerometerRotation(0);
        runCommandAndPrintOutput("settings put system user_rotation " + rotation);
    }

    private int getAccelerometerRotation() throws DeviceNotAvailableException {
        final String rotation =
                runCommandAndPrintOutput("settings get system accelerometer_rotation");
        return Integer.valueOf(rotation.trim());
    }

    private void setAccelerometerRotation(int rotation) throws DeviceNotAvailableException {
        runCommandAndPrintOutput(
                "settings put system accelerometer_rotation " + rotation);
    }

    protected String runCommandAndPrintOutput(String command) throws DeviceNotAvailableException {
        final String output = mDevice.executeShellCommand(command);
        CLog.logAndDisplay(LogLevel.INFO, command);
        CLog.logAndDisplay(LogLevel.INFO, output);
        return output;
    }

    protected void clearLogcat() throws DeviceNotAvailableException {
        mDevice.executeAdbCommand("logcat", "-c");
    }

    protected void assertActivityLifecycle(String activityName, boolean relaunched)
            throws DeviceNotAvailableException {
        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(activityName);

        if (relaunched) {
            if (lifecycleCounts.mDestroyCount < 1) {
                fail(activityName + " must have been destroyed. mDestroyCount="
                        + lifecycleCounts.mDestroyCount);
            }
            if (lifecycleCounts.mCreateCount < 1) {
                fail(activityName + " must have been (re)created. mCreateCount="
                        + lifecycleCounts.mCreateCount);
            }
        } else {
            if (lifecycleCounts.mDestroyCount > 0) {
                fail(activityName + " must *NOT* have been destroyed. mDestroyCount="
                        + lifecycleCounts.mDestroyCount);
            }
            if (lifecycleCounts.mCreateCount > 0) {
                fail(activityName + " must *NOT* have been (re)created. mCreateCount="
                        + lifecycleCounts.mCreateCount);
            }
            if (lifecycleCounts.mConfigurationChangedCount < 1) {
                fail(activityName + " must have received configuration changed. "
                        + "mConfigurationChangedCount="
                        + lifecycleCounts.mConfigurationChangedCount);
            }
        }
    }

    private class ActivityLifecycleCounts {

        private final Pattern mCreatePattern = Pattern.compile("(.+): onCreate");
        private final Pattern mConfigurationChangedPattern =
                Pattern.compile("(.+): onConfigurationChanged");
        private final Pattern mDestroyPattern = Pattern.compile("(.+): onDestroy");

        private final LinkedList<String> mLogs = new LinkedList();
        int mCreateCount;
        int mConfigurationChangedCount;
        int mDestroyCount;

        public ActivityLifecycleCounts(String activityName) throws DeviceNotAvailableException {

            final String logs = mDevice.executeAdbCommand(
                    "logcat", "-v", "brief", "-d", activityName + ":I", "*:S");
            Collections.addAll(mLogs, logs.split("\\n"));

            while (!mLogs.isEmpty()) {
                final String line = mLogs.pop().trim();

                Matcher matcher = mCreatePattern.matcher(line);
                if (matcher.matches()) {
                    mCreateCount++;
                    continue;
                }

                matcher = mConfigurationChangedPattern.matcher(line);
                if (matcher.matches()) {
                    mConfigurationChangedCount++;
                    continue;
                }

                matcher = mDestroyPattern.matcher(line);
                if (matcher.matches()) {
                    mDestroyCount++;
                    continue;
                }
            }
        }
    }
}
