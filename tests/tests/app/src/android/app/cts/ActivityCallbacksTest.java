/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package android.app.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.app.cts.ActivityCallbacksTestActivity.Event;
import android.app.cts.ActivityCallbacksTestActivity.Source;
import android.content.Context;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Pair;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ActivityCallbacksTest {

    private static final long TIMEOUT_SECS = 2;

    @Rule
    public ActivityTestRule<ActivityCallbacksTestActivity> mActivityRule =
            new ActivityTestRule<>(ActivityCallbacksTestActivity.class, false, false);

    private Application.ActivityLifecycleCallbacks mActivityCallbacks;

    @After
    public void tearDown() {
        if (mActivityCallbacks != null) {
            Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
            Application application = (Application) targetContext.getApplicationContext();
            application.unregisterActivityLifecycleCallbacks(mActivityCallbacks);
        }
    }

    @Test
    public void testActivityCallbackOrder() throws InterruptedException {
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Application application = (Application) targetContext.getApplicationContext();
        ArrayList<Pair<Source, Event>> actualEvents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        mActivityCallbacks = new Application.ActivityLifecycleCallbacks() {

            @Override
            public void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {
                ActivityCallbacksTestActivity a = (ActivityCallbacksTestActivity) activity;
                a.collectEvent(Source.ACTIVITY_CALLBACK, Event.ON_PRE_CREATE);
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                ActivityCallbacksTestActivity a = (ActivityCallbacksTestActivity) activity;
                a.collectEvent(Source.ACTIVITY_CALLBACK, Event.ON_CREATE);
            }

            @Override
            public void onActivityStarted(Activity activity) {
                ActivityCallbacksTestActivity a = (ActivityCallbacksTestActivity) activity;
                a.collectEvent(Source.ACTIVITY_CALLBACK, Event.ON_START);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                ActivityCallbacksTestActivity a = (ActivityCallbacksTestActivity) activity;
                a.collectEvent(Source.ACTIVITY_CALLBACK, Event.ON_RESUME);
                a.finish();
            }

            @Override
            public void onActivityPaused(Activity activity) {
                ActivityCallbacksTestActivity a = (ActivityCallbacksTestActivity) activity;
                a.collectEvent(Source.ACTIVITY_CALLBACK, Event.ON_PAUSE);
            }

            @Override
            public void onActivityStopped(Activity activity) {
                ActivityCallbacksTestActivity a = (ActivityCallbacksTestActivity) activity;
                a.collectEvent(Source.ACTIVITY_CALLBACK, Event.ON_STOP);
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                ActivityCallbacksTestActivity a = (ActivityCallbacksTestActivity) activity;
                a.collectEvent(Source.ACTIVITY_CALLBACK, Event.ON_DESTROY);
                actualEvents.addAll(a.getCollectedEvents());
                latch.countDown();
            }
        };

        application.registerActivityLifecycleCallbacks(mActivityCallbacks);

        mActivityRule.launchActivity(null);
        assertTrue("Failed to await for an activity to finish ",
                latch.await(TIMEOUT_SECS, TimeUnit.SECONDS));

        ArrayList<Pair<Source, Event>> expectedEvents = new ArrayList<>();
        for (Event e: Event.values()) {
            if (e != Event.ON_PRE_CREATE) {
                expectedEvents.add(new Pair<>(Source.ACTIVITY, e));
            }
            expectedEvents.add(new Pair<>(Source.ACTIVITY_CALLBACK, e));
        }

        assertEquals(expectedEvents, actualEvents);
    }


}
