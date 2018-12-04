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

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;

import android.app.Application;
import android.content.Context;
import android.contentcaptureservice.cts.common.ActivitiesWatcher;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.intelligence.ContentCaptureEvent;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 * Base class for all (or most :-) integration tests in this CTS suite.
 */
@RunWith(AndroidJUnit4.class)
public abstract class AbstractContentCaptureIntegrationTest {

    protected static final Context sContext = InstrumentationRegistry.getTargetContext();

    private static String sPreviousService;
    private static boolean sSkipped = false;

    protected ActivitiesWatcher mActivitiesWatcher;

    @BeforeClass
    public static void saveService() {
        // TODO(b/119638958): use a @Rule to skip it (once we unhardcode the
        // config.disable_intelligence=true from SystemServer)
        final String checkService = runShellCommand("service check intelligence").trim();
        final boolean notSupported = checkService.contains("not found");
        if (notSupported) {
            sSkipped = true;
            final String msg = "Skipping test because Content Capture is not supported on device";
            Log.i(TAG, msg);
            assumeFalse(msg, notSupported);
            return;
        }

        sPreviousService = runShellCommand("settings get secure smart_suggestions_service").trim();
        if (sPreviousService.equals("null")) {
            sPreviousService = null;
        }
        Log.d(TAG, "Saving previous service: " + sPreviousService);
    }

    @AfterClass
    public static void restoreService() {
        if (sSkipped) return;

        if (sPreviousService != null) {
            setService(sPreviousService);
        } else {
            Log.d(TAG, "No need to restore previous service");
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

    public void assertLifecycleEvent(@NonNull ContentCaptureEvent event, int expected) {
        assertWithMessage("wrong event: %s", event).that(event.getType()).isEqualTo(expected);
    }

    /**
     * Sets {@link CtsSmartSuggestionsService} as the service for the current user.
     */
    public static void enableService() {
        setService("android.contentcaptureservice.cts/."
                + CtsSmartSuggestionsService.class.getSimpleName());
    }

    /**
     * Sets the content capture service.
     */
    public static void setService(@NonNull String service) {
        Log.d(TAG, "Setting service to " + service);
        runShellCommand("settings put secure smart_suggestions_service " + service);
        // TODO(b/119638958): add a more robust mechanism to wait for service to be set.
        // For example, when the service is set using a shell cmd, block until the
        // IntelligencePerUserService is cached.
        SystemClock.sleep(GENERIC_TIMEOUT_MS);
    }
}
