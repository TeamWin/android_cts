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

package android.content.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;

/**
 * To run:
 * cts-tradefed run singleCommand cts-dev -m CtsContentTestCases -t android.content.cts.ClipDescriptionTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
//@AppModeFull // TODO(Instant) Should clip board data be visible?
public class ClipDescriptionTest {
    private UiDevice mUiDevice;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mUiDevice.wakeUp();
        launchActivity(MockActivity.class);
    }

    @UiThreadTest
    @Test
    public void testGetTimestamp() {
        final ClipboardManager clipboardManager = (ClipboardManager)
                InstrumentationRegistry.getTargetContext().getSystemService(
                        Context.CLIPBOARD_SERVICE);
        final long timestampBeforeSet = System.currentTimeMillis();
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Dummy text", "Text"));
        final long timestampAfterSet = System.currentTimeMillis();
        final long timestamp = clipboardManager.getPrimaryClipDescription().getTimestamp();
        if (timestamp < timestampBeforeSet || timestamp > timestampAfterSet) {
            fail("Value of timestamp is not as expected.\n"
                    + "timestamp before setting clip: " + logTime(timestampBeforeSet) + "\n"
                    + "timestamp after setting clip: " + logTime(timestampAfterSet) + "\n"
                    + "actual timestamp: " + logTime(timestamp) + "\n"
                    + "clipdata: " + clipboardManager.getPrimaryClip());
        }
    }

    @Test
    public void testIsStyledText() {
        ClipDescription clipDescription = new ClipDescription(
                "label", new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });

        // False as clip description is not attached to anything.
        assertThat(clipDescription.isStyledText()).isFalse();

        SpannableString spannable = new SpannableString("Hello this is some text");
        spannable.setSpan(new UnderlineSpan(), 6, 10, 0);
        ClipData clipData = new ClipData(clipDescription, new ClipData.Item(spannable));

        assertThat(clipDescription.isStyledText()).isTrue();

        ClipboardManager clipboardManager = (ClipboardManager)
                InstrumentationRegistry.getTargetContext().getSystemService(
                        Context.CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(clipData);
        assertThat(clipboardManager.getPrimaryClipDescription().isStyledText()).isTrue();

        ClipData clipData2 = ClipData.newPlainText("label", spannable);
        assertThat(clipData2.getDescription().isStyledText()).isTrue();
        clipboardManager.setPrimaryClip(clipData2);
        assertThat(clipboardManager.getPrimaryClipDescription().isStyledText()).isTrue();
    }

    @Test
    public void testNotStyledText() {
        ClipDescription clipDescription = new ClipDescription(
                "label", new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });
        ClipData clipData = new ClipData(clipDescription, new ClipData.Item("Just text"));
        assertThat(clipDescription.isStyledText()).isFalse();

        ClipboardManager clipboardManager = (ClipboardManager)
                InstrumentationRegistry.getTargetContext().getSystemService(
                        Context.CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(clipData);
        assertThat(clipboardManager.getPrimaryClipDescription().isStyledText()).isFalse();

        ClipData clipData2 = ClipData.newPlainText("label", "Just some text");
        assertThat(clipData2.getDescription().isStyledText()).isFalse();
        clipboardManager.setPrimaryClip(clipData2);
        assertThat(clipboardManager.getPrimaryClipDescription().isStyledText()).isFalse();

        // Test that a URI is not considered styled text.
        ClipData clipDataUri = ClipData.newRawUri("label", Uri.parse("content://test"));
        assertThat(clipDataUri.getDescription().isStyledText()).isFalse();
        clipboardManager.setPrimaryClip(clipDataUri);
        assertThat(clipboardManager.getPrimaryClipDescription().isStyledText()).isFalse();
    }

    /**
     * Convert a System.currentTimeMillis() value to a time of day value like
     * that printed in logs. MM-DD-YY HH:MM:SS.MMM
     *
     * @param millis since the epoch (1/1/1970)
     * @return String representation of the time.
     */
    public static String logTime(long millis) {
        Calendar c = Calendar.getInstance();
        if (millis >= 0) {
            c.setTimeInMillis(millis);
            return String.format("%tm-%td-%ty %tH:%tM:%tS.%tL", c, c, c, c, c, c, c);
        } else {
            return Long.toString(millis);
        }
    }

    private void launchActivity(Class<? extends Activity> clazz) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(mContext.getPackageName(), clazz.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        mUiDevice.wait(Until.hasObject(By.clazz(clazz)), 5000);
    }
}
