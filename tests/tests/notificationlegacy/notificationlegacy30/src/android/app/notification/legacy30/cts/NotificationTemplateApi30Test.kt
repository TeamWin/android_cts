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
package android.app.notification.legacy30.cts

import android.R
import android.app.Notification
import android.app.cts.NotificationTemplateTestBase
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.common.truth.Truth.assertThat

class NotificationTemplateApi30Test : NotificationTemplateTestBase() {

    override fun setUp() {
        assertThat(mContext.applicationInfo.targetSdkVersion).isEqualTo(30)
    }

    fun testWideIcon_inCollapsedState_isSquareForLegacyApps() {
        val icon = createBitmap(200, 100)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .createContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width).isEqualTo(iconView.height)
        }
    }

    fun testWideIcon_inBigBaseState_isSquareForLegacyApps() {
        val icon = createBitmap(200, 100)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .createBigContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width).isEqualTo(iconView.height)
        }
    }

    fun testWideIcon_inBigPicture_isSquareForLegacyApps() {
        if (isPlatformAutomotive()) {
            Log.i(TAG, "Skipping: testWideIcon_inBigPicture_isSquareForLegacyApps" +
                    " - BigPictureStyle is not supported in automotive.")
            return
        }
        val picture = createBitmap(40, 30)
        val icon = createBitmap(200, 100)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .setStyle(Notification.BigPictureStyle().bigPicture(picture))
                .createBigContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width).isEqualTo(iconView.height)
        }
    }

    fun testWideIcon_inBigText_isSquareForLegacyApps() {
        val bitmap = createBitmap(200, 100)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(bitmap)
                .setStyle(Notification.BigTextStyle().bigText("Big\nText\nContent"))
                .createBigContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width).isEqualTo(iconView.height)
        }
    }

    fun testPromoteBigPicture_withoutLargeIcon() {
        if (isPlatformAutomotive()) {
            Log.i(TAG, "Skipping: testPromoteBigPicture_withoutLargeIcon" +
                    " - BigPictureStyle is not supported in automotive.")
            return
        }
        val picture = createBitmap(40, 30)
        val builder = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setStyle(Notification.BigPictureStyle()
                        .bigPicture(picture)
                        .showBigPictureWhenCollapsed(true)
                )
        // the promoted big picture is shown with enlarged aspect ratio
        checkIconView(builder.createContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
            assertThat(iconView.drawable.intrinsicWidth).isEqualTo(40)
            assertThat(iconView.drawable.intrinsicHeight).isEqualTo(30)
        }
        // there should be no icon in the large state
        checkIconView(builder.createBigContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.GONE)
        }
    }

    fun testPromoteBigPicture_withLargeIcon() {
        if (isPlatformAutomotive()) {
            Log.i(TAG, "Skipping: testPromoteBigPicture_withLargeIcon" +
                    " - BigPictureStyle is not supported in automotive.")
            return
        }
        val picture = createBitmap(40, 30)
        val icon = createBitmap(80, 65)
        val builder = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .setStyle(Notification.BigPictureStyle()
                        .bigPicture(picture)
                        .showBigPictureWhenCollapsed(true)
                )

        // At really high densities the size of rendered icon can dip below the
        // tested size - we allow rendering of smaller icon with the same
        // aspect ratio then.
        val expectedIconWidth = minOf(rightIconSize(), 80)
        val expectedIconHeight = minOf(rightIconSize() * 65 / 80, 65)

        // the promoted big picture is shown with enlarged aspect ratio
        checkIconView(builder.createContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
            assertThat(iconView.drawable.intrinsicWidth).isEqualTo(40)
            assertThat(iconView.drawable.intrinsicHeight).isEqualTo(30)
        }
        // because it doesn't target S, the icon is still shown in a square
        checkIconView(builder.createBigContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width).isEqualTo(iconView.height)
            assertThat(iconView.drawable.intrinsicWidth).isEqualTo(expectedIconWidth)
            assertThat(iconView.drawable.intrinsicHeight).isEqualTo(expectedIconHeight)
        }
    }

    fun testPromoteBigPicture_withBigLargeIcon() {
        if (isPlatformAutomotive()) {
            Log.i(TAG, "Skipping: testPromoteBigPicture_withBigLargeIcon" +
                    " - BigPictureStyle is not supported in automotive.")
            return
        }
        val picture = createBitmap(40, 30)
        val bigIcon = createBitmap(rightIconSize(), rightIconSize() * 3 / 4)
        val builder = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setStyle(Notification.BigPictureStyle()
                        .bigPicture(picture)
                        .bigLargeIcon(bigIcon)
                        .showBigPictureWhenCollapsed(true)
                )
        // the promoted big picture is shown with enlarged aspect ratio
        checkIconView(builder.createContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
            assertThat(iconView.drawable.intrinsicWidth).isEqualTo(40)
            assertThat(iconView.drawable.intrinsicHeight).isEqualTo(30)
        }
        // because it doesn't target S, the icon is still shown in a square
        checkIconView(builder.createBigContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width).isEqualTo(iconView.height)
            assertThat(iconView.drawable.intrinsicWidth).isEqualTo(rightIconSize())
            assertThat(iconView.drawable.intrinsicHeight).isEqualTo(rightIconSize() * 3 / 4)
        }
    }

    fun testBaseTemplate_hasExpandedStateWithoutActions() {
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .createBigContentView()
        assertThat(views).isNotNull()
    }

    fun testDecoratedCustomViewStyle_collapsedState() {
        val customContent = makeCustomContent()
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setCustomContentView(customContent)
                .setStyle(Notification.DecoratedCustomViewStyle())
                .createContentView()
        checkViews(views) {
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>("text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the icon shows
            val iconView = requireViewByIdName<ImageView>("icon")
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
        }
    }

    fun testDecoratedCustomViewStyle_expandedState() {
        val customContent = makeCustomContent()
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setCustomBigContentView(customContent)
                .setStyle(Notification.DecoratedCustomViewStyle())
                .createBigContentView()
        checkViews(views) {
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>("text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the app name text shows
            val appNameView = requireViewByIdName<TextView>("app_name_text")
            assertThat(appNameView.visibility).isEqualTo(View.VISIBLE)

            // check that the icon shows
            val iconView = requireViewByIdName<ImageView>("icon")
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
        }
    }

    fun testCustomViewNotification_collapsedState_isNotDecoratedForLegacyApps() {
        val customContent = makeCustomContent()
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setCustomContentView(customContent)
                .createContentView()
        checkViews(views) {
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>("text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the icon is not present
            val iconView = findViewByIdName<ImageView>("icon")
            assertThat(iconView).isNull()
        }
    }

    fun testCustomViewNotification_expandedState_isNotDecoratedForLegacyApps() {
        val customContent = makeCustomContent()
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setCustomBigContentView(customContent)
                .createBigContentView()
        checkViews(views) {
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>("text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the app name text is not present
            val appNameView = findViewByIdName<TextView>("app_name_text")
            assertThat(appNameView).isNull()

            // check that the icon is not present
            val iconView = findViewByIdName<ImageView>("icon")
            assertThat(iconView).isNull()
        }
    }

    fun testCustomViewNotification_headsUpState_isNotDecoratedForLegacyApps() {
        val customContent = makeCustomContent()
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setCustomHeadsUpContentView(customContent)
                .createHeadsUpContentView()
        checkViews(views) {
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>("text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the icon is not present
            val iconView = findViewByIdName<ImageView>("icon")
            assertThat(iconView).isNull()
        }
    }

    private fun rightIconSize(): Int {
        return mContext.resources.getDimensionPixelSize(
                getAndroidRDimen("notification_right_icon_size"))
    }

    private fun isPlatformAutomotive(): Boolean {
        return mContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    }

    companion object {
        val TAG = NotificationTemplateApi30Test::class.java.simpleName
        const val NOTIFICATION_CHANNEL_ID = "NotificationTemplateApi30Test"
    }
}