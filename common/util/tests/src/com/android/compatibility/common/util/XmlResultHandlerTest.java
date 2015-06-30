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
 * limitations under the License
 */
package com.android.compatibility.common.util;

import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for {@link XmlResultHandler}
 */
public class XmlResultHandlerTest extends TestCase {

    private static final String SUITE_NAME = "CTS";
    private static final String SUITE_VERSION = "5.0";
    private static final String SUITE_PLAN = "cts";
    private static final String REPORT_VERSION = "5.0";
    private static final String OS_NAME = System.getProperty("os.name");
    private static final String OS_VERSION = System.getProperty("os.version");
    private static final String OS_ARCH = System.getProperty("os.arch");
    private static final String JAVA_VENDOR = System.getProperty("java.vendor");
    private static final String JAVA_VERSION = System.getProperty("java.version");
    private static final String NAME_A = "ModuleA";
    private static final String NAME_B = "ModuleB";
    private static final String ABI = "mips64";
    private static final String ID_A = AbiUtils.createId(ABI, NAME_A);
    private static final String ID_B = AbiUtils.createId(ABI, NAME_B);
    private static final String DEVICE_A = "device123";
    private static final String DEVICE_B = "device456";
    private static final String CLASS_A = "android.test.Foor";
    private static final String CLASS_B = "android.test.Bar";
    private static final String METHOD_1 = "testBlah1";
    private static final String METHOD_2 = "testBlah2";
    private static final String METHOD_3 = "testBlah3";
    private static final String METHOD_4 = "testBlah4";
    private static final String TEST_1 = String.format("%s#%s", CLASS_A, METHOD_1);
    private static final String TEST_2 = String.format("%s#%s", CLASS_A, METHOD_2);
    private static final String TEST_3 = String.format("%s#%s", CLASS_B, METHOD_3);
    private static final String TEST_4 = String.format("%s#%s", CLASS_B, METHOD_4);
    private static final String SUMMARY_SOURCE = String.format("%s:20", TEST_4);
    private static final String DETAILS_SOURCE = String.format("%s:18", TEST_4);
    private static final String SUMMARY_MESSAGE = "Headline";
    private static final double SUMMARY_VALUE = 9001;
    private static final String DETAILS_MESSAGE = "Deats";
    private static final double DETAILS_VALUE_1 = 14;
    private static final double DETAILS_VALUE_2 = 18;
    private static final double DETAILS_VALUE_3 = 17;
    private static final String MESSAGE = "Something small is not alright";
    private static final String STACK_TRACE = "Something small is not alright\n " +
            "at four.big.insects.Marley.sing(Marley.java:10)";
    private static final String START = "2015-05-14 00:00:01";
    private static final long START_MS = 1431586801000L;
    private static final String END = "2015-05-14 23:59:59";
    private static final long END_MS = 1431673199000L;
    private static final String JOIN = "%s%s";
    private static final String XML_BASE =
            "<?xml version='1.0' encoding='UTF-8' standalone='no' ?>" +
            "<?xml-stylesheet type=\"text/xsl\" href=\"compatibility-result.xsl\"?>\n" +
            "<Result start=\"%s\" end=\"%s\" suite-name=\"%s\" suite-version=\"%s\" " +
            "suite-plan=\"%s\" report-version=\"%s\" host-name=\"%s\" os-name=\"%s\" " +
            "os-version=\"%s\" os-arch=\"%s\" java-vendor=\"%s\" java-version=\"%s\">\n" +
            "%s%s" +
            "</Result>";
    private static final String XML_SUMMARY =
            "  <Summary pass=\"%d\" failed=\"%d\" not-executed=\"%d\" />\n";
    private static final String XML_MODULE =
            "  <Module name=\"%s\" abi=\"%s\" device=\"%s\">\n" +
            "%s" +
            "  </Module>\n";
    private static final String XML_TEST_PASS =
            "    <Test result=\"pass\" name=\"%s\" start=\"%s\" end=\"%s\" />\n";
    private static final String XML_TEST_NOT_EXECUTED =
            "    <Test result=\"not-executed\" name=\"%s\" start=\"%s\" end=\"%s\" />\n";
    private static final String XML_TEST_FAIL =
            "    <Test result=\"fail\" name=\"%s\" start=\"%s\" end=\"%s\" >\n" +
            "      <Failure message=\"%s\">\n" +
            "        <StackTrace>%s</StackTrace>\n" +
            "      </Failure>\n" +
            "    </Test>\n";
    private static final String XML_TEST_RESULT =
            "    <Test result=\"pass\" name=\"%s\" start=\"%s\" end=\"%s\">\n" +
            "      <Summary>\n" +
            "        <Metric source=\"%s\" message=\"%s\" score-type=\"%s\" score-unit=\"%s\">\n" +
            "           <Value>%s</Value>\n" +
            "        </Metric>\n" +
            "      </Summary>\n" +
            "      <Detail>\n" +
            "        <Metric source=\"%s\" message=\"%s\" score-type=\"%s\" score-unit=\"%s\">\n" +
            "          <Value>%s</Value>\n" +
            "          <Value>%s</Value>\n" +
            "          <Value>%s</Value>\n" +
            "        </Metric>\n" +
            "      </Detail>\n" +
            "    </Test>\n";
    private File resultsDir = null;
    private File resultDir = null;

