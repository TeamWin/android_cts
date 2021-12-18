package android.companion.cts.core

import android.annotation.CallSuper
import android.companion.cts.common.AppHelper
import android.companion.cts.common.TestBase
import kotlin.test.assertTrue

open class CoreTestBase : TestBase() {
    protected val testApp = AppHelper(
            instrumentation, userId, TEST_APP_PACKAGE_NAME, TEST_APP_APK_PATH)

    @CallSuper
    override fun setUp() {
        super.setUp()

        // Make sure test app is installed.
        with(testApp) {
            if (!isInstalled()) install()
            assertTrue("Test app $packageName is not installed") { isInstalled() }
        }
    }
}