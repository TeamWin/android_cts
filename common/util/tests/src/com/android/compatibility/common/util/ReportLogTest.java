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

import com.android.compatibility.common.util.ReportLog.Metric;

import junit.framework.TestCase;

import org.xmlpull.v1.XmlPullParserException;

import java.util.List;

/**
 * Unit tests for {@link ReportLog}
 */
public class ReportLogTest extends TestCase {

    private static final double[] VALUES = new double[] {.1, 124, 4736, 835.683, 98, 395};
    private static final String HEADER_XML =
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>";
    private static final String SUMMARY_XML =
            HEADER_XML + "\r\n" +
            "<Summary>\r\n" +
            "  <Metric source=\"com.android.compatibility.common.util.ReportLogTest#%s\" message=\"Sample\" score_type=\"higher_better\" score_unit=\"byte\">\r\n" +
            "    <Value>1.0</Value>\r\n" +
            "  </Metric>\r\n" +
            "</Summary>";
    private static final String DETAIL_XML =
            "<Detail>\r\n" +
            "  <Metric source=\"com.android.compatibility.common.util.ReportLogTest#%s\" message=\"Details\" score_type=\"neutral\" score_unit=\"fps\">\r\n" +
            "    <Value>0.1</Value>\r\n" +
            "    <Value>124.0</Value>\r\n" +
            "    <Value>4736.0</Value>\r\n" +
            "    <Value>835.683</Value>\r\n" +
            "    <Value>98.0</Value>\r\n" +
            "    <Value>395.0</Value>\r\n" +
            "  </Metric>\r\n" +
            "</Detail>";
    private static final String FULL_XML = SUMMARY_XML + "\r\n" + DETAIL_XML;

    private ReportLog mReportLog;

    @Override
    protected void setUp() throws Exception {
        mReportLog = new ReportLog();
    }

    public void testSerialize_null() throws Exception {
        try {
            ReportLog.serialize(null);
            fail("Expected IllegalArgumentException when serializing an empty report");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    public void testSerialize_noData() throws Exception {
        try {
            ReportLog.serialize(mReportLog);
            fail("Expected IllegalArgumentException when serializing an empty report");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    public void testSerialize_summaryOnly() throws Exception {
        mReportLog.setSummary("Sample", 1.0, ResultType.HIGHER_BETTER, ResultUnit.BYTE);
        assertEquals(String.format(SUMMARY_XML, "testSerialize_summaryOnly:81"),
                ReportLog.serialize(mReportLog));
    }

    public void testSerialize_detailOnly() throws Exception {
        mReportLog.addValues("Details", VALUES, ResultType.NEUTRAL, ResultUnit.FPS);
        try {
            ReportLog.serialize(mReportLog);
            fail("Expected IllegalArgumentException when serializing report without summary");
        } catch(IllegalArgumentException e) {
            // Expected
        }
    }

    public void testSerialize_full() throws Exception {
        mReportLog.setSummary("Sample", 1.0, ResultType.HIGHER_BETTER, ResultUnit.BYTE);
        mReportLog.addValues("Details", VALUES, ResultType.NEUTRAL, ResultUnit.FPS);
        assertEquals(String.format(FULL_XML, "testSerialize_full:97", "testSerialize_full:98"),
                ReportLog.serialize(mReportLog));
    }

    public void testParse_null() throws Exception {
        try {
            ReportLog.parse((String) null);
            fail("Expected IllegalArgumentException when passing a null report");
        } catch(IllegalArgumentException e) {
            // Expected
        }
    }

    public void testParse_noData() throws Exception {
        try {
            ReportLog.parse(HEADER_XML);
            fail("Expected XmlPullParserException when passing a report with no content");
        } catch(XmlPullParserException e) {
            // Expected
        }
    }

    public void testParse_summaryOnly() throws Exception {
        ReportLog report = ReportLog.parse(String.format(SUMMARY_XML, "testParse_summaryOnly:122"));
        assertNotNull(report);
        assertEquals("Sample", report.getSummary().getMessage());
    }

    public void testParse_detailOnly() throws Exception {
        try {
            ReportLog.parse(String.format(DETAIL_XML, "testParse_detailOnly:129"));
            fail("Expected XmlPullParserException when serializing report without summary");
        } catch (XmlPullParserException e) {
            // Expected
        }
    }

    public void testParse_full() throws Exception {
        ReportLog report = ReportLog.parse(String.format(FULL_XML, "testParse_full:137",
                "testParse_full:138"));
        assertNotNull(report);
        assertEquals("Sample", report.getSummary().getMessage());
        List<Metric> details = report.getDetailedMetrics();
        assertEquals(1, details.size());
        assertEquals("Details", details.get(0).getMessage());
    }

}
