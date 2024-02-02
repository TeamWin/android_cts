/*
 * Copyright 2024 The Android Open Source Project
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

package android.packageinstaller.install.cts

import android.app.UiAutomation
import android.content.ComponentName
import android.content.pm.Flags
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DeviceConfig.NAMESPACE_PACKAGE_MANAGER_SERVICE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.DeviceConfigStateManager
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.function.ThrowingRunnable
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant apps do not need pre-verified domains")
class InstallPreVerifiedDomainsTest : PackageInstallerTestBase() {
    companion object {
        private const val PROPERTY_PRE_VERIFIED_DOMAINS_COUNT_LIMIT =
                "pre_verified_domains_count_limit"
        private const val PROPERTY_PRE_VERIFIED_DOMAIN_LENGTH_LIMIT =
                "pre_verified_domain_length_limit"
        private const val TEMP_COUNT_LIMIT = 10
        private const val TEMP_LENGTH_LIMIT = 15
    }

    private val testDomains = setOf("com.foo", "com.bar")
    private var defaultInstantAppInstaller: ComponentName? = null
    private lateinit var fakeInstantAppInstaller: ComponentName

    private val uiAutomation: UiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
    private var mDefaultCountLimit: String? = null
    private var mDefaultLengthLimit: String? = null

    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setUp() {
        defaultInstantAppInstaller = pm.getInstantAppInstallerComponent()
        fakeInstantAppInstaller = ComponentName(
                context.packageName, context.packageName + ".FakeInstantAppInstallerActivity")
        mDefaultCountLimit = getLimitFromDeviceConfig(PROPERTY_PRE_VERIFIED_DOMAINS_COUNT_LIMIT)
        mDefaultLengthLimit = getLimitFromDeviceConfig(PROPERTY_PRE_VERIFIED_DOMAIN_LENGTH_LIMIT)
        setupFakeInstantAppInstaller()
    }

    @After
    fun cleanUp() {
        restoreInstantAppInstaller()
        setLimitInDeviceConfig(PROPERTY_PRE_VERIFIED_DOMAINS_COUNT_LIMIT, mDefaultCountLimit)
        setLimitInDeviceConfig(PROPERTY_PRE_VERIFIED_DOMAIN_LENGTH_LIMIT, mDefaultLengthLimit)
    }

    @RequiresFlagsEnabled(Flags.FLAG_SET_PRE_VERIFIED_DOMAINS)
    @Test(expected = IllegalArgumentException::class)
    fun testSetPreVerifiedDomainsWithEmpty() {
        createSessionWithPreVerifiedDomains(setOf())
    }

    @RequiresFlagsEnabled(Flags.FLAG_SET_PRE_VERIFIED_DOMAINS)
    @Test
    fun testSetPreVerifiedDomainsWithoutPermission() {
        assertThrows(
                "You need android.permission.ACCESS_INSTANT_APPS " +
                        "permission to set pre-verified domains.",
                SecurityException::class.java,
                ThrowingRunnable {
                    createSessionWithPreVerifiedDomains(testDomains)
                }
        )
    }

    @RequiresFlagsEnabled(Flags.FLAG_SET_PRE_VERIFIED_DOMAINS)
    @Test
    fun testSetPreVerifiedDomainsNotInstantAppInstaller() {
        restoreInstantAppInstaller()
        uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.ACCESS_INSTANT_APPS)
        try {
            assertThrows(
                    "Only the instant app installer can call this API.",
                    SecurityException::class.java,
                    ThrowingRunnable {
                        createSessionWithPreVerifiedDomains(testDomains)
                    }
            )
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SET_PRE_VERIFIED_DOMAINS)
    @Test
    fun testSetPreVerifiedDomainsExceedsCountLimit() {
        // Temporarily change the count limit to a much smaller number so the test can exceed it
        setLimitInDeviceConfig(
                PROPERTY_PRE_VERIFIED_DOMAINS_COUNT_LIMIT,
                TEMP_COUNT_LIMIT.toString()
        )
        val domains = mutableSetOf<String>()
        for (i in 0 until(TEMP_COUNT_LIMIT + 1)) {
            domains.add("domain$i")
        }

        uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.ACCESS_INSTANT_APPS)
        try {
            assertThrows(
                    "The number of pre-verified domains have exceeded the maximum of " +
                            TEMP_COUNT_LIMIT,
                    IllegalArgumentException::class.java,
                    ThrowingRunnable {
                        createSessionWithPreVerifiedDomains(domains)
                    }
            )
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SET_PRE_VERIFIED_DOMAINS)
    @Test
    fun testSetPreVerifiedDomainsExceedsLengthLimit() {
        // Temporarily change the count limit to a much smaller number so the test can exceed it
        setLimitInDeviceConfig(
                PROPERTY_PRE_VERIFIED_DOMAIN_LENGTH_LIMIT,
                TEMP_LENGTH_LIMIT.toString()
        )
        val invalidDomain = "a".repeat(TEMP_LENGTH_LIMIT + 1)

        uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.ACCESS_INSTANT_APPS)
        try {
            assertThrows(
                    "Pre-verified domain: [" +
                            invalidDomain + " ] exceeds maximum length allowed: " +
                            TEMP_LENGTH_LIMIT,
                    IllegalArgumentException::class.java,
                    ThrowingRunnable {
                        createSessionWithPreVerifiedDomains(setOf(invalidDomain))
                    }
            )
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SET_PRE_VERIFIED_DOMAINS)
    @Test
    fun testSetAndGetPreVerifiedDomains() {
        // Fake instant app installers can only work on ENG builds
        assumeTrue("eng" == Build.TYPE)
        var session: PackageInstaller.Session? = null
        uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.ACCESS_INSTANT_APPS)
        try {
            val sessionId = createSessionWithPreVerifiedDomains(testDomains)
            session = pi.openSession(sessionId)
            assertThat(session.getPreVerifiedDomains()).isEqualTo(testDomains)
        } finally {
            uiAutomation.dropShellPermissionIdentity()
            session?.abandon()
        }
    }

    private fun getLimitFromDeviceConfig(propertyName: String): String? {
        val stateManager = DeviceConfigStateManager(
                context,
                NAMESPACE_PACKAGE_MANAGER_SERVICE,
                propertyName
        )
        return stateManager.get()
    }

    private fun setLimitInDeviceConfig(propertyName: String, value: String?) {
        val stateManager = DeviceConfigStateManager(
                context,
                NAMESPACE_PACKAGE_MANAGER_SERVICE,
                propertyName
        )
        val currentValue = stateManager.get()
        if (currentValue != value) {
            // Only change the value if the current value is different
            stateManager.set(value)
        }
    }

    private fun createSessionWithPreVerifiedDomains(domains: Set<String>): Int {
        val (sessionId, session) = createSession(0, false, null)
        try {
            session.setPreVerifiedDomains(domains)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
        return sessionId
    }

    private fun setupFakeInstantAppInstaller() {
        // By disabling the original instant app installer, this test app becomes the instant app
        // installer
        uiAutomation.adoptShellPermissionIdentity()
        try {
            // Enable the fake instant app installer before disabling the default one
            pm.setComponentEnabledSetting(
                    fakeInstantAppInstaller,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
            )
            if (defaultInstantAppInstaller != null) {
                pm.setComponentEnabledSetting(
                    defaultInstantAppInstaller!!,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    0
                )
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
        assertThat(fakeInstantAppInstaller).isEqualTo(pm.getInstantAppInstallerComponent())
    }

    private fun restoreInstantAppInstaller() {
        uiAutomation.adoptShellPermissionIdentity()
        try {
            // Enable the original instant app installer before disabling the temporary one, so that
            // there won't be a time when the device doesn't have a valid instant app installer
            if (defaultInstantAppInstaller != null) {
                pm.setComponentEnabledSetting(
                        defaultInstantAppInstaller!!,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        0
                )
            }
            // Be careful not to let this test process killed, or the test will be considered failed
            pm.setComponentEnabledSetting(
                    fakeInstantAppInstaller,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
            )
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }
}
