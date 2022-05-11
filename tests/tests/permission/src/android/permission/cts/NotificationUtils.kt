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
import java.util.concurrent.TimeUnit

/**
 * Utility methods to interact with NotificationManager through the CTS NotificationListenerService
 * to get or clear notifications.
 */
object NotificationUtils {

    private val NOTIFICATION_CANCELLATION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5)

    /**
     * Get a notification listener notification that is currently visible.
     *
     * @param cancelNotification if `true` the notification is canceled inside this method
     * @return The notification or `null` if there is none
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun getNotificationForPackageAndId(
        pkg: String,
        id: Int,
        cancelNotification: Boolean
    ): StatusBarNotification? {
        val notificationService = NotificationListener.getInstance()
        val notifications: List<StatusBarNotification> = getNotificationsForPackage(pkg)
        if (notifications.isEmpty()) {
            return null
        }
        for (notification in notifications) {
            if (notification.id == id) {
                if (cancelNotification) {
                    clearNotification(notification)
                }
                return notification
            }
        }
        return null
    }

    /**
     * Clears all currently visible notifications for a specified package.
     */
    @JvmStatic
    @Throws(Throwable::class)
    fun clearNotificationsForPackage(pkg: String) {
        val notifications: List<StatusBarNotification> = getNotificationsForPackage(pkg)
        if (notifications.isEmpty()) {
            return
        }

        clearNotifications(notifications)
    }

    /** Clears the specified notification and ensures (asserts) it was removed */
    @JvmStatic
    @Throws(Throwable::class)
    fun clearNotification(notification: StatusBarNotification) {
        val notificationService = NotificationListener.getInstance()
        notificationService.cancelNotification(notification.key)

        // Wait for notification to get canceled
        TestUtils.eventually({
            Assert.assertFalse(
                listOf(*notificationService.activeNotifications)
                    .contains(notification)
            )
        }, NOTIFICATION_CANCELLATION_TIMEOUT_MILLIS)
    }

    private fun clearNotifications(notifications: List<StatusBarNotification>) {
        val notificationService = NotificationListener.getInstance()
        notifications.forEach { notificationService.cancelNotification(it.key) }

        // Wait for notification to get canceled
        TestUtils.eventually({
            val activeNotifications: List<StatusBarNotification> =
                listOf(*notificationService.activeNotifications)
            Assert.assertFalse(
                activeNotifications.any { notifications.contains(it) }
            )
        }, NOTIFICATION_CANCELLATION_TIMEOUT_MILLIS)
    }

    /**
     * Get all notifications associated with a given package that are currently visible.
     * @param pkg Package for which to filter the notifications by
     * @return [List] of [StatusBarNotification]
     */
    @Throws(Exception::class)
    private fun getNotificationsForPackage(pkg: String): List<StatusBarNotification> {
        val notificationService = NotificationListener.getInstance()
        val notifications: MutableList<StatusBarNotification> = ArrayList()
        for (notification in notificationService.activeNotifications) {
            if (notification.packageName == pkg) {
                notifications.add(notification)
            }
        }
        return notifications
    }
}