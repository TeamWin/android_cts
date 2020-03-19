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
 * limitations under the License
 */

package android.server.wm;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.Presubmit;
import android.server.wm.app.Components;
import android.view.Display;

import org.junit.Test;

import java.util.List;

@Presubmit
public class PresentationTest extends MultiDisplayTestBase {

    // WindowManager.LayoutParams.TYPE_PRESENTATION
    private static final int TYPE_PRESENTATION = 2037;

    @Test
    public void testPresentationFollowsDisplayFlag() {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        for (Display display : displayManager.getDisplays()) {
            launchPresentationActivity(display.getDisplayId());
            if ((display.getFlags() & Display.FLAG_PRESENTATION) != Display.FLAG_PRESENTATION) {
                assertNoPresentationDisplayed();
            } else {
                assertPresentationOnDisplay(display.getDisplayId());
            }
        }
    }

    @Test
    public void testPresentationAllowedOnPresentationDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            ActivityManagerState.ActivityDisplay display =
                virtualDisplaySession
                    .setPresentationDisplay(true)
                    .setPublicDisplay(true)
                    .createDisplay();

            launchPresentationActivity(display.mId);
            assertPresentationOnDisplay(display.mId);
        }
    }

    @Test
    public void testPresentationBlockedOnNonPresentationDisplay() throws Exception {
        try(final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            ActivityManagerState.ActivityDisplay display =
                   virtualDisplaySession
                            .setPresentationDisplay(false)
                            .createDisplay();
    
            launchPresentationActivity(display.mId);
            assertNoPresentationDisplayed();
        }
    }

    private void assertNoPresentationDisplayed() {
        final List<WindowManagerState.WindowState> presentationWindows =
                mAmWmState.getWmState()
                    .getWindowsByPackageName(
                        Components.PRESENTATION_ACTIVITY.getPackageName(), TYPE_PRESENTATION);
        assertThat(presentationWindows).isEmpty();
    }

    private void assertPresentationOnDisplay(int displayId) {
        final List<WindowManagerState.WindowState> presentationWindows =
                mAmWmState.getWmState()
                    .getWindowsByPackageName(
                        Components.PRESENTATION_ACTIVITY.getPackageName(), TYPE_PRESENTATION);
        assertThat(presentationWindows).hasSize(1);
        WindowManagerState.WindowState presentationWindowState = presentationWindows.get(0);
        assertThat(presentationWindowState.getDisplayId()).isEqualTo(displayId);
    }

    private void launchPresentationActivity(int displayId) {
        Intent intent = new Intent();
        intent.setComponent(Components.PRESENTATION_ACTIVITY);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Components.PresentationActivity.KEY_DISPLAY_ID, displayId);
        mContext.startActivity(intent);
        waitAndAssertTopResumedActivity(
                Components.PRESENTATION_ACTIVITY,
                Display.DEFAULT_DISPLAY,
                "Launched activity must be on top");
    }
}
