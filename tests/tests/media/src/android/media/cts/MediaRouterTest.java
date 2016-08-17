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
package android.media.cts;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteCategory;
import android.media.MediaRouter.RouteInfo;
import android.media.MediaRouter.UserRouteInfo;
import android.media.RemoteControlClient;
import android.test.InstrumentationTestCase;

import java.util.List;
import java.util.ArrayList;

/**
 * Test {@link android.media.MediaRouter}.
 */
public class MediaRouterTest extends InstrumentationTestCase {

    private MediaRouter mMediaRouter;
    private RouteCategory mTestCategory;
    private Drawable mTestIconDrawable;
    private static final String TEST_ROUTE_CATEGORY_NAME = "test_route_category_name";
    private static final String TEST_ROUTE_NAME = "test_user_route_name";
    private static final String TEST_ROUTE_DESCRIPTION = "test_user_route_description";
    private static final String TEST_STATUS = "test_user_route_status";
    private static final int TEST_MAX_VOLUME = 100;
    private static final int TEST_VOLUME = 17;
    private static final int TEST_VOLUME_DIRECTION = -2;
    private static final int TEST_PLAYBACK_STREAM = AudioManager.STREAM_ALARM;
    private static final int TEST_VOLUME_HANDLING = RouteInfo.PLAYBACK_VOLUME_VARIABLE;
    private static final int TEST_PLAYBACK_TYPE = RouteInfo.PLAYBACK_TYPE_LOCAL;
    private static final int TEST_ICON_RESOURCE_ID = android.R.drawable.ic_media_next;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getInstrumentation().getContext();
        mMediaRouter = (MediaRouter) context.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        mTestCategory = mMediaRouter.createRouteCategory(TEST_ROUTE_CATEGORY_NAME, false);
        mTestIconDrawable = Resources.getSystem().getDrawable(TEST_ICON_RESOURCE_ID, null);
    }

    protected void tearDown() throws Exception {
        mMediaRouter.clearUserRoutes();
        super.tearDown();
    }

    /**
     * Test {@link MediaRouter#selectRoute(int, RouteInfo)}.
     */
    public void testSelectRoute() {
        RouteInfo prevSelectedRoute = mMediaRouter.getSelectedRoute(
                MediaRouter.ROUTE_TYPE_LIVE_AUDIO | MediaRouter.ROUTE_TYPE_LIVE_VIDEO
                | MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY);
        assertNotNull(prevSelectedRoute);

        UserRouteInfo userRoute = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute);
        mMediaRouter.selectRoute(userRoute.getSupportedTypes(), userRoute);

        RouteInfo nowSelectedRoute = mMediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_USER);
        assertEquals(userRoute, nowSelectedRoute);
        assertEquals(mTestCategory, nowSelectedRoute.getCategory());

        mMediaRouter.selectRoute(prevSelectedRoute.getSupportedTypes(), prevSelectedRoute);
    }

    /**
     * Test {@link MediaRouter#getRouteCount()}.
     */
    public void testGetRouteCount() {
        final int count = mMediaRouter.getRouteCount();
        assertTrue("By default, a media router has at least one route.", count > 0);

        UserRouteInfo userRoute0 = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute0);
        assertEquals(count + 1, mMediaRouter.getRouteCount());

        mMediaRouter.removeUserRoute(userRoute0);
        assertEquals(count, mMediaRouter.getRouteCount());

        UserRouteInfo userRoute1 = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute0);
        mMediaRouter.addUserRoute(userRoute1);
        assertEquals(count + 2, mMediaRouter.getRouteCount());

        mMediaRouter.clearUserRoutes();
        assertEquals(count, mMediaRouter.getRouteCount());
    }

    /**
     * Test {@link MediaRouter#getRouteAt(int)}.
     */
    public void testGetRouteAt() throws Exception {
        UserRouteInfo userRoute0 = mMediaRouter.createUserRoute(mTestCategory);
        UserRouteInfo userRoute1 = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute0);
        mMediaRouter.addUserRoute(userRoute1);

        int count = mMediaRouter.getRouteCount();
        assertEquals(userRoute0, mMediaRouter.getRouteAt(count - 2));
        assertEquals(userRoute1, mMediaRouter.getRouteAt(count - 1));
    }

    /**
     * Test {@link MediaRouter.UserRouteInfo}.
     */
    public void testUserRouteInfo() {
        UserRouteInfo userRoute = mMediaRouter.createUserRoute(mTestCategory);
        assertTrue(userRoute.isEnabled());
        assertFalse(userRoute.isConnecting());
        assertEquals(mTestCategory, userRoute.getCategory());
        assertEquals(RouteInfo.DEVICE_TYPE_UNKNOWN, userRoute.getDeviceType());
        assertEquals(RouteInfo.PLAYBACK_TYPE_REMOTE, userRoute.getPlaybackType());

        userRoute.setName(TEST_ROUTE_NAME);
        userRoute.setDescription(TEST_ROUTE_DESCRIPTION);
        userRoute.setStatus(TEST_STATUS);
        userRoute.setPlaybackStream(TEST_PLAYBACK_STREAM);

        assertEquals(TEST_ROUTE_NAME, userRoute.getName());
        assertEquals(TEST_ROUTE_DESCRIPTION, userRoute.getDescription());
        assertEquals(TEST_STATUS, userRoute.getStatus());
        assertEquals(TEST_PLAYBACK_STREAM, userRoute.getPlaybackStream());

        userRoute.setIconDrawable(mTestIconDrawable);
        assertEquals(mTestIconDrawable, userRoute.getIconDrawable());

        userRoute.setIconDrawable(null);
        assertNull(userRoute.getIconDrawable());

        userRoute.setIconResource(TEST_ICON_RESOURCE_ID);
        assertTrue(getBitmap(mTestIconDrawable).sameAs(getBitmap(userRoute.getIconDrawable())));

        Object tag = new Object();
        userRoute.setTag(tag);
        assertEquals(tag, userRoute.getTag());

        userRoute.setVolumeMax(TEST_MAX_VOLUME);
        userRoute.setVolume(TEST_VOLUME);
        assertEquals(TEST_MAX_VOLUME, userRoute.getVolumeMax());
        assertEquals(TEST_VOLUME, userRoute.getVolume());

        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        PendingIntent mediaButtonIntent = PendingIntent.getBroadcast(
                getInstrumentation().getContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);
        RemoteControlClient rcc = new RemoteControlClient(mediaButtonIntent);
        userRoute.setRemoteControlClient(rcc);
        assertEquals(rcc, userRoute.getRemoteControlClient());

        userRoute.setVolumeHandling(TEST_VOLUME_HANDLING);
        assertEquals(TEST_VOLUME_HANDLING, userRoute.getVolumeHandling());

        userRoute.setPlaybackType(TEST_PLAYBACK_TYPE);
        assertEquals(TEST_PLAYBACK_TYPE, userRoute.getPlaybackType());
    }

    /**
     * Test {@link MediaRouter.RouteCategory}.
     */
    public void testRouteCategory() {
        assertEquals(TEST_ROUTE_CATEGORY_NAME, mTestCategory.getName());
        assertFalse(mTestCategory.isGroupable());
        assertEquals(MediaRouter.ROUTE_TYPE_USER, mTestCategory.getSupportedTypes());

        final int count = mMediaRouter.getCategoryCount();
        assertTrue("By default, a media router has at least one route category.", count > 0);

        UserRouteInfo userRoute = mMediaRouter.createUserRoute(mTestCategory);
        mMediaRouter.addUserRoute(userRoute);
        assertEquals(count + 1, mMediaRouter.getCategoryCount());
        assertEquals(mTestCategory, mMediaRouter.getCategoryAt(count));

        List<RouteInfo> routesInCategory = new ArrayList<RouteInfo>();
        mTestCategory.getRoutes(routesInCategory);
        assertEquals(1, routesInCategory.size());

        RouteInfo route = routesInCategory.get(0);
        assertEquals(userRoute, route);
    }

    /**
     * Test {@link MediaRouter.VolumeCallback)}.
     */
    public void testVolumeCallback() {
        UserRouteInfo userRoute = mMediaRouter.createUserRoute(mTestCategory);
        userRoute.setVolumeHandling(RouteInfo.PLAYBACK_VOLUME_VARIABLE);
        MediaRouterVolumeCallback callback = new MediaRouterVolumeCallback();
        userRoute.setVolumeCallback(callback);

        userRoute.requestSetVolume(TEST_VOLUME);
        assertTrue(callback.mOnVolumeSetRequestCalled);
        assertEquals(userRoute, callback.mRouteInfo);
        assertEquals(TEST_VOLUME, callback.mVolume);

        callback.reset();
        userRoute.requestUpdateVolume(TEST_VOLUME_DIRECTION);
        assertTrue(callback.mOnVolumeUpdateRequestCalled);
        assertEquals(userRoute, callback.mRouteInfo);
        assertEquals(TEST_VOLUME_DIRECTION, callback.mDirection);
    }

    private Bitmap getBitmap(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);

        return result;
    }

    private class MediaRouterVolumeCallback extends MediaRouter.VolumeCallback {
        private boolean mOnVolumeUpdateRequestCalled;
        private boolean mOnVolumeSetRequestCalled;
        private RouteInfo mRouteInfo;
        private int mDirection;
        private int mVolume;

        public void reset() {
            mOnVolumeUpdateRequestCalled = false;
            mOnVolumeSetRequestCalled = false;
            mRouteInfo = null;
            mDirection = 0;
            mVolume = 0;
        }

        @Override
        public void onVolumeUpdateRequest(RouteInfo info, int direction) {
            mOnVolumeUpdateRequestCalled = true;
            mRouteInfo = info;
            mDirection = direction;
        }

        @Override
        public void onVolumeSetRequest(RouteInfo info, int volume) {
            mOnVolumeSetRequestCalled = true;
            mRouteInfo = info;
            mVolume = volume;
        }
    }
}
