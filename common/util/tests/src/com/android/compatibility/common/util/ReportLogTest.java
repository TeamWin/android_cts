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

import junit.framework.TestCase;

/**
 * Unit tests for {@link ReportLog}
 */
public class ReportLogTest extends TestCase {

    private static final double[] VALUES = new double[] {.1, 124, 4736, 835.683, 98, 395};
    private static final String HEADER = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>";
    private static final String EXPECTED_XML =
            HEADER + "\r\n" +
            "<Summary>\r\n" +
            "  <Metric source=\"com.android.compatibility.common.util.ReportLogTest#testSerialize:62\" message=\"Sample\" score-type=\"higher_better\" score-unit=\"byte\">\r\n" +
            "    <Value>1.0</Value>\r\n" +
            "  </Metric>\r\n" +
            "</Summary>\r\n" +
            "<Detail>\r\n" +
            "  <Metric source=\"com.android.compatibility.common.util.ReportLogTest#testSerialize:63\" message=\"Details\" score-type=\"neutral\" score-unit=\"fps\">\r\n" +
            "    <Value>0.1</Value>\r\n" +
            "    <Value>124.0</Value>\r\n" +
            "    <Value>4736.0</Value>\r\n" +
            "    <Value>835.683</Value>\r\n" +
            "    <Value>98.0</Value>\r\n" +
            "    <Value>395.0</Value>\r\n" +
            "  </Metric>\r\n" +
            "</Detail>";

    private ReportLog mReportLog;

    @Override
    protected void setUp() throws Exception {
        mReportLog = new ReportLog();
    }

    public void testSerialize_null() throws Exception {
        assertEquals(HEADER, ReportLog.serialize(null));
    }

    public void testSerialize_noData() throws Exception {
        assertEquals(HEADER, ReportLog.serialize(mReportLog));
    }

    public void testSerialize() throws Exception {
        mReportLog.setSummary("Sample", 1.0, ResultType.HIGHER_BETTER, ResultUnit.BYTE);
        mReportLog.addValues("Details", VALUES, ResultType.NEUTRAL, ResultUnit.FPS);
        assertEquals(EXPECTED_XML, ReportLog.serialize(mReportLog));
    }

}
