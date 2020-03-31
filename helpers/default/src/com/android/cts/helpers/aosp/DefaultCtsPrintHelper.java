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
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
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

    @Override
    public void selectPrinter(String printerName, long timeout) throws TestHelperException {
        Log.d(LOG_TAG, "Selecting printer " + printerName);
        try {
            UiObject2 destinationSpinner =
                    mDevice.wait(
                            Until.findObject(
                                    By.res("com.android.printspooler:id/destination_spinner")),
                            timeout);

            if (destinationSpinner != null) {
                destinationSpinner.click();
                mDevice.waitForIdle();
            }

            UiObject2 printerOption = mDevice.wait(Until.findObject(By.text(printerName)), timeout);
            if (printerOption == null) {
                throw new UiObjectNotFoundException(printerName + " not found");
            }

            printerOption.click();
            mDevice.waitForIdle();
        } catch (Exception e) {
            dumpWindowHierarchy();
            throw new TestHelperException("Failed to select printer", e);
        }
    }

    @Override
    public void setPageOrientation(String orientation) throws TestHelperException {
        try {
            UiObject orientationSpinner = mDevice.findObject(new UiSelector().resourceId(
                    "com.android.printspooler:id/orientation_spinner"));
            orientationSpinner.click();
            UiObject orientationOption = mDevice.findObject(new UiSelector().text(orientation));
            orientationOption.click();
        } catch (UiObjectNotFoundException e) {
            dumpWindowHierarchy();
            throw new TestHelperException("Failed to set page orientation to " + orientation, e);
        }
    }

    @Override
    public String getPageOrientation() throws TestHelperException {
        try {
            UiObject orientationSpinner = mDevice.findObject(new UiSelector().resourceId(
                    "com.android.printspooler:id/orientation_spinner"));
            return orientationSpinner.getText();
        } catch (UiObjectNotFoundException e) {
            dumpWindowHierarchy();
            throw new TestHelperException("Failed to get page orientation", e);
        }
    }

    @Override
    public void setMediaSize(String mediaSize) throws TestHelperException {
        try {
            UiObject mediaSizeSpinner = mDevice.findObject(new UiSelector().resourceId(
                    "com.android.printspooler:id/paper_size_spinner"));
            mediaSizeSpinner.click();
            UiObject mediaSizeOption = mDevice.findObject(new UiSelector().text(mediaSize));
            mediaSizeOption.click();
        } catch (UiObjectNotFoundException e) {
            dumpWindowHierarchy();
            throw new TestHelperException("Unable to set media size to " + mediaSize, e);
        }
    }

    @Override
    public void setColorMode(String color) throws TestHelperException {
        try {
            UiObject colorSpinner = mDevice.findObject(new UiSelector().resourceId(
                    "com.android.printspooler:id/color_spinner"));
            colorSpinner.click();
            UiObject colorOption = mDevice.findObject(new UiSelector().text(color));
            colorOption.click();
        } catch (UiObjectNotFoundException e) {
            dumpWindowHierarchy();
            throw new TestHelperException("Unable to set color mode to " + color, e);
        }
    }

    @Override
    public String getColorMode() throws TestHelperException {
        try {
            UiObject colorSpinner = mDevice.findObject(new UiSelector().resourceId(
                    "com.android.printspooler:id/color_spinner"));
            return colorSpinner.getText();
        } catch (UiObjectNotFoundException e) {
            dumpWindowHierarchy();
            throw new TestHelperException("Unable to get color mode", e);
        }
    }

    @Override
    public void setDuplexMode(String duplex) throws TestHelperException {
        try {
            UiObject duplexSpinner = mDevice.findObject(new UiSelector().resourceId(
                    "com.android.printspooler:id/duplex_spinner"));
            duplexSpinner.click();
            UiObject duplexOption = mDevice.findObject(new UiSelector().text(duplex));
            duplexOption.click();
        } catch (UiObjectNotFoundException e) {
            dumpWindowHierarchy();
            throw new TestHelperException("Unable to set duplex mode to " + duplex, e);
        }
    }

    @Override
    public void setCopies(int newCopies) throws TestHelperException {
        try {
            UiObject copies = mDevice.findObject(new UiSelector().resourceId(
                    "com.android.printspooler:id/copies_edittext"));
            copies.setText(Integer.valueOf(newCopies).toString());
        } catch (UiObjectNotFoundException e) {
            dumpWindowHierarchy();
            throw new TestHelperException("Unable to set copies to " + newCopies, e);
        }
    }

    @Override
    public int getCopies() throws TestHelperException {
        try {
            UiObject copies = mDevice.findObject(new UiSelector().resourceId(
                    "com.android.printspooler:id/copies_edittext"));
            return Integer.parseInt(copies.getText());
        } catch (UiObjectNotFoundException e) {
            dumpWindowHierarchy();
            throw new TestHelperException("Unable to get number of copies", e);
        }
    }
}
