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
import android.app.PendingIntent
import android.app.Person
import android.app.cts.CtsAppTestUtils.platformNull
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith

class NotificationTemplateTest : NotificationTemplateTestBase() {

    fun testWideIcon_inCollapsedState_cappedTo16By9() {
        val icon = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .createContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 16 / 9).toFloat())
        }
    }

    fun testWideIcon_inCollapsedState_canShowExact4By3() {
        val icon = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .createContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
        }
    }

    fun testWideIcon_inCollapsedState_neverNarrowerThanSquare() {
        val icon = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
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

    fun testWideIcon_inBigBaseState_cappedTo16By9() {
        val icon = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .createBigContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 16 / 9).toFloat())
        }
    }

    fun testWideIcon_inBigBaseState_canShowExact4By3() {
        val icon = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .createBigContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
        }
    }

    fun testWideIcon_inBigBaseState_neverNarrowerThanSquare() {
        val icon = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
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

    fun testWideIcon_inBigPicture_cappedTo16By9() {
        val picture = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        val icon = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .setStyle(Notification.BigPictureStyle().bigPicture(picture))
                .createBigContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 16 / 9).toFloat())
        }
    }

    fun testWideIcon_inBigPicture_canShowExact4By3() {
        val picture = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        val icon = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .setStyle(Notification.BigPictureStyle().bigPicture(picture))
                .createBigContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
        }
    }

    fun testWideIcon_inBigPicture_neverNarrowerThanSquare() {
        val picture = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        val icon = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
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

    fun testWideIcon_inBigText_cappedTo16By9() {
        val icon = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .setStyle(Notification.BigTextStyle().bigText("Big\nText\nContent"))
                .createBigContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 16 / 9).toFloat())
        }
    }

    fun testWideIcon_inBigText_canShowExact4By3() {
        val icon = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .setStyle(Notification.BigTextStyle().bigText("Big\nText\nContent"))
                .createBigContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
        }
    }

    fun testWideIcon_inBigText_neverNarrowerThanSquare() {
        val icon = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .setStyle(Notification.BigTextStyle().bigText("Big\nText\nContent"))
                .createBigContentView()
        checkIconView(views) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width).isEqualTo(iconView.height)
        }
    }

    fun testPromoteBigPicture_withoutLargeIcon() {
        val picture = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        val builder = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setStyle(Notification.BigPictureStyle()
                        .bigPicture(picture)
                        .showBigPictureWhenCollapsed(true)
                )
        checkIconView(builder.createContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
            assertThat(iconView.drawable.intrinsicWidth).isEqualTo(40)
            assertThat(iconView.drawable.intrinsicHeight).isEqualTo(30)
        }
        checkIconView(builder.createBigContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.GONE)
        }
    }

    fun testPromoteBigPicture_withLargeIcon() {
        val picture = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        val icon = Bitmap.createBitmap(80, 65, Bitmap.Config.ARGB_8888)
        val builder = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setLargeIcon(icon)
                .setStyle(Notification.BigPictureStyle()
                        .bigPicture(picture)
                        .showBigPictureWhenCollapsed(true)
                )
        checkIconView(builder.createContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
            assertThat(iconView.drawable.intrinsicWidth).isEqualTo(40)
            assertThat(iconView.drawable.intrinsicHeight).isEqualTo(30)
        }
        checkIconView(builder.createBigContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 80 / 65).toFloat())
            assertThat(iconView.drawable.intrinsicWidth).isEqualTo(80)
            assertThat(iconView.drawable.intrinsicHeight).isEqualTo(65)
        }
    }

    fun testPromoteBigPicture_withBigLargeIcon() {
        val picture = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        val bigIcon = Bitmap.createBitmap(80, 75, Bitmap.Config.ARGB_8888)
        val builder = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setStyle(Notification.BigPictureStyle()
                        .bigPicture(picture)
                        .bigLargeIcon(bigIcon)
                        .showBigPictureWhenCollapsed(true)
                )
        checkIconView(builder.createContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 4 / 3).toFloat())
            assertThat(iconView.drawable.intrinsicWidth).isEqualTo(40)
            assertThat(iconView.drawable.intrinsicHeight).isEqualTo(30)
        }
        checkIconView(builder.createBigContentView()) { iconView ->
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
            assertThat(iconView.width.toFloat())
                    .isWithin(1f)
                    .of((iconView.height * 80 / 75).toFloat())
            assertThat(iconView.drawable.intrinsicWidth).isEqualTo(80)
            assertThat(iconView.drawable.intrinsicHeight).isEqualTo(75)
        }
    }

    @SmallTest
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
        checkViews(views) { activity ->
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>(activity, "text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the icon shows
            val iconView = requireViewByIdName<ImageView>(activity, "icon")
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
        checkViews(views) { activity ->
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>(activity, "text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the app name text shows
            val appNameView = requireViewByIdName<TextView>(activity, "app_name_text")
            assertThat(appNameView.visibility).isEqualTo(View.VISIBLE)

            // check that the icon shows
            val iconView = requireViewByIdName<ImageView>(activity, "icon")
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
        }
    }

    fun testCustomViewNotification_collapsedState_isDecorated() {
        val customContent = makeCustomContent()
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setCustomContentView(customContent)
                .createContentView()
        checkViews(views) { activity ->
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>(activity, "text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)

            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the icon shows
            val iconView = requireViewByIdName<ImageView>(activity, "icon")
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
        }
    }

    fun testCustomViewNotification_expandedState_isDecorated() {
        val customContent = makeCustomContent()
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setCustomBigContentView(customContent)
                .createBigContentView()
        checkViews(views) { activity ->
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>(activity, "text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the app name text shows
            val appNameView = requireViewByIdName<TextView>(activity, "app_name_text")
            assertThat(appNameView.visibility).isEqualTo(View.VISIBLE)

            // check that the icon shows
            val iconView = requireViewByIdName<ImageView>(activity, "icon")
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
        }
    }

    fun testCustomViewNotification_headsUpState_isDecorated() {
        val customContent = makeCustomContent()
        val views = Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentTitle("Title")
                .setCustomHeadsUpContentView(customContent)
                .createHeadsUpContentView()
        checkViews(views) { activity ->
            // first check that the custom view is actually shown
            val customTextView = requireViewByIdName<TextView>(activity, "text1")
            assertThat(customTextView.visibility).isEqualTo(View.VISIBLE)
            assertThat(customTextView.text).isEqualTo("Example Text")

            // check that the icon shows
            val iconView = requireViewByIdName<ImageView>(activity, "icon")
            assertThat(iconView.visibility).isEqualTo(View.VISIBLE)
        }
    }

    @SmallTest
    fun testCallStyle_forIncomingCall_validatesArguments() {
        val namedPerson = Person.Builder().setName("Named Person").build()
        val namelessPerson = Person.Builder().setName("").build()
        assertFailsWith(IllegalArgumentException::class, "person must have a non-empty a name") {
            Notification.CallStyle.forIncomingCall(platformNull(), pendingIntent, pendingIntent)
        }
        assertFailsWith(IllegalArgumentException::class, "person must have a non-empty a name") {
            Notification.CallStyle.forIncomingCall(namelessPerson, pendingIntent, pendingIntent)
        }
        assertFailsWith(NullPointerException::class, "declineIntent is required") {
            Notification.CallStyle.forIncomingCall(namedPerson, platformNull(), pendingIntent)
        }
        assertFailsWith(NullPointerException::class, "answerIntent is required") {
            Notification.CallStyle.forIncomingCall(namedPerson, pendingIntent, platformNull())
        }
        Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setStyle(Notification.CallStyle
                        .forIncomingCall(namedPerson, pendingIntent, pendingIntent))
                .build()
    }

    @SmallTest
    fun testCallStyle_forOngoingCall_validatesArguments() {
        val namedPerson = Person.Builder().setName("Named Person").build()
        val namelessPerson = Person.Builder().setName("").build()
        assertFailsWith(IllegalArgumentException::class, "person must have a non-empty a name") {
            Notification.CallStyle.forOngoingCall(platformNull(), pendingIntent)
        }
        assertFailsWith(IllegalArgumentException::class, "person must have a non-empty a name") {
            Notification.CallStyle.forOngoingCall(namelessPerson, pendingIntent)
        }
        assertFailsWith(NullPointerException::class, "hangUpIntent is required") {
            Notification.CallStyle.forOngoingCall(namedPerson, platformNull())
        }
        Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setStyle(Notification.CallStyle.forOngoingCall(namedPerson, pendingIntent))
                .build()
    }

    @SmallTest
    fun testCallStyle_forScreeningCall_validatesArguments() {
        val namedPerson = Person.Builder().setName("Named Person").build()
        val namelessPerson = Person.Builder().setName("").build()
        assertFailsWith(IllegalArgumentException::class, "person must have a non-empty a name") {
            Notification.CallStyle.forScreeningCall(platformNull(), pendingIntent, pendingIntent)
        }
        assertFailsWith(IllegalArgumentException::class, "person must have a non-empty a name") {
            Notification.CallStyle.forScreeningCall(namelessPerson, pendingIntent, pendingIntent)
        }
        assertFailsWith(NullPointerException::class, "hangUpIntent is required") {
            Notification.CallStyle.forScreeningCall(namedPerson, platformNull(), pendingIntent)
        }
        assertFailsWith(NullPointerException::class, "answerIntent is required") {
            Notification.CallStyle.forScreeningCall(namedPerson, pendingIntent, platformNull())
        }
        Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setStyle(Notification.CallStyle
                        .forScreeningCall(namedPerson, pendingIntent, pendingIntent))
                .build()
    }

    private val pendingIntent by lazy {
        PendingIntent.getBroadcast(mContext, 0, Intent("test"), PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        val TAG = NotificationTemplateTest::class.java.simpleName
        const val NOTIFICATION_CHANNEL_ID = "NotificationTemplateTest"
    }
}