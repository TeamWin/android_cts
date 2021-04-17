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

import static com.android.compatibility.common.util.ActivitiesWatcher.ActivityLifecycle.RESUMED;

import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.os.CancellationSignal;
import android.platform.test.annotations.AppModeFull;
import android.util.ArraySet;
import android.util.Log;
import android.view.translation.TranslationCapability;
import android.view.translation.TranslationContext;
import android.view.translation.TranslationManager;
import android.view.translation.TranslationRequest;
import android.view.translation.TranslationRequestValue;
import android.view.translation.TranslationResponse;
import android.view.translation.TranslationResponseValue;
import android.view.translation.TranslationSpec;
import android.view.translation.Translator;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ActivitiesWatcher;
import com.android.compatibility.common.util.ActivitiesWatcher.ActivityWatcher;
import com.android.compatibility.common.util.RequiredServiceRule;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
    public final RequiredServiceRule mServiceRule = new RequiredServiceRule(
            android.content.Context.TRANSLATION_MANAGER_SERVICE);

    private static final String TAG = "BasicTranslationTest";

    private CtsTranslationService.ServiceWatcher mServiceWatcher;
    private ActivitiesWatcher mActivitiesWatcher;

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
        if (mActivitiesWatcher != null) {
            final Application app = (Application) ApplicationProvider.getApplicationContext();
            app.unregisterActivityLifecycleCallbacks(mActivitiesWatcher);
        }
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

        final TranslationContext translationContext = new TranslationContext.Builder(
                new TranslationSpec(Locale.ENGLISH.getLanguage(),
                        TranslationSpec.DATA_FORMAT_TEXT),
                new TranslationSpec(Locale.FRENCH.getLanguage(),
                        TranslationSpec.DATA_FORMAT_TEXT))
                .build();
        final Translator translator = manager.createOnDeviceTranslator(translationContext);

        try {
            mServiceWatcher.waitOnConnected();
        } catch (InterruptedException e) {
            Log.w(TAG, "Exception waiting for onConnected");
        }

        assertThat(translator.isDestroyed()).isFalse();

        final Consumer<TranslationResponse> callback = new Consumer<TranslationResponse>() {
            @Override
            public void accept(TranslationResponse translationResponse) {
                responseRef.set(translationResponse);
                translationLatch.countDown();
            }
        };

        translator.translate(new TranslationRequest.Builder()
                .addTranslationRequestValue(TranslationRequestValue.forText("hello world"))
                .build(), new CancellationSignal(), (r) -> r.run(), callback);

        sTranslationReplier.getNextTranslationRequest();

        translator.destroy();
        assertThat(translator.isDestroyed()).isTrue();
        try {
            mServiceWatcher.waitOnDisconnected();
        } catch (InterruptedException e) {
            Log.w(TAG, "Exception waiting for onDisconnected");
        }

        // Wait for translation to finish
        translationLatch.await();
        sTranslationReplier.assertNoUnhandledTranslationRequests();

        final TranslationResponse response = responseRef.get();
        Log.v(TAG, "TranslationResponse=" + response);

        assertThat(response).isNotNull();
        assertThat(response.getTranslationStatus())
                .isEqualTo(TranslationResponse.TRANSLATION_STATUS_SUCCESS);
        assertThat(response.isFinalResponse()).isTrue();
        assertThat(response.getTranslationResponseValues().size()).isEqualTo(1);
        assertThat(response.getViewTranslationResponses().size()).isEqualTo(0);

        final TranslationResponseValue value = response.getTranslationResponseValues().get(0);
        assertThat(value.getStatusCode()).isEqualTo(TranslationResponseValue.STATUS_SUCCESS);
        assertThat(value.getText()).isEqualTo("success");
        assertThat(value.getTransliteration()).isNull();
        assertThat(value.getDictionaryDescription()).isNull();
    }

    @Test
    public void testTranslationCancelled() throws Exception{
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

        final TranslationContext translationContext = new TranslationContext.Builder(
                new TranslationSpec(Locale.ENGLISH.getLanguage(),
                        TranslationSpec.DATA_FORMAT_TEXT),
                new TranslationSpec(Locale.FRENCH.getLanguage(),
                        TranslationSpec.DATA_FORMAT_TEXT))
                .build();
        final Translator translator = manager.createOnDeviceTranslator(translationContext);

        try {
            mServiceWatcher.waitOnConnected();
        } catch (InterruptedException e) {
            Log.w(TAG, "Exception waiting for onConnected");
        }

        assertThat(translator.isDestroyed()).isFalse();

        final Consumer<TranslationResponse> callback = new Consumer<TranslationResponse>() {
            @Override
            public void accept(TranslationResponse translationResponse) {
                responseRef.set(translationResponse);
                translationLatch.countDown();
            }
        };

        final CancellationSignal cancellationSignal = new CancellationSignal();

        translator.translate(new TranslationRequest.Builder()
                .addTranslationRequestValue(TranslationRequestValue.forText("hello world"))
                .build(), cancellationSignal, (r) -> r.run(), callback);

        // TODO: implement with cancellation signal listener
        // cancel translation request
        cancellationSignal.cancel();

        sTranslationReplier.assertNoUnhandledTranslationRequests();

        translator.destroy();
        assertThat(translator.isDestroyed()).isTrue();
        try {
            mServiceWatcher.waitOnDisconnected();
        } catch (InterruptedException e) {
            Log.w(TAG, "Exception waiting for onDisconnected");
        }
    }

    @Test
    public void testGetTranslationCapabilities() throws Exception{
        enableCtsTranslationService();

        final TranslationManager manager = sInstrumentation.getContext().getSystemService(
                TranslationManager.class);
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Set<TranslationCapability>> resultRef =
                new AtomicReference<>();

        final Thread th = new Thread(() -> {
            final Set<TranslationCapability> capabilities =
                    manager.getOnDeviceTranslationCapabilities(TranslationSpec.DATA_FORMAT_TEXT,
                            TranslationSpec.DATA_FORMAT_TEXT);
            resultRef.set(capabilities);
            latch.countDown();
        });
        th.start();
        latch.await();

        final ArraySet<TranslationCapability> capabilities = new ArraySet<>(resultRef.get());
        assertThat(capabilities.size()).isEqualTo(1);
        capabilities.forEach((capability) -> {
            assertThat(capability.getState()).isEqualTo(TranslationCapability.STATE_ON_DEVICE);

            assertThat(capability.getSupportedTranslationFlags()).isEqualTo(0);
            assertThat(capability.isUiTranslationEnabled()).isTrue();
            assertThat(capability.getSourceSpec().getLanguage()).isEqualTo("en");
            assertThat(capability.getSourceSpec().getDataFormat())
                    .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);
            assertThat(capability.getTargetSpec().getLanguage()).isEqualTo("es");
            assertThat(capability.getTargetSpec().getDataFormat())
                    .isEqualTo(TranslationSpec.DATA_FORMAT_TEXT);
        });
    }

    @Test
    public void testGetTranslationSettingsActivityIntent() throws Exception{
        enableCtsTranslationService();

        final TranslationManager manager = sInstrumentation.getContext().getSystemService(
                TranslationManager.class);
        final PendingIntent pendingIntent = manager.getOnDeviceTranslationSettingsActivityIntent();

        assertThat(pendingIntent).isNotNull();
        assertThat(pendingIntent.isImmutable()).isTrue();

        // Start Settings Activity and verify if the expected Activity resumed
        mActivitiesWatcher = new ActivitiesWatcher(5_000);
        final Application app = (Application) ApplicationProvider.getApplicationContext();
        app.registerActivityLifecycleCallbacks(mActivitiesWatcher);
        final ActivityWatcher watcher = mActivitiesWatcher.watch(SimpleActivity.class);

        pendingIntent.send();

        watcher.waitFor(RESUMED);
    }

    //TODO(183605243): add test for cancelling translation.

    protected void enableCtsTranslationService() {
        mServiceWatcher = CtsTranslationService.setServiceWatcher();
        Helper.setTemporaryTranslationService(CtsTranslationService.SERVICE_NAME);
    }
}
