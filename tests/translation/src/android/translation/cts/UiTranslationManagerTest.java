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
import static android.provider.Settings.Secure.ENABLED_INPUT_METHODS;
import static android.translation.cts.Helper.ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_FINISH;
import static android.translation.cts.Helper.ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_START;
import static android.translation.cts.Helper.ACTION_REGISTER_UI_TRANSLATION_CALLBACK;
import static android.translation.cts.Helper.ACTION_UNREGISTER_UI_TRANSLATION_CALLBACK;
import static android.translation.cts.Helper.EXTRA_FINISH_COMMAND;
import static android.translation.cts.Helper.EXTRA_SOURCE_LOCALE;
import static android.translation.cts.Helper.EXTRA_TARGET_LOCALE;
import static android.translation.cts.Helper.EXTRA_VERIFY_RESULT;
import static android.translation.cts.Helper.Triple;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.icu.util.ULocale;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.service.contentcapture.ContentCaptureService;
import android.service.translation.TranslationService;
import android.util.Log;
import android.view.autofill.AutofillId;
import android.view.contentcapture.ContentCaptureContext;
import android.view.inputmethod.InputMethodManager;
import android.view.translation.TranslationManager;
import android.view.translation.TranslationRequest;
import android.view.translation.TranslationResponse;
import android.view.translation.TranslationResponseValue;
import android.view.translation.TranslationSpec;
import android.view.translation.UiTranslationManager;
import android.view.translation.UiTranslationStateCallback;
import android.view.translation.ViewTranslationRequest;
import android.view.translation.ViewTranslationResponse;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.RequiredServiceRule;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

