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

package android.autofillservice.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Instrumentation;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.util.Log;

/**
 * Helper for UI-related needs.
 */
final class UiBot {

    private static final String TAG = "AutoFillCtsUiBot";

    private final UiDevice mDevice;
    private final int mTimeout;
    private final String mPackageName;

    UiBot(Instrumentation instrumentation, int timeout) throws Exception {
        mDevice = UiDevice.getInstance(instrumentation);
        mTimeout = timeout;
        mPackageName = instrumentation.getContext().getPackageName();

        collapseStatusBar();
    }

    /**
     * Selects an auto-fill dataset whose name should be visible in the UI.
     */
    void selectDataset(String name) {
        // TODO(b/33197203): use id string when using real auto-fill bar
        Log.v(TAG, "selectDataset(): " + name);

        clickOnNotification(name.toUpperCase());
    }

    /**
     * Triggers IME by tapping a given field.
     *
     * @param id resource id, without the {@code +id} prefix
     */
    void triggerImeByRelativeId(String id) throws UiObjectNotFoundException {
        Log.v(TAG, "triggerImeByRelativeId(): " + id);
        final String fullId = mPackageName + ":id/" + id;
        final UiObject field = mDevice.findObject(new UiSelector().resourceId(fullId));
        final boolean clicked = field.clickAndWaitForNewWindow();
        assertWithMessage("Failed to tap object with id '%s'", fullId).that(clicked).isTrue();
    }

    /**
     * Taps a given UI object.
     *
     * @param id resource id, without the {@code +id} prefix
     */
    void tapByRelativeId(String id) throws UiObjectNotFoundException {
        Log.v(TAG, "tapFieldByRelativeId(): " + id);
        final String fullId = mPackageName + ":id/" + id;
        final UiObject field = mDevice.findObject(new UiSelector().resourceId(fullId));
        final boolean clicked = field.click();
        assertWithMessage("Failed to tap object with id '%s'", fullId).that(clicked).isTrue();
    }

    /////////////////////////////////////////////////////////////////////////////////
    // TODO(b/33197203): temporary code using a notification to request auto-fill. //
    // Will be removed once UX decide the right way to present it to the user.     //
    /////////////////////////////////////////////////////////////////////////////////
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    /**
     * Clicks on a UI element.
     *
     * @param uiObject UI element to be clicked.
     * @param description Elements's description used on logging statements.
     */
    private void click(UiObject uiObject, String description) {
        try {
            boolean clicked = uiObject.click();
            // TODO: assertion below fails sometimes, even though the click succeeded,
            // (specially when clicking the "Just Once" button), so it's currently just logged.
            // assertTrue("could not click on object '" + description + "'", clicked);

            Log.v(TAG, "onClick for " + description + ": " + clicked);
        } catch (UiObjectNotFoundException e) {
            throw new IllegalStateException("exception when clicking on object '" + description
                    + "'", e);
        }
    }

    /**
     * Opens the system notification and clicks a given notification.
     *
     * @param text Notificaton's text as displayed by the UI.
     */
    private void clickOnNotification(String text) {
        final UiObject notification = getNotification(text);
        click(notification, "notification '" + text+ "'");
    }

    private UiObject getNotification(String text) {
        final boolean opened = mDevice.openNotification();
        Log.v(TAG, "openNotification(): " + opened);
        final boolean gotIt = mDevice.wait(Until.hasObject(By.pkg(SYSTEMUI_PACKAGE)), mTimeout);
        assertWithMessage("could not get system ui (%s)", SYSTEMUI_PACKAGE).that(gotIt).isTrue();

        return getObject(text);
    }
    /**
     * Gets an object that might not yet be available in current UI.
     *
     * @param text Object's text as displayed by the UI.
     */
    private UiObject getObject(String text) {
        final boolean gotIt = mDevice.wait(Until.hasObject(By.text(text)), mTimeout);
        assertWithMessage("object with text '%s') not visible yet", text).that(gotIt).isTrue();
        return getVisibleObject(text);
    }

    /**
     * Gets an object which is guaranteed to be present in the current UI.
     *
     * @param text Object's text as displayed by the UI.
     */
    private UiObject getVisibleObject(String text) {
        final UiObject uiObject = mDevice.findObject(new UiSelector().text(text));
        assertWithMessage("could not find object with '%s'", text).that(uiObject.exists()).isTrue();
        return uiObject;
    }

    void collapseStatusBar() throws Exception {
        Helper.runShellCommand("service call statusbar 2");
    }

    void expandStatusBar() throws Exception {
        Helper.runShellCommand("service call statusbar 1");
    }

    /////////////////////////////////////////
    // End of temporary notification code. //
    /////////////////////////////////////////
}
