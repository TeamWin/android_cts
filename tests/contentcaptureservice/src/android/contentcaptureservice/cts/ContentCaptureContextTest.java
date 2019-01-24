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

package android.contentcaptureservice.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.view.contentcapture.ContentCaptureContext;
import android.view.contentcapture.ContentCaptureContext.Builder;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@AppModeFull // Unit test
public class ContentCaptureContextTest {

    private static final Uri URI = Uri.parse("file:/dev/null");

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
    public void testBuilder_invalidExtras() {
        assertThrows(NullPointerException.class, () -> mBuilder.setExtras(null));
    }

    @Test
    public void testAfterBuild_setExtras() {
        assertThat(mBuilder.setUri(URI).build()).isNotNull();
        assertThrows(IllegalStateException.class, () -> mBuilder.setExtras(mExtras));
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
    public void testGetSetBundle() {
        final Builder builder = mBuilder.setExtras(mExtras);
        assertThat(builder).isSameAs(mBuilder);
        final ContentCaptureContext context = builder.build();
        assertThat(context).isNotNull();
        assertExtras(context.getExtras());
    }

    private void assertExtras(@NonNull Bundle bundle) {
        assertThat(bundle).isNotNull();
        assertThat(bundle.keySet()).hasSize(1);
        assertThat(bundle.getString("DUDE")).isEqualTo("SWEET");
    }
}