@AppModeFull(reason = "TODO(b/182330968): disable instant mode. Re-enable after we decouple the "
        + "service from the test package.")
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
        final Triple<CharSequence, List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final CharSequence originalText = result.getFirst();
        final List<AutofillId> views = result.getSecond();
        final ContentCaptureContext contentCaptureContext = result.getThird();

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
                    new TranslationSpec(ULocale.ENGLISH,
                            TranslationSpec.DATA_FORMAT_TEXT),
                    new TranslationSpec(ULocale.FRENCH,
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

    @Test
    public void testIMEUiTranslationStateCallback() throws Throwable {
        try (ImeSession imeSession = new ImeSession(
                new ComponentName(CtsTestIme.IME_SERVICE_PACKAGE, CtsTestIme.class.getName()))) {

            final Triple<CharSequence, List<AutofillId>, ContentCaptureContext> result =
                    enableServicesAndStartActivityForTranslation();
            final List<AutofillId> views = result.getSecond();
            final ContentCaptureContext contentCaptureContext = result.getThird();
            final UiTranslationManager manager =
                    sContext.getSystemService(UiTranslationManager.class);
            sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

            // Send broadcat to request IME to register callback
            BlockingBroadcastReceiver registerResultReceiver =
                    sendCommandToIme(ACTION_REGISTER_UI_TRANSLATION_CALLBACK, false, false);
            // Get result
            registerResultReceiver.awaitForBroadcast();
            registerResultReceiver.unregisterQuietly();

            runWithShellPermissionIdentity(() -> {
                // Call startTranslation API
                manager.startTranslation(
                        new TranslationSpec(ULocale.ENGLISH,
                                TranslationSpec.DATA_FORMAT_TEXT),
                        new TranslationSpec(ULocale.FRENCH,
                                TranslationSpec.DATA_FORMAT_TEXT),
                        views, contentCaptureContext.getActivityId());
                SystemClock.sleep(UI_WAIT_TIMEOUT);
            });
            // Send broadcat to request IME to check the callback result
            BlockingBroadcastReceiver onStartResultReceiver = sendCommandToIme(
                    ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_START, true, true);
            // Get result to check the onStart() was called
            Intent onStartIntent = onStartResultReceiver.awaitForBroadcast();
            boolean onStartVerifyResult = onStartIntent.getBooleanExtra(EXTRA_VERIFY_RESULT, false);
            assertThat(onStartVerifyResult).isTrue();
            onStartResultReceiver.unregisterQuietly();

            // Send broadcat to request IME to unregister callback
            BlockingBroadcastReceiver unRegisterResultReceiver
                    = sendCommandToIme(ACTION_UNREGISTER_UI_TRANSLATION_CALLBACK, false, false);
            unRegisterResultReceiver.awaitForBroadcast();
            unRegisterResultReceiver.unregisterQuietly();

            // Call finishTranslation API
            runWithShellPermissionIdentity(() -> {
                manager.finishTranslation(contentCaptureContext.getActivityId());
                SystemClock.sleep(UI_WAIT_TIMEOUT);
            });
            BlockingBroadcastReceiver onFinishResultReceiver =
                    sendCommandToIme(ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_FINISH, false, true);
            // Get result to check onFinish() didn't be called.
            Intent onFinishIntent = onFinishResultReceiver.awaitForBroadcast();
            boolean onFinishVerifyResult =
                    onFinishIntent.getBooleanExtra(EXTRA_VERIFY_RESULT, true);
            assertThat(onFinishVerifyResult).isFalse();
            onFinishResultReceiver.unregisterQuietly();
        }
    }

    @Test
    public void testNonIMEUiTranslationStateCallback() throws Throwable {
        final Triple<CharSequence, List<AutofillId>, ContentCaptureContext> result =
                enableServicesAndStartActivityForTranslation();

        final List<AutofillId> views = result.getSecond();
        final ContentCaptureContext contentCaptureContext = result.getThird();

        UiTranslationManager manager =
                sContext.getSystemService(UiTranslationManager.class);
        // Set response
        sTranslationReplier.addResponse(createViewsTranslationResponse(views, "success"));

        // Register callback
        final Executor executor = Executors.newSingleThreadExecutor();
        final TestTranslationStateCallback callback = new TestTranslationStateCallback();
        manager.registerUiTranslationStateCallback(executor, callback);
        runWithShellPermissionIdentity(() -> {
            // Call startTranslation API
            manager.startTranslation(
                    new TranslationSpec(ULocale.ENGLISH,
                            TranslationSpec.DATA_FORMAT_TEXT),
                    new TranslationSpec(ULocale.FRENCH,
                            TranslationSpec.DATA_FORMAT_TEXT),
                    views, contentCaptureContext.getActivityId());
            SystemClock.sleep(UI_WAIT_TIMEOUT);

            assertThat(callback.isOnStartedCalled()).isFalse();
        });
    }

    private BlockingBroadcastReceiver sendCommandToIme(String action, boolean includeStartAssert,
            boolean mutable) {
        final String actionImeServiceCommandDone = action + "_" + SystemClock.uptimeMillis();
        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(sContext,
                actionImeServiceCommandDone);
        receiver.register();
        final Intent commandIntent = new Intent(action);
        final PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        sContext,
                        0,
                        new Intent(actionImeServiceCommandDone),
                        mutable ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE);
        commandIntent.putExtra(EXTRA_FINISH_COMMAND, pendingIntent);
        if (includeStartAssert) {
            commandIntent.putExtra(EXTRA_SOURCE_LOCALE, ULocale.ENGLISH);
            commandIntent.putExtra(EXTRA_TARGET_LOCALE, ULocale.FRENCH);
        }
        sContext.sendBroadcast(commandIntent);

        return receiver;
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

    private Triple<CharSequence, List<AutofillId>, ContentCaptureContext>
            enableServicesAndStartActivityForTranslation() throws Exception {
        // Enable CTS ContentCaptureService
        CtsContentCaptureService contentcaptureService = enableContentCaptureService();

        // Start Activity and get needed information
        Intent intent = new Intent(sContext, SimpleActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        AtomicReference<CharSequence> originalTextRef = new AtomicReference<>();
        AtomicReference<List<AutofillId>> viewAutofillIdsRef = new AtomicReference<>();

        mActivityScenario = ActivityScenario.launch(intent);
        mActivityScenario.onActivity(activity -> {
            originalTextRef.set(activity.getHelloText().getText());
            viewAutofillIdsRef.set(activity.getViewsForTranslation());
        });
        CharSequence originalText = originalTextRef.get();
        // Get the views that need to be translated.
        List<AutofillId> views = viewAutofillIdsRef.get();

        // Wait session created and get the ConttCaptureContext from ContentCaptureService
        ContentCaptureContext contentCaptureContext =
                getContentCaptureContextFromContentCaptureService(contentcaptureService);

        // enable CTS TranslationService
        mTranslationServiceServiceWatcher = CtsTranslationService.setServiceWatcher();
        Helper.setTemporaryTranslationService(CtsTranslationService.SERVICE_NAME);

        // TODO(b/184617863): use separate methods not use Triple here.
        return new Triple(originalText, views, contentCaptureContext);
    }

    static class TestTranslationStateCallback implements UiTranslationStateCallback {
        private boolean mStartCalled;
        private boolean mFinishCalled;
        private ULocale mSourceLocale;
        private ULocale mTargetLocale;

        TestTranslationStateCallback() {
            resetStates();
        }

        void resetStates() {
            mStartCalled = false;
            mFinishCalled = false;
            mSourceLocale = null;
            mTargetLocale = null;
        }

        boolean verifyOnStart(ULocale expectedSourceLocale, ULocale expectedTargetLocale) {
            return mSourceLocale.equals(expectedSourceLocale) && mTargetLocale.equals(
                    expectedTargetLocale);
        }

        boolean isOnStartedCalled() {
            return mStartCalled;
        }

        boolean isOnFinishedCalled() {
            return mFinishCalled;
        }

        @Override
        public void onStarted(String source, String target) {
            // no-op
        }

        @Override
        public void onStarted(ULocale sourceLocale, ULocale targetLocale) {
            mStartCalled = true;
            mSourceLocale = sourceLocale;
            mTargetLocale = targetLocale;
        }

        @Override
        public void onPaused() {
            // do nothing
        }

        @Override
        public void onFinished() {
            mFinishCalled = true;
        }
    }

    private static class ImeSession implements AutoCloseable {

        private static final long TIMEOUT = 2000;
        private final ComponentName mImeName;

        ImeSession(ComponentName ime) throws Exception {
            mImeName = ime;
            runShellCommand("ime reset");
            // TODO(b/184617863): get IME component from InputMethodManager#getInputMethodList
            runShellCommand("ime enable " + ime.flattenToShortString());
            runShellCommand("ime set " + ime.flattenToShortString());
            PollingCheck.check("Make sure that MockIME becomes available", TIMEOUT,
                    () -> ime.equals(getCurrentInputMethodId()));
        }

        @Override
        public void close() throws Exception {
            runShellCommand("ime reset");
            PollingCheck.check("Make sure that MockIME becomes unavailable", TIMEOUT, () ->
                    sContext.getSystemService(InputMethodManager.class)
                            .getEnabledInputMethodList()
                            .stream()
                            .noneMatch(info -> mImeName.equals(info.getComponent())));
        }

        private ComponentName getCurrentInputMethodId() {
            return ComponentName.unflattenFromString(
                    Settings.Secure.getString(sContext.getContentResolver(),
                            Settings.Secure.DEFAULT_INPUT_METHOD));
        }
    }
}
