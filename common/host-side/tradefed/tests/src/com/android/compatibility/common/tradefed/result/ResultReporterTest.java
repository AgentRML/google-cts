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

import static com.android.compatibility.common.tradefed.build.CompatibilityBuildInfoTest.BASE_DIR_NAME;
import static com.android.compatibility.common.tradefed.build.CompatibilityBuildInfoTest.ROOT_DIR_NAME;
import static com.android.compatibility.common.tradefed.build.CompatibilityBuildInfoTest.TESTCASES;
import static com.android.compatibility.common.tradefed.build.CompatibilityBuildProviderTest.BUILD_ID;
import static com.android.compatibility.common.tradefed.build.CompatibilityBuildProviderTest.SUITE_FULL_NAME;
import static com.android.compatibility.common.tradefed.build.CompatibilityBuildProviderTest.SUITE_NAME;
import static com.android.compatibility.common.tradefed.build.CompatibilityBuildProviderTest.SUITE_PLAN;
import static com.android.compatibility.common.tradefed.build.CompatibilityBuildProviderTest.SUITE_VERSION;
import static com.android.compatibility.common.tradefed.result.ModuleResultTest.CLASS;
import static com.android.compatibility.common.tradefed.result.ModuleResultTest.ID;
import static com.android.compatibility.common.tradefed.result.ModuleResultTest.METHOD_1;
import static com.android.compatibility.common.tradefed.result.ModuleResultTest.METHOD_2;
import static com.android.compatibility.common.tradefed.result.ModuleResultTest.METHOD_3;
import static com.android.compatibility.common.tradefed.result.ModuleResultTest.STACK_TRACE;
import static com.android.compatibility.common.tradefed.result.ModuleResultTest.TEST_1;
import static com.android.compatibility.common.tradefed.result.ModuleResultTest.TEST_2;
import static com.android.compatibility.common.tradefed.result.ModuleResultTest.TEST_3;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildInfo;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.IModuleResult;
import com.android.compatibility.common.util.IResult;
import com.android.compatibility.common.util.TestStatus;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileFilter;
import java.util.HashMap;
import java.util.List;

public class ResultReporterTest extends TestCase {

    private static final String RESULT_DIR = "result123";
    private static final String[] FORMATTING_FILES = {
        "compatibility-result.css",
        "compatibility-result.xsd",
        "compatibility-result.xsl",
        "logo.png",
        "newrule-green.png"};

    private ResultReporter mReporter;
    private CompatibilityBuildInfo mBuild;

    private File mRoot = null;
    private File mBase = null;
    private File mTests = null;

    @Override
    public void setUp() throws Exception {
        mReporter = new ResultReporter();
        mRoot = FileUtil.createTempDir(ROOT_DIR_NAME);
        mBase = new File(mRoot, BASE_DIR_NAME);
        mBase.mkdirs();
        mTests = new File(mBase, TESTCASES);
        mTests.mkdirs();
        mBuild = new CompatibilityBuildInfo(
                BUILD_ID, SUITE_NAME, SUITE_FULL_NAME, SUITE_VERSION, SUITE_PLAN, mRoot);
    }

    @Override
    public void tearDown() throws Exception {
        mReporter = null;
        FileUtil.recursiveDelete(mRoot);
    }

    public void testSetup() throws Exception {
        mReporter.invocationStarted(mBuild);
        // Should have created a directory for the results
        File[] children = mBuild.getResultsDir().listFiles();
        assertTrue("Didn't create results dir", children.length == 1 && children[0].isDirectory());
        // Should have created a directory for the logs
        children = mBuild.getResultsDir().listFiles();
        assertTrue("Didn't create logs dir", children.length == 1 && children[0].isDirectory());
        mReporter.invocationEnded(10);
        // Should have created a zip file
        children = mBuild.getResultsDir().listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(".zip");
            }
        });
        assertTrue("Didn't create results zip",
                children.length == 1 && children[0].isFile() && children[0].length() > 0);
    }

    public void testResultReporting() throws Exception {
        mReporter.invocationStarted(mBuild);
        mReporter.testRunStarted(ID, 2);
        TestIdentifier test1 = new TestIdentifier(CLASS, METHOD_1);
        mReporter.testStarted(test1);
        mReporter.testEnded(test1, new HashMap<String, String>());
        TestIdentifier test2 = new TestIdentifier(CLASS, METHOD_2);
        mReporter.testStarted(test2);
        mReporter.testFailed(test2, STACK_TRACE);
        TestIdentifier test3 = new TestIdentifier(CLASS, METHOD_3);
        mReporter.testStarted(test3);
        mReporter.testFailed(test3, STACK_TRACE);
        mReporter.testRunEnded(10, new HashMap<String, String>());
        mReporter.invocationEnded(10);
        IInvocationResult result = mReporter.getResult();
        assertEquals("Expected 1 pass", 1, result.countResults(TestStatus.PASS));
        assertEquals("Expected 2 failures", 2, result.countResults(TestStatus.FAIL));
        List<IModuleResult> modules = result.getModules();
        assertEquals("Expected 1 module", 1, modules.size());
        IModuleResult module = modules.get(0);
        assertEquals("Incorrect ID", ID, module.getId());
        List<IResult> results = module.getResults();
        assertEquals("Expected 3 tests", 3, results.size());
        IResult result1 = module.getResult(TEST_1);
        assertNotNull(String.format("Expected result for %s", TEST_1), result1);
        assertEquals(String.format("Expected pass for %s", TEST_1), TestStatus.PASS,
                result1.getResultStatus());
        IResult result2 = module.getResult(TEST_2);
        assertNotNull(String.format("Expected result for %s", TEST_2), result2);
        assertEquals(String.format("Expected fail for %s", TEST_2), TestStatus.FAIL,
                result2.getResultStatus());
        IResult result3 = module.getResult(TEST_3);
        assertNotNull(String.format("Expected result for %s", TEST_3), result3);
        assertEquals(String.format("Expected fail for %s", TEST_3), TestStatus.FAIL,
                result3.getResultStatus());
    }

    public void testCopyFormattingFiles() throws Exception {
        File resultDir = new File(mBuild.getResultsDir(), RESULT_DIR);
        resultDir.mkdirs();
        ResultReporter.copyFormattingFiles(resultDir);
        for (String filename : FORMATTING_FILES) {
            File file = new File(resultDir, filename);
            assertTrue(String.format("%s (%s) was not created", filename, file.getAbsolutePath()),
                    file.exists() && file.isFile() && file.length() > 0);
        }
    }
}
