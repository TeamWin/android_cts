/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package android.slice.cts;

import static java.util.stream.Collectors.toList;

import android.app.PendingIntent;
import android.app.slice.SliceSpec;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.app.slice.Slice;
import android.app.slice.Slice.Builder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SliceProvider extends android.app.slice.SliceProvider {

    static final String[] PATHS = new String[]{
            "/set_flag",
            "/subslice",
            "/text",
            "/icon",
            "/action",
            "/int",
            "/timestamp",
            "/hints",
            "/bundle"
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Collection<Uri> onGetSliceDescendants(Uri uri) {
        if (uri.getPath().equals("/")) {
            Uri.Builder builder = new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority("android.slice.cts");
            return Arrays.asList(PATHS).stream().map(s ->
                    builder.path(s).build()).collect(toList());
        }
        return Collections.emptyList();
    }

    @Override
    public Slice onBindSlice(Uri sliceUri, List<SliceSpec> specs) {
        switch (sliceUri.getPath()) {
            case "/set_flag":
                SliceTest.sFlag = true;
                break;
            case "/subslice":
                Builder b = new Builder(sliceUri);
                return b.addSubSlice(new Slice.Builder(b).build(), "subslice").build();
            case "/text":
                return new Slice.Builder(sliceUri).addText("Expected text", "text").build();
            case "/icon":
                return new Slice.Builder(sliceUri).addIcon(
                        Icon.createWithResource(getContext(), R.drawable.size_48x48), "icon").build();
            case "/action":
                Builder builder = new Builder(sliceUri);
                Slice subSlice = new Slice.Builder(builder).build();
                PendingIntent broadcast = PendingIntent.getBroadcast(getContext(), 0,
                        new Intent(getContext().getPackageName() + ".action"), 0);
                return builder.addAction(broadcast, subSlice, "action").build();
            case "/int":
                return new Slice.Builder(sliceUri).addInt(0xff121212, "int").build();
            case "/timestamp":
                return new Slice.Builder(sliceUri).addTimestamp(43, "timestamp").build();
            case "/hints":
                return new Slice.Builder(sliceUri)
                        .addHints(Slice.HINT_LIST)
                        .addText("Text", null, Slice.HINT_TITLE)
                        .addIcon(Icon.createWithResource(getContext(), R.drawable.size_48x48),
                                null, Slice.HINT_NO_TINT, Slice.HINT_LARGE)
                        .build();
            case "/bundle":
                Bundle b1 = new Bundle();
                b1.putParcelable("a", new TestParcel());
                return new Slice.Builder(sliceUri).addBundle(b1, "bundle").build();
        }
        return new Slice.Builder(sliceUri).build();
    }

    public static class TestParcel implements Parcelable {

        private final int mValue;

        public TestParcel() {
            mValue = 42;
        }

        protected TestParcel(Parcel in) {
            mValue = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mValue);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            try {
                TestParcel p = (TestParcel) obj;
                return p.mValue == mValue;
            } catch (ClassCastException e) {
                return false;
            }
        }

        public static final Creator<TestParcel> CREATOR = new Creator<TestParcel>() {
            @Override
            public TestParcel createFromParcel(Parcel in) {
                return new TestParcel(in);
            }

            @Override
            public TestParcel[] newArray(int size) {
                return new TestParcel[size];
            }
        };
    }
}
