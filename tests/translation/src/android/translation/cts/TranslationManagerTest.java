/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.translation.cts;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.translation.TranslationManager;
import android.view.translation.TranslationRequest;
import android.view.translation.TranslationRequestValue;
import android.view.translation.TranslationResponse;
import android.view.translation.TranslationResponseValue;
import android.view.translation.TranslationSpec;
import android.view.translation.Translator;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.compatibility.common.util.RequiredServiceRule;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for {@link TranslationManager} related APIs.
 *
 * <p>
 * We use a non-standard {@link android.service.translation.TranslationService} for e2e CTS tests
 * that is set via shell command. This temporary service is not defined in the trusted
 * TranslationService, it should only receive queries from clients in the same package.</p>
 */
@AppModeFull(reason = "TODO(b/182330968): disable instant mode. Re-enable after we decouple the "
        + "service from the test package.")
@RunWith(AndroidJUnit4.class)
public class TranslationManagerTest {

    @Rule
    public final RequiredFeatureRule mFeatureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_TRANSLATION);

    @Rule
    public final RequiredServiceRule mServiceRule = new RequiredServiceRule(
            android.content.Context.TRANSLATION_MANAGER_SERVICE);

    private static final String TAG = "BasicTranslationTest";

    private CtsTranslationService.ServiceWatcher mServiceWatcher;

    private static Instrumentation sInstrumentation;
    private static CtsTranslationService.TranslationReplier sTranslationReplier;

    @BeforeClass
    public static void oneTimeSetup() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sTranslationReplier = CtsTranslationService.getTranslationReplier();
    }

    @Before
    public void setup() {
        CtsTranslationService.resetStaticState();
    }

    @After
    public void cleanup() {
        Helper.resetTemporaryTranslationService();
    }

    @Test
    public void testSingleTranslation() throws Exception{
        enableCtsTranslationService();

        final TranslationManager manager = sInstrumentation.getContext().getSystemService(
                TranslationManager.class);

        sTranslationReplier.addResponse(
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS)
                        .setTranslationResponseValue(0, new TranslationResponseValue
                                .Builder(TranslationResponseValue.STATUS_SUCCESS)
                                .setText("success")
                                .build())
                        .build());

        final CountDownLatch translationLatch = new CountDownLatch(1);
        final AtomicReference<TranslationResponse> responseRef = new AtomicReference<>();

        final Thread th = new Thread(() -> {
            final Translator translator = manager.createTranslator(
                    new TranslationSpec(Locale.ENGLISH.getLanguage(),
                            TranslationSpec.DATA_FORMAT_TEXT),
                    new TranslationSpec(Locale.FRENCH.getLanguage(),
                            TranslationSpec.DATA_FORMAT_TEXT));
            try {
                mServiceWatcher.waitOnConnected();
            } catch (InterruptedException e) {
                Log.w(TAG, "Exception waiting for onConnected");
            }
            assertThat(translator.isDestroyed()).isFalse();

            final TranslationResponse response = translator.translate(
                    new TranslationRequest.Builder()
                            .addTranslationRequestValue(
                                    TranslationRequestValue.forText("hello world"))
                            .build());

            sTranslationReplier.getNextTranslationRequest();

            responseRef.set(response);
            translationLatch.countDown();

            translator.destroy();
            assertThat(translator.isDestroyed()).isTrue();
            try {
                mServiceWatcher.waitOnDisconnected();
            } catch (InterruptedException e) {
                Log.w(TAG, "Exception waiting for onDisconnected");
            }
        });

        th.start();
        translationLatch.await();
        sTranslationReplier.assertNoUnhandledTranslationRequests();

        final TranslationResponse response = responseRef.get();
        Log.v(TAG, "TranslationResponse=" + response);

        assertThat(response).isNotNull();
        assertThat(response.getTranslationStatus())
                .isEqualTo(TranslationResponse.TRANSLATION_STATUS_SUCCESS);
        assertThat(response.getTranslationResponseValues().size()).isEqualTo(1);
        assertThat(response.getViewTranslationResponses().size()).isEqualTo(0);

        final TranslationResponseValue value = response.getTranslationResponseValues().get(0);
        assertThat(value.getStatusCode()).isEqualTo(TranslationResponseValue.STATUS_SUCCESS);
        assertThat(value.getText()).isEqualTo("success");
        assertThat(value.getTransliteration()).isNull();
        assertThat(value.getDictionaryDescription()).isNull();
    }

    @Test
    public void testGetSupportedLocales() throws Exception{
        enableCtsTranslationService();

        final TranslationManager manager = sInstrumentation.getContext().getSystemService(
                TranslationManager.class);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<List<String>> resultRef = new AtomicReference<>();

        final Thread th = new Thread(() -> {
            final List<String> supportLocales = manager.getSupportedLocales();
            // TODO(b/178651514): empty implementation not forward to service, should update tests
            resultRef.set(supportLocales);
            latch.countDown();
        });
        th.start();
        latch.await();

        final List<String> supportLocales  = resultRef.get();
        // TODO(b/178651514): empty implementation and has bug now. It will return null instead of
        //  empty list
        assertThat(supportLocales).isNull();
    }

    protected void enableCtsTranslationService() {
        mServiceWatcher = CtsTranslationService.setServiceWatcher();
        Helper.setTemporaryTranslationService(CtsTranslationService.SERVICE_NAME);
    }
}
