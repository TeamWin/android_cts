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
package android.sample.cts;

import android.sample.SampleDeviceActivity;
import android.test.ActivityInstrumentationTestCase2;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.DynamicConfigDeviceSide;
import com.android.compatibility.common.util.MeasureRun;
import com.android.compatibility.common.util.MeasureTime;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.Stat;

import java.util.Arrays;
import java.util.Random;

/**
 * A simple compatibility test which includes results in the report.
 *
 * This test measures the time taken to run a workload and adds in the report.
 */
public class SampleDeviceResultTest extends ActivityInstrumentationTestCase2<SampleDeviceActivity> {

    /**
     * The default name for metrics report log file.
     */
    private String DEFAULT_REPORT_LOG_NAME = "DefaultMetrics";

    /**
     * The default name for a metrics stream.
     */
    private String DEFAULT_STREAM_NAME = "DefaultStream";

    /**
     * The number of times to repeat the test.
     */
    private static final int REPEAT = 5;

    /**
     * A {@link Random} to generate random integers to test the sort.
     */
    private static final Random random = new Random(12345);

    /**
     * Constructor which passes the class of the activity to be instrumented.
     */
    public SampleDeviceResultTest() {
        super(SampleDeviceActivity.class);
    }

    /**
     * Measures the time taken to sort an array.
     */
    public void testSort() throws Exception {
        // MeasureTime runs the workload N times and records the time taken by each run.
        double[] result = MeasureTime.measure(REPEAT, new MeasureRun() {
            /**
             * The size of the array to sort.
             */
            private static final int ARRAY_SIZE = 100000;
            private int[] array;
            @Override
            public void prepare(int i) throws Exception {
                array = createArray(ARRAY_SIZE);
            }
            @Override
            public void run(int i) throws Exception {
                Arrays.sort(array);
                assertTrue("Array not sorted", isSorted(array));
            }
        });
        // Compute the stats.
        Stat.StatResult stat = Stat.getStat(result);
        // Create a new report to hold the metrics.
        String reportLogName = getReportLogName();
        String streamName = getStreamName("testSort-stream-name");
        DeviceReportLog reportLog = new DeviceReportLog(reportLogName, streamName);
        // Add the results to the report.
        reportLog.addValues("Times", result, ResultType.LOWER_BETTER, ResultUnit.MS);
        reportLog.addValue("Min", stat.mMin, ResultType.LOWER_BETTER, ResultUnit.MS);
        reportLog.addValue("Max", stat.mMax, ResultType.LOWER_BETTER, ResultUnit.MS);
        // Every report must have a summary,
        reportLog.setSummary("Average", stat.mAverage, ResultType.LOWER_BETTER, ResultUnit.MS);
        // Submit the report to the given instrumentation.
        reportLog.submit(getInstrumentation());
    }

    /**
     * Creates an array filled with random numbers of the given size.
     */
    private static int[] createArray(int size) {
        int[] array = new int[size];
        for (int i = 0; i < size; i++) {
            array[i] = random.nextInt();
        }
        return array;
    }

    /**
     * Tests an array is sorted.
     */
    private static boolean isSorted(int[] array) {
        int len = array.length;
        for (int i = 0, j = 1; j < len; i++, j++) {
            if (array[i] > array[j]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Retreives name of report log from dynamic config.
     *
     * @return {@link String} name of the report log.
     */
    private String getReportLogName() {
        try {
            DynamicConfigDeviceSide dynamicConfig = new DynamicConfigDeviceSide(
                    "CtsSampleDeviceTestCases");
            return dynamicConfig.getValues("report-log-name").get(0);
        } catch (Exception e) {
            // Do nothing.
        }
        return DEFAULT_REPORT_LOG_NAME;
    }

    /**
     * Retreives name of metrics stream from dynamic config.
     *
     * @param key The key in dynamic config containing stream name.
     * @return {@link String} name of the stream.
     */
    private String getStreamName(String key) {
        try {
            DynamicConfigDeviceSide dynamicConfig = new DynamicConfigDeviceSide(
                    "CtsSampleDeviceTestCases");
            return dynamicConfig.getValues(key).get(0);
        } catch (Exception e) {
            // Do nothing.
        }
        return DEFAULT_STREAM_NAME ;
    }


}
