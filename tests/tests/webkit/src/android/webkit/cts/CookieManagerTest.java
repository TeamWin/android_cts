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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NullWebViewUtils;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AppModeFull
@MediumTest
@RunWith(AndroidJUnit4.class)
public class CookieManagerTest {
    private static final int TEST_TIMEOUT = 5000;

    private CookieManager mCookieManager;
    private WebViewOnUiThread mOnUiThread;
    private CtsTestServer mServer;

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(CookieSyncManagerCtsActivity.class);

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());
        mActivityScenarioRule.getScenario().onActivity(activity -> {
            CookieSyncManagerCtsActivity cookieSyncManagerCtsActivity =
                    (CookieSyncManagerCtsActivity) activity;
            WebView webView = cookieSyncManagerCtsActivity.getWebView();
            if (webView != null) {
                mOnUiThread = new WebViewOnUiThread(webView);
            }
        });
        mCookieManager = CookieManager.getInstance();
        assertNotNull(mCookieManager);

        // We start with no cookies.
        mCookieManager.removeAllCookie();
        assertFalse(mCookieManager.hasCookies());

        // But accepting cookies.
        mCookieManager.setAcceptCookie(false);
        assertFalse(mCookieManager.acceptCookie());
    }

    @After
    public void tearDown() throws Exception {
        if (mServer != null) {
            mServer.shutdown();
        }
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
    }

    private CtsTestServer createWebServer() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        return new CtsTestServer(context);
    }

    private CtsTestServer createWebServer(@SslMode int sslMode) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        return new CtsTestServer(context, sslMode);
    }

    private CtsTestServer createWebServer(@SslMode int sslMode, int keyResId, int certResId)
            throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        return new CtsTestServer(context, sslMode, keyResId, certResId);
    }

    @Test
    public void testGetInstance() {
        mOnUiThread.cleanUp();
        CookieManager c1 = CookieManager.getInstance();
        CookieManager c2 = CookieManager.getInstance();

        assertSame(c1, c2);
    }

    @Test
    public void testFlush() {
        mCookieManager.flush();
    }

    @Test
    public void testAcceptCookie() throws Exception {
        mCookieManager.setAcceptCookie(false);
        assertFalse(mCookieManager.acceptCookie());

        mServer = createWebServer(SslMode.INSECURE);
        String url = mServer.getCookieUrl("conquest.html");
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("0", mOnUiThread.getTitle()); // no cookies passed
        Thread.sleep(500);
        assertNull(mCookieManager.getCookie(url));

        mCookieManager.setAcceptCookie(true);
        assertTrue(mCookieManager.acceptCookie());

        url = mServer.getCookieUrl("war.html");
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("0", mOnUiThread.getTitle()); // no cookies passed
        waitForCookie(url);
        String cookie = mCookieManager.getCookie(url);
        assertNotNull(cookie);
        // 'count' value of the returned cookie is 0
        final Pattern pat = Pattern.compile("count=(\\d+)");
        Matcher m = pat.matcher(cookie);
        assertTrue(m.matches());
        assertEquals("0", m.group(1));

        url = mServer.getCookieUrl("famine.html");
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("1|count=0", mOnUiThread.getTitle()); // outgoing cookie
        waitForCookie(url);
        cookie = mCookieManager.getCookie(url);
        assertNotNull(cookie);
        m = pat.matcher(cookie);
        assertTrue(m.matches());
        assertEquals("1", m.group(1)); // value got incremented

        url = mServer.getCookieUrl("death.html");
        mCookieManager.setCookie(url, "count=41");
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("1|count=41", mOnUiThread.getTitle()); // outgoing cookie
        waitForCookie(url);
        cookie = mCookieManager.getCookie(url);
        assertNotNull(cookie);
        m = pat.matcher(cookie);
        assertTrue(m.matches());
        assertEquals("42", m.group(1)); // value got incremented
    }

    @Test
    public void testSetCookie() {
        String url = "http://www.example.com";
        String cookie = "name=test";
        mCookieManager.setCookie(url, cookie);
        assertEquals(cookie, mCookieManager.getCookie(url));
        assertTrue(mCookieManager.hasCookies());
    }

    @Test
    public void testSetCookieNullCallback() {
        final String url = "http://www.example.com";
        final String cookie = "name=test";
        mCookieManager.setCookie(url, cookie, null);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                String c = mCookieManager.getCookie(url);
                return mCookieManager.getCookie(url).contains(cookie);
            }
        }.run();
    }

    @Test
    public void testSetCookieCallback() throws Throwable {
        final Semaphore s = new Semaphore(0);
        final AtomicBoolean status = new AtomicBoolean();
        final ValueCallback<Boolean> callback = new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean success) {
                status.set(success);
                s.release();
            }
        };
    }

    @Test
    public void testRemoveCookies() throws InterruptedException {
        final String url = "http://www.example.com";
        final String sessionCookie = "cookie1=peter";
        final String longCookie = "cookie2=sue";
        final String quickCookie = "cookie3=marc";

        mCookieManager.setCookie(url, sessionCookie);
        mCookieManager.setCookie(url, makeExpiringCookie(longCookie, 600));
        mCookieManager.setCookie(url, makeExpiringCookieMs(quickCookie, 1500));

        String allCookies = mCookieManager.getCookie(url);
        assertTrue(allCookies.contains(sessionCookie));
        assertTrue(allCookies.contains(longCookie));
        assertTrue(allCookies.contains(quickCookie));

        mCookieManager.removeSessionCookie();
        allCookies = mCookieManager.getCookie(url);
        assertFalse(allCookies.contains(sessionCookie));
        assertTrue(allCookies.contains(longCookie));
        assertTrue(allCookies.contains(quickCookie));

        Thread.sleep(2000); // wait for quick cookie to expire
        mCookieManager.removeExpiredCookie();
        allCookies = mCookieManager.getCookie(url);
        assertFalse(allCookies.contains(sessionCookie));
        assertTrue(allCookies.contains(longCookie));
        assertFalse(allCookies.contains(quickCookie));

        mCookieManager.removeAllCookie();
        assertNull(mCookieManager.getCookie(url));
        assertFalse(mCookieManager.hasCookies());
    }

    @Test
    public void testRemoveCookiesNullCallback() throws InterruptedException {
        final String url = "http://www.example.com";
        final String sessionCookie = "cookie1=peter";
        final String longCookie = "cookie2=sue";
        final String quickCookie = "cookie3=marc";

        mCookieManager.setCookie(url, sessionCookie);
        mCookieManager.setCookie(url, makeExpiringCookie(longCookie, 600));
        mCookieManager.setCookie(url, makeExpiringCookieMs(quickCookie, 1500));

        String allCookies = mCookieManager.getCookie(url);
        assertTrue(allCookies.contains(sessionCookie));
        assertTrue(allCookies.contains(longCookie));
        assertTrue(allCookies.contains(quickCookie));

        mCookieManager.removeSessionCookies(null);
        allCookies = mCookieManager.getCookie(url);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                String c = mCookieManager.getCookie(url);
                return !c.contains(sessionCookie) &&
                        c.contains(longCookie) &&
                        c.contains(quickCookie);
            }
        }.run();

        mCookieManager.removeAllCookies(null);
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return !mCookieManager.hasCookies();
            }
        }.run();
        assertNull(mCookieManager.getCookie(url));
    }

    @Test
    public void testRemoveCookiesCallback() throws InterruptedException {
        final Semaphore s = new Semaphore(0);
        final AtomicBoolean anyDeleted = new AtomicBoolean();
        final ValueCallback<Boolean> callback = new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean n) {
                anyDeleted.set(n);
                s.release();
            }
        };

        final String url = "http://www.example.com";
        final String sessionCookie = "cookie1=peter";
        final String normalCookie = "cookie2=sue";

        // We set one session cookie and one normal cookie.
        mCookieManager.setCookie(url, sessionCookie);
        mCookieManager.setCookie(url, makeExpiringCookie(normalCookie, 600));

        String allCookies = mCookieManager.getCookie(url);
        assertTrue(allCookies.contains(sessionCookie));
        assertTrue(allCookies.contains(normalCookie));

        // When we remove session cookies there are some to remove.
        removeSessionCookiesOnUiThread(callback);
        assertTrue(s.tryAcquire(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(anyDeleted.get());

        assertTrue("The normal cookie should not be removed",
                mCookieManager.getCookie(url).contains(normalCookie));

        // When we remove session cookies again there are none to remove.
        removeSessionCookiesOnUiThread(callback);
        assertTrue(s.tryAcquire(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(anyDeleted.get());

        // When we remove all cookies there are some to remove.
        removeAllCookiesOnUiThread(callback);
        assertTrue(s.tryAcquire(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(anyDeleted.get());

        assertFalse("We should have no more cookies", mCookieManager.hasCookies());
        assertNull(mCookieManager.getCookie(url));

        // When we remove all cookies again there are none to remove.
        removeAllCookiesOnUiThread(callback);
        assertTrue(s.tryAcquire(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(anyDeleted.get());
    }

    @Test
    public void testThirdPartyCookie() throws Throwable {
        // In theory we need two servers to test this, one server ('the first party')
        // which returns a response with a link to a second server ('the third party')
        // at different origin. This second server attempts to set a cookie which should
        // fail if AcceptThirdPartyCookie() is false.
        // Strictly according to the letter of RFC6454 it should be possible to set this
        // situation up with two TestServers on different ports (these count as having
        // different origins) but Chrome is not strict about this and does not check the
        // port. Instead we cheat making some of the urls come from localhost and some
        // from 127.0.0.1 which count (both in theory and pratice) as having different
        // origins.
        mServer = createWebServer();

        // Turn on Javascript (otherwise <script> aren't fetched spoiling the test).
        mOnUiThread.getSettings().setJavaScriptEnabled(true);

        // Turn global allow on.
        mCookieManager.setAcceptCookie(true);
        assertTrue(mCookieManager.acceptCookie());

        // When third party cookies are disabled...
        mOnUiThread.setAcceptThirdPartyCookies(false);
        assertFalse(mOnUiThread.acceptThirdPartyCookies());

        // ...we can't set third party cookies.
        // First on the third party server we get a url which tries to set a cookie.
        String cookieUrl = toThirdPartyUrl(
                mServer.getSetCookieUrl("/cookie_1.js", "test1", "value1", "SameSite=None; Secure"));
        // Then we create a url on the first party server which links to the first url.
        String url = mServer.getLinkedScriptUrl("/content_1.html", cookieUrl);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertNull(mCookieManager.getCookie(cookieUrl));

        // When third party cookies are enabled...
        mOnUiThread.setAcceptThirdPartyCookies(true);
        assertTrue(mOnUiThread.acceptThirdPartyCookies());

        // ...we can set third party cookies.
        cookieUrl = toThirdPartyUrl(
                mServer.getSetCookieUrl("/cookie_2.js", "test2", "value2", "SameSite=None; Secure"));
        url = mServer.getLinkedScriptUrl("/content_2.html", cookieUrl);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        waitForCookie(cookieUrl);
        String cookie = mCookieManager.getCookie(cookieUrl);
        assertNotNull(cookie);
        assertTrue(cookie.contains("test2"));
    }

    @Test
    public void testSameSiteLaxByDefault() throws Throwable {
        mServer = createWebServer();
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mCookieManager.setAcceptCookie(true);
        mOnUiThread.setAcceptThirdPartyCookies(true);

        // Verify that even with third party cookies enabled, cookies that don't explicitly
        // specify SameSite=none are treated as SameSite=lax and not set in a 3P context.
        String cookieUrl = toThirdPartyUrl(
                mServer.getSetCookieUrl("/cookie_1.js", "test1", "value1"));
        String url = mServer.getLinkedScriptUrl("/content_1.html", cookieUrl);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertNull(mCookieManager.getCookie(cookieUrl));
    }

    @Test
    public void testSameSiteNoneRequiresSecure() throws Throwable {
        mServer = createWebServer();
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mCookieManager.setAcceptCookie(true);

        // Verify that cookies with SameSite=none are ignored when the cookie is not also Secure.
        String cookieUrl =
                mServer.getSetCookieUrl("/cookie_1.js", "test1", "value1", "SameSite=None");
        String url = mServer.getLinkedScriptUrl("/content_1.html", cookieUrl);
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertNull(mCookieManager.getCookie(cookieUrl));
    }

    @Test
    public void testSchemefulSameSite() throws Throwable {
        mServer = createWebServer();
        mOnUiThread.getSettings().setJavaScriptEnabled(true);
        mCookieManager.setAcceptCookie(true);
        mOnUiThread.setAcceptThirdPartyCookies(true);

        // Verify that two servers with different schemes on the same host are not considered
        // same-site to each other.
        CtsTestServer secureServer = createWebServer(SslMode.NO_CLIENT_AUTH, R.raw.trustedkey,
                R.raw.trustedcert);
        try {
            String cookieUrl = secureServer.getSetCookieUrl("/cookie_1.js", "test1", "value1");
            String url = mServer.getLinkedScriptUrl("/content_1.html", cookieUrl);
            mOnUiThread.loadUrlAndWaitForCompletion(url);
            assertNull(mCookieManager.getCookie(cookieUrl));
        } finally {
            secureServer.shutdown();
        }
    }

    @Test
    public void testb3167208() throws Exception {
        String uri = "http://host.android.com/path/";
        // note the space after the domain=
        String problemCookie = "foo=bar; domain= .android.com; path=/";
        mCookieManager.setCookie(uri, problemCookie);
        String cookie = mCookieManager.getCookie(uri);
        assertNotNull(cookie);
        assertTrue(cookie.contains("foo=bar"));
    }

    private void waitForCookie(final String url) {
        new PollingCheck(WebkitUtils.TEST_TIMEOUT_MS) {
            @Override
            protected boolean check() {
                return mCookieManager.getCookie(url) != null;
            }
        }.run();
    }

    @SuppressWarnings("deprecation")
    private String makeExpiringCookie(String cookie, int secondsTillExpiry) {
        return makeExpiringCookieMs(cookie, 1000*secondsTillExpiry);
    }

    @SuppressWarnings("deprecation")
    private String makeExpiringCookieMs(String cookie, int millisecondsTillExpiry) {
        Date date = new Date();
        date.setTime(date.getTime() + millisecondsTillExpiry);
        return cookie + "; expires=" + date.toGMTString();
    }

    private void removeAllCookiesOnUiThread(final ValueCallback<Boolean> callback) {
        WebkitUtils.onMainThreadSync(() -> {
            mCookieManager.removeAllCookies(callback);
        });
    }

    private void removeSessionCookiesOnUiThread(final ValueCallback<Boolean> callback) {
        WebkitUtils.onMainThreadSync(() -> {
            mCookieManager.removeSessionCookies(callback);
        });
    }

    /**
     * Makes a url look as if it comes from a different host.
     * @param url the url to fake.
     * @return the resulting url after faking.
     */
    public String toThirdPartyUrl(String url) {
        return url.replace("localhost", "127.0.0.1");
    }
 }
