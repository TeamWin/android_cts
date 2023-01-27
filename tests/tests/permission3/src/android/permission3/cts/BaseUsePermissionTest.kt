/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.permission3.cts

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.DeviceConfig
import android.provider.Settings
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.UiObjectNotFoundException
import android.support.test.uiautomator.UiScrollable
import android.support.test.uiautomator.UiSelector
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.modules.utils.build.SdkLevel
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before

abstract class BaseUsePermissionTest : BasePermissionTest() {
    companion object {
        const val APP_APK_NAME_31 = "CtsUsePermissionApp31.apk"

        const val APP_APK_PATH_22 = "$APK_DIRECTORY/CtsUsePermissionApp22.apk"
        const val APP_APK_PATH_22_CALENDAR_ONLY =
            "$APK_DIRECTORY/CtsUsePermissionApp22CalendarOnly.apk"
        const val APP_APK_PATH_22_NONE = "$APK_DIRECTORY/CtsUsePermissionApp22None.apk"
        const val APP_APK_PATH_23 = "$APK_DIRECTORY/CtsUsePermissionApp23.apk"
        const val APP_APK_PATH_25 = "$APK_DIRECTORY/CtsUsePermissionApp25.apk"
        const val APP_APK_PATH_26 = "$APK_DIRECTORY/CtsUsePermissionApp26.apk"
        const val APP_APK_PATH_28 = "$APK_DIRECTORY/CtsUsePermissionApp28.apk"
        const val APP_APK_PATH_29 = "$APK_DIRECTORY/CtsUsePermissionApp29.apk"
        const val APP_APK_PATH_30 = "$APK_DIRECTORY/CtsUsePermissionApp30.apk"
        const val APP_APK_PATH_31 = "$APK_DIRECTORY/$APP_APK_NAME_31"
        const val APP_APK_PATH_32 = "$APK_DIRECTORY/CtsUsePermissionApp32.apk"

        const val APP_APK_PATH_30_WITH_BACKGROUND =
                "$APK_DIRECTORY/CtsUsePermissionApp30WithBackground.apk"
        const val APP_APK_PATH_30_WITH_BLUETOOTH =
                "$APK_DIRECTORY/CtsUsePermissionApp30WithBluetooth.apk"
        const val APP_APK_PATH_LATEST = "$APK_DIRECTORY/CtsUsePermissionAppLatest.apk"
        const val APP_APK_PATH_LATEST_NONE = "$APK_DIRECTORY/CtsUsePermissionAppLatestNone.apk"
        const val APP_APK_PATH_WITH_OVERLAY = "$APK_DIRECTORY/CtsUsePermissionAppWithOverlay.apk"
        const val APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31 =
            "$APK_DIRECTORY/CtsCreateNotificationChannelsApp31.apk"
        const val APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_33 =
            "$APK_DIRECTORY/CtsCreateNotificationChannelsApp33.apk"
        const val APP_APK_PATH_MEDIA_PERMISSION_33_WITH_STORAGE =
            "$APK_DIRECTORY/CtsMediaPermissionApp33WithStorage.apk"
        const val APP_APK_PATH_IMPLICIT_USER_SELECT_STORAGE =
            "$APK_DIRECTORY/CtsUsePermissionAppImplicitUserSelectStorage.apk"
        const val APP_APK_PATH_OTHER_APP =
            "$APK_DIRECTORY/CtsDifferentPkgNameApp.apk"
        const val APP_PACKAGE_NAME = "android.permission3.cts.usepermission"
        const val OTHER_APP_PACKAGE_NAME = "android.permission3.cts.usepermissionother"
        const val TEST_INSTALLER_PACKAGE_NAME = "android.permission3.cts"

        const val ALLOW_ALL_PHOTOS_BUTTON =
            "com.android.permissioncontroller:id/permission_allow_all_photos_button"
        const val SELECT_PHOTOS_BUTTON =
            "com.android.permissioncontroller:id/permission_allow_selected_photos_button"
        const val SELECT_MORE_PHOTOS_BUTTON =
            "com.android.permissioncontroller:id/permission_allow_more_selected_photos_button"
        const val ALLOW_BUTTON =
                "com.android.permissioncontroller:id/permission_allow_button"
        const val ALLOW_FOREGROUND_BUTTON =
                "com.android.permissioncontroller:id/permission_allow_foreground_only_button"
        const val DENY_BUTTON = "com.android.permissioncontroller:id/permission_deny_button"
        const val DENY_AND_DONT_ASK_AGAIN_BUTTON =
                "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button"
        const val NO_UPGRADE_BUTTON =
                "com.android.permissioncontroller:id/permission_no_upgrade_button"
        const val NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON =
                "com.android.permissioncontroller:" +
                        "id/permission_no_upgrade_and_dont_ask_again_button"

        const val ALLOW_ALWAYS_RADIO_BUTTON =
                "com.android.permissioncontroller:id/allow_always_radio_button"
        const val ALLOW_RADIO_BUTTON = "com.android.permissioncontroller:id/allow_radio_button"
        const val ALLOW_FOREGROUND_RADIO_BUTTON =
                "com.android.permissioncontroller:id/allow_foreground_only_radio_button"
        const val ASK_RADIO_BUTTON = "com.android.permissioncontroller:id/ask_radio_button"
        const val DENY_RADIO_BUTTON = "com.android.permissioncontroller:id/deny_radio_button"
        const val SELECT_PHOTOS_RADIO_BUTTON =
            "com.android.permissioncontroller:id/select_photos_radio_button"

        const val NOTIF_TEXT = "permgrouprequest_notifications"
        const val ALLOW_BUTTON_TEXT = "grant_dialog_button_allow"
        const val ALLOW_ALL_FILES_BUTTON_TEXT = "app_permission_button_allow_all_files"
        const val ALLOW_FOREGROUND_BUTTON_TEXT = "grant_dialog_button_allow_foreground"
        const val ALLOW_FOREGROUND_PREFERENCE_TEXT = "permission_access_only_foreground"
        const val ASK_BUTTON_TEXT = "app_permission_button_ask"
        const val ALLOW_ONE_TIME_BUTTON_TEXT = "grant_dialog_button_allow_one_time"
        const val DENY_BUTTON_TEXT = "grant_dialog_button_deny"
        const val DENY_ANYWAY_BUTTON_TEXT = "grant_dialog_button_deny_anyway"
        const val DENY_AND_DONT_ASK_AGAIN_BUTTON_TEXT =
                "grant_dialog_button_deny_and_dont_ask_again"
        const val NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON_TEXT = "grant_dialog_button_no_upgrade"
        const val ALERT_DIALOG_MESSAGE = "android:id/message"
        const val ALERT_DIALOG_OK_BUTTON = "android:id/button1"
        const val APP_PERMISSION_RATIONALE_CONTAINER_VIEW =
            "com.android.permissioncontroller:id/app_permission_rationale_container"
        const val APP_PERMISSION_RATIONALE_CONTENT_VIEW =
            "com.android.permissioncontroller:id/app_permission_rationale_content"
        const val GRANT_DIALOG_PERMISSION_RATIONALE_CONTAINER_VIEW =
            "com.android.permissioncontroller:id/permission_rationale_container"
        const val PERMISSION_RATIONALE_ACTIVITY_TITLE_VIEW =
            "com.android.permissioncontroller:id/permission_rationale_title"
        const val PERMISSION_RATIONALE_SETTINGS_SECTION =
            "com.android.permissioncontroller:id/settings_section"

        const val REQUEST_LOCATION_MESSAGE = "permgrouprequest_location"

        val TEST_INSTALLER_ACTIVITY_COMPONENT_NAME =
            ComponentName(context, TestInstallerActivity::class.java)

        val MEDIA_PERMISSIONS: Set<String> = mutableSetOf(
            Manifest.permission.ACCESS_MEDIA_LOCATION,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        ).apply {
            if (SdkLevel.isAtLeastU()) {
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
        }.toSet()

        val STORAGE_AND_MEDIA_PERMISSIONS = MEDIA_PERMISSIONS
            .plus(Manifest.permission.READ_EXTERNAL_STORAGE)
            .plus(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        @JvmStatic
        protected val PICKER_ENABLED_SETTING = "photo_picker_prompt_enabled"

        @JvmStatic
        protected fun isPhotoPickerPermissionPromptEnabled(): Boolean {
            return SystemUtil.callWithShellPermissionIdentity { DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_PRIVACY, PICKER_ENABLED_SETTING, false)
            }
        }
    }

    enum class PermissionState {
        ALLOWED,
        DENIED,
        DENIED_WITH_PREJUDICE
    }

    private val platformResources = context.createPackageContext("android", 0).resources
    private val permissionToLabelResNameMap = mapOf(
            // Contacts
            android.Manifest.permission.READ_CONTACTS
                    to "@android:string/permgrouplab_contacts",
            android.Manifest.permission.WRITE_CONTACTS
                    to "@android:string/permgrouplab_contacts",
            // Calendar
            android.Manifest.permission.READ_CALENDAR
                    to "@android:string/permgrouplab_calendar",
            android.Manifest.permission.WRITE_CALENDAR
                    to "@android:string/permgrouplab_calendar",
            // SMS
            android.Manifest.permission.SEND_SMS to "@android:string/permgrouplab_sms",
            android.Manifest.permission.RECEIVE_SMS to "@android:string/permgrouplab_sms",
            android.Manifest.permission.READ_SMS to "@android:string/permgrouplab_sms",
            android.Manifest.permission.RECEIVE_WAP_PUSH to "@android:string/permgrouplab_sms",
            android.Manifest.permission.RECEIVE_MMS to "@android:string/permgrouplab_sms",
            "android.permission.READ_CELL_BROADCASTS" to "@android:string/permgrouplab_sms",
            // Storage
            android.Manifest.permission.READ_EXTERNAL_STORAGE
                    to "@android:string/permgrouplab_storage",
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    to "@android:string/permgrouplab_storage",
            // Location
            android.Manifest.permission.ACCESS_FINE_LOCATION
                    to "@android:string/permgrouplab_location",
            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    to "@android:string/permgrouplab_location",
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    to "@android:string/permgrouplab_location",
            // Phone
            android.Manifest.permission.READ_PHONE_STATE
                    to "@android:string/permgrouplab_phone",
            android.Manifest.permission.CALL_PHONE to "@android:string/permgrouplab_phone",
            "android.permission.ACCESS_IMS_CALL_SERVICE"
                    to "@android:string/permgrouplab_phone",
            android.Manifest.permission.READ_CALL_LOG to "@android:string/permgrouplab_phone",
            android.Manifest.permission.WRITE_CALL_LOG to "@android:string/permgrouplab_phone",
            android.Manifest.permission.ADD_VOICEMAIL to "@android:string/permgrouplab_phone",
            android.Manifest.permission.USE_SIP to "@android:string/permgrouplab_phone",
            android.Manifest.permission.PROCESS_OUTGOING_CALLS
                    to "@android:string/permgrouplab_phone",
            // Microphone
            android.Manifest.permission.RECORD_AUDIO
                    to "@android:string/permgrouplab_microphone",
            // Camera
            android.Manifest.permission.CAMERA to "@android:string/permgrouplab_camera",
            // Body sensors
            android.Manifest.permission.BODY_SENSORS to "@android:string/permgrouplab_sensors",
            android.Manifest.permission.BODY_SENSORS_BACKGROUND
                    to "@android:string/permgrouplab_sensors",
            android.Manifest.permission.BODY_SENSORS_WRIST_TEMPERATURE
                    to "@android:string/permgrouplab_sensors",
            android.Manifest.permission.BODY_SENSORS_WRIST_TEMPERATURE_BACKGROUND
                    to "@android:string/permgrouplab_sensors",
            // Bluetooth
            android.Manifest.permission.BLUETOOTH_CONNECT to
                    "@android:string/permgrouplab_nearby_devices",
            android.Manifest.permission.BLUETOOTH_SCAN to
                    "@android:string/permgrouplab_nearby_devices",
            // Aural
            android.Manifest.permission.READ_MEDIA_AUDIO to
                "@android:string/permgrouplab_readMediaAural",
            // Visual
            android.Manifest.permission.READ_MEDIA_IMAGES to
                "@android:string/permgrouplab_readMediaVisual",
            android.Manifest.permission.READ_MEDIA_VIDEO to
                "@android:string/permgrouplab_readMediaVisual"
    )

    @Before
    @After
    fun uninstallApp() {
        uninstallPackage(APP_PACKAGE_NAME, requireSuccess = false)
    }

    protected fun clearTargetSdkWarning() =
        waitFindObjectOrNull(By.res("android:id/button1"))?.click()?.also { waitForIdle() }

    protected fun clickPermissionReviewContinue() {
        if (isAutomotive || isWatch) {
            click(By.text(getPermissionControllerString("review_button_continue")))
        } else {
            click(By.res("com.android.permissioncontroller:id/continue_button"))
        }
    }

    protected fun installPackageWithInstallSourceAndEmptyMetadata(
        apkName: String
    ) {
    installPackageViaSession(apkName, AppMetadata.createEmptyAppMetadata())
    }

    protected fun installPackageWithInstallSourceAndMetadata(
        apkName: String
    ) {
        installPackageViaSession(apkName, AppMetadata.createDefaultAppMetadata())
    }

    protected fun installPackageWithInstallSourceAndNoMetadata(
      apkName: String
    ) {
        installPackageViaSession(apkName)
    }

    protected fun installPackageWithInstallSourceAndInvalidMetadata(
      apkName: String
    ) {
        installPackageViaSession(apkName, AppMetadata.createInvalidAppMetadata())
    }

    protected fun installPackageWithInstallSourceAndMetadataWithoutTopLevelVersion(
        apkName: String
    ) {
        installPackageViaSession(
            apkName, AppMetadata.createInvalidAppMetadataWithoutTopLevelVersion())
    }

    protected fun installPackageWithInstallSourceAndMetadataWithInvalidTopLevelVersion(
        apkName: String
    ) {
        installPackageViaSession(
            apkName, AppMetadata.createInvalidAppMetadataWithInvalidTopLevelVersion())
    }

    protected fun installPackageWithInstallSourceAndMetadataWithoutSafetyLabelVersion(
        apkName: String
    ) {
        installPackageViaSession(
            apkName, AppMetadata.createInvalidAppMetadataWithoutSafetyLabelVersion())
    }

     protected fun installPackageWithInstallSourceAndMetadataWithInvalidSafetyLabelVersion(
         apkName: String
     ) {
        installPackageViaSession(
            apkName, AppMetadata.createInvalidAppMetadataWithInvalidSafetyLabelVersion())
    }

    protected fun installPackageWithoutInstallSource(
        apkName: String
    ) {
        // TODO(b/257293222): Update/remove when hooking up PackageManager APIs
        installPackage(apkName)
    }

    protected fun assertPermissionRationaleDialogSettingsSectionIsVisible(
        expected: Boolean
    ) {
        findView(By.res(PERMISSION_RATIONALE_SETTINGS_SECTION), expected = expected)
    }
    protected fun clickPermissionReviewCancel() {
        if (isAutomotive || isWatch) {
            click(By.text(getPermissionControllerString("review_button_cancel")))
        } else {
            click(By.res("com.android.permissioncontroller:id/cancel_button"))
        }
    }

    protected fun approvePermissionReview() {
        startAppActivityAndAssertResultCode(Activity.RESULT_OK) {
            clickPermissionReviewContinue()
        }
    }

    protected fun cancelPermissionReview() {
        startAppActivityAndAssertResultCode(Activity.RESULT_CANCELED) {
            clickPermissionReviewCancel()
        }
    }

    protected fun assertAppDoesNotNeedPermissionReview() {
        startAppActivityAndAssertResultCode(Activity.RESULT_OK) {}
    }

    protected inline fun startAppActivityAndAssertResultCode(
        expectedResultCode: Int,
        block: () -> Unit
    ) {
        val future = startActivityForFuture(
            Intent().apply {
                component = ComponentName(
                    APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.FinishOnCreateActivity"
                )
            }
        )
        block()
        assertEquals(
            expectedResultCode, future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).resultCode
        )
    }

