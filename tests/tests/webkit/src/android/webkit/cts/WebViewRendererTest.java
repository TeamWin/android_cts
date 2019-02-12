/*
 * Copyright 2019 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.test.ActivityInstrumentationTestCase2;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewRenderer;

import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.Future;

@AppModeFull
public class WebViewRendererTest extends ActivityInstrumentationTestCase2<WebViewCtsActivity> {
    private WebViewOnUiThread mOnUiThread;

    public WebViewRendererTest() {
        super("com.android.cts.webkit", WebViewCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final WebViewCtsActivity activity = getActivity();
        WebView webView = activity.getWebView();
        mOnUiThread = new WebViewOnUiThread(webView);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
        super.tearDown();
    }

    private boolean terminateRendererOnUiThread(
            final WebViewRenderer renderer) {
        return WebkitUtils.onMainThreadSync(() -> {
            return renderer.terminate();
        });
    }

    WebViewRenderer getRendererOnUiThread(final WebView webView) {
        return WebkitUtils.onMainThreadSync(() -> {
            return webView.getWebViewRenderer();
        });
    }

    private Future<WebViewRenderer> startAndGetRenderer(
            final WebView webView) throws Throwable {
        final SettableFuture<WebViewRenderer> future = SettableFuture.create();

        WebkitUtils.onMainThread(() -> {
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    WebViewRenderer result = webView.getWebViewRenderer();
                    future.set(result);
                }
            });
            webView.loadUrl("about:blank");
        });

        return future;
    }

    Future<Boolean> catchRendererTermination(final WebView webView) {
        final SettableFuture<Boolean> future = SettableFuture.create();

        WebkitUtils.onMainThread(() -> {
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean onRenderProcessGone(
                        WebView view,
                        RenderProcessGoneDetail detail) {
                    view.destroy();
                    future.set(true);
                    return true;
                }
            });
        });

        return future;
    }

    public void testGetWebViewRenderer() throws Throwable {
        final WebView webView = mOnUiThread.getWebView();
        final WebViewRenderer preStartRenderer = getRendererOnUiThread(webView);

        if (!getActivity().isMultiprocessMode()) {
            assertNull(
                    "getWebViewRenderer should return null is multiprocess is off.",
                    preStartRenderer);
            return;
        }

        assertNotNull(
                "Should be possible to obtain a renderer handle before the renderer has started.",
                preStartRenderer);
        assertFalse(
                "Should not be able to terminate an unstarted renderer.",
                terminateRendererOnUiThread(preStartRenderer));

        final WebViewRenderer renderer = WebkitUtils.waitForFuture(startAndGetRenderer(webView));
        assertSame(
                "The pre- and post-start renderer handles should be the same object.",
                renderer, preStartRenderer);

        assertSame(
                "When getWebViewRender is called a second time, it should return the same object.",
                renderer, WebkitUtils.waitForFuture(startAndGetRenderer(webView)));

        Future<Boolean> terminationFuture = catchRendererTermination(webView);
        assertTrue(
                "A started renderer should be able to be terminated.",
                terminateRendererOnUiThread(renderer));
        assertTrue(
                "Terminating a renderer should result in onRenderProcessGone being called.",
                WebkitUtils.waitForFuture(terminationFuture));

        assertFalse(
                "It should not be possible to terminate a renderer that has already terminated.",
                terminateRendererOnUiThread(renderer));

        WebView webView2 = mOnUiThread.createWebView();
        try {
            assertNotSame(
                    "After a renderer restart, the new renderer handle object should be different.",
                    renderer, WebkitUtils.waitForFuture(startAndGetRenderer(webView2)));
        } finally {
            // Ensure that we clean up webView2. webView has been destroyed by the WebViewClient
            // installed by catchRendererTermination
            WebkitUtils.onMainThreadSync(() -> webView2.destroy());
        }
    }
}
