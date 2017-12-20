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

package android.webkit.cts;


import android.content.Context;
import android.test.AndroidTestCase;
import android.webkit.WebView;

import com.android.compatibility.common.util.NullWebViewUtils;

public class WebViewDataDirTest extends AndroidTestCase {

    private static final long REMOTE_TIMEOUT_MS = 5000;

    static class TestDisableThenUseImpl extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) {
            WebView.disableWebView();
            try {
                new WebView(ctx);
                fail("didn't throw IllegalStateException");
            } catch (IllegalStateException e) {}
        }
    }

    public void testDisableThenUse() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        try (TestProcessClient process = TestProcessClient.createProcessA(getContext())) {
            process.run(TestDisableThenUseImpl.class, REMOTE_TIMEOUT_MS);
        }
    }

    static class TestUseThenDisableImpl extends TestProcessClient.TestRunnable {
        @Override
        public void run(Context ctx) {
            new WebView(ctx);
            try {
                WebView.disableWebView();
                fail("didn't throw IllegalStateException");
            } catch (IllegalStateException e) {}
        }
    }

    public void testUseThenDisable() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        try (TestProcessClient process = TestProcessClient.createProcessA(getContext())) {
            process.run(TestUseThenDisableImpl.class, REMOTE_TIMEOUT_MS);
        }
    }
}
