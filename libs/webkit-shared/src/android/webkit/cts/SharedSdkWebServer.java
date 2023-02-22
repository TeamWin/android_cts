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

import android.os.RemoteException;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * This class serves as the public fronting API for tests to interact with the CtsTestServer.
 *
 * <p>This is a light wrapper around the Binder Proxy so that we can wrap all the RemoteExceptions
 * in a Runtime exception so that the WebServer methods can be used the same as they were used
 * previously.
 */
public final class SharedSdkWebServer {
    private final IWebServer mWebServer;

    public SharedSdkWebServer(IWebServer webServer) {
        mWebServer = webServer;
    }

    /** Starts the web server. */
    public void start(@SslMode int sslMode) {
        start(sslMode, null, 0, 0);
    }

    /** Starts the web server using the provided parameters}. */
    public void start(@SslMode int sslMode, @Nullable byte[] acceptedIssuerDer,
            int keyResId, int certResId) {
        try {
            mWebServer.start(sslMode, acceptedIssuerDer, keyResId, certResId);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Shuts down the web server if it was started. */
    public void shutdown() {
        try {
            mWebServer.shutdown();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Resets all request state stored. */
    public void resetRequestState() {
        try {
            mWebServer.resetRequestState();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets a response to be returned when a particular request path is passed in (with the option
     * to specify additional headers).
     */
    public String setResponse(
            String path, String responseString, List<HttpHeader> responseHeaders) {
        // We can't send a null value as a list
        // so default to an empty list if null was provided.
        if (responseHeaders == null) {
            responseHeaders = Collections.emptyList();
        }
        try {
            return mWebServer.setResponse(path, responseString, responseHeaders);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Return the absolute URL that refers to a path. */
    public String getAbsoluteUrl(String path) {
        try {
            return mWebServer.getAbsoluteUrl(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns a url that will contain the user agent in the header and in the body. */
    public String getUserAgentUrl() {
        try {
            return mWebServer.getUserAgentUrl();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get a delayed assert url for an asset path. */
    public String getDelayedAssetUrl(String path) {
        try {
            return mWebServer.getDelayedAssetUrl(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get a url that will redirect for a path. */
    public String getRedirectingAssetUrl(String path) {
        try {
            return mWebServer.getRedirectingAssetUrl(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get the full url for an asset. */
    public String getAssetUrl(String path) {
        try {
            return mWebServer.getAssetUrl(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get the full auth url for an asset. */
    public String getAuthAssetUrl(String path) {
        try {
            return mWebServer.getAuthAssetUrl(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get a binary url. */
    public String getBinaryUrl(String mimeType, int contentLength) {
        try {
            return mWebServer.getBinaryUrl(mimeType, contentLength);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns the url to the app cache. */
    public String getAppCacheUrl() {
        try {
            return mWebServer.getAppCacheUrl();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns how many requests have been made. */
    public int getRequestCount() {
        try {
            return mWebServer.getRequestCount();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns the request count for a particular path */
    public int getRequestCount(String path) {
        try {
            return mWebServer.getRequestCountWithPath(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Verify if a resource was requested. */
    public boolean wasResourceRequested(String url) {
        try {
            return mWebServer.wasResourceRequested(url);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Retrieve the last request to be made on a url. */
    public HttpRequest getLastRequest(String path) {
        try {
            return mWebServer.getLastRequest(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Retrieve the last request for an asset path to be made on a url. */
    public HttpRequest getLastAssetRequest(String url) {
        try {
            return mWebServer.getLastAssetRequest(url);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns a url that will contain the path as a cookie. */
    public String getCookieUrl(String path) {
        try {
            return mWebServer.getCookieUrl(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a URL that attempts to set the cookie
     * "key=value" with the given list of attributes when fetched.
    */
    public String getSetCookieUrl(String path, String key, String value, String attributes) {
        try {
            return mWebServer.getSetCookieUrl(path, key, value, attributes);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns a URL for a page with a script tag where src equals the URL passed in. */
    public String getLinkedScriptUrl(String path, String url) {
        try {
            return mWebServer.getLinkedScriptUrl(path, url);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}
