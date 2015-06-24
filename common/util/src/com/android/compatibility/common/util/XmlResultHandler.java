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
    private static final String DETAIL_TAG = "Detail";
    private static final String DEVICE_ATTR = "device";
    private static final String END_TIME_ATTR = "end";
    private static final String FAILED_ATTR = "failed";
    private static final String FAILURE_TAG = "Failure";
    private static final String HOST_NAME_ATTR = "host-name";
    private static final String JAVA_VENDOR_ATTR = "java-vendor";
    private static final String JAVA_VERSION_ATTR = "java-version";
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
    private static final String SCORETYPE_ATTR = "score-type";
    private static final String SCOREUNIT_ATTR = "score-unit";
    private static final String SOURCE_ATTR = "source";
    private static final String STACK_TAG = "StackTrace";
    private static final String START_TIME_ATTR = "start";
    private static final String SUITE_NAME_ATTR = "suite-name";
    private static final String SUITE_PLAN_ATTR = "suite-plan";
    private static final String SUITE_VERSION_ATTR = "suite-version";
    private static final String SUMMARY_TAG = "Summary";
    private static final String TARGET_ATTR = "target";
    private static final String TEST_TAG = "Test";
    private static final String VALUE_TAG = "Value";

    /**
     * @param resultsDir
     */
    public static List<IInvocationResult> getResults(File resultsDir) {
        ArrayList<IInvocationResult> results = new ArrayList<>();
        for (File resultDir : resultsDir.listFiles()) {
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
                        parser.require(XmlPullParser.START_TAG, NS, TEST_TAG);
                        String testName = parser.getAttributeValue(NS, NAME_ATTR);
                        IResult test = module.getOrCreateResult(testName);
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
                            } else {
                                parser.require(XmlPullParser.START_TAG, NS, SUMMARY_TAG);
                                ReportLog report = new ReportLog();
                                report.setSummary(parseResult(parser));
                                parser.require(XmlPullParser.END_TAG, NS, SUMMARY_TAG);
                                while (parser.nextTag() == XmlPullParser.START_TAG) {
                                    parser.require(XmlPullParser.START_TAG, NS, DETAIL_TAG);
                                    report.addDetail(parseResult(parser));
                                    parser.require(XmlPullParser.END_TAG, NS, DETAIL_TAG);
                                }
                                test.setReportLog(report);
                            }
                        }
                        parser.require(XmlPullParser.END_TAG, NS, TEST_TAG);
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
     * @param parser
     * @throws IOException
     * @throws XmlPullParserException
     * @throws NumberFormatException
     */
    private static ReportLog.Result parseResult(XmlPullParser parser) throws NumberFormatException,
            XmlPullParserException, IOException {
        String source = parser.getAttributeValue(NS, SOURCE_ATTR);
        String message = parser.getAttributeValue(NS, MESSAGE_ATTR);
        String target = parser.getAttributeValue(NS, TARGET_ATTR);
        ResultType type = ResultType.parseReportString(parser.getAttributeValue(NS, SCORETYPE_ATTR));
        ResultUnit unit = ResultUnit.parseReportString(parser.getAttributeValue(NS, SCOREUNIT_ATTR));
        List<Double> valuesList = new ArrayList<>();
        while (parser.nextTag() == XmlPullParser.START_TAG) {
            parser.require(XmlPullParser.START_TAG, NS, VALUE_TAG);
            valuesList.add(Double.parseDouble(parser.nextText()));
            parser.require(XmlPullParser.END_TAG, NS, VALUE_TAG);
        }
        int length = valuesList.size();
        double[] values = new double[length];
        for (int i = 0; i < length; i++) {
            values[i] = valuesList.get(i);
        }
        if (target != null && !target.equals("")) {
            return new ReportLog.Result(source, message, values, Double.parseDouble(target), type,
                    unit);
        }
        return new ReportLog.Result(source, message, values, type, unit);
    }

    /**
     * @param result
     * @param resultDir
     * @param startTime
     * @throws IOException
     * @throws XmlPullParserException
     */
    public static void writeResults(String suiteName, String suiteVersion, String suitePlan,
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
        serializer.setFeature(
                "http://xmlpull.org/v1/doc/features.html#indent-output", true);
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
            for (IResult r : module.getResults()) {
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
                ReportLog report = r.getReportLog();
                if (report != null) {
                    ReportLog.Result summary = report.getSummary();
                    serializer.startTag(NS, SUMMARY_TAG);
                    serializeResult(serializer, summary);
                    serializer.endTag(NS, SUMMARY_TAG);
                    List<ReportLog.Result> details = report.getDetailedMetrics();
                    for (ReportLog.Result detail : details) {
                        serializer.startTag(NS, DETAIL_TAG);
                        serializeResult(serializer, detail);
                        serializer.endTag(NS, DETAIL_TAG);
                    }
                }
                serializer.endTag(NS, TEST_TAG);
            }
            serializer.endTag(NS, MODULE_TAG);
        }
        serializer.endDocument();
    }

    /**
     * @param serializer
     * @param result
     * @throws IOException
     */
    private static void serializeResult(XmlSerializer serializer, ReportLog.Result result)
            throws IOException {
        serializer.attribute(NS, SOURCE_ATTR, result.getLocation());
        serializer.attribute(NS, MESSAGE_ATTR, result.getMessage());
        if (result.getTarget() != null) {
            serializer.attribute(NS, TARGET_ATTR, result.getTarget().toString());
        }
        serializer.attribute(NS, SCORETYPE_ATTR, result.getType().toString());
        serializer.attribute(NS, SCOREUNIT_ATTR, result.getUnit().toString());
        for (double d : result.getValues()) {
            serializer.startTag(NS, VALUE_TAG);
            serializer.text(Double.toString(d));
            serializer.endTag(NS, VALUE_TAG);
        }
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
