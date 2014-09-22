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
 * limitations under the License.
 */

package com.android.cts.verifier.sensors.base;

import com.android.cts.verifier.sensors.reporting.SensorTestDetails;

import android.hardware.cts.helpers.SensorTestStateNotSupportedException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * An Activity that provides a test execution engine for Sensor CtsVerifier tests. The tests are
 * able to interact with an operator.
 *
 * Sub-classes reuse its own class definition to 'load' tests at runtime through reflection.
 */
public abstract class SensorCtsVerifierTestActivity extends BaseSensorTestActivity {
    private volatile int mTestPassedCounter;
    private volatile int mTestSkippedCounter;
    private volatile int mTestFailedCounter;

    /**
     * Constructor for a CtsVerifier test executor. It executes tests defined in the same class.
     *
     * @param testClass The test class to execute, this is the same subclass implementing the
     *                  executor. It must be a subclass of {@link SensorCtsVerifierTestActivity}
     */
    protected SensorCtsVerifierTestActivity(
            Class<? extends SensorCtsVerifierTestActivity> testClass) {
        super(testClass);
    }

    /**
     * Constructor for a CtsVerifier test executor. It executes tests defined in the same class.
     *
     * @param testClass The test class to execute, this is the same subclass implementing the
     *                  executor. It must be a subclass of {@link SensorCtsVerifierTestActivity}
     * @param layoutId The Id of the layout to use for the test UI. The layout must contain all the
     *                 elements in the base layout {@code R.layout.snsr_semi_auto_test}.
     */
    protected SensorCtsVerifierTestActivity(
            Class<? extends SensorCtsVerifierTestActivity> testClass,
            int layoutId) {
        super(testClass, layoutId);
    }

    /**
     * Executes Semi-automated Sensor tests.
     * Execution is driven by this class, and allows discovery of tests using reflection.
     */
    @Override
    protected SensorTestDetails executeTests() {
        // TODO: use reporting to log individual test results
        StringBuilder overallTestResults = new StringBuilder();
        for (Method testMethod : findTestMethods()) {
            SensorTestDetails testDetails = executeTest(testMethod);
            getTestLogger().logTestDetails(testDetails);
            overallTestResults.append(testDetails.toString());
            overallTestResults.append("\n");
        }

        return new SensorTestDetails(
                getApplicationContext(),
                getTestClassName(),
                mTestPassedCounter,
                mTestSkippedCounter,
                mTestFailedCounter);
    }

    private List<Method> findTestMethods() {
        ArrayList<Method> testMethods = new ArrayList<Method>();
        for (Method method : mTestClass.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())
                    && method.getParameterTypes().length == 0
                    && method.getName().startsWith("test")
                    && method.getReturnType().equals(String.class)) {
                testMethods.add(method);
            }
        }
        return testMethods;
    }

    private SensorTestDetails executeTest(Method testMethod) {
        String testMethodName = testMethod.getName();
        String testName = String.format("%s#%s", getTestClassName(), testMethodName);
        String testSummary;
        SensorTestDetails.ResultCode testResultCode;

        try {
            getTestLogger().logTestStart(testMethod.getName());
            testSummary = (String) testMethod.invoke(this);
            testResultCode = SensorTestDetails.ResultCode.PASS;
            ++mTestPassedCounter;
        } catch (InvocationTargetException e) {
            // get the inner exception, because we use reflection APIs to execute the test
            Throwable cause = e.getCause();
            testSummary = cause.getMessage();
            if (cause instanceof SensorTestStateNotSupportedException) {
                testResultCode = SensorTestDetails.ResultCode.SKIPPED;
                ++mTestSkippedCounter;
            } else {
                testResultCode = SensorTestDetails.ResultCode.FAIL;
                ++mTestFailedCounter;
            }
        } catch (Throwable e) {
            testSummary = e.getMessage();
            testResultCode = SensorTestDetails.ResultCode.FAIL;
            ++mTestFailedCounter;
        }

        return new SensorTestDetails(testName, testResultCode, testSummary);
    }
}
