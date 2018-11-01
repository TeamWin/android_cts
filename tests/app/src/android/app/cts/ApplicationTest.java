/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.app.stubs.MockApplication;
import android.app.stubs.MockApplicationActivity;
import android.app.stubs.OrientationTestUtils;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.cts.util.PollingCheck;
import android.test.InstrumentationTestCase;


/**
 * Test {@link Application}.
 */
public class ApplicationTest extends InstrumentationTestCase {

    public void testApplication() throws Throwable {
        final Instrumentation instrumentation = getInstrumentation();
        final Context targetContext = instrumentation.getTargetContext();

        final Intent intent = new Intent(targetContext, MockApplicationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        final Activity activity = instrumentation.startActivitySync(intent);
        final MockApplication mockApp = (MockApplication) activity.getApplication();
        assertTrue(mockApp.isConstructorCalled);
        assertTrue(mockApp.isOnCreateCalled);

        //skip if the device doesn't support both of portrait and landscape orientation screens.
        final PackageManager pm = targetContext.getPackageManager();
        if(!(pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE)
                && pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT))){
            return;
        }
        // Wait for each orientation change request because it may restart activity.
        // Don't use OrientationTestUtils.Observer because global configuration
        // change won't happen in multi window mode.
        final int orientations[] = OrientationTestUtils.getOrientations(activity);
        runTestOnUiThread(new Runnable() {
            public void run() {
                activity.setRequestedOrientation(orientations[1]);
            }
        });

        // Wait until rotation finishes. Needed for slow devices. But don't wait on Multi Window
        // mode since orientation request gets ignored.
        // orientation[1], on "non Multi Window mode" guarantees an onConfigurationChanged event,
        // since it cannot be SCREEN_ORIENTATION_USER.
        if (!activity.isInMultiWindowMode()) {
            PollingCheck.waitFor(() -> mockApp.isOnConfigurationChangedCalled == true);
        }
        instrumentation.waitForIdleSync();

        runTestOnUiThread(new Runnable() {
            public void run() {
                activity.setRequestedOrientation(orientations[0]);
            }
        });

        // Restore original orientation. But don't wait for 'onConfigurationChanged' since the
        // original orientation could be 'SCREEN_ORIENTATION_USER'. And that won't trigger any
        // event.
        instrumentation.waitForIdleSync();

        if (activity.isInMultiWindowMode()) {
            assertFalse("Orientation change should not trigger global configuration change when "
                    + " in multi-window mode.", mockApp.isOnConfigurationChangedCalled);
        } else {
            assertTrue(mockApp.isOnConfigurationChangedCalled);
        }
    }

}
