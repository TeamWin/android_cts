/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.text.method.cts;


import android.app.Instrumentation;
import android.cts.util.CtsKeyEventUtil;
import android.cts.util.PollingCheck;
import android.os.ParcelFileDescriptor;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.test.ActivityInstrumentationTestCase2;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.KeyCharacterMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Scanner;

import static org.mockito.Mockito.*;

/**
 * Test {@link PasswordTransformationMethod}.
 */
public class PasswordTransformationMethodTest extends
        ActivityInstrumentationTestCase2<CtsActivity> {
    private static final int EDIT_TXT_ID = 1;

    /** original text */
    private static final String TEST_CONTENT = "test content";

    /** text after transformation: ************(12 dots) */
    private static final String TEST_CONTENT_TRANSFORMED =
        "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

    private int mPasswordPrefBackUp;

    private boolean isPasswordPrefSaved;

    private CtsActivity mActivity;

    private PasswordTransformationMethod mMethod;

    private EditText mEditText;

    private CharSequence mTransformedText;

    private Instrumentation mInstrumentation;

    public PasswordTransformationMethodTest() {
        super("android.text.cts", CtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        new PollingCheck(1000) {
            @Override
            protected boolean check() {
                return mActivity.hasWindowFocus();
            }
        }.run();
        mInstrumentation = getInstrumentation();
        mMethod = spy(new PasswordTransformationMethod());
        try {
            runTestOnUiThread(() -> {
                EditText editText = new EditTextNoIme(mActivity);
                editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                editText.setId(EDIT_TXT_ID);
                editText.setTransformationMethod(mMethod);
                Button button = new Button(mActivity);
                LinearLayout layout = new LinearLayout(mActivity);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.addView(editText, new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT));
                layout.addView(button, new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT));
                mActivity.setContentView(layout);
                editText.requestFocus();
            });
        } catch (Throwable e) {
            fail("Exception thrown is UI thread:" + e.getMessage());
        }
        mInstrumentation.waitForIdleSync();

        mEditText = (EditText) getActivity().findViewById(EDIT_TXT_ID);
        assertTrue(mEditText.isFocused());

        enableAppOps();
        savePasswordPref();
        switchShowPassword(true);
    }

    private void enableAppOps() {
        StringBuilder cmd = new StringBuilder();
        cmd.append("appops set ");
        cmd.append(mInstrumentation.getContext().getPackageName());
        cmd.append(" android:write_settings allow");
        mInstrumentation.getUiAutomation().executeShellCommand(cmd.toString());

        StringBuilder query = new StringBuilder();
        query.append("appops get ");
        query.append(mInstrumentation.getContext().getPackageName());
        query.append(" android:write_settings");
        String queryStr = query.toString();

        String result = "No operations.";
        while (result.contains("No operations")) {
            ParcelFileDescriptor pfd = mInstrumentation.getUiAutomation().executeShellCommand(
                                        queryStr);
            InputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
            result = convertStreamToString(inputStream);
        }
    }

    private String convertStreamToString(InputStream is) {
        try (Scanner scanner = new Scanner(is).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    @Override
    protected void tearDown() throws Exception {
        resumePasswordPref();
        super.tearDown();
    }

    public void testConstructor() {
        new PasswordTransformationMethod();
    }

    public void testTextChangedCallBacks() throws Throwable {
        runTestOnUiThread(() -> {
            mTransformedText = mMethod.getTransformation(mEditText.getText(), mEditText);
        });

        reset(mMethod);
        // 12-key support
        KeyCharacterMap keymap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        if (keymap.getKeyboardType() == KeyCharacterMap.NUMERIC) {
            // "HELLO" in case of 12-key(NUMERIC) keyboard
            CtsKeyEventUtil.sendKeys(mInstrumentation, mEditText,
                    "6*4 6*3 7*5 DPAD_RIGHT 7*5 7*6 DPAD_RIGHT");
        }
        else {
            CtsKeyEventUtil.sendKeys(mInstrumentation, mEditText, "H E 2*L O");
        }
        verify(mMethod, atLeastOnce()).beforeTextChanged(any(), anyInt(), anyInt(), anyInt());
        verify(mMethod, atLeastOnce()).onTextChanged(any(), anyInt(), anyInt(), anyInt());
        verify(mMethod, atLeastOnce()).afterTextChanged(any());

        reset(mMethod);

        runTestOnUiThread(() -> mEditText.append(" "));

        // the appended string will not get transformed immediately
        // "***** "
        assertEquals("\u2022\u2022\u2022\u2022\u2022 ", mTransformedText.toString());
        verify(mMethod, atLeastOnce()).beforeTextChanged(any(), anyInt(), anyInt(), anyInt());
        verify(mMethod, atLeastOnce()).onTextChanged(any(), anyInt(), anyInt(), anyInt());
        verify(mMethod, atLeastOnce()).afterTextChanged(any());

        // it will get transformed after a while
        // "******"
        PollingCheck.waitFor(() -> mTransformedText.toString()
                .equals("\u2022\u2022\u2022\u2022\u2022\u2022"));
    }

    public void testGetTransformation() {
        PasswordTransformationMethod method = new PasswordTransformationMethod();

        assertEquals(TEST_CONTENT_TRANSFORMED,
                method.getTransformation(TEST_CONTENT, null).toString());

        CharSequence transformed = method.getTransformation(null, mEditText);
        assertNotNull(transformed);
        try {
            transformed.toString();
            fail("Should throw NullPointerException if the source is null.");
        } catch (NullPointerException e) {
            // expected
        }
    }

    public void testGetInstance() {
        PasswordTransformationMethod method0 = PasswordTransformationMethod.getInstance();
        assertNotNull(method0);

        PasswordTransformationMethod method1 = PasswordTransformationMethod.getInstance();
        assertNotNull(method1);
        assertSame(method0, method1);
    }

    public void testOnFocusChanged() {
        // lose focus
        reset(mMethod);
        assertTrue(mEditText.isFocused());
        CtsKeyEventUtil.sendKeys(mInstrumentation, mEditText, "DPAD_DOWN");
        assertFalse(mEditText.isFocused());
        verify(mMethod, atLeastOnce()).onFocusChanged(any(), any(), anyBoolean(), anyInt(), any());

        // gain focus
        reset(mMethod);
        assertFalse(mEditText.isFocused());
        CtsKeyEventUtil.sendKeys(mInstrumentation, mEditText, "DPAD_UP");
        assertTrue(mEditText.isFocused());
        verify(mMethod, atLeastOnce()).onFocusChanged(any(), any(), anyBoolean(), anyInt(), any());
    }

    private void savePasswordPref() {
        try {
            mPasswordPrefBackUp = System.getInt(mActivity.getContentResolver(),
                    System.TEXT_SHOW_PASSWORD);
            isPasswordPrefSaved = true;
        } catch (SettingNotFoundException e) {
            isPasswordPrefSaved = false;
        }
    }

    private void resumePasswordPref() {
        if (isPasswordPrefSaved) {
            System.putInt(mActivity.getContentResolver(), System.TEXT_SHOW_PASSWORD,
                    mPasswordPrefBackUp);
        }
    }

    private void switchShowPassword(boolean on) {
        System.putInt(mActivity.getContentResolver(), System.TEXT_SHOW_PASSWORD,
                on ? 1 : 0);
    }
}
