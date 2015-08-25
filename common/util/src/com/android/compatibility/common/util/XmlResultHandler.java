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
package com.android.compatibility.common.util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map.Entry;
import java.util.List;

/**
 * Handles conversion of results to/from XML.
 */
public class XmlResultHandler {

    private static final String ENCODING = "UTF-8";
    private static final String TYPE = "org.kxml2.io.KXmlParser,org.kxml2.io.KXmlSerializer";
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final String NS = null;
    private static final String RESULT_FILE_VERSION = "5.0";
    /* package */ static final String TEST_RESULT_FILE_NAME = "test-result.xml";

    // XML constants
    private static final String ABI_ATTR = "abi";
    private static final String BUGREPORT_TAG = "BugReport";
    private static final String CASE_TAG = "TestCase";
    private static final String DEVICE_ATTR = "device";
    private static final String DEVICE_INFO_TAG = "DeviceInfo";
    private static final String END_TIME_ATTR = "end";
    private static final String FAILED_ATTR = "failed";
    private static final String FAILURE_TAG = "Failure";
    private static final String HOST_NAME_ATTR = "host-name";
    private static final String JAVA_VENDOR_ATTR = "java-vendor";
    private static final String JAVA_VERSION_ATTR = "java-version";
    private static final String LOGCAT_TAG = "Logcat";
    private static final String MESSAGE_ATTR = "message";
    private static final String MODULE_TAG = "Module";
    private static final String NAME_ATTR = "name";
    private static final String NOT_EXECUTED_ATTR = "not-executed";
    private static final String OS_ARCH_ATTR = "os-arch";
    private static final String OS_NAME_ATTR = "os-name";
    private static final String OS_VERSION_ATTR = "os-version";
    private static final String PASS_ATTR = "pass";
    private static final String REPORT_VERSION_ATTR = "report-version";
    private static final String RESULT_ATTR = "result";
    private static final String RESULT_TAG = "Result";
    private static final String SCREENSHOT_TAG = "Screenshot";
    private static final String STACK_TAG = "StackTrace";
    private static final String START_TIME_ATTR = "start";
    private static final String SUITE_NAME_ATTR = "suite-name";
    private static final String SUITE_PLAN_ATTR = "suite-plan";
    private static final String SUITE_VERSION_ATTR = "suite-version";
    private static final String SUMMARY_TAG = "Summary";
    private static final String TEST_TAG = "Test";

