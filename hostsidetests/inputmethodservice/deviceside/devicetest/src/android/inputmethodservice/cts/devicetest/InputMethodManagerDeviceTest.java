/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.inputmethodservice.cts.devicetest;

import static android.inputmethodservice.cts.common.BusyWaitUtils.pollingCheck;

import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.inputmethodservice.cts.common.Ime1Constants;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * Test public APIs defined in InputMethodManager.
 */
@RunWith(AndroidJUnit4.class)
public class InputMethodManagerDeviceTest {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long EXPECTED_TIMEOUT = TimeUnit.SECONDS.toMillis(2);

    /**
     * Make sure {@link InputMethodManager#getInputMethodList()} contains
     * {@link Ime1Constants#IME_ID}.
     */
    @Test
    public void testIme1InInputMethodList() throws Throwable {
        final Context context = InstrumentationRegistry.getTargetContext();
        final InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        pollingCheck(() -> imm.getInputMethodList().stream().anyMatch(
                imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID)),
                TIMEOUT, "Ime1 must exist.");
    }

    /**
     * Make sure {@link InputMethodManager#getInputMethodList()} does not contain
     * {@link Ime1Constants#IME_ID}.
     */
    @Test
    public void testIme1NotInInputMethodList() throws Throwable {
        final Context context = InstrumentationRegistry.getTargetContext();
        final InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        Thread.sleep(EXPECTED_TIMEOUT);
        assertFalse(imm.getInputMethodList().stream().anyMatch(
                imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID)));
    }

    /**
     * Make sure
     * {@link InputMethodManager#getEnabledInputMethodSubtypeList(InputMethodInfo, boolean)} for
     * {@link Ime1Constants#IME_ID} returns the implicitly enabled subtype.
     */
    @Test
    public void testIme1ImplicitlyEnabledSubtypeExists() throws Throwable {
        final Context context = InstrumentationRegistry.getTargetContext();
        final InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        pollingCheck(() -> imm.getInputMethodList().stream()
                        .filter(imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID))
                        .flatMap(imi -> imm.getEnabledInputMethodSubtypeList(imi, true).stream())
                        .anyMatch(InputMethodSubtype::overridesImplicitlyEnabledSubtype),
                TIMEOUT, "Implicitly enabled Subtype must exist for IME1.");
    }

    /**
     * Make sure
     * {@link InputMethodManager#getEnabledInputMethodSubtypeList(InputMethodInfo, boolean)} for
     * {@link Ime1Constants#IME_ID} does not return the implicitly enabled subtype.
     */
    @Test
    public void testIme1ImplicitlyEnabledSubtypeNotExist() throws Throwable {
        final Context context = InstrumentationRegistry.getTargetContext();
        final InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        Thread.sleep(EXPECTED_TIMEOUT);
        assertFalse(imm.getInputMethodList().stream()
                .filter(imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID))
                .flatMap(imi -> imm.getEnabledInputMethodSubtypeList(imi, true).stream())
                .anyMatch(InputMethodSubtype::overridesImplicitlyEnabledSubtype));
    }
}
