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

package android.voiceinteraction.cts;

import static android.service.voice.VoiceInteractionSession.KEY_SHOW_SESSION_ID;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

/**
 * Tests for {@link VoiceInteractionService} APIs.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode")
public class VoiceInteractionServiceTest {

    private static final String TAG = "VoiceInteractionServiceTest";
    private static final String KEY_SHOW_SESSION_TEST = "showSessionTest";

    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";
    protected final Context mContext = getInstrumentation().getTargetContext();

    private CtsBasicVoiceInteractionService mService;

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(mContext, getTestVoiceInteractionService());

    public String getTestVoiceInteractionService() {
        Log.d(TAG, "getTestVoiceInteractionService()");
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }

    @Before
    public void setup() {
        // VoiceInteractionServiceConnectedRule handles the service connected, we should be
        // able to get service
        mService = (CtsBasicVoiceInteractionService) BaseVoiceInteractionService.getService();
        // Check we can get the service, we need service object to call the service provided method
        Objects.requireNonNull(mService);
    }

    @After
    public void tearDown() {
        mService = null;
    }

    @Test
    public void testShowSession_onPrepareToShowSessionCalled() throws Exception {
        final Bundle args = new Bundle();
        final int value = 100;
        args.putInt(KEY_SHOW_SESSION_TEST, value);
        final int flags = VoiceInteractionSession.SHOW_WITH_ASSIST;

        BaseVoiceInteractionService.initShowSessionLatch();
        mService.showSession(args, flags);
        BaseVoiceInteractionService.waitOnPrepareToShowSession();

        final Bundle resultArgs = mService.getPrepareToShowSessionArgs();
        assertThat(resultArgs.getInt(KEY_SHOW_SESSION_TEST, /* defaultValue= */ -1))
                .isEqualTo(value);
        assertThat(resultArgs.containsKey(KEY_SHOW_SESSION_ID)).isTrue();
        assertThat(mService.getPrepareToShowSessionFlags()).isEqualTo(flags);
    }

    @Test
    public void testShowSessionWithNullArgs_onPrepareToShowSessionCalledHasId() throws Exception {
        final int flags = VoiceInteractionSession.SHOW_WITH_ASSIST;

        BaseVoiceInteractionService.initShowSessionLatch();
        mService.showSession(/* args= */ null, flags);
        BaseVoiceInteractionService.waitOnPrepareToShowSession();

        final Bundle resultArgs = mService.getPrepareToShowSessionArgs();
        assertThat(resultArgs).isNotNull();
        assertThat(resultArgs.containsKey(KEY_SHOW_SESSION_ID)).isTrue();
        assertThat(mService.getPrepareToShowSessionFlags()).isEqualTo(flags);
    }

    @Test
    public void testShowSession_onPrepareToShowSessionCalledTwiceIdIsDifferent() throws Exception {
        final Bundle args = new Bundle();
        final int flags = 0;

        // trigger showSession first time
        BaseVoiceInteractionService.initShowSessionLatch();
        mService.showSession(args, flags);
        BaseVoiceInteractionService.waitOnPrepareToShowSession();

        // get the first showSession id
        Bundle resultArgs = mService.getPrepareToShowSessionArgs();
        assertThat(resultArgs.containsKey(KEY_SHOW_SESSION_ID)).isTrue();
        final int firstId = resultArgs.getInt(KEY_SHOW_SESSION_ID);

        // trigger showSession second time
        BaseVoiceInteractionService.initShowSessionLatch();
        mService.showSession(args, flags);
        BaseVoiceInteractionService.waitOnPrepareToShowSession();

        // get the second showSession id
        resultArgs = mService.getPrepareToShowSessionArgs();
        assertThat(resultArgs.containsKey(KEY_SHOW_SESSION_ID)).isTrue();
        final int secondId = resultArgs.getInt(KEY_SHOW_SESSION_ID);

        assertThat(secondId).isGreaterThan(firstId);
    }
}
