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

package android.view.cts;

import static android.view.ContentInfo.SOURCE_CLIPBOARD;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.net.Uri;
import android.view.ContentInfo;
import android.view.OnReceiveContentListener;
import android.view.View;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link View#performReceiveContent} and related code.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ViewOnReceiveContentTest {
    @Rule
    public ActivityTestRule<ViewTestCtsActivity> mActivityRule = new ActivityTestRule<>(
            ViewTestCtsActivity.class);

    private Instrumentation mInstrumentation;
    private Context mContext;
    private ViewTestCtsActivity mActivity;
    private OnReceiveContentListener mReceiver;

    @Before
    public void before() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
        mReceiver = mock(OnReceiveContentListener.class);
    }

    @Test
    public void testOnReceiveContent_mimeTypes() {
        View view = new View(mActivity);

        // MIME types are null by default
        assertThat(view.getOnReceiveContentMimeTypes()).isNull();

        // Setting MIME types with a non-null callback works
        String[] mimeTypes = new String[] {"image/*", "video/mp4"};
        view.setOnReceiveContentListener(mimeTypes, mReceiver);
        assertThat(view.getOnReceiveContentMimeTypes()).isEqualTo(mimeTypes);

        // Setting null MIME types and null callback works
        view.setOnReceiveContentListener(null, null);
        assertThat(view.getOnReceiveContentMimeTypes()).isNull();

        // Setting empty MIME types and null callback works
        view.setOnReceiveContentListener(new String[0], null);
        assertThat(view.getOnReceiveContentMimeTypes()).isNull();

        // Setting MIME types with a null callback works
        view.setOnReceiveContentListener(mimeTypes, null);
        assertThat(view.getOnReceiveContentMimeTypes()).isEqualTo(mimeTypes);

        // Setting null or empty MIME types with a non-null callback is not allowed
        try {
            view.setOnReceiveContentListener(null, mReceiver);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
        try {
            view.setOnReceiveContentListener(new String[0], mReceiver);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }

        // Passing "*/*" as a MIME type is not allowed
        try {
            view.setOnReceiveContentListener(new String[] {"image/gif", "*/*"}, mReceiver);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    @Test
    public void testPerformReceiveContent() {
        View view = new View(mActivity);
        String[] mimeTypes = new String[] {"image/*", "video/mp4"};
        ContentInfo samplePayloadGif = sampleUriPayload("image/gif");
        ContentInfo samplePayloadPdf = sampleUriPayload("application/pdf");

        // Calling performReceiveContent() returns the payload if there's no listener (default)
        assertThat(view.performReceiveContent(samplePayloadGif)).isEqualTo(samplePayloadGif);

        // Calling performReceiveContent() calls the configured listener
        view.setOnReceiveContentListener(mimeTypes, mReceiver);
        when(mReceiver.onReceiveContent(any(), any())).thenReturn(null);
        assertThat(view.performReceiveContent(samplePayloadGif)).isNull();

        // Calling performReceiveContent() calls the configured listener even if the MIME type of
        // the content is not in the set of supported MIME types
        assertThat(view.performReceiveContent(samplePayloadPdf)).isNull();

        // Clearing the listener restores default behavior
        view.setOnReceiveContentListener(null, null);
        assertThat(view.performReceiveContent(samplePayloadGif)).isEqualTo(samplePayloadGif);
    }

    @Test
    public void testOnReceiveContent() {
        View view = new View(mActivity);
        String[] mimeTypes = new String[] {"image/*", "video/mp4"};
        ContentInfo samplePayloadGif = sampleUriPayload("image/gif");

        // Calling onReceiveContent() returns the payload if there's no listener
        assertThat(view.performReceiveContent(samplePayloadGif)).isEqualTo(samplePayloadGif);

        // Calling onReceiveContent() returns the payload even if there is a listener
        view.setOnReceiveContentListener(mimeTypes, mReceiver);
        when(mReceiver.onReceiveContent(any(), any())).thenReturn(null);
        assertThat(view.onReceiveContent(samplePayloadGif)).isEqualTo(samplePayloadGif);
    }

    private static ContentInfo sampleUriPayload(String ... mimeTypes) {
        ClipData clip = new ClipData(
                new ClipDescription("test", mimeTypes),
                new ClipData.Item(Uri.parse("content://example/1")));
        return new ContentInfo.Builder(clip, SOURCE_CLIPBOARD).build();
    }
}
