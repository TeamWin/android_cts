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
package android.app.cts

import android.R
import android.app.Notification
import android.app.stubs.NotificationHostActivity
import android.content.Intent
import android.graphics.Bitmap
import android.test.AndroidTestCase
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.annotation.DimenRes
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.ActivityAction
import com.google.common.truth.Truth.assertThat

class NotificationTemplateTest : AndroidTestCase() {
    fun testWideIcon_inCollapsedState_cappedTo16By9() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(bitmap)
                .createContentView()
        checkViews(views) { activity ->
            val root = activity.notificationRoot
            val iconView = findIconView(root, bitmap)!!
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 16 / 9).toFloat())
        }
    }

    fun testWideIcon_inCollapsedState_canShowExact4By3() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(bitmap)
                .createContentView()
        checkViews(views) { activity ->
            val root = activity.notificationRoot
            val iconView = findIconView(root, bitmap)
                    ?: throw NullPointerException("Unable to find ")
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
        }
    }

    fun testWideIcon_inCollapsedState_neverNarrowerThanSquare() {
        val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(bitmap)
                .createContentView()
        checkViews(views) { activity ->
            val root = activity.notificationRoot
            val iconView = findIconView(root, bitmap)!!
            assertThat(iconView.width).isEqualTo(iconView.height)
        }
    }

    private fun findIconView(root: View, icon: Bitmap): ImageView? {
        (root as? ImageView)?.drawable?.also { drawable ->
            if (drawable.intrinsicWidth == icon.width && drawable.intrinsicHeight == icon.height) {
                return root
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findIconView(root.getChildAt(i), icon)?.also { return it }
            }
        }
        return null
    }

    private fun checkViews(
        views: RemoteViews,
        @DimenRes heightDimen: Int? = null,
        activityAction: ActivityAction<NotificationHostActivity>
    ) {
        val activityIntent = Intent(context, NotificationHostActivity::class.java)
        activityIntent.putExtra(NotificationHostActivity.EXTRA_REMOTE_VIEWS, views)
        heightDimen?.also {
            activityIntent.putExtra(NotificationHostActivity.EXTRA_HEIGHT,
                    context.resources.getDimensionPixelSize(it))
        }
        ActivityScenario.launch<NotificationHostActivity>(activityIntent).also { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity(activityAction)
            scenario.moveToState(Lifecycle.State.DESTROYED)
        }
    }

    companion object {
        const val DEBUG = false
        val TAG = NotificationTemplateTest::class.java.simpleName
        const val NOTIFICATION_CHANNEL_ID = "NotificationTemplateTest"
    }
}