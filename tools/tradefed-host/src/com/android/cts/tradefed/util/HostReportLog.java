/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.cts.tradefed.util;

import com.android.compatibility.common.util.MetricsReportLog;
import com.android.cts.util.ReportLog;
import com.android.tradefed.build.IBuildInfo;

/**
 * ReportLog for host tests
 * Note that setTestInfo should be set before throwing report
 *
 * This class is deprecated, use {@link MetricsReportLog} instead.
 */
@Deprecated
public class HostReportLog extends ReportLog {
    /**
     * @param buildInfo the test build info.
     * @param abiName the name of the ABI on which the test was run.
     * @param classMethodName class name and method name of the test in class#method format.
     *        Note that ReportLog.getClassMethodNames() provide this.
     */
    public HostReportLog(IBuildInfo buildInfo, String abiName, String classMethodName) {
        super(new MetricsReportLog(buildInfo, abiName, classMethodName));
    }

    public void deliverReportToHost() {
        ((MetricsReportLog) mReportLog).submit();
    }
}