    /**
     * @param resultsDir
     */
    public static List<IInvocationResult> getResults(File resultsDir) {
        List<IInvocationResult> results = new ArrayList<>();
        File[] files = resultsDir.listFiles();
        if (files == null || files.length == 0) {
            // No results, just return the empty list
            return results;
        }
        for (File resultDir : files) {
            if (!resultDir.isDirectory()) {
                continue;
            }
            try {
                IInvocationResult invocation = new InvocationResult(resultDir);
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser parser = factory.newPullParser();
                parser.setInput(new FileReader(new File(resultDir, TEST_RESULT_FILE_NAME)));
                parser.nextTag();
                parser.require(XmlPullParser.START_TAG, NS, RESULT_TAG);
                invocation.setStartTime(parseTimeStamp(
                        parser.getAttributeValue(NS, START_TIME_ATTR)));
                invocation.setTestPlan(parser.getAttributeValue(NS, SUITE_PLAN_ATTR));
                parser.nextTag();
                parser.require(XmlPullParser.START_TAG, NS, DEVICE_INFO_TAG);
                // TODO(stuartscott): may want to reload these incase the retry was done with
                // --skip-device-info flag
                parser.nextTag();
                parser.require(XmlPullParser.END_TAG, NS, DEVICE_INFO_TAG);
                parser.nextTag();
                parser.require(XmlPullParser.START_TAG, NS, SUMMARY_TAG);
                parser.nextTag();
                parser.require(XmlPullParser.END_TAG, NS, SUMMARY_TAG);
                while (parser.nextTag() == XmlPullParser.START_TAG) {
                    parser.require(XmlPullParser.START_TAG, NS, MODULE_TAG);
                    String name = parser.getAttributeValue(NS, NAME_ATTR);
                    String abi = parser.getAttributeValue(NS, ABI_ATTR);
                    String id = AbiUtils.createId(abi, name);
                    IModuleResult module = invocation.getOrCreateModule(id);
                    module.setDeviceSerial(parser.getAttributeValue(NS, DEVICE_ATTR));
                    while (parser.nextTag() == XmlPullParser.START_TAG) {
                        parser.require(XmlPullParser.START_TAG, NS, CASE_TAG);
                        String caseName = parser.getAttributeValue(NS, NAME_ATTR);
                        ICaseResult testCase = module.getOrCreateResult(caseName);
                        while (parser.nextTag() == XmlPullParser.START_TAG) {
                            parser.require(XmlPullParser.START_TAG, NS, TEST_TAG);
                            String testName = parser.getAttributeValue(NS, NAME_ATTR);
                            ITestResult test = testCase.getOrCreateResult(testName);
                            String result = parser.getAttributeValue(NS, RESULT_ATTR);
                            test.setResultStatus(TestStatus.getStatus(result));
                            test.setStartTime(parseTimeStamp(
                                    parser.getAttributeValue(NS, START_TIME_ATTR)));
                            test.setEndTime(parseTimeStamp(
                                    parser.getAttributeValue(NS, END_TIME_ATTR)));
                            if (parser.nextTag() == XmlPullParser.START_TAG) {
                                if (parser.getName().equals(FAILURE_TAG)) {
                                    test.setMessage(parser.getAttributeValue(NS, MESSAGE_ATTR));
                                    if (parser.nextTag() == XmlPullParser.START_TAG) {
                                        parser.require(XmlPullParser.START_TAG, NS, STACK_TAG);
                                        test.setStackTrace(parser.nextText());
                                        parser.require(XmlPullParser.END_TAG, NS, STACK_TAG);
                                        parser.nextTag();
                                    }
                                    parser.require(XmlPullParser.END_TAG, NS, FAILURE_TAG);
                                    parser.nextTag();
                                } else if (parser.getName().equals(BUGREPORT_TAG)) {
                                    test.setBugReport(parser.nextText());
                                    parser.nextTag();
                                } else if (parser.getName().equals(LOGCAT_TAG)) {
                                    test.setLog(parser.nextText());
                                    parser.nextTag();
                                } else if (parser.getName().equals(SCREENSHOT_TAG)) {
                                    test.setScreenshot(parser.nextText());
                                    parser.nextTag();
                                } else {
                                    test.setReportLog(ReportLog.parse(parser));
                                    parser.nextTag();
                                }
                            }
                            parser.require(XmlPullParser.END_TAG, NS, TEST_TAG);
                        }
                        parser.require(XmlPullParser.END_TAG, NS, CASE_TAG);
                    }
                    parser.require(XmlPullParser.END_TAG, NS, MODULE_TAG);
                }
                parser.require(XmlPullParser.END_TAG, NS, RESULT_TAG);
                results.add(invocation);
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return results;
    }

    /**
     * @param result
     * @param resultDir
     * @param startTime
     * @return The result file created.
     * @throws IOException
     * @throws XmlPullParserException
     */
    public static File writeResults(String suiteName, String suiteVersion, String suitePlan,
            IInvocationResult result, File resultDir, long startTime, long endTime)
                    throws IOException, XmlPullParserException {
        int passed = result.countResults(TestStatus.PASS);
        int failed = result.countResults(TestStatus.FAIL);
        int notExecuted = result.countResults(TestStatus.NOT_EXECUTED);
        File resultFile = new File(resultDir, TEST_RESULT_FILE_NAME);
        OutputStream stream = new FileOutputStream(resultFile);
        XmlSerializer serializer = XmlPullParserFactory.newInstance(TYPE, null).newSerializer();
        serializer.setOutput(stream, ENCODING);
        serializer.startDocument(ENCODING, false);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serializer.processingInstruction(
                "xml-stylesheet type=\"text/xsl\" href=\"compatibility-result.xsl\"");
        serializer.startTag(NS, RESULT_TAG);
        serializer.attribute(NS, START_TIME_ATTR, formatTimeStamp(startTime));
        serializer.attribute(NS, END_TIME_ATTR, formatTimeStamp(endTime));
        serializer.attribute(NS, SUITE_NAME_ATTR, suiteName);
        serializer.attribute(NS, SUITE_VERSION_ATTR, suiteVersion);
        serializer.attribute(NS, SUITE_PLAN_ATTR, suitePlan);
        serializer.attribute(NS, REPORT_VERSION_ATTR, RESULT_FILE_VERSION);

        String hostName = "";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {}
        serializer.attribute(NS, HOST_NAME_ATTR, hostName);
        serializer.attribute(NS, OS_NAME_ATTR, System.getProperty("os.name"));
        serializer.attribute(NS, OS_VERSION_ATTR, System.getProperty("os.version"));
        serializer.attribute(NS, OS_ARCH_ATTR, System.getProperty("os.arch"));
        serializer.attribute(NS, JAVA_VENDOR_ATTR, System.getProperty("java.vendor"));
        serializer.attribute(NS, JAVA_VERSION_ATTR, System.getProperty("java.version"));

        // Device Info
        serializer.startTag(NS, DEVICE_INFO_TAG);
        for (Entry<String, String> entry : result.getDeviceInfo().entrySet()) {
            serializer.attribute(NS, entry.getKey(), entry.getValue());
        }
        serializer.endTag(NS, DEVICE_INFO_TAG);

        // Summary
        serializer.startTag(NS, SUMMARY_TAG);
        serializer.attribute(NS, PASS_ATTR, Integer.toString(passed));
        serializer.attribute(NS, FAILED_ATTR, Integer.toString(failed));
        serializer.attribute(NS, NOT_EXECUTED_ATTR, Integer.toString(notExecuted));
        serializer.endTag(NS, SUMMARY_TAG);

        // Results
        for (IModuleResult module : result.getModules()) {
            serializer.startTag(NS, MODULE_TAG);
            serializer.attribute(NS, NAME_ATTR, module.getName());
            serializer.attribute(NS, ABI_ATTR, module.getAbi());
            serializer.attribute(NS, DEVICE_ATTR, module.getDeviceSerial());
            for (ICaseResult cr : module.getResults()) {
                serializer.startTag(NS, CASE_TAG);
                serializer.attribute(NS, NAME_ATTR, cr.getName());
                for (ITestResult r : cr.getResults()) {
                    serializer.startTag(NS, TEST_TAG);
                    serializer.attribute(NS, RESULT_ATTR, r.getResultStatus().getValue());
                    serializer.attribute(NS, NAME_ATTR, r.getName());
                    serializer.attribute(NS, START_TIME_ATTR, formatTimeStamp(r.getStartTime()));
                    serializer.attribute(NS, END_TIME_ATTR, formatTimeStamp(r.getEndTime()));
                    String message = r.getMessage();
                    if (message != null) {
                        serializer.startTag(NS, FAILURE_TAG);
                        serializer.attribute(NS, MESSAGE_ATTR, message);
                        String stackTrace = r.getStackTrace();
                        if (stackTrace != null) {
                            serializer.startTag(NS, STACK_TAG);
                            serializer.text(stackTrace);
                            serializer.endTag(NS, STACK_TAG);
                        }
                        serializer.endTag(NS, FAILURE_TAG);
                    }
                    String bugreport = r.getBugReport();
                    if (bugreport != null) {
                        serializer.startTag(NS, BUGREPORT_TAG);
                        serializer.text(bugreport);
                        serializer.endTag(NS, BUGREPORT_TAG);
                    }
                    String logcat = r.getLog();
                    if (logcat != null) {
                        serializer.startTag(NS, LOGCAT_TAG);
                        serializer.text(logcat);
                        serializer.endTag(NS, LOGCAT_TAG);
                    }
                    String screenshot = r.getScreenshot();
                    if (screenshot != null) {
                        serializer.startTag(NS, SCREENSHOT_TAG);
                        serializer.text(screenshot);
                        serializer.endTag(NS, SCREENSHOT_TAG);
                    }
                    ReportLog.serialize(serializer, r.getReportLog());
                    serializer.endTag(NS, TEST_TAG);
                }
                serializer.endTag(NS, CASE_TAG);
            }
            serializer.endTag(NS, MODULE_TAG);
        }
        serializer.endDocument();
        return resultFile;
    }

    private static String formatTimeStamp(long epochTime) {
        return TIME_FORMAT.format(new Date(epochTime));
    }

    private static long parseTimeStamp(String time) {
        try {
            return TIME_FORMAT.parse(time).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return 0L;
    }
}
