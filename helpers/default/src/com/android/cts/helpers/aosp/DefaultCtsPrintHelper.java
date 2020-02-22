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
 * limitations under the License.
 */

package com.android.cts.helpers.aosp;

import android.app.Instrumentation;
import android.app.UiAutomation;

import android.platform.helpers.exceptions.TestHelperException;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;

import com.android.cts.helpers.ICtsPrintHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class DefaultCtsPrintHelper implements ICtsPrintHelper {
    private static final String LOG_TAG = DefaultCtsPrintHelper.class.getSimpleName();

    protected static final long OPERATION_TIMEOUT_MILLIS = 60000;

    protected Instrumentation mInstrumentation;
    protected UiDevice mDevice;
    protected UiAutomation mAutomation;

    public DefaultCtsPrintHelper(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
        mDevice = UiDevice.getInstance(mInstrumentation);
        mAutomation = mInstrumentation.getUiAutomation();
    }

    protected void dumpWindowHierarchy() throws TestHelperException {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            mDevice.dumpWindowHierarchy(os);

            Log.w(LOG_TAG, "Window hierarchy:");
            for (String line : os.toString("UTF-8").split("\n")) {
                Log.w(LOG_TAG, line);
            }
        } catch (IOException e) {
            throw new TestHelperException(e);
        }
    }

    @Override
    public void submitPrintJob() throws TestHelperException {
        Log.d(LOG_TAG, "Clicking print button");

        mDevice.waitForIdle();

        UiObject2 printButton =
                mDevice.wait(
                        Until.findObject(By.res("com.android.printspooler:id/print_button")),
                        OPERATION_TIMEOUT_MILLIS);
        if (printButton == null) {
            dumpWindowHierarchy();
            throw new TestHelperException("print button not found");
        }

        printButton.click();
    }
}
