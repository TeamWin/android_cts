/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.cts;

import android.service.GraphicsStatsHistogramBucketProto;
import android.service.GraphicsStatsJankSummaryProto;
import android.service.GraphicsStatsProto;
import android.service.GraphicsStatsServiceDumpProto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GraphicsStatsValidationTest extends ProtoDumpTestCase {
    private static final String TAG = "GraphicsStatsValidationTest";

    private static final String DEVICE_SIDE_TEST_APK = "CtsGraphicsStatsApp.apk";
    private static final String DEVICE_SIDE_TEST_PACKAGE
            = "com.android.server.cts.device.graphicsstats";

    @Override
    protected void tearDown() throws Exception {
        // TODO: Re-enable log rotation
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);
        super.tearDown();
    }

    @Override
    protected void setUp() throws Exception {
        // TODO: Disable log rotation
        super.setUp();
    }

    public void testBasicDrawFrame() throws Exception {
        installPackage(DEVICE_SIDE_TEST_APK, /* grantPermissions= */ true);

        // Ensure that we have a starting point for our stats
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".SimpleDrawFrameTests",
                "testDrawTenFrames");
        // Kill to ensure that stats persist/merge across process death
        killTestApp();

        GraphicsStatsProto statsBefore = fetchStats();
        assertNotNull(statsBefore);
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".SimpleDrawFrameTests",
                "testDrawTenFrames");
        GraphicsStatsProto statsAfter = fetchStats();
        assertNotNull(statsAfter);
        assertEquals(statsBefore.getStatsStart(), statsAfter.getStatsStart());
        validate(statsBefore);
        validate(statsAfter);
        GraphicsStatsJankSummaryProto summaryBefore = statsBefore.getSummary();
        GraphicsStatsJankSummaryProto summaryAfter = statsAfter.getSummary();
        assertTrue(summaryAfter.getTotalFrames() > summaryBefore.getTotalFrames());

        int frameDelta = summaryAfter.getTotalFrames() - summaryBefore.getTotalFrames();
        int jankyDelta = summaryAfter.getJankyFrames() - summaryBefore.getJankyFrames();
        // We expect 11 frames to have been drawn (first frame + the 10 more explicitly requested)
        // but we accept a bit of slop in case other things drove a few extra frames.
        assertTrue(frameDelta > 10);
        assertTrue(frameDelta < 20);
        assertTrue(jankyDelta < 5);
    }

    private void validate(GraphicsStatsProto proto) {
        assertNotNull(proto.getPackageName());
        assertFalse(proto.getPackageName().isEmpty());
        assertTrue(proto.getVersionCode() > 0);
        assertTrue(proto.getStatsStart() > 0);
        assertTrue(proto.getStatsEnd() > 0);
        assertTrue(proto.hasSummary());
        GraphicsStatsJankSummaryProto summary = proto.getSummary();
        assertTrue(summary.getTotalFrames() > 0);
        // Our test app won't produce that many frames, so we can assert this is a realistic
        // number. We cap it at 1,000,000 in case the test is repeated many, many times in one day
        assertTrue(summary.getTotalFrames() < 1000000);
        // We can't generically assert things about the janky frames, so just assert they fall into
        // valid ranges.
        assertTrue(summary.getJankyFrames() <= summary.getTotalFrames());
        assertTrue(summary.getMissedVsyncCount() <= summary.getJankyFrames());
        assertTrue(summary.getHighInputLatencyCount() <= summary.getJankyFrames());
        assertTrue(summary.getSlowUiThreadCount() <= summary.getJankyFrames());
        assertTrue(summary.getSlowBitmapUploadCount() <= summary.getJankyFrames());
        assertTrue(summary.getSlowDrawCount() <= summary.getJankyFrames());
        assertTrue(proto.getHistogramCount() > 0);

        int histogramTotal = countTotalFrames(proto);
        assertSame(histogramTotal, summary.getTotalFrames());
    }

    private int countTotalFrames(GraphicsStatsProto proto) {
        int totalFrames = 0;
        for (GraphicsStatsHistogramBucketProto bucket : proto.getHistogramList()) {
            totalFrames += bucket.getFrameCount();
        }
        return totalFrames;
    }

    private void killTestApp() throws Exception {
        getDevice().executeShellCommand("am kill " + DEVICE_SIDE_TEST_PACKAGE);
    }

    private GraphicsStatsProto fetchStats() throws Exception {
        GraphicsStatsServiceDumpProto serviceDumpProto = getDump(GraphicsStatsServiceDumpProto.parser(),
                "dumpsys graphicsstats --proto");
        List<GraphicsStatsProto> protos = filterPackage(serviceDumpProto, DEVICE_SIDE_TEST_PACKAGE);
        return findLatest(protos);
    }

    private List<GraphicsStatsProto> filterPackage(GraphicsStatsServiceDumpProto dump, String pkgName) {
        return filterPackage(dump.getStatsList(), pkgName);
    }

    private List<GraphicsStatsProto> filterPackage(List<GraphicsStatsProto> list, String pkgName) {
        ArrayList<GraphicsStatsProto> filtered = new ArrayList<>();
        for (GraphicsStatsProto proto : list) {
            if (pkgName.equals(proto.getPackageName())) {
                filtered.add(proto);
            }
        }
        return filtered;
    }

    private GraphicsStatsProto findLatest(List<GraphicsStatsProto> list) {
        if (list.size() == 0) { return null; }
        GraphicsStatsProto latest = list.get(0);
        Date latestDate = new Date();
        Date compareTo = new Date();
        latestDate.setTime(latest.getStatsEnd());
        for (int i = 1; i < list.size(); i++) {
            GraphicsStatsProto proto = list.get(i);
            compareTo.setTime(proto.getStatsEnd());
            if (compareTo.after(latestDate)) {
                latestDate.setTime(proto.getStatsEnd());
                latest = proto;
            }
        }
        return latest;
    }
}
