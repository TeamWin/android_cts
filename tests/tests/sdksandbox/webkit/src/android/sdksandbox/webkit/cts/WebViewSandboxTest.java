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

package android.sdksandbox.webkit.cts;

import static android.app.sdksandbox.testutils.testscenario.SdkSandboxScenarioRule.ENABLE_LIFE_CYCLE_ANNOTATIONS;

import android.app.sdksandbox.testutils.testscenario.KeepSdkSandboxAliveRule;
import android.app.sdksandbox.testutils.testscenario.SdkSandboxScenarioRule;
import android.webkit.cts.SharedWebViewTestEnvironment;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WebViewSandboxTest {
    // TODO(b/260196711): We are not able to inject input events
    // from the SDK Runtime.
    // This prevents some tests from running.
    private static final boolean CAN_INJECT_INPUT_EVENTS = false;

    @ClassRule
    public static final KeepSdkSandboxAliveRule sSdkTestSuiteSetup =
            new KeepSdkSandboxAliveRule("com.android.emptysdkprovider");

    @Rule
    public final SdkSandboxScenarioRule sdkTester =
            new SdkSandboxScenarioRule(
                    "com.android.cts.sdksidetests.webviewsandboxtest",
                    SharedWebViewTestEnvironment.createHostAppInvoker(
                            ApplicationProvider.getApplicationContext()),
                    ENABLE_LIFE_CYCLE_ANNOTATIONS);

    @Test
    @MediumTest
    public void testScrollBarOverlay() throws Exception {
        sdkTester.assertSdkTestRunPasses("testScrollBarOverlay");
    }

    @Test
    @MediumTest
    public void testFlingScroll() throws Exception {
        sdkTester.assertSdkTestRunPasses("testFlingScroll");
    }

    @Test
    @MediumTest
    public void testLoadUrl() throws Exception {
        sdkTester.assertSdkTestRunPasses("testLoadUrl");
    }

    @Test
    @MediumTest
    public void testPostUrlWithNetworkUrl() throws Exception {
        sdkTester.assertSdkTestRunPasses("testPostUrlWithNetworkUrl");
    }

    @Test
    @MediumTest
    public void testAppInjectedXRequestedWithHeaderIsNotOverwritten() throws Exception {
        sdkTester.assertSdkTestRunPasses("testAppInjectedXRequestedWithHeaderIsNotOverwritten");
    }

    @Test
    @MediumTest
    public void testAppCanInjectHeadersViaImmutableMap() throws Exception {
        sdkTester.assertSdkTestRunPasses("testAppCanInjectHeadersViaImmutableMap");
    }

    @Test
    @MediumTest
    public void testCanInjectHeaders() throws Exception {
        sdkTester.assertSdkTestRunPasses("testCanInjectHeaders");
    }

    @Test
    @MediumTest
    public void testGetVisibleTitleHeight() throws Exception {
        sdkTester.assertSdkTestRunPasses("testGetVisibleTitleHeight");
    }

    @Test
    @MediumTest
    public void testGetOriginalUrl() throws Exception {
        sdkTester.assertSdkTestRunPasses("testGetOriginalUrl");
    }

    @Test
    @MediumTest
    public void testStopLoading() throws Exception {
        sdkTester.assertSdkTestRunPasses("testStopLoading");
    }

    @Test
    @MediumTest
    public void testGoBackAndForward() throws Exception {
        sdkTester.assertSdkTestRunPasses("testGoBackAndForward");
    }

    @Test
    @MediumTest
    public void testAddJavascriptInterface() throws Exception {
        sdkTester.assertSdkTestRunPasses("testAddJavascriptInterface");
    }

    @Test
    @MediumTest
    public void testCapturePicture() throws Exception {
        sdkTester.assertSdkTestRunPasses("testCapturePicture");
    }

    @Test
    @MediumTest
    public void testSetPictureListener() throws Exception {
        sdkTester.assertSdkTestRunPasses("testSetPictureListener");
    }

    @Test
    @MediumTest
    public void testLoadData() throws Exception {
        sdkTester.assertSdkTestRunPasses("testLoadData");
    }

    @Test
    @MediumTest
    public void testLoadDataWithBaseUrl_resolvesRelativeToBaseUrl() throws Exception {
        sdkTester.assertSdkTestRunPasses("testLoadDataWithBaseUrl_resolvesRelativeToBaseUrl");
    }

    @Test
    @MediumTest
    public void testLoadDataWithBaseUrl_javascriptCanAccessOrigin() throws Exception {
        sdkTester.assertSdkTestRunPasses("testLoadDataWithBaseUrl_javascriptCanAccessOrigin");
    }

    @Test
    @MediumTest
    public void testDocumentHasImages() throws Exception {
        sdkTester.assertSdkTestRunPasses("testDocumentHasImages");
    }

    @Test
    @MediumTest
    public void testClearHistory() throws Exception {
        sdkTester.assertSdkTestRunPasses("testClearHistory");
    }

    @Test
    @MediumTest
    public void testSaveAndRestoreState() throws Exception {
        sdkTester.assertSdkTestRunPasses("testSaveAndRestoreState");
    }

    @Test
    @MediumTest
    public void testSetDownloadListener() throws Exception {
        sdkTester.assertSdkTestRunPasses("testSetDownloadListener");
    }

    @Test
    @MediumTest
    public void testSetNetworkAvailable() throws Exception {
        sdkTester.assertSdkTestRunPasses("testSetNetworkAvailable");
    }

    @Test
    @MediumTest
    public void testSetWebChromeClient() throws Exception {
        sdkTester.assertSdkTestRunPasses("testSetWebChromeClient");
    }

    @Test
    @MediumTest
    public void testRequestFocusNodeHref() throws Exception {
        Assume.assumeTrue(CAN_INJECT_INPUT_EVENTS);
        sdkTester.assertSdkTestRunPasses("testRequestFocusNodeHref");
    }

    @Test
    @MediumTest
    public void testRequestImageRef() throws Exception {
        Assume.assumeTrue(CAN_INJECT_INPUT_EVENTS);
        sdkTester.assertSdkTestRunPasses("testRequestImageRef");
    }
}
