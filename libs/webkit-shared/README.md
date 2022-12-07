# Shared Webkit Environment

## Overview

This helper lib makes a test suite extendable to run in both an activity based environment and
within the SDK Runtime.

[design](go/shared-sdk-sandbox-webview-tests) (*only visible to Googlers)

## Expected prior knowledge

Read the test scenario documentation to get a better understanding of how we invoke tests inside
the SDK Runtime:
`//packages/modules/AdServices/sdksandbox/tests/testutils/testscenario/README.md`

## Tutorial

*** aside
**Tip:**  If your test suite already extends SharedWebViewTest, you can skip to section
3, "Converting a test to shared"
***

### 1. Making a test suite sharable with the SDK Runtime

If you want to share webkit tests inside the SDK runtime, you will need to make your
test suite inherit from `android.webkit.cts.SharedWebViewTest`. This is used to indicate
that a test suite has a configurable environment.

Eg:
```java
- public class WebViewTest
+ public class WebViewTest extends SharedWebViewTest
```

*** aside
**Note:**  WebView tests are quite old so you may need to first migrate the test suite
from `ActivityInstrumentationTestCase2`, to use `ActivityScenarioRule`. You can see an example
of this migration [here](http://ag/20258043) and [here](http://ag/20300224).
***

This abstract class requires you to implement the method `createTestEnvironment` that
defines the test environment for your test suite. Think of the test environment as a
concrete description of where this test suite will execute. `createTestEnvironment` should
have all the references your test has to an activity. This will be overridden later on by the
SDK tests using the API method `setTestEnvironment`.

Eg:
```java
@Override
protected SharedWebViewTestEnvironment createTestEnvironment() {
    return new SharedWebViewTestEnvironment.Builder()
            .setContext(mActivity)
            .setWebView(mWebView)
            // This allows SDK methods to invoke APIs from outside the SDK.
            // The Activity based tests don't need to worry about this so you can
            // just provide the host app invoker directly to this environment.
            .setHostAppInvoker(SharedWebViewTestEnvironment.createHostAppInvoker())
            .build();
}
```

Your test suite is now sharable with an SDK runtime test suite!

### 2. Creating a new SDK Runtime test suite

The SDK Runtime tests for webkit live under `//cts/tests/tests/sdksandbox/webkit`.

You need to create a test SDK that will actually have your tests, and a
JUnit test suite that JUnit will have to invoke your tests from an activity.

You can follow the "Creating new SDK Runtime tests" section under the SDK testscenario
guide (store these SDK tests in `//cts/tests/tests/sdksandbox/webkit`):
`//packages/modules/AdServices/sdksandbox/tests/testutils/testscenario/README.md`

By the end of that guide, you should have two new modules:
*  A test SDK that will reference your shared tests
*  A JUnit test suite that will load your test SDK

Your SharedWebViewTest will be provided as a test instance to be executed via the test SDK.
You can use `SharedWebViewTest.setTestEnvironment` to override the test environment for the
SDK Runtime.
You can look at WebViewSandboxTestSdk for an example of how this is done:
`//cts/tests/tests/sdksandbox/webkit/sdksidetests/WebViewSandboxTest/src/com/android/cts/sdksidetests/webviewsandboxtest/WebViewSandboxTestSdk.java`

The "Custom test instances" section under the SDK testscenario has more info on how this works:
`//packages/modules/AdServices/sdksandbox/tests/testutils/testscenario/README.md`

*** aside
**Note:**   Your test SDK needs to depend on `CtsWebkitTestCasesSharedWithSdk`. This contains
the JUnit test suite you will depend on.
***

Congratulations! Your webkit tests are now shared with your SDK Runtime tests!

### 3. Converting a test to shared

You need to do two things when you are making a test shared:
1. Update the test suite to use the `SharedWebViewTestEnvironment`
2. Update the SDK JUnit Test Suite to invoke the test

We will use `WebViewTest` as an example:
`//cts/tests/tests/webkit/src/android/webkit/cts/WebViewTest.java`

Search for `getTestEnvironment()`. This method returns a `SharedWebViewTestEnvironment`.
Whenever your test needs to refer to anything that is not available in the SDK runtime,
or needs to be shared between the SDK runtime and the activity based tests,
use this class.

Open `SharedWebViewTestEnvironment` to familiarize yourself with what is available:
`//cts/libs/webkit-shared/src/android/webkit/cts/SharedWebViewTestEnvironment.java`

First convert any direct references to any variable that should come from the shared test
environment.

Eg:
```java
@Test
public void testExample() throws Throwable {
    - InstrumentationRegistry.getInstrumentation().waitForIdleSync();

    + getTestEnvironment().waitForIdleSync();
    ...
}
```

*** note
**Tip:**  You can likely just update your setup method to pull from the test environment for
minimal refactoring. Eg:

```java
@Test
public void setup() {
    mWebView = getTestEnvironment().getWebView();
    ...
}
```
***

Next you will invoke this shared test from your SDK JUnit test suite. An example of a JUnit
test suite can be found here:
`//cts/tests/tests/sdksandbox/webkit/src/android/sdksandbox/webkit/cts/WebViewSandboxTest.java`

You can see in this file that we use `SdkSandboxScenarioRule#assertSdkTestRunPasses` to invoke
test methods.

And that's it! Your test should now run! You can test that your method was added with `atest`:

```sh
# Confirm the test runs in the sandbox
atest CtsSdkSandboxWebkitTestCases:WebViewSandboxTest#<shared_test>
# Confirm the test still runs normally
atest CtsWebkitTestCases:WebViewTest#<shared_test>
```

## Invoking behavior in the Activity

There are some things you won't be able to initiate from within the SDK runtime
that are needed to write tests. For example, you cannot start a local server
in the SDK runtime, but this would be useful for testing against.

You can use the
ActivityInvoker (`//cts/libs/webkit-shared/src/android/webkit/cts/IActivityInvoker.aidl`)
to add this functionality.
The activity invoker allows SDK runtime tests to initiate events in the activity driving
the tests.

Once you have added a new ActivityInvoker API, provide a wrapper API to SharedWebViewTestEnvironment
to abstract these APIs away from test authors.
