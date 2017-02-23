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
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;

/**
 * Helper for UI-related needs.
 */
final class UiBot {

    private static final String TAG = "AutoFillCtsUiBot";

    private final UiDevice mDevice;
    private final int mTimeout;

    UiBot(Instrumentation instrumentation, int timeout) throws Exception {
        mDevice = UiDevice.getInstance(instrumentation);
        mTimeout = timeout;
    }

    /**
     * Asserts the dataset chooser is not shown.
     */
    void assertNoDatasets() {
        final UiObject2 ui;
        try {
            ui = waitForObject(By.res("android", "list"));
        } catch (Throwable t) {
            // TODO(b/33197203): use a more elegant check than catching the expection because it's
            // not showing...
            return;
        }
        throw new AssertionError("floating ui is shown: " + ui);
    }

    /**
     * Selects a dataset that should be visible in the floating UI.
     */
    void selectDataset(String name) {
        // TODO(b/33197203): Use more qualified ids for UI.
        waitForObject(By.res("android", "list"));
        selectByText(name);
    }

    /**
     * Selects a view by text.
     */
    void selectByText(String name) {
        Log.v(TAG, "selectByText(): " + name);

        final UiObject2 dataset = waitForObject(By.text(name));
        dataset.click();
    }

    /**
     * Taps an option in the save snackbar.
     *
     * @param yesDoIt {@code true} for 'YES', {@code false} for 'NO THANKS'.
     */
    void saveForAutofill(boolean yesDoIt) {
        final String id = yesDoIt ? "autofill_save_yes" : "autofill_save_no";
        final UiObject2 button = waitForObject(By.res("android", id));
        button.click();
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
