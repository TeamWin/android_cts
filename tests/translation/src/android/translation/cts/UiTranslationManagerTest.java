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

import static android.content.Context.CONTENT_CAPTURE_MANAGER_SERVICE;
import static android.content.Context.TRANSLATION_MANAGER_SERVICE;
import static android.view.translation.TranslationResponseValue.STATUS_SUCCESS;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.service.contentcapture.ContentCaptureService;
import android.service.translation.TranslationService;
import android.util.Log;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureContext;
import android.view.translation.TranslationManager;
import android.view.translation.TranslationRequest;
import android.view.translation.TranslationResponse;
import android.view.translation.TranslationResponseValue;
import android.view.translation.TranslationSpec;
import android.view.translation.UiTranslationManager;
import android.view.translation.ViewTranslationRequest;
import android.view.translation.ViewTranslationResponse;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.compatibility.common.util.RequiredServiceRule;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Locale;

/**
 * Tests for {@link UiTranslationManager} related APIs.
 *
 * <p>
 * {@link UiTranslationManager} needs a token that reports by {@link ContentCaptureService}. We use
 * a non pre-configured {@link ContentCaptureService} and a {@link TranslationService} temporary
 * service for CTS tests that is set via shell command. The test will get the token from the
 * {@link ContentCaptureService} then uses this token in {@link UiTranslationManager} APIs.</p>
 */
@RunWith(AndroidJUnit4.class)
public class UiTranslationManagerTest {

    private static final String TAG = "UiTranslationManagerTest";

    private static final long UI_WAIT_TIMEOUT = 2000;

    private static Context sContext;
    private static CtsTranslationService.TranslationReplier sTranslationReplier;

    private CtsContentCaptureService.ServiceWatcher mContentCaptureServiceWatcher;
    private CtsTranslationService.ServiceWatcher mTranslationServiceServiceWatcher;
    private ActivityScenario<SimpleActivity> mActivityScenario;

    @Rule
    public final RequiredFeatureRule mFeatureRule =
            new RequiredFeatureRule(PackageManager.FEATURE_TRANSLATION);
    @Rule
    public final RequiredServiceRule mContentCaptureServiceRule =
            new RequiredServiceRule(CONTENT_CAPTURE_MANAGER_SERVICE);

    @Rule
    public final RequiredServiceRule mTranslationServiceRule =
            new RequiredServiceRule(TRANSLATION_MANAGER_SERVICE);

    @BeforeClass
    public static void oneTimeSetup() {
        sContext = ApplicationProvider.getApplicationContext();
        sTranslationReplier = CtsTranslationService.getTranslationReplier();

        Helper.allowSelfForContentCapture(sContext);
        Helper.setDefaultContentCaptureServiceEnabled(/* enabled= */ false);
    }

    @AfterClass
    public static void oneTimeReset() {
        Helper.unAllowSelfForContentCapture(sContext);
        Helper.setDefaultContentCaptureServiceEnabled(/* enabled= */ true);
    }

    @Before
    public void setup() throws Exception {
        prepareDevice();
        CtsContentCaptureService.resetStaticState();
        CtsTranslationService.resetStaticState();
    }

    @After
    public void cleanup() throws Exception {
        mActivityScenario.moveToState(Lifecycle.State.DESTROYED);

        Helper.resetTemporaryContentCaptureService();
        Helper.resetTemporaryTranslationService();
    }

    private void prepareDevice() throws Exception {
        // Unlock screen.
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        // Dismiss keyguard, in case it's set as "Swipe to unlock".
        runShellCommand("wm dismiss-keyguard");
        // Collapse notifications.
        runShellCommand("cmd statusbar collapse");
    }

    @Test
    public void testUiTranslation() throws Throwable {
        // Enable CTS ContentCaptureService
        final CtsContentCaptureService contentcaptureService = enableContentCaptureService();

        // Start Activity and get needed information
        final Intent intent = new Intent(sContext, SimpleActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mActivityScenario = ActivityScenario.launch(intent);
        final AtomicReference<CharSequence> originalTextRef = new AtomicReference<>();
        final AtomicReference<List<AutofillId>> viewAutofillIdsTextRef = new AtomicReference<>();
        mActivityScenario.onActivity(activity -> {
            originalTextRef.set(activity.getHelloText().getText());
            viewAutofillIdsTextRef.set(activity.getViewsForTranslation());
        });
        final CharSequence originalText = originalTextRef.get();
        // Get the views that need to be translated.
        final List<AutofillId> views = viewAutofillIdsTextRef.get();

        // Wait session created and get the ConttCaptureContext from ContentCaptureService
        final ContentCaptureContext contentCaptureContext =
                getContentCaptureContextFromContentCaptureService(contentcaptureService);

        // enable CTS TranslationService
        mTranslationServiceServiceWatcher = CtsTranslationService.setServiceWatcher();
        Helper.setTemporaryTranslationService(CtsTranslationService.SERVICE_NAME);

        final String translatedText = "success";
        final UiTranslationManager manager = sContext.getSystemService(UiTranslationManager.class);
        final UiObject2 helloText = Helper.findObjectByResId(SimpleActivity.ACTIVITY_PACKAGE,
                SimpleActivity.HELLO_TEXT_ID);
        assertThat(helloText).isNotNull();
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, translatedText));

