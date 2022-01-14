/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.cts.builtin.os;

import static com.google.common.truth.Truth.assertThat;

import android.car.builtin.os.SystemPropertiesHelper;
import android.os.SystemProperties;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SystemPropertiesHelperTest {
    private static final String TAG = SystemPropertiesHelperTest.class.getSimpleName();

    // a temporary SystemProperty for CTS. it will be cleared after device reboot.
    private static final String CTS_TEST_PROPERTY_KEY = "cts.car.builtin_property_helper.String";
    private static final String CTS_TEST_PROPERTY_VAL = "SystemPropertiesHelperTest";

    @Test
    public void testSet() {
        SystemPropertiesHelper.set(CTS_TEST_PROPERTY_KEY, CTS_TEST_PROPERTY_VAL);
        String val = SystemProperties.get(CTS_TEST_PROPERTY_KEY);
        Log.d(TAG, val);
        assertThat(val).isEqualTo(CTS_TEST_PROPERTY_VAL);
    }
}
