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

import com.android.compatibility.common.tradefed.build.BuildHelper;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.targetprep.PushFilePreparer;

import java.io.File;

/**
 * Pushes specified testing artifacts from Compatibility repository.
 */
@OptionClass(alias="file-pusher")
public class FilePusher extends PushFilePreparer {

    private BuildHelper mBuildHelper;

    protected BuildHelper getBuildHelper(IBuildInfo buildInfo) {
        if (mBuildHelper == null) {
            mBuildHelper = new BuildHelper((CompatibilityBuildInfo) buildInfo);
        }
        return mBuildHelper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File resolveRelativeFilePath(IBuildInfo buildInfo, String fileName) {
        return new File(getBuildHelper(buildInfo).getTestsDir(), fileName);
    }
}
