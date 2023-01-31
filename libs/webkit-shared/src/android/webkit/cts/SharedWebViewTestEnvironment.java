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

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.view.MotionEvent;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

import org.apache.http.util.EncodingUtils;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * This class contains all the environmental variables that need to be configured for WebView tests
 * to either run inside the SDK Runtime or within an Activity.
 */
public final class SharedWebViewTestEnvironment {
    @Nullable private final Context mContext;
    @Nullable private final WebView mWebView;
    private final IHostAppInvoker mHostAppInvoker;

    private SharedWebViewTestEnvironment(
            Context context,
            WebView webView,
            IHostAppInvoker hostAppInvoker) {
        mContext = context;
        mWebView = webView;
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

    /**
     * Apache Utils can't be statically linked so we can't use them directly inside the SDK Runtime.
     * Use this method instead of EncodingUtils.getBytes.
     */
    public byte[] getEncodingBytes(String data, String charset) {
        try {
            return mHostAppInvoker.getEncodingBytes(data, charset);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Invokes waitForIdleSync on the {@link Instrumentation} in the activity. */
    public void waitForIdleSync() {
        try {
            mHostAppInvoker.waitForIdleSync();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Invokes sendKeyDownUpSync on the {@link Instrumentation} in the activity. */
    public void sendKeyDownUpSync(int keyCode) {
        try {
            mHostAppInvoker.sendKeyDownUpSync(keyCode);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Invokes sendPointerSync on the {@link Instrumentation} in the activity. */
    public void sendPointerSync(MotionEvent event) {
        try {
            mHostAppInvoker.sendPointerSync(event);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns a web server that can be used for web based testing. */
    public SharedSdkWebServer getWebServer() {
        try {
            return new SharedSdkWebServer(mHostAppInvoker.getWebServer());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Use this builder to create a {@link SharedWebViewTestEnvironment}. The {@link
     * SharedWebViewTestEnvironment} can not be built directly.
     */
    public static final class Builder {
        private Context mContext;
        private WebView mWebView;
        private IHostAppInvoker mHostAppInvoker;

        /** Provide a {@link Context} the tests should use for your environment. */
        public Builder setContext(@NonNull Context context) {
            mContext = context;
            return this;
        }

        /** Provide a {@link WebView} the tests should use for your environment. */
        public Builder setWebView(@NonNull WebView webView) {
            mWebView = webView;
            return this;
        }

        /**
         * Provide a {@link IHostAppInvoker} the tests should use for your environment.
         *
         * <p>This can be created with {@link createHostAppInvoker}.
         *
         * <p>Note: This is required.
         */
        public Builder setHostAppInvoker(@NonNull IHostAppInvoker hostAppInvoker) {
            mHostAppInvoker = hostAppInvoker;
            return this;
        }

        /** Build a new SharedWebViewTestEnvironment. */
        public SharedWebViewTestEnvironment build() {
            if (mHostAppInvoker == null) {
                throw new NullPointerException("The host app invoker is required");
            }
            return new SharedWebViewTestEnvironment(
                    mContext, mWebView, mHostAppInvoker);
        }
    }

    /**
     * This will generate a new {@link IHostAppInvoker} binder node. This should be called from
     * wherever the activity exists for test cases.
     */
    public static IHostAppInvoker.Stub createHostAppInvoker(Context applicationContext) {
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

            public byte[] getEncodingBytes(String data, String charset) {
                return EncodingUtils.getBytes(data, charset);
            }

            public IWebServer getWebServer() {
                return new IWebServer.Stub() {
                    private CtsTestServer mWebServer;

                    public void start(@SslMode int sslMode, @Nullable byte[] acceptedIssuerDer) {
                        assertNull(mWebServer);
                        final X509Certificate[] acceptedIssuerCerts;
                        if (acceptedIssuerDer != null) {
                            try {
                                CertificateFactory certFactory = CertificateFactory.getInstance(
                                        "X.509");
                                acceptedIssuerCerts = new X509Certificate[]{
                                        (X509Certificate) certFactory.generateCertificate(
                                                new ByteArrayInputStream(acceptedIssuerDer))};
                            } catch (CertificateException e) {
                                // Throw manually, because compiler does not understand that fail()
                                // does not return.
                                throw new AssertionError(
                                        "Failed to create certificate chain: " + e.toString());
                            }
                        } else {
                            acceptedIssuerCerts = null;
                        }
                        try {
                            X509TrustManager trustManager = new CtsTestServer.CtsTrustManager() {
                                @Override
                                public X509Certificate[] getAcceptedIssuers() {
                                    return acceptedIssuerCerts;
                                }
                            };
                            mWebServer = new CtsTestServer(applicationContext, sslMode,
                                    trustManager);
                        } catch (Exception e) {
                            fail("Failed to launch CtsTestServer");
                        }
                    }

                    public void shutdown() {
                        if (mWebServer == null) {
                            return;
                        }
                        ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
                        ThreadPolicy tmpPolicy =
                                new ThreadPolicy.Builder(oldPolicy).permitNetwork().build();
                        StrictMode.setThreadPolicy(tmpPolicy);
                        mWebServer.shutdown();
                        mWebServer = null;
                        StrictMode.setThreadPolicy(oldPolicy);
                    }

                    public void resetRequestState() {
                        assertNotNull("The WebServer needs to be started", mWebServer);
                        mWebServer.resetRequestState();
                    }

                    public String getDelayedAssetUrl(String path) {
                        assertNotNull("The WebServer needs to be started", mWebServer);
                        return mWebServer.getDelayedAssetUrl(path);
                    }

                    public String getRedirectingAssetUrl(String path) {
                        assertNotNull("The WebServer needs to be started", mWebServer);
                        return mWebServer.getRedirectingAssetUrl(path);
                    }

                    public String getAssetUrl(String path) {
                        assertNotNull("The WebServer needs to be started", mWebServer);
                        return mWebServer.getAssetUrl(path);
                    }

                    public String getAuthAssetUrl(String path) {
                        assertNotNull("The WebServer needs to be started", mWebServer);
                        return mWebServer.getAuthAssetUrl(path);
                    }

                    public String getBinaryUrl(String mimeType, int contentLength) {
                        assertNotNull("The WebServer needs to be started", mWebServer);
                        return mWebServer.getBinaryUrl(mimeType, contentLength);
                    }

                    public boolean wasResourceRequested(String url) {
                        assertNotNull("The WebServer needs to be started", mWebServer);
                        return mWebServer.wasResourceRequested(url);
                    }

                    public HttpRequest getLastAssetRequest(String url) {
                        assertNotNull("The WebServer needs to be started", mWebServer);
                        org.apache.http.HttpRequest request = mWebServer.getLastAssetRequest(url);
                        if (request == null) {
                            return null;
                        }

                        return new HttpRequest(url, request);
                    }
                };
            }
        };
    }
}
