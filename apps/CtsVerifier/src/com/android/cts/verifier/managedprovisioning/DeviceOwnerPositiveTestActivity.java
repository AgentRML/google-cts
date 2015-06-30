/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.verifier.managedprovisioning;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.IntentDrivenTestActivity;
import com.android.cts.verifier.IntentDrivenTestActivity.ButtonInfo;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;
import com.android.cts.verifier.TestResult;

/**
 * Activity that lists all positive device owner tests. Requires the following adb command be issued
 * by the user prior to starting the tests:
 *
 * adb shell dpm set-device-owner
 *  'com.android.cts.verifier/com.android.cts.verifier.managedprovisioning.DeviceAdminTestReceiver'
 */
public class DeviceOwnerPositiveTestActivity extends PassFailButtons.TestListActivity {
    private static final String TAG = "DeviceOwnerPositiveTestActivity";

    static final String EXTRA_COMMAND = "extra-command";
    static final String EXTRA_TEST_ID = "extra-test-id";
    static final String COMMAND_SET_POLICY = "set-policy";
    static final String EXTRA_POLICY = "extra-policy";
    static final String EXTRA_PARAMETER_1 = "extra_parameter_1";
    static final String EXTRA_PARAMETER_2 = "extra_parameter_2";
    static final String COMMAND_ADD_USER_RESTRICTION = "add-user-restriction";
    static final String COMMAND_CLEAR_USER_RESTRICTION = "clear-user-restriction";
    static final String EXTRA_RESTRICTION = "extra-restriction";
    static final String COMMAND_REMOVE_DEVICE_OWNER = "remove-device-owner";
    static final String COMMAND_CHECK_DEVICE_OWNER = "check-device-owner";
    static final String COMMAND_SET_GLOBAL_SETTING = "set-global-setting";
    static final String EXTRA_SETTING = "extra-setting";

    private static final String CHECK_DEVICE_OWNER_TEST_ID = "CHECK_DEVICE_OWNER";
    private static final String REMOVE_DEVICE_OWNER_TEST_ID = "REMOVE_DEVICE_OWNER";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.positive_device_owner);
        setInfoResources(R.string.device_owner_positive_tests,
                R.string.device_owner_positive_tests_info, 0);
        setPassFailButtonClickListeners();

        final ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);
        adapter.add(TestListItem.newCategory(this, R.string.device_owner_positive_category));

        addTestsToAdapter(adapter);

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });

        setTestListAdapter(adapter);

        View setDeviceOwnerButton = findViewById(R.id.set_device_owner_button);
        setDeviceOwnerButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(
                        DeviceOwnerPositiveTestActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .setTitle(R.string.set_device_owner_dialog_title)
                        .setMessage(R.string.set_device_owner_dialog_text)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });

    }

    @Override
    public void finish() {
        // Pass and fail buttons are known to call finish() when clicked, and this is when we want
        // to remove the device owner.
        startActivity(createRemoveDeviceOwnerIntent());
        super.finish();
    }

    /**
     * Enable Pass Button when all tests passed.
     */
    private void updatePassButton() {
        getPassButton().setEnabled(mAdapter.allTestsPassed());
    }

    private void addTestsToAdapter(final ArrayTestListAdapter adapter) {
        adapter.add(createTestItem(this, CHECK_DEVICE_OWNER_TEST_ID,
                R.string.device_owner_check_device_owner_test,
                new Intent(this, CommandReceiver.class)
                        .putExtra(EXTRA_COMMAND, COMMAND_CHECK_DEVICE_OWNER)
                        ));
        adapter.add(createInteractiveTestItem(this, REMOVE_DEVICE_OWNER_TEST_ID,
                R.string.device_owner_remove_device_owner_test,
                R.string.device_owner_remove_device_owner_test_info,
                new ButtonInfo(
                        R.string.remove_device_owner_button,
                        createRemoveDeviceOwnerIntent())));
    }

    static TestListItem createInteractiveTestItem(Activity activity, String id, int titleRes,
            int infoRes, ButtonInfo buttonInfo) {
        return TestListItem.newTest(activity, titleRes,
                id, new Intent(activity, IntentDrivenTestActivity.class)
                .putExtra(IntentDrivenTestActivity.EXTRA_ID, id)
                .putExtra(IntentDrivenTestActivity.EXTRA_TITLE, titleRes)
                .putExtra(IntentDrivenTestActivity.EXTRA_INFO, infoRes)
                .putExtra(IntentDrivenTestActivity.EXTRA_BUTTONS, new ButtonInfo[] { buttonInfo }),
                null);
    }

    static TestListItem createTestItem(Activity activity, String id, int titleRes,
            Intent intent) {
        return TestListItem.newTest(activity, titleRes, id, intent.putExtra(EXTRA_TEST_ID, id),
                null);
    }

    private Intent createRemoveDeviceOwnerIntent() {
        return new Intent(this, CommandReceiver.class)
                .putExtra(EXTRA_COMMAND, COMMAND_REMOVE_DEVICE_OWNER);
    }

    public static class CommandReceiver extends Activity {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Intent intent = getIntent();
            String command = intent.getStringExtra(EXTRA_COMMAND);
            try {
                DevicePolicyManager dpm = (DevicePolicyManager)
                        getSystemService(Context.DEVICE_POLICY_SERVICE);
                Log.i(TAG, "Command: " + command);

                if (COMMAND_ADD_USER_RESTRICTION.equals(command)) {
                    String restrictionKey = intent.getStringExtra(EXTRA_RESTRICTION);
                    dpm.addUserRestriction(DeviceAdminTestReceiver.getReceiverComponentName(),
                            restrictionKey);
                    Log.i(TAG, "Added user restriction " + restrictionKey);
                } else if (COMMAND_CLEAR_USER_RESTRICTION.equals(command)) {
                    String restrictionKey = intent.getStringExtra(EXTRA_RESTRICTION);
                    dpm.clearUserRestriction(DeviceAdminTestReceiver.getReceiverComponentName(),
                            restrictionKey);
                    Log.i(TAG, "Cleared user restriction " + restrictionKey);
                } else if (COMMAND_REMOVE_DEVICE_OWNER.equals(command)) {
                    dpm.clearDeviceOwnerApp(DeviceAdminTestReceiver.getReceiverComponentName()
                            .getPackageName());
                } else if (COMMAND_SET_GLOBAL_SETTING.equals(command)) {
                    final String setting = intent.getStringExtra(EXTRA_SETTING);
                    final String value = intent.getStringExtra(EXTRA_PARAMETER_1);
                    dpm.setGlobalSetting(DeviceAdminTestReceiver.getReceiverComponentName(),
                            setting, value);
                } else if (COMMAND_CHECK_DEVICE_OWNER.equals(command)) {
                    if (dpm.isDeviceOwnerApp(getPackageName())) {
                        TestResult.setPassedResult(this, intent.getStringExtra(EXTRA_TEST_ID),
                                null, null);
                    } else {
                        TestResult.setFailedResult(this, intent.getStringExtra(EXTRA_TEST_ID),
                                getString(R.string.device_owner_incorrect_device_owner), null);
                    }
                } else {
                    Log.e(TAG, "Invalid command: " + command);
                }
            } catch (Exception e) {
                Log.e(TAG, "Command " + command + " failed with exception " + e);
            } finally {
                // No matter what happened, don't let the activity run
                finish();
            }
        }
    }
}

