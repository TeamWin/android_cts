/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.contentcaptureservice.cts.Helper.GENERIC_TIMEOUT_MS;
import static android.contentcaptureservice.cts.Helper.TAG;
import static android.contentcaptureservice.cts.Helper.resetService;
import static android.contentcaptureservice.cts.Helper.setService;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;

import android.app.Application;
import android.content.Context;
import android.contentcaptureservice.cts.common.ActivitiesWatcher;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.contentcapture.ContentCaptureEvent;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 * Base class for all (or most :-) integration tests in this CTS suite.
 */
@RunWith(AndroidJUnit4.class)
public abstract class AbstractContentCaptureIntegrationTest {

    protected static final Context sContext = InstrumentationRegistry.getTargetContext();

    protected ActivitiesWatcher mActivitiesWatcher;

    @BeforeClass
    public static void checkSupported() {
        // TODO(b/119638958): use a @Rule to skip it and/or check for the Global Settings directly
        final String checkService = runShellCommand("service check content_capture").trim();
        final boolean notSupported = checkService.contains("not found");
        if (notSupported) {
            final String msg = "Skipping test because Content Capture is not supported on device";
            Log.i(TAG, msg);
            assumeFalse(msg, notSupported);
            return;
        }
    }

    @Before
    public void registerLifecycleCallback() {
        Log.d(TAG, "Registering lifecycle callback");
        final Application app = (Application) sContext.getApplicationContext();
        mActivitiesWatcher = new ActivitiesWatcher(GENERIC_TIMEOUT_MS);
        app.registerActivityLifecycleCallbacks(mActivitiesWatcher);
    }

    @After
    public void unregisterLifecycleCallback() {
        if (mActivitiesWatcher != null) {
            Log.d(TAG, "Unregistering lifecycle callback");
            final Application app = (Application) sContext.getApplicationContext();
            app.unregisterActivityLifecycleCallbacks(mActivitiesWatcher);
        }
    }

    @After
    public void restoreDefaultService() {
        resetService();
    }

    public void assertLifecycleEvent(@NonNull ContentCaptureEvent event, int expected) {
        assertWithMessage("wrong event: %s", event).that(event.getType()).isEqualTo(expected);
    }

    /**
     * Sets {@link CtsSmartSuggestionsService} as the service for the current user.
     */
    public static void enableService() {
        setService(CtsSmartSuggestionsService.SERVICE_NAME);
    }
}
