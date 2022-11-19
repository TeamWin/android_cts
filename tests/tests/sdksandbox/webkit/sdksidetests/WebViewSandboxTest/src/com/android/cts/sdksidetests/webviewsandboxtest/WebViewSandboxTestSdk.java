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

package com.android.cts.sdksidetests.webviewsandboxtest;

import android.app.sdksandbox.testutils.testscenario.SdkSandboxTestScenarioRunner;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.cts.IHostAppInvoker;
import android.webkit.cts.SharedWebViewTestEnvironment;
import android.webkit.cts.WebViewOnUiThread;
import android.webkit.cts.WebViewTest;

public class WebViewSandboxTestSdk extends SdkSandboxTestScenarioRunner {
    private WebViewTest mTestInstance = new WebViewTest();
    private WebView mWebView;
    private WebViewOnUiThread mOnUiThread;

    @Override
    public Object getTestInstance() {
        return mTestInstance;
    }

    @Override
    public View beforeEachTest(Context windowContext, Bundle params, int width, int height) {
        mWebView = new WebView(windowContext);
        mOnUiThread = new WebViewOnUiThread(mWebView);

        SharedWebViewTestEnvironment testEnvironment =
                new SharedWebViewTestEnvironment.Builder()
                        .setContext(getContext())
                        .setWebView(mWebView)
                        .setWebViewOnUiThread(mOnUiThread)
                        .setHostAppInvoker(IHostAppInvoker.Stub.asInterface(getCustomInterface()))
                        .build();

        mTestInstance.setTestEnvironment(testEnvironment);

        return mWebView;
    }
}
