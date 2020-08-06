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

@file:JvmName("NotificationUtils")
package android.systemui.tv.cts

import android.app.Notification
import android.app.PendingIntent
import java.net.URLEncoder

/** Extract a pending intent that was put by a [android.app.Notification.TvExtender]. */
fun Notification.pendingTvIntent(key: String): PendingIntent =
    extras.getBundle(TVNotificationExtender.EXTRA_TV_EXTENDER)?.getParcelable(key)
        ?: error("No pending intent found for key $key")

fun Notification.title(): String = extras.getString(Notification.EXTRA_TITLE) ?: ""

internal fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")
