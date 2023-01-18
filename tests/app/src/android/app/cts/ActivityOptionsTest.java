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

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityOptions;
import android.os.Bundle;
import android.test.AndroidTestCase;

public class ActivityOptionsTest extends AndroidTestCase {

    public void testActivityOptionsBundle_makeBasic() throws Throwable {
        ActivityOptions options = ActivityOptions.makeBasic();
        Bundle bundle = options.toBundle();

        assertNotNull(bundle);
    }

    public void testGetPendingIntentBackgroundActivityLaunchAllowedDefault() {
        ActivityOptions options = ActivityOptions.makeBasic();
        // backwards compatibility
        assertThat(options.isPendingIntentBackgroundActivityLaunchAllowed()).isTrue();
        assertThat(options.getPendingIntentBackgroundActivityStartMode()).isEqualTo(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED);
    }

    public void testGetSetPendingIntentBackgroundActivityLaunchAllowedTrue() {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityLaunchAllowed(true);
        assertThat(options.isPendingIntentBackgroundActivityLaunchAllowed()).isTrue();
        assertThat(options.getPendingIntentBackgroundActivityStartMode()).isEqualTo(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
    }

    public void testGetSetPendingIntentBackgroundActivityLaunchAllowedFalse() {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityLaunchAllowed(false);
        assertThat(options.isPendingIntentBackgroundActivityLaunchAllowed()).isFalse();
        assertThat(options.getPendingIntentBackgroundActivityStartMode()).isEqualTo(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED);
    }

    public void testGetSetPendingIntentBackgroundActivityStartModeAllowed() {
        ActivityOptions options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        assertThat(options.isPendingIntentBackgroundActivityLaunchAllowed()).isTrue();
        assertThat(options.getPendingIntentBackgroundActivityStartMode()).isEqualTo(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
    }

    public void testGetSetPendingIntentBackgroundActivityStartModeDenied() {
        ActivityOptions options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED);
        assertThat(options.isPendingIntentBackgroundActivityLaunchAllowed()).isFalse();
        assertThat(options.getPendingIntentBackgroundActivityStartMode()).isEqualTo(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED);
    }
}
