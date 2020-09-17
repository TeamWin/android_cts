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

package android.cts.tagging.sdk30;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.cts.tagging.Utils;
import android.os.Build;

import androidx.test.filters.SmallTest;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;

import com.android.compatibility.common.util.DropBoxReceiver;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TaggingTest {
    private static final String NATIVE_CRASH_TAG = "data_app_native_crash";

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testHeapTaggingEnabled() {
        assertNotEquals(0, Utils.nativeHeapPointerTag());
    }

    @Test
    public void testHeapTaggingDisabled() {
        assertEquals(0, Utils.nativeHeapPointerTag());
    }

    @Test
    public void testMemoryTagChecksEnabled() throws Exception {
        final DropBoxReceiver receiver =
                new DropBoxReceiver(
                        mContext,
                        NATIVE_CRASH_TAG,
                        mContext.getPackageName() + ":CrashProcess",
                        "backtrace:");
        Intent intent = new Intent();
        intent.setClass(mContext, CrashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);

        assertTrue(receiver.await());
    }

    @Test
    public void testMemoryTagChecksDisabled() {
        Utils.accessMistaggedPointer();
    }
}
