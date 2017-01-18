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

package com.android.cts.ssaidapp1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ClientTest {
    private static final String EXTRA_SSAID = "SSAID";
    private static final String PREF_SSAID = "SSAID";
    private static final String INTENT_RECEIVER_PKG = "com.android.cts.ssaidapp2";
    private static final String INTENT_SENDER_PKG = "com.android.cts.ssaidapp1";
    private static final String RECEIVER_ACTIVTIY_NAME = "com.android.cts.ssaidapp2.SsaidActivity";
    private static final String SENDER_ACTIVTIY_NAME = "com.android.cts.ssaidapp1.SsaidActivity";

    private SsaidActivity mActivity;
    private Context mContext;

    private SharedPreferences mSharedPref;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mActivity = startActivity();
        mSharedPref = mActivity.getPreferences(Context.MODE_PRIVATE);
    }

    private SsaidActivity startActivity() {
        final Intent intent = new Intent();
        intent.setClassName(INTENT_SENDER_PKG, SENDER_ACTIVTIY_NAME);
        final Activity activity = InstrumentationRegistry.getInstrumentation()
            .startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return (SsaidActivity) activity;
    }

    @After
    public void tearDown() throws Exception {
        mActivity.finish();
    }

    @Test
    public void testValidSsaid() throws Exception {
        final ContentResolver r = mContext.getContentResolver();
        final String ssaid = Settings.Secure.getString(r, Settings.Secure.ANDROID_ID);

        assertNotNull(ssaid);
        assertEquals(16, ssaid.length());

        final String ssaid2 = Settings.Secure.getString(r, Settings.Secure.ANDROID_ID);
        assertNotNull(ssaid2);
        assertEquals(16, ssaid2.length());

        assertEquals(ssaid, ssaid2);
    }

    @Test
    public void testFirstInstallSsaid() throws Exception {
        final Intent intent = new Intent();
        intent.setClassName(INTENT_RECEIVER_PKG, RECEIVER_ACTIVTIY_NAME);
        final Intent result = mActivity.getResult(intent);
        assertNotNull(result);

        final SharedPreferences.Editor editor = mSharedPref.edit();
        editor.putString(PREF_SSAID, result.getStringExtra(EXTRA_SSAID));
        editor.commit();
    }

    @Test
    public void testSecondInstallSsaid() throws Exception {
        final Intent intent = new Intent();
        intent.setClassName(INTENT_RECEIVER_PKG, RECEIVER_ACTIVTIY_NAME);
        final Intent result = mActivity.getResult(intent);
        assertNotNull(result);

        final String firstInstallSsaid = mSharedPref.getString(PREF_SSAID, null);
        assertNotNull(firstInstallSsaid);
        assertEquals(firstInstallSsaid, result.getStringExtra(EXTRA_SSAID));
    }

    @Test
    public void testAppsReceiveDifferentSsaid() throws Exception {
        final Intent intent = new Intent();
        intent.setClassName(INTENT_RECEIVER_PKG, RECEIVER_ACTIVTIY_NAME);
        final Intent result = mActivity.getResult(intent);
        assertNotNull(result);

        final ContentResolver r = mContext.getContentResolver();
        final String ssaid = Settings.Secure.getString(r, Settings.Secure.ANDROID_ID);

        assertNotEquals(ssaid, result.getStringExtra(EXTRA_SSAID));
    }
}
