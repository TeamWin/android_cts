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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.webkit.TracingConfig;
import android.webkit.TracingController;
import android.webkit.WebView;
import android.webkit.cts.WebViewOnUiThread.WaitForLoadedClient;

import com.android.compatibility.common.util.NullWebViewUtils;
import com.android.compatibility.common.util.PollingCheck;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Callable;


public class TracingControllerTest extends ActivityInstrumentationTestCase2<WebViewCtsActivity> {

    public static class TracingReceiver extends OutputStream {
        private int mChunkCount = 0;
        private boolean mComplete = false;
        private ByteArrayOutputStream outputStream;
        private Handler mHandler;

        public TracingReceiver(Handler handler) {
            outputStream = new ByteArrayOutputStream();
            mHandler = handler;
        }

        @Override
        public void write(byte[] chunk) {
            validateThread();
            mChunkCount++;
            try {
                outputStream.write(chunk);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            validateThread();
            mComplete = true;
        }

        @Override
        public void flush() {
            fail("flush should not be called");
        }

        @Override
        public void write(int b) {
            fail("write(int) should not be called");
        }

        @Override
        public void write(byte[] b, int off, int len) {
            fail("write(byte[], int, int) should not be called");
        }

        private void validateThread() {
            // Ensure the listener is called on the correct thread.
            if (mHandler == null) {
                assertEquals(Looper.getMainLooper(), Looper.myLooper());
            } else {
                assertEquals(mHandler.getLooper(), Looper.myLooper());
            }
        }

        int getNbChunks() { return mChunkCount; }
        boolean getComplete() { return mComplete; }
        ByteArrayOutputStream getOutputStream() { return outputStream; }
    }

    private static final int POLLING_TIMEOUT = 60 * 1000;
    private WebViewOnUiThread mOnUiThread;

    public TracingControllerTest() throws Exception {
        super("android.webkit.cts", WebViewCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        WebView webview = getActivity().getWebView();
        if (webview == null) return;
        mOnUiThread = new WebViewOnUiThread(this, webview);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
        super.tearDown();
    }

// b/63750258 : Disable tests for landing due to API update,
// re-enable once the chromium implementation lands and rolls into android master.
//
//    // Test that callbacks are invoked and tracing data is returned on the UI thread.
//    public void testTracingControllerCallbacks() throws Throwable {
//        if (!NullWebViewUtils.isWebViewAvailable()) {
//            return;
//        }
//        runTracingTestWithCallbacks(null);
//    }
//
//    // Test that callbacks are invoked and tracing data is returned on custom thread.
//    public void testTracingControllerCallbacksOnCustomThread() throws Throwable {
//        if (!NullWebViewUtils.isWebViewAvailable()) {
//            return;
//        }
//
//        final HandlerThread handlerThread = new HandlerThread("TracingControllerTest");
//        handlerThread.start();
//        final Handler handler = new Handler(handlerThread.getLooper());
//        runTracingTestWithCallbacks(handler);
//        handlerThread.quitSafely();
//    }
//
//    // Test that tracing cannot be started if already tracing.
//    @UiThreadTest
//    public void testTracingCannotStartIfAlreadyTracing() throws Exception {
//        if (!NullWebViewUtils.isWebViewAvailable()) {
//            return;
//        }
//
//        TracingController tracingController = TracingController.getInstance();
//        boolean resultStart = tracingController.start(new TracingConfig(TracingConfig.CATEGORIES_WEB_DEVELOPER));
//        assertTrue(resultStart);
//        assertTrue(tracingController.isTracing());
//        assertFalse(tracingController.start(new TracingConfig(TracingConfig.CATEGORIES_RENDERING)));
//        assertTrue(tracingController.stop());
//   }
//
//    // Test that tracing stop has no effect if tracing has not been started.
//    @UiThreadTest
//    public void testTracingStopFalseIfNotTracing() throws Exception {
//        if (!NullWebViewUtils.isWebViewAvailable()) {
//            return;
//        }
//
//        TracingController tracingController = TracingController.getInstance();
//        assertFalse(tracingController.stop());
//        assertFalse(tracingController.isTracing());
//    }
//
//    // Generic helper function for running tracing using a given handler.
//    private void runTracingTestWithCallbacks(Handler handler) throws Throwable {
//        final TracingReceiver tracingReceiver = new TracingReceiver(handler);
//        runTestOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                TracingController tracingController = TracingController.getInstance();
//                assertNotNull(tracingController);
//                boolean resultStart = tracingController.start(new TracingConfig(TracingConfig.CATEGORIES_WEB_DEVELOPER));
//                assertTrue(resultStart);
//                assertTrue(tracingController.isTracing());
//
//                mOnUiThread.loadUrlAndWaitForCompletion("about:blank");
//                boolean resultStop = tracingController.stopAndFlush(tracingReceiver, handler);
//                assertTrue(resultStop);
//            }
//        });
//
//        Callable<Boolean> tracingComplete = new Callable<Boolean>() {
//            @Override
//            public Boolean call() {
//                return tracingReceiver.getComplete();
//            }
//         };
//         PollingCheck.check("Tracing did not complete", POLLING_TIMEOUT, tracingComplete);
//         assertTrue(tracingReceiver.getNbChunks() > 0);
//         assertTrue(tracingReceiver.getOutputStream().size() > 0);
//         assertTrue(tracingReceiver.getOutputStream().toString().startsWith("{\"traceEvents\":"));
//    }

}

