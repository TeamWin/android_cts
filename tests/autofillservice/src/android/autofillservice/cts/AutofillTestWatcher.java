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
package android.autofillservice.cts;

import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.Set;

/**
 * Custom {@link TestWatcher} that's the outer rule of all {@link AutoFillServiceTestCase} tests.
 */
public final class AutofillTestWatcher extends TestWatcher {

    private static final String TAG = "AutofillTestWatcher";

    private static final Set<AbstractAutoFillActivity> sUnfinishedBusiness = new ArraySet<>();
    private static final Object sLock = new Object();

    @Override
    protected void starting(Description description) {
        final String testName = description.getDisplayName();
        Log.i(TAG, "Starting " + testName);
        JUnitHelper.setCurrentTestName(testName);
    }

    @Override
    protected void finished(Description description) {
        final String testName = description.getDisplayName();
        finishActivities();
        Log.i(TAG, "Finished " + testName);
        JUnitHelper.setCurrentTestName(null);
    }

    /**
     * Registers an activity so it's automatically finished (if necessary) after the test.
     */
    public static void registerActivity(@NonNull AbstractAutoFillActivity activity) {
        Log.v(TAG, "registerActivity(): " + activity);
        synchronized (sLock) {
            sUnfinishedBusiness.add(activity);
        }
    }

    /**
     * Unregisters an activity so it's not automatically finished after the test.
     */
    public static void unregisterActivity(@NonNull AbstractAutoFillActivity activity) {
        Log.v(TAG, "unregisterActivity(): " + activity);
        synchronized (sLock) {
            sUnfinishedBusiness.remove(activity);
        }
    }

    private void finishActivities() {
        synchronized (sLock) {
            if (sUnfinishedBusiness.isEmpty()) {
                return;
            }
            try {
                for (AbstractAutoFillActivity activity : sUnfinishedBusiness) {
                    if (activity.isFinishing()) {
                        Log.v(TAG, "Ignoring activity that isFinishing(): " + activity);
                    } else {
                        Log.d(TAG, "Finishing activity: " + activity);
                        activity.finishOnly();
                    }
                }
            } finally {
                sUnfinishedBusiness.clear();
            }
        }
    }
}
