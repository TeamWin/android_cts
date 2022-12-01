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

package android.content.om.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.cts.R;
import android.content.om.FabricatedOverlay;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import android.util.TypedValue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FabricatedOverlayTest {
    private Context mContext;

    @Rule public TestName mTestName = new TestName();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void newBuilder_withGoodOverlayName_shouldSucceed() {
        new FabricatedOverlay.Builder("I_am_good_name", mContext.getPackageName());
    }

    @Test
    public void newBuilder_withNullOverlayName_shouldFail() {
        assertThrows(
                "The build can't accept bad name",
                IllegalArgumentException.class,
                () -> new FabricatedOverlay.Builder(null /* name */, mContext.getPackageName()));
    }

    @Test
    public void newBuilder_withEmptyOverlayName_shouldFail() {
        assertThrows(
                "The build can't accept bad name",
                IllegalArgumentException.class,
                () -> new FabricatedOverlay.Builder("" /* name */, mContext.getPackageName()));
    }

    @Test
    public void newBuilder_withBadOverlayName_shouldFail() {
        assertThrows(
                "The build can't accept bad name",
                IllegalArgumentException.class,
                () ->
                        new FabricatedOverlay.Builder(
                                "../../etc/password", mContext.getPackageName()));
    }

    private FabricatedOverlay.Builder createBuilder() {
        return new FabricatedOverlay.Builder(mTestName.getMethodName(), mContext.getPackageName());
    }

    @Test
    public void setResourceValue_forResourceName_withoutSlash_shouldBeInvalid() {
        final FabricatedOverlay.Builder builder = createBuilder();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.setResourceValue(
                                "demo", TypedValue.TYPE_INT_COLOR_ARGB8, Color.WHITE));
    }

    @Test
    public void setResourceValue_forResourceName_colonAfterSlash_shouldBeInvalid() {
        final FabricatedOverlay.Builder builder = createBuilder();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.setResourceValue(
                                "color/" + mContext.getPackageName() + " :demo",
                                TypedValue.TYPE_INT_COLOR_ARGB8,
                                Color.WHITE));
    }

    @Test
    public void setResourceValue_forResourceName_invalidResourceType_shouldFail() {
        final FabricatedOverlay.Builder builder = createBuilder();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.setResourceValue(
                                "s/demo", TypedValue.TYPE_INT_COLOR_ARGB8, Color.WHITE));
    }

    @Test
    public void setResourceValue_forResourceName_colonBeforeSlash_shouldBeValid() {
        final FabricatedOverlay.Builder builder = createBuilder();

        assertThat(
                        builder.setResourceValue(
                                mContext.getPackageName() + ":color/demo",
                                TypedValue.TYPE_INT_COLOR_ARGB8,
                                Color.WHITE))
                .isNotNull();
    }

    @Test
    public void setResourceValue_forIntType_colorAsStringType_shouldFail() {
        final FabricatedOverlay.Builder builder = createBuilder();

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.setResourceValue("color/demo", TypedValue.TYPE_STRING, Color.WHITE));
    }

    @Test
    public void setResourceValue_forIntType_forColorType_shouldBeValid() {
        final FabricatedOverlay.Builder builder = createBuilder();

        builder.setResourceValue("color/demo", TypedValue.TYPE_INT_COLOR_ARGB4, Color.WHITE);

        assertThat(builder.build()).isNotNull();
    }

    @Test
    public void setResourceValue_forIntType_withConfigurations_shouldBeValid() {
        final FabricatedOverlay.Builder builder = createBuilder();

        builder.setResourceValue(
                "color/demo", TypedValue.TYPE_INT_COLOR_ARGB8, Color.WHITE, "port");
        builder.setResourceValue(
                "color/demo", TypedValue.TYPE_INT_COLOR_ARGB8, Color.WHITE, "land");

        assertThat(builder.build()).isNotNull();
    }

    @Test
    public void setResourceValue_forIntType_forNotExistColor_shouldBeValid() {
        final FabricatedOverlay.Builder builder = createBuilder();

        builder.setResourceValue(
                "color/want ../../etc/password", TypedValue.TYPE_INT_COLOR_ARGB8, Color.WHITE);

        assertThat(builder.build()).isNotNull();
    }

    @Test
    public void setResourceValue_forStringType_forNotExistString_shouldBeValid() {
        final FabricatedOverlay.Builder builder = createBuilder();

        builder.setResourceValue(
                "string/want ../../etc/password",
                TypedValue.TYPE_STRING,
                "Try to replace non-exist string");

        assertThat(builder.build()).isNotNull();
    }

    @Test
    public void setResourceValue_forStringType_withConfigurations_shouldBeValid() {
        final FabricatedOverlay.Builder builder = createBuilder();

        builder.setResourceValue(
                "string/demo", TypedValue.TYPE_STRING, "I am string for port", "port");
        builder.setResourceValue(
                "string/demo", TypedValue.TYPE_STRING, "I am string for land", "land");

        assertThat(builder.build()).isNotNull();
    }

    @Test
    public void setResourceValue_forStringType_nullString_shouldFail() {
        final FabricatedOverlay.Builder builder = createBuilder();

        assertThrows(
                NullPointerException.class,
                () -> builder.setResourceValue("string/demo", TypedValue.TYPE_STRING, null));
    }

    @Test
    public void setResourceValue_forStringType_stringAsColorType_shouldFail() {
        final FabricatedOverlay.Builder builder = createBuilder();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.setResourceValue(
                                "string/demo", TypedValue.TYPE_INT_COLOR_ARGB8, "Hello"));
    }

    @Test
    public void setResourceValue_forParcelFileDescriptor_withNull_shouldFail() {
        final FabricatedOverlay.Builder builder = createBuilder();

        assertThrows(
                NullPointerException.class,
                () ->
                        builder.setResourceValue(
                                "layout/demo", null /* value */, null /* configuration */));
    }

    @Test
    public void build_noEntry_shouldSucceed() {
        final FabricatedOverlay.Builder builder = createBuilder();

        assertThat(builder.build()).isNotNull();
    }

    @Test
    public void build_multipleEntries_shouldSucceed() {
        final FabricatedOverlay.Builder builder = createBuilder();
        final ParcelFileDescriptor parcelFileDescriptor =
                mContext.getResources().openRawResourceFd(R.raw.text).getParcelFileDescriptor();

        builder.setResourceValue("color/demo1", TypedValue.TYPE_INT_COLOR_ARGB4, Color.WHITE);
        builder.setResourceValue("color/demo2", TypedValue.TYPE_INT_COLOR_ARGB8, Color.WHITE);
        builder.setResourceValue("string/demo1", TypedValue.TYPE_STRING, "white");
        builder.setResourceValue("string/demo2", TypedValue.TYPE_STRING, "black");
        builder.setResourceValue("raw/demo", parcelFileDescriptor, null);

        assertThat(builder.build()).isNotNull();
    }
}
