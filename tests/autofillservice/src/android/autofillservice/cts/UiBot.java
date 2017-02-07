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
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObject2;
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
    }

    /**
     * Selects an auto-fill dataset whose name should be visible in the UI.
     */
    void selectDataset(String name) {
        Log.v(TAG, "selectDataset(): " + name);

        // TODO(b/33197203): Use more qualified ids for UI.
        final UiObject2 dataset = waitForObject(By.res("android", "text1").text(name));
        dataset.click();
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

    /**
     * Waits for and returns an object.
     *
     * @param selector {@link BySelector} that identifies the object.
     */
    private UiObject2 waitForObject(BySelector selector) {
        final boolean gotIt = mDevice.wait(Until.hasObject(selector), mTimeout);
        assertWithMessage("object for '%s' not found in %s ms", selector, mTimeout).that(gotIt)
                .isTrue();

        final UiObject2 uiObject = mDevice.findObject(selector);
        assertWithMessage("object for '%s' null in %s ms", selector, mTimeout).that(uiObject)
                .isNotNull();
        return uiObject;
    }
}
