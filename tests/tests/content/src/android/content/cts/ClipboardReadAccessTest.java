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

package android.content.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ClipboardReadAccessTest {
    private Context mContext;
    private ClipboardManager mClipboardManager;

    @Before
    public void before() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        assumeTrue("Skipping Test: Wear-Os does not support ClipboardService",
                hasAutoFillFeature());
        mClipboardManager = mContext.getSystemService(ClipboardManager.class);
    }

    @After
    public void after() {
        if (mClipboardManager != null) {
            mClipboardManager.clearPrimaryClip();
        }
    }

    @Test
    public void testReadInBackground_requiresPermission() throws Exception {
        ClipData clip = ClipData.newPlainText("TextLabel", "Text1");
        mClipboardManager.setPrimaryClip(clip);

        // Without the READ_CLIPBOARD_IN_BACKGROUND permission, we should see an empty clipboard.
        assertFalse(mClipboardManager.hasPrimaryClip());
        assertFalse(mClipboardManager.hasText());
        assertNull(mClipboardManager.getPrimaryClip());
        assertNull(mClipboardManager.getPrimaryClipDescription());

        // Having the READ_CLIPBOARD_IN_BACKGROUND permission should allow us to read the clipboard
        // even when we are not in the foreground. We use the shell identity to simulate holding
        // this permission; in practice, only privileged system apps such as "Android System
        // Intelligence" can hold this permission.
        ClipData actual = SystemUtil.callWithShellPermissionIdentity(
                () -> mClipboardManager.getPrimaryClip(),
                android.Manifest.permission.READ_CLIPBOARD_IN_BACKGROUND);
        assertEquals("Text1", actual.getItemAt(0).getText());
    }

    private boolean hasAutoFillFeature() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOFILL);
    }
}
