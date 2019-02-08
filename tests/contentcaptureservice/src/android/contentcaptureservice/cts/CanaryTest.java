/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.contentcaptureservice.cts;

import static android.contentcaptureservice.cts.Helper.RESOURCE_STRING_SERVICE_NAME;
import static android.contentcaptureservice.cts.Helper.SYSTEM_SERVICE_NAME;
import static android.contentcaptureservice.cts.Helper.getInternalString;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.provider.DeviceConfig;
import android.text.TextUtils;
import android.util.Log;

import com.android.compatibility.common.util.RequiredServiceRule;

import org.junit.Test;

/**
 * No-op test used to make sure the other tests are not passing because the feature is disabled.
 */
public class CanaryTest {

    private static final String TAG = CanaryTest.class.getSimpleName();

    @Test
    public void logHasService() {
        final boolean hasService = RequiredServiceRule.hasService(SYSTEM_SERVICE_NAME);
        Log.d(TAG, "has " + SYSTEM_SERVICE_NAME + ": " + hasService);
        assumeTrue("device doesn't have service " + SYSTEM_SERVICE_NAME, hasService);
    }

    @Test
    public void assertHasService() {
        final String serviceName = getInternalString(RESOURCE_STRING_SERVICE_NAME);
        final String enableSettings = DeviceConfig.getProperty(
                DeviceConfig.ContentCapture.NAMESPACE,
                DeviceConfig.ContentCapture.PROPERTY_CONTENTCAPTURE_ENABLED);
        final boolean hasService = RequiredServiceRule.hasService(SYSTEM_SERVICE_NAME);
        Log.d(TAG, "Service resource: '" + serviceName + "' Settings: '" + enableSettings
                + "' Has '" + SYSTEM_SERVICE_NAME + "': " + hasService);

        // We're only asserting when the OEM defines a service
        assumeTrue("service resource (" + serviceName + ")is not defined",
                !TextUtils.isEmpty(serviceName));
        assertWithMessage("Should be enabled when resource '%s' is not empty (settings='%s')",
                serviceName, enableSettings).that(hasService).isTrue();
    }
}
