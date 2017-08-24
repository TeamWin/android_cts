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
package android.autofillservice.cts;

import android.os.Bundle;
import android.support.test.uiautomator.UiObject2;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

public class WebViewActivity extends AbstractAutoFillActivity {

    WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.webview_activity);

        mWebView = (WebView) findViewById(R.id.webview);
        mWebView.setWebViewClient(new WebViewClient());
        mWebView.loadUrl("file:///android_res/raw/login.html");
    }

    public UiObject2 getUsernameLabel(UiBot uiBot) {
        return getLabel(uiBot, "Username: ");
    }

    public UiObject2 getPasswordLabel(UiBot uiBot) {
        return getLabel(uiBot, "Password: ");
    }


    public UiObject2 getUsernameInput(UiBot uiBot) {
        return getInput(uiBot, "Username: ");
    }

    public UiObject2 getPasswordInput(UiBot uiBot) {
        return getInput(uiBot, "Password: ");
    }

    public UiObject2 getLoginButton(UiBot uiBot) {
        return uiBot.assertShownByContentDescription("Login");
    }

    private UiObject2 getLabel(UiBot uiBot, String contentDescription) {
        final UiObject2 label = uiBot.assertShownByContentDescription(contentDescription);
        return label;
    }

    private UiObject2 getInput(UiBot uiBot, String contentDescription) {
        // First get the label..
        final UiObject2 label = getLabel(uiBot, contentDescription);

        // Then the input is next.
        final UiObject2 parent = label.getParent();
        UiObject2 previous = null;
        for (UiObject2 child : parent.getChildren()) {
            if (label.equals(previous)) {
                if (child.getClassName().equals(EditText.class.getName())) {
                    return child;
                }
                throw new IllegalStateException("Invalid class for " + child);
            }
            previous = child;
        }
        throw new IllegalStateException("could not find username (label=" + label + ")");
    }
}
