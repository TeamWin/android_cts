/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.app.cts;

import android.app.Notification;
import android.app.Notification.TvExtender;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.test.AndroidTestCase;

public class NotificationTvExtenderTest extends AndroidTestCase {

    private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext();
    }

    public void testTvExtender_EmptyConstructor() {
        TvExtender extender = new TvExtender();
        assertNotNull(extender);
        assertTrue(extender.isAvailableOnTv());
    }

    public void testTvExtender_NotifConstructor() {
        Notification notification = new Notification();
        TvExtender extender = new TvExtender(notification);
        assertNotNull(extender);
        assertFalse(extender.isAvailableOnTv());

        assertEquals(null, extender.getChannelId());
        assertEquals(null, extender.getContentIntent());
        assertEquals(null, extender.getDeleteIntent());
        assertFalse(extender.getSuppressShowOverApps());
    }

    public void testTvExtender_SetFields() {
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0,
                new Intent("contentIntent"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent deleteIntent = PendingIntent.getActivity(mContext, 0,
                new Intent("deleteIntent"), PendingIntent.FLAG_IMMUTABLE);

        TvExtender extender = new TvExtender().setChannelId("channelId").setContentIntent(
                contentIntent).setDeleteIntent(deleteIntent).setSuppressShowOverApps(true);

        assertEquals("channelId", extender.getChannelId());
        assertEquals(contentIntent, extender.getContentIntent());
        assertEquals(deleteIntent, extender.getDeleteIntent());
        assertTrue(extender.isSuppressShowOverApps());
    }

    public void testTvExtender_extend() {
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0,
                new Intent("contentIntent"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent deleteIntent = PendingIntent.getActivity(mContext, 0,
                new Intent("deleteIntent"), PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder notifBuilder = new Notification.Builder(mContext,
                "test id").setSmallIcon(1);

        TvExtender extender = new TvExtender().setChannelId("channelId").setContentIntent(
                contentIntent).setDeleteIntent(deleteIntent).setSuppressShowOverApps(true);

        extender.extend(notifBuilder);
        Notification notification = notifBuilder.build();
        TvExtender receiveTvExtender = new TvExtender(notification);

        assertNotNull(receiveTvExtender);
        assertEquals("channelId", receiveTvExtender.getChannelId());
        assertEquals(contentIntent, receiveTvExtender.getContentIntent());
        assertEquals(deleteIntent, receiveTvExtender.getDeleteIntent());
        assertTrue(receiveTvExtender.isSuppressShowOverApps());
    }
}
