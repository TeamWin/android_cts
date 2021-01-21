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
package android.app.cts

import android.R
import android.app.stubs.shared.NotificationHostActivity
import android.content.Intent
import android.test.AndroidTestCase
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.annotation.DimenRes
import androidx.annotation.IdRes
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.ActivityAction

open class NotificationTemplateTestBase : AndroidTestCase() {

    protected fun checkIconView(views: RemoteViews, iconCheck: (ImageView) -> Unit) {
        checkViews(views) { activity ->
            iconCheck(requireViewByIdName(activity, "right_icon"))
        }
    }

    protected fun checkViews(
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

    protected fun makeCustomContent(): RemoteViews {
        val customContent = RemoteViews(mContext.packageName, R.layout.simple_list_item_1)
        val textId = getAndroidRId("text1")
        customContent.setTextViewText(textId, "Example Text")
        return customContent
    }

    protected fun <T : View> requireViewByIdName(
        activity: NotificationHostActivity,
        idName: String
    ): T {
        val viewId = getAndroidRId(idName)
        return activity.notificationRoot.findViewById<T>(viewId)
                ?: throw NullPointerException("No view with id: android.R.id.$idName ($viewId)")
    }

    protected fun <T : View> findViewByIdName(
        activity: NotificationHostActivity,
        idName: String
    ): T? = activity.notificationRoot.findViewById<T>(getAndroidRId(idName))

    @IdRes
    protected fun getAndroidRId(idName: String): Int =
            mContext.resources.getIdentifier(idName, "id", "android")
}