        runWithShellPermissionIdentity(() -> {
            // Call startTranslation API
            manager.startTranslation(
                    new TranslationSpec(Locale.ENGLISH.getLanguage(),
                            TranslationSpec.DATA_FORMAT_TEXT),
                    new TranslationSpec(Locale.FRENCH.getLanguage(),
                            TranslationSpec.DATA_FORMAT_TEXT),
                    views, contentCaptureContext.getActivityId());

            // Check request
            final TranslationRequest request = sTranslationReplier.getNextTranslationRequest();
            final List<ViewTranslationRequest> requests = request.getViewTranslationRequests();
            final ViewTranslationRequest viewRequest = requests.get(0);
            assertThat(viewRequest.getAutofillId()).isEqualTo(views.get(0));
            assertThat(viewRequest.getKeys().size()).isEqualTo(1);
            assertThat(viewRequest.getKeys()).containsExactly(ViewTranslationRequest.ID_TEXT);
            assertThat(viewRequest.getValue(ViewTranslationRequest.ID_TEXT).getText())
                    .isEqualTo(originalText);

            SystemClock.sleep(UI_WAIT_TIMEOUT);
            assertThat(helloText.getText()).isEqualTo(translatedText);

            // Call pauseTranslation API
            manager.pauseTranslation(contentCaptureContext.getActivityId());

            SystemClock.sleep(UI_WAIT_TIMEOUT);
            assertThat(helloText.getText()).isEqualTo(originalText);

            // Call resumeTranslation API
            manager.resumeTranslation(contentCaptureContext.getActivityId());

            SystemClock.sleep(UI_WAIT_TIMEOUT);
            assertThat(helloText.getText()).isEqualTo(translatedText);

            // Call finishTranslation API
            manager.finishTranslation(contentCaptureContext.getActivityId());

            SystemClock.sleep(UI_WAIT_TIMEOUT);
            assertThat(helloText.getText()).isEqualTo(originalText);

            // Check the Translation session is destroyed after calling finishTranslation()
            CtsTranslationService translationService =
                    mTranslationServiceServiceWatcher.getService();
            translationService.awaitSessionDestroyed();
        });
    }

    private CtsContentCaptureService enableContentCaptureService() throws Exception {
        mContentCaptureServiceWatcher = CtsContentCaptureService.setServiceWatcher();
        Helper.setTemporaryContentCaptureService(CtsContentCaptureService.SERVICE_NAME);
        mContentCaptureServiceWatcher.setAllowSelf();
        return mContentCaptureServiceWatcher.waitOnConnected();
    }

    private ContentCaptureContext getContentCaptureContextFromContentCaptureService(
            CtsContentCaptureService service) {
        service.awaitSessionCreated(CtsContentCaptureService.GENERIC_TIMEOUT_MS);
        final ContentCaptureContext contentCaptureContext = service.getContentCaptureContext();
        Log.d(TAG, "contentCaptureContext = " + contentCaptureContext);

        assertThat(contentCaptureContext).isNotNull();
        assertThat(contentCaptureContext.getActivityId()).isNotNull();

        return contentCaptureContext;
    }

    private TranslationResponse createViewsTranslationResponse(List<AutofillId> viewAutofillIds,
            String translatedText) {
        final TranslationResponse.Builder responseBuilder =
                new TranslationResponse.Builder(TranslationResponse.TRANSLATION_STATUS_SUCCESS);
        for (int i = 0; i < viewAutofillIds.size(); i++) {
            ViewTranslationResponse.Builder responseDataBuilder =
                    new ViewTranslationResponse.Builder(viewAutofillIds.get(i))
                            .setValue(ViewTranslationRequest.ID_TEXT,
                                    new TranslationResponseValue.Builder(STATUS_SUCCESS)
                                            .setText(translatedText).build());
            responseBuilder.setViewTranslationResponse(i, responseDataBuilder.build());
        }
        return responseBuilder.build();
    }
}
