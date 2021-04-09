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

import static android.translation.cts.Helper.ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_FINISH;
import static android.translation.cts.Helper.ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_START;
import static android.translation.cts.Helper.ACTION_REGISTER_UI_TRANSLATION_CALLBACK;
import static android.translation.cts.Helper.ACTION_UNREGISTER_UI_TRANSLATION_CALLBACK;
import static android.translation.cts.Helper.EXTRA_FINISH_COMMAND;
import static android.translation.cts.Helper.EXTRA_SOURCE_LOCALE;
import static android.translation.cts.Helper.EXTRA_TARGET_LOCALE;
import static android.translation.cts.Helper.EXTRA_VERIFY_RESULT;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.translation.UiTranslationManager;
import android.view.translation.UiTranslationStateCallback;
import android.widget.LinearLayout;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Implementation of {@link InputMethodService} used in CTS tests.
 */
public final class CtsTestIme extends InputMethodService {

    private static final String TAG = "CtsTestIme";

    static String IME_SERVICE_PACKAGE = "android.translation.cts";

    private Context mContext;
    private UiTranslationManagerTest.TestTranslationStateCallback mCallback;
    private CommandReceiver mReceiver;

    @Override
    public View onCreateInputView() {
        return new LinearLayout(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
        if (mReceiver == null) {
            mReceiver = new CommandReceiver(mContext);
            mReceiver.register();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) {
            mReceiver.unRegister();
            mReceiver = null;
        }
    }

    void registerUiTranslationStateCallback(Intent intent) {
        final UiTranslationManager manager = mContext.getSystemService(UiTranslationManager.class);
        mCallback = new UiTranslationManagerTest.TestTranslationStateCallback();
        final Executor executor = Executors.newSingleThreadExecutor();
        manager.registerUiTranslationStateCallback(executor, mCallback);

        notifyCommandDone(intent, /* inCludeResult= */ false, /* assertResult= */ false);
    }

    void unregisterUiTranslationStateCallback(Intent intent) {
        final UiTranslationManager manager = mContext.getSystemService(UiTranslationManager.class);
        manager.unregisterUiTranslationStateCallback(mCallback);

        notifyCommandDone(intent, /* inCludeResult= */ false, /* assertResult= */ false);
    }

    void assertOnStart(Intent intent) {
        final String expectedSource = intent.getStringExtra(EXTRA_SOURCE_LOCALE);
        final String expectedTarget = intent.getStringExtra(EXTRA_TARGET_LOCALE);
        final boolean result = mCallback.verifyOnStart(expectedSource, expectedTarget);
        notifyCommandDone(intent, /* inCludeResult= */ true, result);
    }

    void assertOnFinish(Intent intent) {
        final boolean result = mCallback.isOnFinishedCalled();
        notifyCommandDone(intent, /* inCludeResult= */ true, result);
    }

    private void notifyCommandDone(Intent intent, boolean inCludeResult, boolean assertResult) {
        final PendingIntent pendingIntent = intent.getParcelableExtra(EXTRA_FINISH_COMMAND);
        if (pendingIntent != null) {
            try {
                if (inCludeResult) {
                    // TODO(b/184617863): better to return the values to the test code not return
                    // the assert result to the test code.
                    final Intent result = new Intent();
                    result.putExtra(EXTRA_VERIFY_RESULT, assertResult);
                    pendingIntent.send(mContext, 0, result);
                    return;
                }
                pendingIntent.send();
            } catch (CanceledException e) {
                Log.w(TAG, "Pending intent " + pendingIntent + " canceled");
            }
        }
    }

    private final class CommandReceiver extends BroadcastReceiver {

        Context mContext;

        CommandReceiver(Context context) {
            mContext = context;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch(action) {
                case ACTION_REGISTER_UI_TRANSLATION_CALLBACK:
                    registerUiTranslationStateCallback(intent);
                    break;
                case ACTION_UNREGISTER_UI_TRANSLATION_CALLBACK:
                    unregisterUiTranslationStateCallback(intent);
                    break;
                case ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_START:
                    assertOnStart(intent);
                    break;
                case ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_FINISH:
                    assertOnFinish(intent);
                    break;
            }
        }

        void register() {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_REGISTER_UI_TRANSLATION_CALLBACK);
            filter.addAction(ACTION_UNREGISTER_UI_TRANSLATION_CALLBACK);
            filter.addAction(ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_START);
            filter.addAction(ACTION_ASSERT_UI_TRANSLATION_CALLBACK_ON_FINISH);
            mContext.registerReceiver(this, filter);
        }

        void unRegister() {
            mContext.unregisterReceiver(this);
        }
    }
}
