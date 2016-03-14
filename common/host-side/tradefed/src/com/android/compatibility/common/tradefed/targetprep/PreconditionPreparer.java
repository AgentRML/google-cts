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

package com.android.compatibility.common.tradefed.targetprep;

import com.android.compatibility.common.tradefed.testtype.CompatibilityTest;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * An {@link ITargetPreparer} that performs checks and/or tasks to ensure the 
 * the device is ready to run the test suite.
 */
public abstract class PreconditionPreparer implements ITargetPreparer {

    @Option(name = CompatibilityTest.SKIP_PRECONDITIONS_OPTION,
            shortName = 'o',
            description = "Whether preconditions should be skipped")
    private boolean mSkipPreconditions = false;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        if (!mSkipPreconditions) {
            run(device, buildInfo);
        }
    }

    public abstract void run(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException;
}
