/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.net.wifi.sharedconnectivity.cts;

import static org.junit.Assert.fail;

import android.content.Context;
import android.net.wifi.sharedconnectivity.app.SharedConnectivityManager;
import android.net.wifi.sharedconnectivity.app.cts.TestSharedConnectivityClientCallback;
import android.net.wifi.sharedconnectivity.service.cts.TestSharedConnectivityService;
import android.os.Build;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * These tests cover both SharedConnectivityService and SharedConnectivityManager.
 * Testing is done on these classes in their bound state.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
@NonMainlineTest
public class SharedConnectivityTest {
    private static final String TAG = "SharedConnectivityTest";

    private static final String SERVICE_PACKAGE_NAME = "android.net.wifi.cts";
    private static final String SERVICE_INTENT_ACTION =
            "android.net.wifi.sharedconnectivity.service.cts.TestSharedConnectivityService.BIND";

    // Time between checks for state we expect.
    private static final long CHECK_DELAY_MILLIS = 250;
    // Number of times to check before failing.
    private static final long CHECK_RETRIES = 15;

    @Test
    public void registerCallback_withoutPermission_throwsSecurityException() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SharedConnectivityManager manager = getManager(context);
        TestSharedConnectivityService service = getService();

        try {
            manager.registerCallback(Runnable::run, new TestSharedConnectivityClientCallback());
            fail("SecurityException not thrown");
        } catch (SecurityException e) {
            // Exception expected
        }
    }

    private SharedConnectivityManager getManager(Context context) {
        return SharedConnectivityManager.create(context, SERVICE_PACKAGE_NAME,
                SERVICE_INTENT_ACTION);
    }

    private TestSharedConnectivityService getService() throws InterruptedException {
        TestSharedConnectivityService service = TestSharedConnectivityService.getInstance();
        for (int i = 0; service == null && i < CHECK_RETRIES; i++) {
            Thread.sleep(CHECK_DELAY_MILLIS);
            service = TestSharedConnectivityService.getInstance();
        }
        return service;
    }
}
