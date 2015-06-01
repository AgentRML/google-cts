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
package com.android.compatibility.common.tradefed.result;

import com.android.compatibility.common.util.AbiUtils;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data structure for a Compatibility test module result.
 */
public class ModuleResult implements IModuleResult {

    public static final String RESULT_KEY = "COMPATIBILITY_TEST_RESULT";

    private static final Pattern mLogPattern = Pattern.compile("(.*)\\+\\+\\+\\+(.*)");

    private String mDeviceSerial;
    private String mId;

    private Map<TestIdentifier, IResult> mResults = new HashMap<>();
    private Map<IResult, Map<String, String>> mMetrics = new HashMap<>();

    /**
     * Creates a {@link ModuleResult} for the given id, created with
     * {@link AbiUtils#createId(String, String)}
     */
    public ModuleResult(String id) {
        mId = id;
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceSerial(String deviceSerial) {
        mDeviceSerial = deviceSerial;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeviceSerial() {
        return mDeviceSerial;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return mId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return AbiUtils.parseTestName(mId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbi() {
        return AbiUtils.parseAbi(mId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IResult getOrCreateResult(TestIdentifier testId) {
        IResult result = mResults.get(testId);
        if (result == null) {
            result = new Result(String.format("%s#%s", testId.getClassName(), testId.getTestName()));
            mResults.put(testId, result);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IResult getResult(TestIdentifier testId) {
        return mResults.get(testId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IResult> getResults(TestStatus status) {
        List<IResult> results = new ArrayList<>();
        for (IResult result : mResults.values()) {
            if (result.getResultStatus() == status) {
                results.add(result);
            }
        }
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IResult> getResults() {
        return new ArrayList<>(mResults.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countResults(TestStatus status) {
        int total = 0;
        for (IResult result : mResults.values()) {
            if (result.getResultStatus() == status) {
                total++;
            }
        }
        return total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void populateMetrics(Map<String, String> metrics) {
        // Collect performance results
        for (Entry<IResult, Map<String, String>> entry : mMetrics.entrySet()) {
            IResult result = entry.getKey();
            // device test can have performance results in test metrics
            String perfResult = entry.getValue().get(RESULT_KEY);
            // host test should be checked in HostStore.
            if (perfResult == null) {
                perfResult = HostStore.removeResult(mDeviceSerial, getAbi(), result.getName());
            }
            if (perfResult != null) {
                // Compatibility result is passed in Summary++++Details format.
                // Extract Summary and Details, and pass them.
                Matcher m = mLogPattern.matcher(perfResult);
                if (m.find()) {
                    result.setResultStatus(TestStatus.PASS);
                    result.setSummary(m.group(1));
                    result.setDetails(m.group(2));
                } else {
                    CLog.e("Compatibility Result unrecognizable:" + perfResult);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reportTestFailure(TestIdentifier test, TestStatus status, String trace) {
        IResult result = getResult(test);
        result.setResultStatus(status);
        result.setStackTrace(trace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reportTestEnded(TestIdentifier test, Map<String, String> testMetrics) {
        IResult result = getResult(test);
        if (!result.getResultStatus().equals(TestStatus.FAIL)) {
            result.setResultStatus(TestStatus.PASS);
        }
        if (mMetrics.containsKey(test)) {
            CLog.e("Test metrics already contains key: " + test);
        }
        mMetrics.put(result, testMetrics);
        CLog.i("Test metrics:" + testMetrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(IModuleResult another) {
        return getId().compareTo(another.getId());
    }

}
