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
package android.telephony.ims.cts;

import static android.provider.Telephony.RcsColumns.IS_RCS_TABLE_SCHEMA_CODE_COMPLETE;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentProviderClient;
import android.content.Context;
import android.provider.Telephony;

import androidx.test.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class RcsProviderPermissionsTest {
    @BeforeClass
    public static void ensureDefaultSmsApp() {
        DefaultSmsAppHelper.ensureDefaultSmsApp();
    }

    @Before
    public void setupTestEnvironment() {
        // Used to skip tests for production builds without RCS tables, will be removed when
        // IS_RCS_TABLE_SCHEMA_CODE_COMPLETE flag is removed.
        Assume.assumeTrue(IS_RCS_TABLE_SCHEMA_CODE_COMPLETE);
    }

    @Test
    public void testRcsProvider_shouldNotHaveAccess() {
        Context context = InstrumentationRegistry.getTargetContext();

        try (ContentProviderClient client =
                     context.getContentResolver().acquireContentProviderClient(
                             Telephony.RcsColumns.AUTHORITY)) {
            assertThat(client).isNull();
        } catch (SecurityException e) {
            return;
        }
        Assert.fail();
    }
}
