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

package android.media.router.cts.proxymediacontentcontrolapp;

import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_SECONDARY_USER_HELPER_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.TARGET_USER_ID_KEY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRouter2;
import android.os.Bundle;
import android.os.Looper;
import android.os.UserHandle;
import android.platform.test.annotations.LargeTest;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.media.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Device-side test for {@link MediaRouter2} functionality. */
@LargeTest
public class MediaRouter2DeviceTest {
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CROSS_USER_ROUTING_IN_MEDIA_ROUTER2)
    @Test
    public void getInstance_acrossUsers_withInteractAcrossUsersFull_returnsInstance() {
        Bundle args = InstrumentationRegistry.getArguments();
        assertThat(args.containsKey(TARGET_USER_ID_KEY)).isTrue();
        int targetUserId = Integer.parseInt(args.getString(TARGET_USER_ID_KEY));

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                        Manifest.permission.MEDIA_CONTENT_CONTROL);

        assertThat(mContext.checkSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        assertThat(mContext.checkSelfPermission(Manifest.permission.MEDIA_CONTENT_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);

        assertThat(
                        MediaRouter2.getInstance(
                                mContext,
                                Looper.getMainLooper(),
                                MEDIA_ROUTER_SECONDARY_USER_HELPER_PACKAGE,
                                UserHandle.of(targetUserId)))
                .isNotNull();
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CROSS_USER_ROUTING_IN_MEDIA_ROUTER2)
    @Test
    public void getInstance_acrossUsers_withoutInteractAcrossUsersFull_throwsSecurityException() {
        Bundle args = InstrumentationRegistry.getArguments();
        assertThat(args.containsKey(TARGET_USER_ID_KEY)).isTrue();
        int targetUserId = Integer.parseInt(args.getString(TARGET_USER_ID_KEY));

        assertThat(mContext.checkSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        assertThrows(
                SecurityException.class,
                () ->
                        MediaRouter2.getInstance(
                                mContext,
                                Looper.getMainLooper(),
                                MEDIA_ROUTER_SECONDARY_USER_HELPER_PACKAGE,
                                UserHandle.of(targetUserId)));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CROSS_USER_ROUTING_IN_MEDIA_ROUTER2)
    @SuppressLint("MissingPermission")
    @Test
    public void getInstance_acrossUsers_withFakePackageName_throwsIAE() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MediaRouter2.getInstance(
                                mContext,
                                Looper.getMainLooper(),
                                "FAKE_PACKAGE_NAME",
                                mContext.getUser()));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CROSS_USER_ROUTING_IN_MEDIA_ROUTER2)
    @Test
    public void getInstance_withinUser_returnsInstance() {
        assertThat(
                        MediaRouter2.getInstance(
                                mContext,
                                Looper.getMainLooper(),
                                mContext.getPackageName(),
                                mContext.getUser()))
                .isNotNull();
    }
}