    @Override
    public void setUp() throws Exception {
        resultsDir = FileUtil.createTempDir("results");
        resultDir = FileUtil.createTempDir("12345", resultsDir);
    }

    @Override
    public void tearDown() throws Exception {
        if (resultsDir != null) {
            FileUtil.recursiveDelete(resultsDir);
        }
    }

    public void testSerialization() throws Exception {
        IInvocationResult result = new InvocationResult(resultDir);
        result.setStartTime(START_MS);
        result.setTestPlan(SUITE_PLAN);
        IModuleResult moduleA = result.getOrCreateModule(ID_A);
        moduleA.setDeviceSerial(DEVICE_A);
        IResult moduleATest1 = moduleA.getOrCreateResult(TEST_1);
        moduleATest1.setStartTime(START_MS);
        moduleATest1.setResultStatus(TestStatus.PASS);
        moduleATest1.setEndTime(END_MS);
        IResult moduleATest2 = moduleA.getOrCreateResult(TEST_2);
        moduleATest2.setStartTime(START_MS);
        moduleATest2.setResultStatus(TestStatus.NOT_EXECUTED);
        moduleATest2.setEndTime(END_MS);

        IModuleResult moduleB = result.getOrCreateModule(ID_B);
        moduleB.setDeviceSerial(DEVICE_B);
        IResult moduleBTest3 = moduleB.getOrCreateResult(TEST_3);
        moduleBTest3.setStartTime(START_MS);
        moduleBTest3.setResultStatus(TestStatus.FAIL);
        moduleBTest3.setMessage(MESSAGE);
        moduleBTest3.setStackTrace(STACK_TRACE);
        moduleBTest3.setEndTime(END_MS);
        IResult moduleBTest4 = moduleB.getOrCreateResult(TEST_4);
        moduleBTest4.setStartTime(START_MS);
        moduleBTest4.setResultStatus(TestStatus.PASS);
        ReportLog report = new ReportLog();
        ReportLog.Metric summary = new ReportLog.Metric(SUMMARY_SOURCE, SUMMARY_MESSAGE,
                SUMMARY_VALUE, ResultType.HIGHER_BETTER, ResultUnit.SCORE);
        report.setSummary(summary);
        ReportLog.Metric details = new ReportLog.Metric(DETAILS_SOURCE, DETAILS_MESSAGE,
                new double[] {DETAILS_VALUE_1, DETAILS_VALUE_2, DETAILS_VALUE_3},
                ResultType.LOWER_BETTER, ResultUnit.MS);
        report.addMetric(details);
        moduleBTest4.setReportLog(report);
        moduleBTest4.setEndTime(END_MS);

        // Serialize to file
        XmlResultHandler.writeResults(SUITE_NAME, SUITE_VERSION, SUITE_PLAN, result, resultDir,
                START_MS, END_MS);

        // Parse the results and assert correctness
        checkResult(XmlResultHandler.getResults(resultsDir), resultDir);
    }