    protected inline fun requestAppPermissionsForNoResult(
        vararg permissions: String?,
        block: () -> Unit
    ) {
        // Request the permissions
        context.startActivity(
                Intent().apply {
                    component = ComponentName(
                            APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.RequestPermissionsActivity"
                    )
                    putExtra("$APP_PACKAGE_NAME.PERMISSIONS", permissions)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
        )
        waitForIdle()
        // Perform the post-request action
        block()
    }

    protected inline fun requestAppPermissions(
        vararg permissions: String?,
        expectTargetSdkWarning: Boolean = false,
        block: () -> Unit
    ): Instrumentation.ActivityResult {
        // Request the permissions
        val future = startActivityForFuture(
            Intent().apply {
                component = ComponentName(
                    APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.RequestPermissionsActivity"
                )
                putExtra("$APP_PACKAGE_NAME.PERMISSIONS", permissions)
            }
        )
        waitForIdle()

        // Clear the low target SDK warning message if it's expected
        if (expectTargetSdkWarning) {
            clearTargetSdkWarning()
        }

        // Notification permission prompt is shown first, so get it out of the way
        clickNotificationPermissionRequestAllowButtonIfAvailable()
        // Perform the post-request action
        block()
        return future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    }

    protected inline fun requestAppPermissionsAndAssertResult(
        permissions: Array<out String?>,
        permissionAndExpectedGrantResults: Array<out Pair<String?, Boolean>>,
        expectTargetSdkWarning: Boolean = false,
        block: () -> Unit
    ) {
        val result = requestAppPermissions(
            *permissions, expectTargetSdkWarning = expectTargetSdkWarning, block = block
        )
        assertEquals(Activity.RESULT_OK, result.resultCode)
        assertEquals(
            result.resultData!!.getStringArrayExtra("$APP_PACKAGE_NAME.PERMISSIONS")!!.size,
            result.resultData!!.getIntArrayExtra("$APP_PACKAGE_NAME.GRANT_RESULTS")!!.size
        )

        assertEquals(
            permissionAndExpectedGrantResults.toList(),
            result.resultData!!.getStringArrayExtra("$APP_PACKAGE_NAME.PERMISSIONS")!!
                .zip(
                    result.resultData!!.getIntArrayExtra("$APP_PACKAGE_NAME.GRANT_RESULTS")!!
                        .map { it == PackageManager.PERMISSION_GRANTED }
                )
        )
        permissionAndExpectedGrantResults.forEach {
            it.first?.let { permission ->
                assertAppHasPermission(permission, it.second)
            }
        }
    }

    protected inline fun requestAppPermissionsAndAssertResult(
        vararg permissionAndExpectedGrantResults: Pair<String?, Boolean>,
        expectTargetSdkWarning: Boolean = false,
        block: () -> Unit
    ) = requestAppPermissionsAndAssertResult(
        permissionAndExpectedGrantResults.map { it.first }.toTypedArray(),
        permissionAndExpectedGrantResults,
        expectTargetSdkWarning,
        block
    )

    protected fun clickPermissionRequestAllowButton(timeoutMillis: Long = 20000) {
        if (isAutomotive) {
            click(By.text(getPermissionControllerString(ALLOW_BUTTON_TEXT)), timeoutMillis)
        } else {
            click(By.res(ALLOW_BUTTON), timeoutMillis)
        }
    }

    protected fun clickPermissionRequestAllowAllPhotosButton(timeoutMillis: Long = 20000) {
        click(By.res(ALLOW_ALL_PHOTOS_BUTTON), timeoutMillis)
    }

    /**
     * Only for use in tests that are not testing the notification permission popup, on T devices
     */
    protected fun clickNotificationPermissionRequestAllowButtonIfAvailable() {
        if (!SdkLevel.isAtLeastT()) {
            return
        }

        if (waitFindObjectOrNull(By.text(getPermissionControllerString(
                NOTIF_TEXT, APP_PACKAGE_NAME)), 1000) != null) {
            if (isAutomotive) {
                click(By.text(getPermissionControllerString(ALLOW_BUTTON_TEXT)))
            } else {
                click(By.res(ALLOW_BUTTON))
            }
        }
    }

    protected fun clickPermissionRequestSettingsLinkAndAllowAlways() {
        clickPermissionRequestSettingsLink()
        eventually({
            clickAllowAlwaysInSettings()
        }, TIMEOUT_MILLIS * 2)
        pressBack()
    }

    protected fun clickAllowAlwaysInSettings() {
        if (isAutomotive || isTv || isWatch) {
            click(By.text(getPermissionControllerString("app_permission_button_allow_always")))
        } else {
            click(By.res("com.android.permissioncontroller:id/allow_always_radio_button"))
        }
    }

    protected fun clickAllowForegroundInSettings() {
        click(By.res(ALLOW_FOREGROUND_RADIO_BUTTON))
    }

    protected fun clicksDenyInSettings() {
        if (isAutomotive || isWatch) {
            click(By.text(getPermissionControllerString("app_permission_button_deny")))
        } else {
            click(By.res("com.android.permissioncontroller:id/deny_radio_button"))
        }
    }

    protected fun clickPermissionRequestAllowForegroundButton(timeoutMillis: Long = 10_000) {
        if (isAutomotive) {
            click(By.text(
                    getPermissionControllerString(ALLOW_FOREGROUND_BUTTON_TEXT)), timeoutMillis)
        } else {
            click(By.res(ALLOW_FOREGROUND_BUTTON), timeoutMillis)
        }
    }

    protected fun clickPermissionRequestDenyButton() {
        if (isAutomotive || isWatch || isTv) {
            click(By.text(getPermissionControllerString(DENY_BUTTON_TEXT)))
        } else {
            click(By.res(DENY_BUTTON))
        }
    }

    protected fun clickPermissionRequestSettingsLinkAndDeny() {
        clickPermissionRequestSettingsLink()
        eventually({
            clicksDenyInSettings()
        }, TIMEOUT_MILLIS * 2)
        waitForIdle()
        pressBack()
    }

    protected fun clickPermissionRequestSettingsLink() {
        waitForIdle()
        eventually {
            // UiObject2 doesn't expose CharSequence.
            val node = if (isAutomotive) {
                // Should match "Allow in settings." (location) and "go to settings." (body sensors)
                uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(
                        " settings."
                )[0]
            } else {
                uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByViewId(
                        "com.android.permissioncontroller:id/detail_message"
                )[0]
            }
            assertTrue(node.isVisibleToUser)
            val text = node.text as Spanned
            val clickableSpan = text.getSpans(0, text.length, ClickableSpan::class.java)[0]
            // We could pass in null here in Java, but we need an instance in Kotlin.
            clickableSpan.onClick(View(context))
        }
        waitForIdle()
    }

    protected fun clickPermissionRequestDenyAndDontAskAgainButton() {
        if (isAutomotive) {
            click(By.text(getPermissionControllerString(DENY_AND_DONT_ASK_AGAIN_BUTTON_TEXT)))
        } else if (isWatch) {
            click(By.text(getPermissionControllerString(DENY_BUTTON_TEXT)))
        } else {
            click(By.res(DENY_AND_DONT_ASK_AGAIN_BUTTON))
        }
    }

    // Only used in TV and Watch form factors
    protected fun clickPermissionRequestDontAskAgainButton() {
        if (isWatch) {
            click(By.text(getPermissionControllerString(DENY_BUTTON_TEXT)))
        } else {
            click(
                By.res("com.android.permissioncontroller:id/permission_deny_dont_ask_again_button")
            )
        }
    }

    protected fun clickPermissionRequestNoUpgradeAndDontAskAgainButton() {
        if (isAutomotive) {
            click(By.text(getPermissionControllerString(NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON_TEXT)))
        } else {
            click(By.res(NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON))
        }
    }

    protected fun clickPermissionRationaleContentInAppPermission() {
        waitForIdle()
        click(By.res(APP_PERMISSION_RATIONALE_CONTENT_VIEW))
    }

    protected fun clickPermissionRationaleViewInGrantDialog() {
        waitForIdle()
        click(By.res(GRANT_DIALOG_PERMISSION_RATIONALE_CONTAINER_VIEW))
    }

    protected fun grantAppPermissions(vararg permissions: String, targetSdk: Int = 30) {
        setAppPermissionState(*permissions, state = PermissionState.ALLOWED, isLegacyApp = false,
                targetSdk = targetSdk)
    }

    protected fun revokeAppPermissions(
        vararg permissions: String,
        isLegacyApp: Boolean = false,
        targetSdk: Int = 30
    ) {
        setAppPermissionState(*permissions, state = PermissionState.DENIED,
                isLegacyApp = isLegacyApp, targetSdk = targetSdk)
    }

    private fun navigateToAppPermissionSettings() {
        if (isTv) {
            // Dismiss DeprecatedTargetSdkVersionDialog, if present
            if (waitFindObjectOrNull(By.text(APP_PACKAGE_NAME), 1000L) != null) {
                pressBack()
            }
            pressHome()
        } else {
            pressBack()
            pressBack()
            pressBack()
        }

        // Try multiple times as the AppInfo page might have read stale data
        eventually({
            try {
                // Open the app details settings
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", APP_PACKAGE_NAME, null)
                        addCategory(Intent.CATEGORY_DEFAULT)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
                // Open the permissions UI
                click(byTextRes(R.string.permissions).enabled(true))
            } catch (e: Exception) {
                pressBack()
                throw e
            }
        }, TIMEOUT_MILLIS)
    }

    protected fun navigateToIndividualPermissionSetting(permission: String) {
        navigateToAppPermissionSettings()
        val permissionLabel = getPermissionLabel(permission)
        if (isWatch) {
            click(By.text(permissionLabel), 40_000)
        } else {
            clickPermissionControllerUi(By.text(permissionLabel))
        }
    }

    private fun setAppPermissionState(
        vararg permissions: String,
        state: PermissionState,
        isLegacyApp: Boolean,
        targetSdk: Int,
        manuallyNavigate: Boolean = false,
    ) {
        val useLegacyNavigation = isWatch || isAutomotive || isTv || manuallyNavigate
        if (useLegacyNavigation) {
            navigateToAppPermissionSettings()
        }

        val navigatedGroupLabels = mutableSetOf<String>()
        for (permission in permissions) {
            // Find the permission screen
            val permissionLabel = getPermissionLabel(permission)
            if (navigatedGroupLabels.contains(getPermissionLabel(permission))) {
                continue
            }
            navigatedGroupLabels.add(permissionLabel)
            if (useLegacyNavigation) {
                if (isWatch) {
                    click(By.text(permissionLabel), 40_000)
                } else {
                    clickPermissionControllerUi(By.text(permissionLabel))
                }
            } else {
                runWithShellPermissionIdentity {
                    context.startActivity(
                        Intent(Intent.ACTION_MANAGE_APP_PERMISSION).apply {
                            putExtra(Intent.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME)
                            putExtra(Intent.EXTRA_PERMISSION_NAME, permission)
                            putExtra(Intent.EXTRA_USER, Process.myUserHandle())
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                }
            }

            val wasGranted = if (isAutomotive) {
                // Automotive doesn't support one time permissions, and thus
                // won't show an "Ask every time" message
                !waitFindObject(By.text(
                        getPermissionControllerString("app_permission_button_deny"))).isChecked
            } else if (isTv || isWatch) {
                !(waitFindObject(
                    By.text(getPermissionControllerString(DENY_BUTTON_TEXT))).isChecked ||
                    (!isLegacyApp && hasAskButton(permission) && waitFindObject(
                        By.text(getPermissionControllerString(ASK_BUTTON_TEXT))).isChecked))
            } else {
                !(waitFindObject(By.res(DENY_RADIO_BUTTON)).isChecked ||
                    (!isLegacyApp && hasAskButton(permission) &&
                        waitFindObject(By.res(ASK_RADIO_BUTTON)).isChecked))
            }
            var alreadyChecked = false
            val button = waitFindObject(
                if (isAutomotive) {
                    // Automotive doesn't support one time permissions, and thus
                    // won't show an "Ask every time" message
                    when (state) {
                        PermissionState.ALLOWED ->
                            if (showsForegroundOnlyButton(permission)) {
                                By.text(getPermissionControllerString(
                                        "app_permission_button_allow_foreground"))
                            } else if (showsAllowPhotosButton(permission)) {
                                By.text(getPermissionControllerString(
                                        "app_permission_button_allow_all_photos"))
                            } else {
                                By.text(getPermissionControllerString(
                                    "app_permission_button_allow"))
                            }
                        PermissionState.DENIED -> By.text(
                                getPermissionControllerString("app_permission_button_deny"))
                        PermissionState.DENIED_WITH_PREJUDICE -> By.text(
                                getPermissionControllerString("app_permission_button_deny"))
                    }
                } else if (isTv || isWatch) {
                    when (state) {
                        PermissionState.ALLOWED ->
                            if (showsForegroundOnlyButton(permission)) {
                                By.text(getPermissionControllerString(
                                        ALLOW_FOREGROUND_PREFERENCE_TEXT))
                            } else {
                                byAnyText(getPermissionControllerResString(ALLOW_BUTTON_TEXT),
                                    getPermissionControllerResString(ALLOW_ALL_FILES_BUTTON_TEXT))
                            }
                        PermissionState.DENIED ->
                            if (!isLegacyApp && hasAskButton(permission)) {
                                By.text(getPermissionControllerString(ASK_BUTTON_TEXT))
                            } else {
                                By.text(getPermissionControllerString(DENY_BUTTON_TEXT))
                            }
                        PermissionState.DENIED_WITH_PREJUDICE -> By.text(
                                getPermissionControllerString(DENY_BUTTON_TEXT))
                    }
                } else {
                    when (state) {
                        PermissionState.ALLOWED ->
                            if (showsForegroundOnlyButton(permission)) {
                                By.res(ALLOW_FOREGROUND_RADIO_BUTTON)
                            } else if (showsAlwaysButton(permission)) {
                                By.res(ALLOW_ALWAYS_RADIO_BUTTON)
                            } else {
                                By.res(ALLOW_RADIO_BUTTON)
                            }
                        PermissionState.DENIED ->
                            if (!isLegacyApp && hasAskButton(permission)) {
                                By.res(ASK_RADIO_BUTTON)
                            } else {
                                By.res(DENY_RADIO_BUTTON)
                            }
                        PermissionState.DENIED_WITH_PREJUDICE -> By.res(DENY_RADIO_BUTTON)
                    }
                }
            )
            alreadyChecked = button.isChecked
            if (!alreadyChecked) {
                button.click()
            }

            val shouldShowStorageWarning = SdkLevel.isAtLeastT() &&
                targetSdk <= Build.VERSION_CODES.S_V2 &&
                permission in MEDIA_PERMISSIONS
            if (shouldShowStorageWarning) {
                click(By.res(ALERT_DIALOG_OK_BUTTON))
            } else if (!alreadyChecked && isLegacyApp && wasGranted) {
                if (!isTv) {
                    // Wait for alert dialog to popup, then scroll to the bottom of it
                    waitFindObject(By.res(ALERT_DIALOG_MESSAGE))
                    scrollToBottom()
                }

                // Due to the limited real estate, Wear uses buttons with icons instead of text
                // for dialogs
                if (isWatch) {
                    click(By.res(
                        "com.android.permissioncontroller:id/wear_alertdialog_positive_button"))
                } else {
                    val resources = context.createPackageContext(
                        packageManager.permissionControllerPackageName, 0
                    ).resources
                    val confirmTextRes = resources.getIdentifier(
                        "com.android.permissioncontroller:string/grant_dialog_button_deny_anyway",
                        null, null
                    )

                    val confirmText = resources.getString(confirmTextRes)
                    click(byTextStartsWithCaseInsensitive(confirmText))
                }
            }
            pressBack()
        }
        pressBack()
        pressBack()
    }

    private fun getPermissionLabel(permission: String): String {
        val labelResName = permissionToLabelResNameMap[permission]
        assertNotNull("Unknown permission $permission", labelResName)
        val labelRes = platformResources.getIdentifier(labelResName, null, null)
        return platformResources.getString(labelRes)
    }

    private fun hasAskButton(permission: String): Boolean =
        when (permission) {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION -> true
            else -> false
        }
    private fun showsAllowPhotosButton(permission: String): Boolean {
        if (!isPhotoPickerPermissionPromptEnabled()) {
            return false
        }
        return when (permission) {
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO -> true
            else -> false
        }
    }

    private fun showsForegroundOnlyButton(permission: String): Boolean =
        when (permission) {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO -> true
            else -> false
        }

    private fun showsAlwaysButton(permission: String): Boolean =
        when (permission) {
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION -> true
            else -> false
        }

    private fun scrollToBottom() {
        val scrollable = UiScrollable(UiSelector().scrollable(true)).apply {
            if (isWatch) {
                swipeDeadZonePercentage = 0.1
            } else {
                swipeDeadZonePercentage = 0.25
            }
        }
        waitForIdle()
        if (scrollable.exists()) {
            try {
                scrollable.flingToEnd(10)
            } catch (e: UiObjectNotFoundException) {
                // flingToEnd() sometimes still fails despite waitForIdle() and the exists() check
                // (b/246984354).
                e.printStackTrace()
            }
        }
    }

    private fun byTextRes(textRes: Int): BySelector = By.text(context.getString(textRes))

    private fun byTextStartsWithCaseInsensitive(prefix: String): BySelector =
        By.text(Pattern.compile("(?i)^${Pattern.quote(prefix)}.*$"))

    protected fun assertAppHasPermission(permissionName: String, expectPermission: Boolean) {
        assertEquals( "Permission $permissionName",
            if (expectPermission) {
                PackageManager.PERMISSION_GRANTED
            } else {
                PackageManager.PERMISSION_DENIED
            },
            packageManager.checkPermission(permissionName, APP_PACKAGE_NAME)
        )
    }

    protected fun assertAppHasCalendarAccess(expectAccess: Boolean) {
        val future = startActivityForFuture(
            Intent().apply {
                component = ComponentName(
                    APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.CheckCalendarAccessActivity"
                )
            }
        )
        waitForIdle()
        clickNotificationPermissionRequestAllowButtonIfAvailable()
        val result = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        assertEquals(Activity.RESULT_OK, result.resultCode)
        assertTrue(result.resultData!!.hasExtra("$APP_PACKAGE_NAME.HAS_ACCESS"))
        assertEquals(
            expectAccess,
            result.resultData!!.getBooleanExtra("$APP_PACKAGE_NAME.HAS_ACCESS", false)
        )
    }

    protected fun assertPermissionFlags(permName: String, vararg flags: Pair<Int, Boolean>) {
        val user = Process.myUserHandle()
        SystemUtil.runWithShellPermissionIdentity {
            val currFlags =
                packageManager.getPermissionFlags(permName, APP_PACKAGE_NAME, user)
            for ((flag, set) in flags) {
                assertEquals("flag $flag: ", set, currFlags and flag != 0)
            }
        }
    }
}
