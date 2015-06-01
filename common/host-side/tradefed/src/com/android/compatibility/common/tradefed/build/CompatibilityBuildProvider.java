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
package com.android.compatibility.common.tradefed.build;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;

import java.io.File;

/**
 * A simple {@link IBuildProvider} that uses a pre-existing Compatibility install.
 */
public class CompatibilityBuildProvider implements IBuildProvider {

    private final String mSuiteBuildId;
    private final String mSuiteName;
    private final String mSuiteFullName;
    private final String mSuiteVersion;

    /**
     * Creates a new {@link CompatibilityBuildProvider} which reads Test Suite-specific information
     * from the jar's manifest file.
     */
    public CompatibilityBuildProvider() {
        Package pkg = Package.getPackage("com.android.compatibility.tradefed.command");
        mSuiteFullName = pkg.getSpecificationTitle();
        mSuiteName = pkg.getSpecificationVendor();
        mSuiteVersion = pkg.getSpecificationVersion();
        mSuiteBuildId = pkg.getImplementationVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() {
        return getCompatibilityBuild();
    }

    /**
     * Returns the {@link CompatibilityBuildInfo} for this test suite.
     *
     * Note: this is a convenience method for {@code (CompatibilityBuildInfo) getBuild()}
     */
    public CompatibilityBuildInfo getCompatibilityBuild() {
        String mRootDirPath = System.getProperty(String.format("%s_ROOT", mSuiteName));
        if (mRootDirPath == null || mRootDirPath.equals("")) {
            throw new IllegalArgumentException(
                    String.format("Missing install path property %s_ROOT", mSuiteName));
        }
        CompatibilityBuildInfo build = new CompatibilityBuildInfo(mSuiteBuildId, mSuiteName,
                mSuiteFullName, mSuiteVersion);
        build.setRootDir(new File(mRootDirPath));
        return build;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildNotTested(IBuildInfo info) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        // ignore
    }
}
