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

package android.permission.cts

import android.service.notification.StatusBarNotification
import org.junit.Assert
import android.permission.cts.TestUtils.ensure
import android.permission.cts.TestUtils.eventually

object NotificationListenerUtils {

    private const val NOTIFICATION_CANCELLATION_TIMEOUT_MILLIS = 5000L
    private const val NOTIFICATION_WAIT_MILLIS = 2000L
    private val notificationService = NotificationListener.getInstance()

    @JvmStatic
    fun assertEmptyNotification(packageName: String, notificationId: Int) {
        ensure({
            Assert.assertNull(
            "Expected no notification",
            getNotification(packageName, notificationId))
        }, NOTIFICATION_WAIT_MILLIS)
    }

    @JvmStatic
    fun assertNotificationExist(packageName: String, notificationId: Int) {
        eventually({
            Assert.assertNotNull(
                "Expected notification, none found",
                getNotification(packageName, notificationId))
        }, NOTIFICATION_WAIT_MILLIS)
    }

    @JvmStatic
    fun cancelNotification(packageName: String, notificationId: Int) {
        val notification = getNotification(packageName, notificationId)
        if (notification != null) {
            notificationService.cancelNotification(notification.key)
            eventually({
                Assert.assertTrue(getNotification(packageName, notificationId) == null)
            }, NOTIFICATION_CANCELLATION_TIMEOUT_MILLIS)
        }
    }

    @JvmStatic
    fun cancelNotifications(packageName: String) {
        val notifications = getNotifications(packageName)
        if (notifications.isNotEmpty()) {
            notifications.forEach { notification ->
                notificationService.cancelNotification(notification.key)
            }
            eventually({
                Assert.assertTrue(getNotifications(packageName).isEmpty())
            }, NOTIFICATION_CANCELLATION_TIMEOUT_MILLIS)
        }
    }

    @JvmStatic
    fun getNotification(packageName: String, notificationId: Int): StatusBarNotification? {
        return getNotifications(packageName).firstOrNull {
            it.id == notificationId
        }
    }

    @JvmStatic
    fun getNotifications(packageName: String): List<StatusBarNotification> {
        val notifications: MutableList<StatusBarNotification> = ArrayList()
        for (notification in notificationService.activeNotifications) {
            if (notification.packageName == packageName) {
                notifications.add(notification)
            }
        }
        return notifications
    }
}