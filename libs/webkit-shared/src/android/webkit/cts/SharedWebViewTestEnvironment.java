/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.webkit.cts;

import android.app.Instrumentation;
import android.content.Context;
import android.view.MotionEvent;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

/**
 * This class contains all the environmental variables that need to be configured for WebView tests
 * to either run inside the SDK Runtime or within an Activity.
 */
public final class SharedWebViewTestEnvironment {
    @Nullable private final Context mContext;
    @Nullable private final WebView mWebView;
    @Nullable private final WebViewOnUiThread mWebViewOnUiThread;
    @Nullable private final IHostAppInvoker mHostAppInvoker;

    private SharedWebViewTestEnvironment(
            Context context,
            WebView webView,
            WebViewOnUiThread webViewOnUiThread,
            IHostAppInvoker hostAppInvoker) {
        mContext = context;
        mWebView = webView;
        mWebViewOnUiThread = webViewOnUiThread;
        mHostAppInvoker = hostAppInvoker;
    }

    @Nullable
    public Context getContext() {
        return mContext;
    }

    @Nullable
    public WebView getWebView() {
        return mWebView;
    }

    @Nullable
    public WebViewOnUiThread getWebViewOnUiThread() {
        return mWebViewOnUiThread;
    }

    /**
     * Invokes waitForIdleSync on the {@link Instrumentation}
     * in the activity.
     */
    public void waitForIdleSync() throws Exception {
        mHostAppInvoker.waitForIdleSync();
    }

    /**
     * Invokes sendKeyDownUpSync on the {@link Instrumentation}
     * in the activity.
     */
    public void sendKeyDownUpSync(int keyCode) throws Exception {
        mHostAppInvoker.sendKeyDownUpSync(keyCode);
    }

    /**
     * Invokes sendPointerSync on the {@link Instrumentation}
     * in the activity.
     */
    public void sendPointerSync(MotionEvent event) throws Exception {
        mHostAppInvoker.sendPointerSync(event);
    }

    /**
     * Use this builder to create a {@link SharedWebViewTestEnvironment}.
     * The {@link SharedWebViewTestEnvironment} can not be built directly.
     */
    public static final class Builder {
        private Context mContext;
        private WebView mWebView;
        private WebViewOnUiThread mWebViewOnUiThread;
        private IHostAppInvoker mHostAppInvoker;

        /**
         * Provide a {@link Context} the tests should use
         * for your environment.
         */
        public Builder setContext(@NonNull Context context) {
            mContext = context;
            return this;
        }

        /**
         * Provide a {@link WebView} the tests should use
         * for your environment.
         */
        public Builder setWebView(@NonNull WebView webView) {
            mWebView = webView;
            return this;
        }

        /**
         * Provide a {@link WebViewOnUiThread} the tests should use
         * for your environment.
         */
        public Builder setWebViewOnUiThread(@NonNull WebViewOnUiThread webViewOnUiThread) {
            mWebViewOnUiThread = webViewOnUiThread;
            return this;
        }

        /**
         * Provide a {@link IHostAppInvoker} the tests should use
         * for your environment.
         *
         * This can be created with {@link createHostAppInvoker}.
         *
         * Note: This is required.
         */
        public Builder setHostAppInvoker(@NonNull IHostAppInvoker hostAppInvoker) {
            mHostAppInvoker = hostAppInvoker;
            return this;
        }

        /**
         * Build a new SharedWebViewTestEnvironment.
         */
        public SharedWebViewTestEnvironment build() {
            if (mHostAppInvoker == null) {
                throw new NullPointerException("The host app invoker is required");
            }
            return new SharedWebViewTestEnvironment(
                    mContext, mWebView, mWebViewOnUiThread, mHostAppInvoker);
        }
    }

    /**
     * This will generate a new {@link IHostAppInvoker} binder node. This should be called from
     * wherever the activity exists for test cases.
     */
    public static IHostAppInvoker.Stub createHostAppInvoker() {
        return new IHostAppInvoker.Stub() {
            private Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

            public void waitForIdleSync() {
                mInstrumentation.waitForIdleSync();
            }

            public void sendKeyDownUpSync(int keyCode) {
                mInstrumentation.sendKeyDownUpSync(keyCode);
            }

            public void sendPointerSync(MotionEvent event) {
                mInstrumentation.sendPointerSync(event);
            }
        };
    }
}
