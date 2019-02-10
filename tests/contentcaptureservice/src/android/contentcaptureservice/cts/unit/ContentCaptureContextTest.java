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

package android.contentcaptureservice.cts.unit;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;
import android.view.contentcapture.ContentCaptureContext;
import android.view.contentcapture.ContentCaptureContext.Builder;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@AppModeFull(reason = "unit test")
@RunWith(JUnit4.class)
public class ContentCaptureContextTest {

    private static final Uri URI = Uri.parse("file:/dev/null");
    private static final String ACTION = "Jackson";

    private final ContentCaptureContext.Builder mBuilder = new ContentCaptureContext.Builder();

    private final Bundle mExtras = new Bundle();

    @Before
    public void setExtras() {
        mExtras.putString("DUDE", "SWEET");
    }

    @Test
    public void testBuilder_invalidUri() {
        assertThrows(NullPointerException.class, () -> mBuilder.setUri(null));
    }

    @Test
    public void testBuilder_invalidAction() {
        assertThrows(NullPointerException.class, () -> mBuilder.setAction(null));
    }

    @Test
    public void testBuilder_invalidExtras() {
        assertThrows(NullPointerException.class, () -> mBuilder.setExtras(null));
    }

    @Test
    public void testAfterBuild_setExtras() {
        assertThat(mBuilder.setUri(URI).build()).isNotNull();
        assertThrows(IllegalStateException.class, () -> mBuilder.setExtras(mExtras));
    }

    @Test
    public void testAfterBuild_setAction() {
        assertThat(mBuilder.setUri(URI).build()).isNotNull();
        assertThrows(IllegalStateException.class, () -> mBuilder.setAction(ACTION));
    }

    @Test
    public void testAfterBuild_setUri() {
        assertThat(mBuilder.setExtras(mExtras).build()).isNotNull();
        assertThrows(IllegalStateException.class, () -> mBuilder.setUri(URI));
    }

    @Test
    public void testAfterBuild_build() {
        assertThat(mBuilder.setExtras(mExtras).build()).isNotNull();
        assertThrows(IllegalStateException.class, () -> mBuilder.build());
    }

    @Test
    public void testBuild_empty() {
        assertThrows(IllegalStateException.class, () -> mBuilder.build());
    }

    @Test
    public void testSetGetUri() {
        final Builder builder = mBuilder.setUri(URI);
        assertThat(builder).isSameAs(mBuilder);
        final ContentCaptureContext context = builder.build();
        assertThat(context).isNotNull();
        assertThat(context.getUri()).isEqualTo(URI);
    }

    @Test
    public void testSetGetAction() {
        final Builder builder = mBuilder.setAction(ACTION);
        assertThat(builder).isSameAs(mBuilder);
        final ContentCaptureContext context = builder.build();
        assertThat(context).isNotNull();
        assertThat(context.getAction()).isEqualTo(ACTION);
    }

    @Test
    public void testGetSetBundle() {
        final Builder builder = mBuilder.setExtras(mExtras);
        assertThat(builder).isSameAs(mBuilder);
        final ContentCaptureContext context = builder.build();
        assertThat(context).isNotNull();
        assertExtras(context.getExtras());
    }

    @Test
    public void testParcel() {
        final Builder builder = mBuilder
                .setUri(URI)
                .setAction(ACTION)
                .setExtras(mExtras);
        assertThat(builder).isSameAs(mBuilder);
        final ContentCaptureContext context = builder.build();
        assertEverything(context);

        final ContentCaptureContext clone = cloneThroughParcel(context);
        assertEverything(clone);
    }

    private void assertEverything(@NonNull ContentCaptureContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getUri()).isEqualTo(URI);
        assertThat(context.getAction()).isEqualTo(ACTION);
        assertExtras(context.getExtras());
    }

    private void assertExtras(@NonNull Bundle bundle) {
        assertThat(bundle).isNotNull();
        assertThat(bundle.keySet()).hasSize(1);
        assertThat(bundle.getString("DUDE")).isEqualTo("SWEET");
    }

    private ContentCaptureContext cloneThroughParcel(@NonNull ContentCaptureContext context) {
        final Parcel parcel = Parcel.obtain();

        try {
            // Write to parcel
            parcel.setDataPosition(0); // Sanity / paranoid check
            context.writeToParcel(parcel, 0);

            // Read from parcel
            parcel.setDataPosition(0);
            final ContentCaptureContext clone = ContentCaptureContext.CREATOR
                    .createFromParcel(parcel);
            assertThat(clone).isNotNull();
            return clone;
        } finally {
            parcel.recycle();
        }
    }

}
