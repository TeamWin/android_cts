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

package com.android.compatibility.common.util;

import static android.view.Display.INVALID_DISPLAY;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

/**
 * Helper class providing methods to interact with the user under test.
 *
 * <p>For example, it knows if the user was {@link
 * android.app.ActivityManager#startUserInBackgroundVisibleOnDisplay(int, int) started visible in a
 * display} and provide methods (like {@link #injectDisplayIdIfNeeded(ActivityOptions)}) to help
 * tests support such behavior.
 */
// TODO(b/271153404): move logic to bedstead and/or rename it to UserVisibilityHelper
public final class UserHelper {

    private static final String TAG = "CtsUserHelper";

    private final UserHandle mUser;
    private final boolean mIsVisibleBackgroundFullUser;
    private final int mDisplayId;

    /**
     * Creates a helper using {@link InstrumentationRegistry#getTargetContext()}.
     */
    public UserHelper() {
        Context context = InstrumentationRegistry.getTargetContext();
        mUser = context.getUser();
        UserManager userManager = context.getSystemService(UserManager.class);
        mDisplayId = userManager.getDisplayIdAssignedToUser();
        boolean visibleBackgroundUsersSupported = userManager
                .isVisibleBackgroundUsersSupported();
        boolean isVisible = userManager.isUserVisible();
        boolean isForeground = userManager.isUserForeground();
        boolean isProfile = userManager.isProfile();
        Log.v(TAG, "Constructor: mUser=" + mUser + ", visible=" + isVisible + ", isForeground="
                + isForeground + ", isProfile=" + isProfile + ", mDisplayId=" + mDisplayId
                + ", visibleBackgroundUsersSupported=" + visibleBackgroundUsersSupported);
        if (visibleBackgroundUsersSupported && isVisible && !isForeground && !isProfile) {
            if (mDisplayId == INVALID_DISPLAY) {
                throw new IllegalStateException("UserManager returned INVALID_DISPLAY for visible"
                        + "background user " + mUser);
            }
            mIsVisibleBackgroundFullUser = true;
            Log.i(TAG, "Test user " + mUser + " is running on background, visible on display "
                    + mDisplayId);
        } else {
            mIsVisibleBackgroundFullUser = false;
        }
    }

    /**
     * Checks if the user is a full user (i.e, not a {@link UserManager#isProfile() profile}) and
     * is {@link UserManager#isVisibleBackgroundUsersEnabled() running in the background but
     * visible in a display}; if it's not, then it's either the
     * {@link android.app.ActivityManager#getCurrentUser() current foreground user}, a profile, or a
     * full user running in background but not {@link UserManager#isUserVisible() visible}.
     */
    public boolean isVisibleBackgroundUser() {
        return mIsVisibleBackgroundFullUser;
    }

    /**
     * Convenience method to get the user running this test.
     */
    public UserHandle getUser() {
        return mUser;
    }

    /**
     * Convenience method to get the id of the {@link #getUser() user running this test}.
     */
    public int getUserId() {
        return mUser.getIdentifier();
    }

    /**
     * Gets the display id the {@link #getUser() user} {@link #isVisibleBackgroundUser() is
     * running visible on}.
     *
     * <p>Notice that this id is not necessarily the same as the id returned by
     * {@link Context#getDisplayId()}, as that method returns {@link INVALID_DISPLAY} on contexts
     * that are not associated with a {@link Context#isUiContext() UI}.
     */
    public int getMainDisplayId() {
        return mDisplayId;
    }

    /**
     * Gets an {@link ActivityOptions} that can be used to launch an activity in the display under
     * test.
     */
    public ActivityOptions getActivityOptions() {
        return injectDisplayIdIfNeeded(/* options= */ null);
    }

    /**
     * Augments a existing {@link ActivityOptions} (or create a new one), injecting the
     * {{@link #getMainDisplayId()} if needed.
     */
    public ActivityOptions injectDisplayIdIfNeeded(@Nullable ActivityOptions options) {
        ActivityOptions augmentedOptions = options != null ? options : ActivityOptions.makeBasic();
        if (mIsVisibleBackgroundFullUser) {
            augmentedOptions.setLaunchDisplayId(mDisplayId);
        }
        Log.v(TAG, "injectDisplayId(): returning " + augmentedOptions);
        return augmentedOptions;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[user=" + mUser + ", displayId=" + mDisplayId
                + ", isVisibleBackgroundUser=" + mIsVisibleBackgroundFullUser + "]";
    }
}
