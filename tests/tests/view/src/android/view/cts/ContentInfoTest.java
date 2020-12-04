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

import android.content.ClipData;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.view.ContentInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link ContentInfo}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContentInfoTest {

    @Test
    public void testPartition_multipleItems() throws Exception {
        Uri sampleUri = Uri.parse("content://com.example/path");
        ClipData clip = ClipData.newPlainText("", "Hello");
        clip.addItem(new ClipData.Item("Hi", "<b>Salut</b>"));
        clip.addItem(new ClipData.Item(sampleUri));
        ContentInfo payload = new ContentInfo.Builder(clip, SOURCE_CLIPBOARD)
                .setFlags(ContentInfo.FLAG_CONVERT_TO_PLAIN_TEXT)
                .setLinkUri(Uri.parse("http://example.com"))
                .setExtras(new Bundle())
                .build();

        // Test splitting when some items match and some don't.
        Pair<ContentInfo, ContentInfo> split;
        split = payload.partition(item -> item.getUri() != null);
        assertThat(split.first.getClip().getItemCount()).isEqualTo(1);
        assertThat(split.second.getClip().getItemCount()).isEqualTo(2);
        assertThat(split.first.getClip().getItemAt(0).getUri()).isEqualTo(sampleUri);
        assertThat(split.first.getClip().getDescription()).isNotSameInstanceAs(
                payload.getClip().getDescription());
        assertThat(split.second.getClip().getDescription()).isNotSameInstanceAs(
                payload.getClip().getDescription());
        assertThat(split.first.getSource()).isEqualTo(SOURCE_CLIPBOARD);
        assertThat(split.first.getLinkUri()).isNotNull();
        assertThat(split.first.getExtras()).isNotNull();
        assertThat(split.second.getSource()).isEqualTo(SOURCE_CLIPBOARD);
        assertThat(split.second.getLinkUri()).isNotNull();
        assertThat(split.second.getExtras()).isNotNull();

        // Test splitting when none of the items match.
        split = payload.partition(item -> false);
        assertThat(split.first).isNull();
        assertThat(split.second).isSameInstanceAs(payload);

        // Test splitting when all of the items match.
        split = payload.partition(item -> true);
        assertThat(split.first).isSameInstanceAs(payload);
        assertThat(split.second).isNull();
    }

    @Test
    public void testPartition_singleItem() throws Exception {
        ClipData clip = ClipData.newPlainText("", "Hello");
        ContentInfo payload = new ContentInfo.Builder(clip, SOURCE_CLIPBOARD)
                .setFlags(ContentInfo.FLAG_CONVERT_TO_PLAIN_TEXT)
                .setLinkUri(Uri.parse("http://example.com"))
                .setExtras(new Bundle())
                .build();

        Pair<ContentInfo, ContentInfo> split;
        split = payload.partition(item -> false);
        assertThat(split.first).isNull();
        assertThat(split.second).isSameInstanceAs(payload);

        split = payload.partition(item -> true);
        assertThat(split.first).isSameInstanceAs(payload);
        assertThat(split.second).isNull();
    }
}
