/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.packagemanager.verify.domain.host

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper
import com.android.cts.packagemanager.verify.domain.java.DomainUtils
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.CALLING_PKG_NAME
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_APK_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_APK_2
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_2
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DeviceJUnit4ClassRunner::class)
class DomainVerificationIntentHostTimedTests : BaseHostJUnit4Test() {

    private val buildHelper by lazy { CompatibilityBuildHelper(build) }

    @Before
    @After
    fun uninstall() {
        device.uninstallPackage(DECLARING_PKG_NAME_1)
        device.uninstallPackage(DECLARING_PKG_NAME_2)
    }

    @Test
    fun multipleVerifiedTakeLastFirstInstall() {
        installPackage(DECLARING_PKG_APK_2)

        // Ensure a later install time
        Thread.sleep(500)

        installPackage(DECLARING_PKG_APK_1)

        // Install an update, which should not take precedence
        installPackage(DECLARING_PKG_APK_2, reinstall = true)

        runDeviceTests(DeviceTestRunOptions(CALLING_PKG_NAME).apply {
            testClassName = "$CALLING_PKG_NAME.DomainVerificationIntentHostTimedTests"
        })
    }

    private fun installPackage(apkName: DomainUtils.ApkName, reinstall: Boolean = false) =
        device.installPackage(buildHelper.getTestFile(apkName.value), reinstall)
}