    public void testParsing() throws Exception {
        File resultsDir = null;
        FileWriter writer = null;
        try {
            resultsDir = FileUtil.createTempDir("results");
            File resultDir = FileUtil.createTempDir("12345", resultsDir);
            // Create the result file
            File resultFile = new File(resultDir, XmlResultHandler.TEST_RESULT_FILE_NAME);
            writer = new FileWriter(resultFile);
            String summary = String.format(XML_SUMMARY, 2, 1, 1);
            String moduleATest1 = String.format(XML_TEST_PASS, TEST_1, START, END);
            String moduleATest2 = String.format(XML_TEST_NOT_EXECUTED, TEST_2, START, END);
            String moduleATests = String.format(JOIN, moduleATest1, moduleATest2);
            String moduleA = String.format(XML_MODULE, NAME_A, ABI, DEVICE_A, moduleATests);
            String moduleBTest3 = String.format(XML_TEST_FAIL, TEST_3, START, END, MESSAGE, STACK_TRACE);
            String moduleBTest4 = String.format(XML_TEST_RESULT, TEST_4, START, END,
                    SUMMARY_SOURCE, SUMMARY_MESSAGE, ResultType.HIGHER_BETTER.toReportString(),
                    ResultUnit.SCORE.toReportString(), Double.toString(SUMMARY_VALUE),
                    DETAILS_SOURCE, DETAILS_MESSAGE, ResultType.LOWER_BETTER.toReportString(),
                    ResultUnit.MS.toReportString(), Double.toString(DETAILS_VALUE_1),
                    Double.toString(DETAILS_VALUE_2), Double.toString(DETAILS_VALUE_3));
            String moduleBTests = String.format(JOIN, moduleBTest3, moduleBTest4);
            String moduleB = String.format(XML_MODULE, NAME_B, ABI, DEVICE_B, moduleBTests);
            String modules = String.format(JOIN, moduleA, moduleB);
            String hostName = "";
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException ignored) {}
            String output = String.format(XML_BASE, START, END, SUITE_NAME, SUITE_VERSION,
                    SUITE_PLAN, REPORT_VERSION, hostName, OS_NAME,  OS_VERSION, OS_ARCH,
                    JAVA_VENDOR, JAVA_VERSION, summary, modules);
            writer.write(output);
            writer.flush();

            // Parse the results and assert correctness
            checkResult(XmlResultHandler.getResults(resultsDir), resultDir);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
        
    private void checkResult(List<IInvocationResult> results, File resultDir) throws Exception {
        assertEquals("Expected 1 result", 1, results.size());
        IInvocationResult result = results.get(0);
        assertEquals("Expected 2 passes", 2, result.countResults(TestStatus.PASS));
        assertEquals("Expected 1 failure", 1, result.countResults(TestStatus.FAIL));
        assertEquals("Expected 1 not executed", 1, result.countResults(TestStatus.NOT_EXECUTED));
        Set<String> serials = result.getDeviceSerials();
        assertEquals("Expected 2 devices", 2, serials.size());
        assertTrue("Incorrect devices", serials.contains(DEVICE_A) && serials.contains(DEVICE_B));
        assertEquals("Incorrect result dir", resultDir.getAbsolutePath(),
                result.getResultDir().getAbsolutePath());
        assertEquals("Incorrect start time", START_MS, result.getStartTime());
        assertEquals("Incorrect test plan", SUITE_PLAN, result.getTestPlan());

        List<IModuleResult> modules = result.getModules();
        assertEquals("Expected 2 modules", 2, modules.size());

        IModuleResult moduleA = modules.get(0);
        assertEquals("Expected 1 pass", 1, moduleA.countResults(TestStatus.PASS));
        assertEquals("Expected 0 failures", 0, moduleA.countResults(TestStatus.FAIL));
        assertEquals("Expected 1 not executed", 1, moduleA.countResults(TestStatus.NOT_EXECUTED));
        assertEquals("Incorrect ABI", ABI, moduleA.getAbi());
        assertEquals("Incorrect name", NAME_A, moduleA.getName());
        assertEquals("Incorrect ID", ID_A, moduleA.getId());
        assertEquals("Incorrect device", DEVICE_A, moduleA.getDeviceSerial());
        List<IResult> moduleAResults = moduleA.getResults();
        assertEquals("Expected 2 results", 2, moduleAResults.size());
        IResult moduleATest1 = moduleAResults.get(0);
        assertEquals("Incorrect name", TEST_1, moduleATest1.getName());
        assertEquals("Incorrect start time", START_MS, moduleATest1.getStartTime());
        assertEquals("Incorrect end time", END_MS, moduleATest1.getEndTime());
        assertEquals("Incorrect result", TestStatus.PASS, moduleATest1.getResultStatus());
        assertNull("Unexpected log", moduleATest1.getLog());
        assertNull("Unexpected message", moduleATest1.getMessage());
        assertNull("Unexpected stack trace", moduleATest1.getStackTrace());
        assertNull("Unexpected report", moduleATest1.getReportLog());
        IResult moduleATest2 = moduleAResults.get(1);
        assertEquals("Incorrect name", TEST_2, moduleATest2.getName());
        assertEquals("Incorrect start time", START_MS, moduleATest2.getStartTime());
        assertEquals("Incorrect end time", END_MS, moduleATest2.getEndTime());
        assertEquals("Incorrect result", TestStatus.NOT_EXECUTED, moduleATest2.getResultStatus());
        assertNull("Unexpected log", moduleATest2.getLog());
        assertNull("Unexpected message", moduleATest2.getMessage());
        assertNull("Unexpected stack trace", moduleATest2.getStackTrace());
        assertNull("Unexpected report", moduleATest2.getReportLog());

        IModuleResult moduleB = modules.get(1);
        assertEquals("Expected 1 pass", 1, moduleB.countResults(TestStatus.PASS));
        assertEquals("Expected 1 failure", 1, moduleB.countResults(TestStatus.FAIL));
        assertEquals("Expected 0 not executed", 0, moduleB.countResults(TestStatus.NOT_EXECUTED));
        assertEquals("Incorrect ABI", ABI, moduleB.getAbi());
        assertEquals("Incorrect name", NAME_B, moduleB.getName());
        assertEquals("Incorrect ID", ID_B, moduleB.getId());
        assertEquals("Incorrect device", DEVICE_B, moduleB.getDeviceSerial());
        List<IResult> moduleBResults = moduleB.getResults();
        assertEquals("Expected 2 results", 2, moduleBResults.size());
        IResult moduleBTest3 = moduleBResults.get(0);
        assertEquals("Incorrect name", TEST_3, moduleBTest3.getName());
        assertEquals("Incorrect start time", START_MS, moduleBTest3.getStartTime());
        assertEquals("Incorrect end time", END_MS, moduleBTest3.getEndTime());
        assertEquals("Incorrect result", TestStatus.FAIL, moduleBTest3.getResultStatus());
        assertNull("Unexpected log", moduleBTest3.getLog());
        assertEquals("Incorrect message", MESSAGE, moduleBTest3.getMessage());
        assertEquals("Incorrect stack trace", STACK_TRACE, moduleBTest3.getStackTrace());
        assertNull("Unexpected report", moduleBTest3.getReportLog());
        IResult moduleBTest4 = moduleBResults.get(1);
        assertEquals("Incorrect name", TEST_4, moduleBTest4.getName());
        assertEquals("Incorrect start time", START_MS, moduleBTest4.getStartTime());
        assertEquals("Incorrect end time", END_MS, moduleBTest4.getEndTime());
        assertEquals("Incorrect result", TestStatus.PASS, moduleBTest4.getResultStatus());
        assertNull("Unexpected log", moduleBTest4.getLog());
        assertNull("Unexpected message", moduleBTest4.getMessage());
        assertNull("Unexpected stack trace", moduleBTest4.getStackTrace());
        ReportLog report = moduleBTest4.getReportLog();
        assertNotNull("Expected report", report);
        ReportLog.Metric summary = report.getSummary();
        assertNotNull("Expected report summary", summary);
        assertEquals("Incorrect source", SUMMARY_SOURCE, summary.getSource());
        assertEquals("Incorrect message", SUMMARY_MESSAGE, summary.getMessage());
        assertEquals("Incorrect type", ResultType.HIGHER_BETTER, summary.getType());
        assertEquals("Incorrect unit", ResultUnit.SCORE, summary.getUnit());
        assertTrue("Incorrect values", Arrays.equals(new double[] { SUMMARY_VALUE },
                summary.getValues()));
        List<ReportLog.Metric> details = report.getDetailedMetrics();
        assertEquals("Expected 1 report detail", 1, details.size());
        ReportLog.Metric detail = details.get(0);
        assertEquals("Incorrect source", DETAILS_SOURCE, detail.getSource());
        assertEquals("Incorrect message", DETAILS_MESSAGE, detail.getMessage());
        assertEquals("Incorrect type", ResultType.LOWER_BETTER, detail.getType());
        assertEquals("Incorrect unit", ResultUnit.MS, detail.getUnit());
        assertTrue("Incorrect values", Arrays.equals(new double[] { DETAILS_VALUE_1,
                DETAILS_VALUE_2, DETAILS_VALUE_3 }, detail.getValues()));
    }
}
