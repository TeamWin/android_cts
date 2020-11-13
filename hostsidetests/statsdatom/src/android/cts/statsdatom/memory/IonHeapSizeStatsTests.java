/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.cts.statsdatom.memory;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.util.PropertyUtil;
import com.android.os.AtomsProto;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public class IonHeapSizeStatsTests extends DeviceTestCase implements IBuildReceiver {
    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testIonHeapSize_optional() throws Exception {
        if (isIonHeapSizeMandatory()) {
            return;
        }

        List<AtomsProto.Atom> atoms = pullIonHeapSizeAsGaugeMetric();
        if (atoms.isEmpty()) {
            // No support.
            return;
        }
        assertIonHeapSize(atoms);
    }

    public void testIonHeapSize_mandatory() throws Exception {
        if (!isIonHeapSizeMandatory()) {
            return;
        }

        List<AtomsProto.Atom> atoms = pullIonHeapSizeAsGaugeMetric();
        assertIonHeapSize(atoms);
    }

    /** Returns whether IonHeapSize atom is supported. */
    private boolean isIonHeapSizeMandatory() throws Exception {
        // Support is guaranteed by libmeminfo VTS.
        return PropertyUtil.getFirstApiLevel(getDevice()) >= 30;
    }

    /** Returns IonHeapSize atoms pulled as a simple gauge metric while test app is running. */
    private List<AtomsProto.Atom> pullIonHeapSizeAsGaugeMetric() throws Exception {
        // Get IonHeapSize as a simple gauge metric.
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.ION_HEAP_SIZE_FIELD_NUMBER);

        // Start test app and trigger a pull while it is running.
        try (AutoCloseable a = DeviceUtils.withActivity(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG, "StatsdCtsForegroundActivity", "action",
                "action.show_notification")) {
            AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
            Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
        }

        return ReportUtils.getGaugeMetricAtoms(getDevice());
    }

    private static void assertIonHeapSize(List<AtomsProto.Atom> atoms) {
        assertThat(atoms).hasSize(1);
        AtomsProto.IonHeapSize ionHeapSize = atoms.get(0).getIonHeapSize();
        assertThat(ionHeapSize.getTotalSizeKb()).isAtLeast(0);
    }

}
