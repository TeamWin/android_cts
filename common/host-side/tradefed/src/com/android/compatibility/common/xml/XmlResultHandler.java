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
package com.android.compatibility.common.xml;

import com.android.compatibility.common.tradefed.result.IInvocationResult;
import com.android.compatibility.common.tradefed.result.IModuleResult;
import com.android.compatibility.common.tradefed.result.IResult;
import com.android.compatibility.common.tradefed.result.TestStatus;
import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ReportLog.Result;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.TimeUtil;

import org.kxml2.io.KXmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles conversion of results to/from XML.
 */
public class XmlResultHandler {

    private static final String NS = null;
    private static final String TEST_RESULT_FILE_NAME = "test-result.xml";
    private static final String RESULT_FILE_VERSION = "5.0";
    private static final String[] RESULT_RESOURCES = {"compatibility-result.css",
        "compatibility-result.xsd", "compatibility-result.xsl", "logo.png", "newrule-green.png"};

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
    private static final String NAME_ATTR = "name";
    private static final String NOT_EXECUTED_ATTR = "not-executed";
    private static final String OS_ARCH_ATTR = "os-arch";
    private static final String OS_NAME_ATTR = "os-name";
    private static final String OS_VERSION_ATTR = "os-version";
    private static final String PASS_ATTR = "pass";
    private static final String PLAN_ATTR = "plan";
    private static final String REPORT_VERSION_ATTR = "report-version";
    private static final String RESULT_ATTR = "result";
    private static final String RESULT_TAG = "Result";
    private static final String SCORETYPE_ATTR = "score-type";
    private static final String SCOREUNIT_ATTR = "score-unit";
    private static final String SOURCE_ATTR = "source";
    private static final String STACK_TAG = "StackTrace";
    private static final String START_TIME_ATTR = "start";
    private static final String SUITE_NAME_ATTR = "suite-name";
    private static final String SUITE_VERSION_ATTR = "suite-version";
    private static final String SUMMARY_TAG = "Summary";
    private static final String TARGET_ATTR = "target";
    private static final String VALUE_TAG = "Value";

    /**
     * @param resultDir
     */
    public static List<IInvocationResult> getResults(File resultDir) {
        // TODO(stuartscott): Auto-generated method stub
        return new ArrayList<IInvocationResult>();
    }

    /**
     * @param planName
     * @param result
     * @param resultDir
     * @param logDir
     * @param startTime
     */
    public static void writeResults(String suiteName, String suiteVersion, String planName,
            IInvocationResult result, File resultDir, File logDir, long startTime, long endTime) {
        int passed = result.countResults(TestStatus.PASS);
        int failed = result.countResults(TestStatus.FAIL);
        int notExecuted = result.countResults(TestStatus.NOT_EXECUTED);
        File resultFile = new File(resultDir, TEST_RESULT_FILE_NAME);
        OutputStream stream = null;
        try {
            stream = new FileOutputStream(resultFile);
            KXmlSerializer serializer = new KXmlSerializer();
            serializer.setOutput(stream, "UTF-8");
            serializer.startDocument("UTF-8", false);
            serializer.setFeature(
                    "http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.processingInstruction(
                    "xml-stylesheet type=\"text/xsl\" href=\"compatibility-result.xsl\"");
            serializer.startTag(NS, RESULT_TAG);
            serializer.attribute(NS, PLAN_ATTR, planName == null ? "" : planName);
            serializer.attribute(NS, START_TIME_ATTR, TimeUtil.formatTimeStamp(startTime));
            serializer.attribute(NS, END_TIME_ATTR, TimeUtil.formatTimeStamp(endTime));
            serializer.attribute(NS, SUITE_NAME_ATTR, suiteName);
            serializer.attribute(NS, SUITE_VERSION_ATTR, suiteVersion);
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
                serializer.startTag(NS, "Module");
                serializer.attribute(NS, NAME_ATTR, module.getName());
                serializer.attribute(NS, ABI_ATTR, module.getAbi());
                serializer.attribute(NS, DEVICE_ATTR, module.getDeviceSerial());
                for (IResult r : module.getResults()) {
                    serializer.startTag(NS, "Test");
                    serializer.attribute(NS, RESULT_ATTR, r.getResultStatus().getValue());
                    serializer.attribute(NS, NAME_ATTR, r.getName());
                    serializer.attribute(NS, START_TIME_ATTR, TimeUtil.formatTimeStamp(r.getStartTime()));
                    serializer.attribute(NS, END_TIME_ATTR, TimeUtil.formatTimeStamp(r.getEndTime()));
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
                        Result summary = report.getSummary();
                        serializer.startTag(NS, SUMMARY_TAG);
                        serializeResult(serializer, summary);
                        serializer.endTag(NS, SUMMARY_TAG);
                        List<Result> details = report.getDetailedMetrics();
                        for (Result detail : details) {
                            serializer.startTag(NS, DETAIL_TAG);
                            serializeResult(serializer, detail);
                            serializer.endTag(NS, DETAIL_TAG);
                        }
                    }
                    serializer.endTag(NS, "Test");
                }
                serializer.endTag(NS, "Module");
            }
            serializer.endDocument();
            copyFormattingFiles(resultDir);
            zipResults(resultDir);
            CLog.i("Saved results in %s", resultFile.getAbsolutePath());
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    /**
     * @param serializer
     * @param result
     * @throws IOException
     */
    private static void serializeResult(KXmlSerializer serializer, Result result)
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

    /**
     * Copy the xml formatting files stored in this jar to the results directory
     *
     * @param resultsDir
     */
    private static void copyFormattingFiles(File resultsDir) {
        for (String resultFileName : RESULT_RESOURCES) {
            InputStream configStream = XmlResultHandler.class.getResourceAsStream(
                    String.format("/report/%s", resultFileName));
            if (configStream != null) {
                File resultFile = new File(resultsDir, resultFileName);
                try {
                    FileUtil.writeToFile(configStream, resultFile);
                } catch (IOException e) {
                    CLog.w(String.format("Failed to write %s to file", resultFileName));
                }
            } else {
                CLog.w(String.format("Failed to load %s from jar", resultFileName));
            }
        }
    }

    /**
     * Zip the contents of the given results directory.
     *
     * @param resultsDir
     */
    @SuppressWarnings("deprecation")
    private static void zipResults(File resultsDir) {
        try {
            // create a file in parent directory, with same name as resultsDir
            File zipResultFile = new File(resultsDir.getParent(), String.format("%s.zip",
                    resultsDir.getName()));
            FileUtil.createZip(resultsDir, zipResultFile);
        } catch (IOException e) {
            CLog.w(String.format("Failed to create zip for %s", resultsDir.getName()));
        }
    }
}
