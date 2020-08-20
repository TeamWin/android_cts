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

package android.cts.statsdatom.lib;

import com.android.os.AtomsProto.AppBreadcrumbReported;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

/**
 * Contains miscellaneous helper functions that are used in statsd atom tests
 */
public final class AtomTestUtils {

    public static final int WAIT_TIME_SHORT = 500;
    public static final int WAIT_TIME_LONG = 1000;

    /**
     * Sends an AppBreadcrumbReported atom to statsd. For GaugeMetrics that are added using
     * ConfigUtils, pulls are triggered when statsd receives an AppBreadcrumbReported atom, so
     * calling this function is necessary for gauge data to be acquired.
     *
     * @param device test device can be retrieved using getDevice()
     */
    public static void sendAppBreadcrumbReportedAtom(ITestDevice device)
            throws DeviceNotAvailableException {
        String cmd = String.format("cmd stats log-app-breadcrumb %d %d", /*label=*/1,
                AppBreadcrumbReported.State.START.ordinal());
        device.executeShellCommand(cmd);
    }

    private AtomTestUtils() {}
}
