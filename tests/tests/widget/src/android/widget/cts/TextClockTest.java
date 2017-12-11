/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.format.DateFormat;
import android.widget.TextClock;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link TextClock}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TextClockTest {
    private Activity mActivity;
    private TextClock mTextClock;
    private boolean mStartedAs24;

    @Rule
    public ActivityTestRule<TextClockCtsActivity> mActivityRule =
            new ActivityTestRule<>(TextClockCtsActivity.class);

    @Before
    public void setup() throws Throwable {
        mActivity = mActivityRule.getActivity();
        mTextClock = mActivity.findViewById(R.id.textclock);
        mStartedAs24 = DateFormat.is24HourFormat(mActivity);
    }

    public void teardown() throws Throwable {
        int base = mStartedAs24 ? 24 : 12;
        Settings.System.putInt(mActivity.getContentResolver(), Settings.System.TIME_12_24, base);
    }

    @Test
    public void testUpdate12_24() throws Throwable {
        grantWriteSettingsPermission();
        final ContentResolver resolver = mActivity.getContentResolver();
        Calendar mNow = Calendar.getInstance();
        // make sure the clock is showing some time > 12pm and not near midnight
        for (String id : TimeZone.getAvailableIDs()) {
            final TimeZone timeZone = TimeZone.getTimeZone(id);
            mNow.setTimeZone(timeZone);
            int hour = mNow.get(Calendar.HOUR_OF_DAY);
            if (hour < 22 && hour > 12) {
                mActivityRule.runOnUiThread(() -> {
                    mTextClock.setTimeZone(timeZone.getID());
                });
                break;
            }
        }

        final CountDownLatch twelveTwentyFourChange = registerForChanges(Settings.System.TIME_12_24,
                2);

        mActivityRule.runOnUiThread(() -> {
            Settings.System.putInt(resolver, Settings.System.TIME_12_24, 12);
            mTextClock.setFormat12Hour("h");
            mTextClock.setFormat24Hour("H");
        });

        mActivityRule.runOnUiThread(() -> {
            int hour = Integer.parseInt(mTextClock.getText().toString());
            assertTrue(hour >= 1 && hour < 12);
            Settings.System.putInt(resolver, Settings.System.TIME_12_24, 24);
        });

        assertTrue(twelveTwentyFourChange.await(1, TimeUnit.SECONDS));

        mActivityRule.runOnUiThread(() -> {
            int hour = Integer.parseInt(mTextClock.getText().toString());
            assertTrue(hour > 12 && hour < 24);
        });

        // Now test that it isn't updated when a non-12/24 hour setting is set
        mActivityRule.runOnUiThread(() -> {
            mTextClock.setText("Nothing");
        });

        mActivityRule.runOnUiThread(() -> {
            assertEquals("Nothing", mTextClock.getText().toString());
        });

        final CountDownLatch otherChange = registerForChanges(Settings.System.TEXT_AUTO_CAPS, 1);
        mActivityRule.runOnUiThread(() -> {
            int autoCaps = 0;
            try {
                autoCaps = Settings.System.getInt(resolver, Settings.System.TEXT_AUTO_CAPS);
            } catch (Settings.SettingNotFoundException e) {
                // the setting hasn't been set before. Just default to 0
            }
            try {
                int newVal = autoCaps == 0 ? 1 : 0;
                Settings.System.putInt(resolver, Settings.System.TEXT_AUTO_CAPS, newVal);
            } finally {
                Settings.System.putInt(resolver, Settings.System.TEXT_AUTO_CAPS,
                        autoCaps);
            }
        });

        assertTrue(otherChange.await(1, TimeUnit.SECONDS));

        mActivityRule.runOnUiThread(() -> {
            assertEquals("Nothing", mTextClock.getText().toString());
        });
    }

    private CountDownLatch registerForChanges(String setting, int changes) throws Throwable {
        final CountDownLatch latch = new CountDownLatch(changes);

        mActivityRule.runOnUiThread(() -> {
            final ContentResolver resolver = mActivity.getContentResolver();
            Uri uri = Settings.System.getUriFor(setting);
            resolver.registerContentObserver(uri, true,
                    new ContentObserver(new Handler()) {
                        @Override
                        public void onChange(boolean selfChange) {
                            countDownAndRemove();
                        }

                        @Override
                        public void onChange(boolean selfChange, Uri uri, int userId) {
                            countDownAndRemove();
                        }

                        private void countDownAndRemove() {
                            latch.countDown();
                            if (latch.getCount() == 0) {
                                resolver.unregisterContentObserver(this);
                            }
                        }
                    });
        });
        return latch;
    }

    private void grantWriteSettingsPermission() throws IOException {
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "appops set " + mActivity.getPackageName() + " "
                        + AppOpsManager.OPSTR_WRITE_SETTINGS + " allow");
    }
}
