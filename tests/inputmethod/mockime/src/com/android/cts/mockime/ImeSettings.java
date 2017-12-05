/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.cts.mockime;

import android.os.Parcel;
import android.os.PersistableBundle;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * An immutable data store to control the behavior of {@link MockIme}.
 */
public class ImeSettings {

    @NonNull
    private final String mEventCallbackActionName;

    private static final String BACKGROUND_COLOR_KEY = "BackgroundColor";
    private static final String INPUT_VIEW_HEIGHT_WITHOUT_SYSTEM_WINDOW_INSET =
            "InputViewHeightWithoutSystemWindowInset";
    private static final String WINDOW_FLAGS = "WindowFlags";
    private static final String FULLSCREEN_MODE_ALLOWED = "FullscreenModeAllowed";

    @NonNull
    private final PersistableBundle mBundle;

    ImeSettings(@NonNull Parcel parcel) {
        mEventCallbackActionName = parcel.readString();
        mBundle = parcel.readPersistableBundle();
    }

    @Nullable
    String getEventCallbackActionName() {
        return mEventCallbackActionName;
    }

    public boolean fullscreenModeAllowed(boolean defaultValue) {
        return mBundle.getBoolean(FULLSCREEN_MODE_ALLOWED, defaultValue);
    }

    @ColorInt
    public int getBackgroundColor(@ColorInt int defaultColor) {
        return mBundle.getInt(BACKGROUND_COLOR_KEY, defaultColor);
    }

    public int getInputViewHeightWithoutSystemWindowInset(int defaultHeight) {
        return mBundle.getInt(INPUT_VIEW_HEIGHT_WITHOUT_SYSTEM_WINDOW_INSET, defaultHeight);
    }

    public int getWindowFlags(int defaultFlags) {
        return mBundle.getInt(WINDOW_FLAGS, defaultFlags);
    }

    static void writeToParcel(@NonNull Parcel parcel, @NonNull String eventCallbackActionName,
            @Nullable Builder builder) {
        parcel.writeString(eventCallbackActionName);
        if (builder != null) {
            parcel.writePersistableBundle(builder.mBundle);
        } else {
            parcel.writePersistableBundle(PersistableBundle.EMPTY);
        }
    }

    /**
     * The builder class for {@link ImeSettings}.
     */
    public static final class Builder {
        private final PersistableBundle mBundle = new PersistableBundle();

        /**
         * Controls whether fullscreen mode is allowed or not.
         *
         * <p>By default, fullscreen mode is not allowed in {@link MockIme}.</p>
         *
         * @param allowed {@code true} if fullscreen mode is allowed
         * @see MockIme#onEvaluateFullscreenMode()
         */
        public Builder setFullscreenModeAllowed(boolean allowed) {
            mBundle.putBoolean(FULLSCREEN_MODE_ALLOWED, allowed);
            return this;
        }

        /**
         * Sets the background color of the {@link MockIme}.
         * @param color background color to be used
         */
        public Builder setBackgroundColor(@ColorInt int color) {
            mBundle.putInt(BACKGROUND_COLOR_KEY, color);
            return this;
        }

        /**
         * Sets the input view height measured from the bottom system window inset.
         * @param height height of the soft input view. This does not include the system window
         *               inset such as navigation bar
         */
        public Builder setInputViewHeightWithoutSystemWindowInset(int height) {
            mBundle.putInt(INPUT_VIEW_HEIGHT_WITHOUT_SYSTEM_WINDOW_INSET, height);
            return this;
        }

        /**
         * Sets window flags to be specified to {@link android.view.Window#setFlags(int, int)} of
         * the main {@link MockIme} window.
         *
         * <p>When {@link android.view.WindowManager.LayoutParams#FLAG_LAYOUT_IN_OVERSCAN} is set,
         * {@link MockIme} tries to render the navigation bar by itself.</p>
         *
         * @param flags flags to be specified
         * @see android.view.WindowManager
         */
        public Builder setWindowFlags(int flags) {
            mBundle.putInt(WINDOW_FLAGS, flags);
            return this;
        }
    }
}
