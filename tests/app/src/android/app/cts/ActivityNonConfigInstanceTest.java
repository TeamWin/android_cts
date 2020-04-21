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

package android.app.cts;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.stubs.ActivityNonConfigInstanceActivity;
import android.content.Intent;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public final class ActivityNonConfigInstanceTest {

    @Rule
    public ActivityTestRule<ActivityNonConfigInstanceActivity> mActivityRule =
            new ActivityTestRule<>(ActivityNonConfigInstanceActivity.class);

    @Test
    @UiThreadTest
    public void unableToGetNullClass() {
        ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();
        try {
            activity.getNonConfigurationInstance(null);
            fail();
        } catch (NullPointerException expected) {
        }
    }

    @Test
    @UiThreadTest
    public void unableToPutNullClass() {
        ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();
        try {
            activity.putNonConfigurationInstance(null, null);
            fail();
        } catch (NullPointerException expected) {
        }
    }

    @Test
    @UiThreadTest
    public void noInstanceReturnsNull() {
        ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();
        Object actual = activity.getNonConfigurationInstance(Object.class);
        assertNull(actual);
    }

    @Test
    public void putRetained() throws Throwable {
        Object instance = new Object();

        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            Object replaced = activity.putNonConfigurationInstance(Object.class, instance);
            assertNull(replaced);

            activity.recreate();
        });

        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            Object actual = activity.getNonConfigurationInstance(Object.class);
            assertSame(instance, actual);
        });
    }

    @Test
    public void putInOnStopRetained() throws Throwable {
        // Legacy non-config instance capture happens between onStop and onDestroy. Since
        // the new API has no explicitly lifecycle hook, we want to ensure that you can still
        // put instances in onStop.

        Object instance = new Object();

        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            activity.onStop = () -> {
                activity.putNonConfigurationInstance(Object.class, instance);
            };
            activity.recreate();
        });

        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            Object actual = activity.getNonConfigurationInstance(Object.class);
            assertSame(instance, actual);
        });
    }

    @Test
    public void putInOnDestroyRetained() throws Throwable {
        // Legacy non-config instance capture happens between onStop and onDestroy. Since
        // the new API has no explicitly lifecycle hook, we want to ensure that you can still
        // put instances in onDestroy.

        Object instance = new Object();

        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            activity.onDestroy = () -> {
                activity.putNonConfigurationInstance(Object.class, instance);
            };
            activity.recreate();
        });

        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            Object actual = activity.getNonConfigurationInstance(Object.class);
            assertSame(instance, actual);
        });
    }

    @Test
    public void putNotRetainedForNonConfigurationChangeRestarts() throws Throwable {
        ActivityNonConfigInstanceActivity first = mActivityRule.getActivity();

        mActivityRule.runOnUiThread(() -> {
            first.putNonConfigurationInstance(Object.class, new Object());
        });

        // Trigger recreation without retaining non-config instances:
        // Step 1: Start a new activity on top of the existing one.
        Intent overlayIntent = new Intent(first, ActivityNonConfigInstanceActivity.class);
        overlayIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity overlay = InstrumentationRegistry.getInstrumentation()
                .startActivitySync(overlayIntent);
        mActivityRule.runOnUiThread(() -> {
            // Step 2: Ask the original activity not to retain its instance.
            first.releaseInstance();
            // Step 3: Finish the new activity causing recreation of old one.
            overlay.finish();
        });

        // Finally, ensure non-config instances are not available:
        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity second = mActivityRule.getActivity();

            Object actual = second.getNonConfigurationInstance(Object.class);
            assertNull(actual);
        });
    }

    @Test
    public void lastPutRetained() throws Throwable {
        Object instance1 = new Object();
        Object instance2 = new Object();

        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            Object replaced1 = activity.putNonConfigurationInstance(Object.class, instance1);
            assertNull(replaced1);

            Object replaced2 = activity.putNonConfigurationInstance(Object.class, instance2);
            assertSame(instance1, replaced2);

            activity.recreate();
        });

        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            Object actual = activity.getNonConfigurationInstance(Object.class);
            assertSame(instance2, actual);
        });
    }

    @Test
    public void putNullRemovesInstance() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            Object instance = new Object();
            activity.putNonConfigurationInstance(Object.class, instance);

            Object replaced = activity.putNonConfigurationInstance(Object.class, null);
            assertSame(instance, replaced);

            activity.recreate();
        });

        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            Object actual = activity.getNonConfigurationInstance(Object.class);
            assertNull(actual);
        });
    }

    @Test
    public void putInstanceRetainedAcrossMultipleRecreations() throws Throwable {
        Object instance = new Object();

        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            activity.putNonConfigurationInstance(Object.class, instance);
            activity.recreate();
        });

        mActivityRule.runOnUiThread(() -> {
            mActivityRule.getActivity().recreate();
        });

        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            Object actual = activity.getNonConfigurationInstance(Object.class);
            assertSame(instance, actual);
        });
    }

    @Test
    public void putNullRemovesInstanceAfterBeingRetained() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            activity.putNonConfigurationInstance(Object.class, new Object());
            activity.recreate();
        });

        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            activity.putNonConfigurationInstance(Object.class, null);
            activity.recreate();
        });

        mActivityRule.runOnUiThread(() -> {
            ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

            Object actual = activity.getNonConfigurationInstance(Object.class);
            assertNull(actual);
        });
    }

    @Test
    @UiThreadTest
    @SuppressWarnings({"unchecked", "rawtypes"}) // Intentionally bypassing safety.
    public void storingIncorrectTypeThrows() {
        ActivityNonConfigInstanceActivity activity = mActivityRule.getActivity();

        try {
            activity.putNonConfigurationInstance((Class) String.class, new Object());
            fail();
        } catch (ClassCastException expected) {
        }
    }
}
