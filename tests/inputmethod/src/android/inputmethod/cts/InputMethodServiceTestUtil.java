/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.inputmethod.cts;

import android.annotation.NonNull;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility functions for testing of input method stuff.
 */
public final class InputMethodServiceTestUtil {
    private static final String TAG = InputMethodServiceTestUtil.class.getSimpleName();

    // Prevents this from being instantiated.
    private InputMethodServiceTestUtil() {}

    @NonNull
    private static String executeShellCommand(final UiAutomation uiAutomation, final String[] cmd) {
        final String flattenCmd = TextUtils.join(" ", cmd);
        List<String> output = new ArrayList<>();

        try (final ParcelFileDescriptor fd = uiAutomation.executeShellCommand(flattenCmd);
             final FileReader fr = new FileReader(fd.getFileDescriptor());
             final BufferedReader br = new BufferedReader(fr)) {

            String line;
            while ((line = br.readLine()) != null) {
                output.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // The output from the "ime" command should be only one line.
        if (output.size() != 1) {
            throw new IllegalStateException(
                    "The output from 'ime' command should be one line, but it outputs multiples: " +
                    TextUtils.join("\n", output));
        }
        return output.get(0);
    }

    @NonNull
    public static String getCurrentImeId(final Instrumentation inst) {
        return Settings.Secure.getString(inst.getContext().getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
    }

    public static boolean isImeEnabled(final Instrumentation inst, final String imeId) {
        final List<String> enabledImes = getEnabledImeIds(inst);
        return enabledImes.contains(imeId);
    }

    @NonNull
    public static List<String> getEnabledImeIds(final Instrumentation inst) {
        InputMethodManager imm = (InputMethodManager) inst.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        final List<InputMethodInfo> enabledImes = imm.getEnabledInputMethodList();
        List<String> result = new ArrayList<>();
        for (final InputMethodInfo enabledIme : enabledImes) {
            result.add(enabledIme.getId());
        }
        return result;
    }

    /**
     * Puts the specified IME into the available input method list.
     *
     * This operation will be done synchronously in "ime" command using
     * {@link com.android.server.InputMethodManagerService#setInputMethodEnabled(String, boolean)},
     * which is synchronous.
     *
     * @param imeId IME ID to be enabled.
     * @return {@code true} if the target IME gets enabled successfully. {@code false} if failed.
     */
    public static boolean enableIme(final Instrumentation inst, final String imeId) {
        // Needs to check the output message from the checking command, since executeShellCommand()
        // does not pass the exit status code back to the test.
        final String output = executeShellCommand(
                inst.getUiAutomation(), new String[]{"ime", "enable", imeId});
        final String expectedOutput = "Input method " + imeId + ": now enabled";
        if (!output.equals(expectedOutput)) {
            Log.e(TAG, "Unexpected output message. Expected: " + expectedOutput +
                    ", Actual: " + output);
            return false;
        }

        final InputMethodManager imm = (InputMethodManager) inst.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        final List<InputMethodInfo> enabledInputMethods = imm.getEnabledInputMethodList();
        for (final InputMethodInfo imi : enabledInputMethods) {
            if (imi.getId().equals(imeId))
                return true;
        }

        Log.e(TAG, "Failed to enable the given IME (IME ID: " + imeId + ").");
        return false;
    }

    /**
     * Removes the specified IME from the available input method list.
     *
     * This operation will {@code @NonNull} final be done synchronously in "ime" command using
     * {@link com.android.server.InputMethodManagerService#setInputMethodEnabled(String, boolean)},
     * which is synchronous.
     *
     * @param imeId IME ID to be disabled.
     * @return {@code true} if the target IME gets disabled successfully. {@code false} if failed.
     */
    public static boolean disableIme(final Instrumentation inst, final String imeId) {
        // Needs to check the output message from the checking command, since executeShellCommand()
        // does not pass the exit status code back to the test.
        final String output = executeShellCommand(
                inst.getUiAutomation(), new String[]{"ime", "disable", imeId});
        final String expectedOutput = "Input method " + imeId + ": now disabled";
        if (!output.equals(expectedOutput)) {
            Log.w(TAG, "Unexpected output message. Expected: " + expectedOutput +
                    ", Actual: " + output);
            return false;
        }

        final InputMethodManager imm = (InputMethodManager) inst.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        final List<InputMethodInfo> enabledInputMethods = imm.getEnabledInputMethodList();
        for (final InputMethodInfo imi : enabledInputMethods) {
            if (imi.getId().equals(imeId)) {
                Log.e(TAG, "Failed to disable the given IME (IME ID: " + imeId + ").");
                return false;
            }
        }

        return true;
    }

    /**
     * Switches to the specified IME.
     *
     * This operation will be done synchronously in the "ime" command using
     * {@link InputMethodManager#setInputMethod(IBinder, String)}, which is synchronous.
     *
     * @param imeId IME ID to be switched to.
     * @return {@code true} if the target IME gets active successfully. {@code false} if failed.
     */
    public static boolean setIme(final Instrumentation inst, final String imeId) {
        // Needs to check the output message from the checking command, since executeShellCommand()
        // does not pass the exit status code back to the test.
        final String output = executeShellCommand(
                inst.getUiAutomation(), new String[]{"ime", "set", imeId});
        final String expectedOutput = "Input method " + imeId + " selected";
        if (!output.equals(expectedOutput)) {
            Log.w(TAG, "Unexpected output message. Expected: " + expectedOutput +  ", Actual: " +
                    output);
            return false;
        }

        final String currentImeId = getCurrentImeId(inst);
        if (!TextUtils.equals(currentImeId, imeId)) {
            Log.e(TAG, "Failed to switch the current IME. Expected: " + imeId +  ", Actual: " +
                    currentImeId);
            return false;
        }

        return true;
    }
}
