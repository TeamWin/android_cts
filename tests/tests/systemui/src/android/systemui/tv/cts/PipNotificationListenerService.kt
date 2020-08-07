/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.systemui.tv.cts

import android.content.ComponentName
import android.server.wm.Condition
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.systemui.tv.cts.ResourceNames.SYSTEM_UI_CTS_PACKAGE
import javax.annotation.concurrent.GuardedBy

/**
 * This service exposes pip notifications to the tests.
 */
class PipNotificationListenerService : NotificationListenerService() {
    /**
     * Stores notifications mapped by their notifying app uid and the notification id.
     * We cannot store these efficiently in a set as they only support referential equality.
     */
    @GuardedBy("this")
    private val _activeNotifications = mutableMapOf<Pair<Int, Int>, StatusBarNotification>()

    /** Currently active notifications from the [ResourceNames.SYSTEM_UI_PACKAGE] package. */
    val activePipNotifications: List<StatusBarNotification>
        get() = synchronized(this) { _activeNotifications.values.toList() }

    /** Clear the internal active and removed notification sets. */
    fun clearNotifications() {
        synchronized(this) {
            _activeNotifications.clear()
        }
    }

    /** Find a notification by the given title or return null. */
    fun findActivePipNotification(title: String): StatusBarNotification? =
        Condition.waitForResult("find notification with title $title") { condition ->
            condition.setResultSupplier {
                activePipNotifications.find { it.title() == title }
            }
            condition.setResultValidator { it != null }
            condition.setReturnLastResult(true)
        }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName == ResourceNames.SYSTEM_UI_PACKAGE) {
            synchronized(this) {
                _activeNotifications[sbn.asKey()] = sbn
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName == ResourceNames.SYSTEM_UI_PACKAGE) {
            synchronized(this) {
                _activeNotifications.remove(sbn.asKey())
            }
        }
    }

    /**
     * Produce a unique key for this notification.
     * Specifically, this is a pair of notifying app uid and the notification id.
     */
    private fun StatusBarNotification.asKey() = uid to id

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    companion object {
        @JvmField
        val componentName: ComponentName = ComponentName(
            SYSTEM_UI_CTS_PACKAGE,
            PipNotificationListenerService::class.java.canonicalName
        )

        /** Expose this to our tests */
        @get:JvmStatic
        internal var instance: PipNotificationListenerService? = null
            private set
    }
}
