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
 * limitations under the License.
 */

package android.app.cts;

import static android.window.SplashScreen.SPLASH_SCREEN_STYLE_ICON;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityOptions;
import android.graphics.Rect;
import android.os.Bundle;
import android.test.AndroidTestCase;

public class ActivityOptionsTest extends AndroidTestCase {

    public void testActivityOptionsBundle_makeBasic() throws Throwable {
        ActivityOptions options = ActivityOptions.makeBasic();
        Bundle bundle = options.toBundle();

        assertNotNull(bundle);
    }

    public void testActivityOptionsBundle_fromBundle() {
        final int displayId = 9;
        final Rect bounds = new Rect(0, 10, 100, 90);
        // Construct some options with set values
        ActivityOptions opts = ActivityOptions.makeBasic();
        opts.setLaunchDisplayId(displayId);
        opts.setLockTaskEnabled(true);
        opts.setLaunchBounds(bounds);
        opts.setSplashScreenStyle(SPLASH_SCREEN_STYLE_ICON);

        Bundle optsBundle = opts.toBundle();

        ActivityOptions opts2 = ActivityOptions.fromBundle(optsBundle);
        assertThat(opts2.getLaunchDisplayId()).isEqualTo(displayId);
        assertThat(opts2.getLockTaskMode()).isTrue();
        assertThat(opts2.getLaunchBounds()).isEqualTo(bounds);
        assertThat(opts2.getSplashScreenStyle()).isEqualTo(SPLASH_SCREEN_STYLE_ICON);
    }

    public void testGetSetPendingIntentBackgroundActivityLaunchAllowed() {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityLaunchAllowed(true);
        assertThat(options.isPendingIntentBackgroundActivityLaunchAllowed()).isTrue();
        options.setPendingIntentBackgroundActivityLaunchAllowed(false);
        assertThat(options.isPendingIntentBackgroundActivityLaunchAllowed()).isFalse();
    }
}
