/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.verifier.sensors.helpers;

import com.android.cts.verifier.R;

import android.util.Log;

/**
 * A helper class for {@link SensorFeaturesDeactivator}. It abstracts the responsibility of handling
 * device settings that affect sensors.
 *
 * This class is not thread safe. It is meant to be used only by {@link SensorFeaturesDeactivator}.
 */
abstract class SensorSettingContainer {
    private static final String TAG = "SensorSettingContainer";
    private final SensorFeaturesDeactivator.ActivityHandler mActivityHandler;
    private final String mAction;
    private final int mSettingNameResId;

    private boolean mCapturedModeOn;

    public SensorSettingContainer(
            SensorFeaturesDeactivator.ActivityHandler activityHandler,
            String action,
            int settingNameResId) {
        mActivityHandler = activityHandler;
        mAction = action;
        mSettingNameResId = settingNameResId;
    }

    public void captureInitialState() {
        mCapturedModeOn = getCurrentSettingMode();
    }

    public synchronized void requestToSetMode(boolean modeOn) {
        String settingName = mActivityHandler.getString(mSettingNameResId);
        if (getCurrentSettingMode() == modeOn) {
            mActivityHandler.logInstructions(R.string.snsr_setting_mode_set, settingName, modeOn);
            return;
        }

        mActivityHandler.logInstructions(R.string.snsr_setting_mode_request, settingName, modeOn);
        mActivityHandler.logInstructions(R.string.snsr_on_complete_return);
        mActivityHandler.waitForUser();

        mActivityHandler.launchAndWaitForSubactivity(mAction);
        if (getCurrentSettingMode() != modeOn) {
            String message = mActivityHandler
                    .getString(R.string.snsr_setting_mode_not_set, settingName, modeOn);
            throw new IllegalStateException(message);
        }
    }

    public synchronized void requestToResetMode() {
        try {
            requestToSetMode(mCapturedModeOn);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error restoring state of action: " + mAction, e);
        }
    }

    private boolean getCurrentSettingMode() {
        return getSettingMode() != 0;
    }

    protected abstract int getSettingMode();
}
