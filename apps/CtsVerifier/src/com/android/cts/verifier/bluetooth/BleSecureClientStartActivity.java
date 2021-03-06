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

package com.android.cts.verifier.bluetooth;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.android.cts.verifier.R;

public class BleSecureClientStartActivity extends BleClientTestBaseActivity {
    private Intent mIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setInfoResources(R.string.ble_client_test_name,
                R.string.ble_secure_client_test_info, -1);

        mIntent = new Intent(this, BleClientService.class);
        mIntent.setAction(BleClientService.BLE_CLIENT_ACTION_CLIENT_CONNECT_SECURE);

        startService(mIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService(mIntent);
    }

    @Override
    public boolean shouldRebootBluetoothAfterTest() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.M);
    }
}
