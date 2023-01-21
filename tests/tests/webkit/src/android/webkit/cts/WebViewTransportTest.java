/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.webkit.WebView;
import android.webkit.WebView.WebViewTransport;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.NullWebViewUtils;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class WebViewTransportTest {
    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(WebViewCtsActivity.class);

    private ActivityScenario mScenario;
    private WebViewCtsActivity mActivity;

    @Before
    public void setUp() throws Throwable {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());

        mScenario = mActivityScenarioRule.getScenario();
        mScenario.onActivity(activity -> {
            mActivity = (WebViewCtsActivity) activity;
        });
    }

    @Test
    public void testAccessWebView() {
        WebkitUtils.onMainThreadSync(() -> {
            WebView webView = mActivity.getWebView();
            WebViewTransport transport = webView.new WebViewTransport();

            assertNull(transport.getWebView());

            transport.setWebView(webView);
            assertSame(webView, transport.getWebView());
        });
    }
}
