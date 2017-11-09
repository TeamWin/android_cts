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

package android.autofillservice.cts;

import static android.service.autofill.FillResponse.FLAG_DISABLE_ACTIVITY_ONLY;
import static android.service.autofill.FillResponse.FLAG_TRACK_CONTEXT_COMMITED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertThrows;

import android.content.IntentSender;
import android.os.Bundle;
import android.service.autofill.Dataset;
import android.service.autofill.FieldsDetection;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveInfo;
import android.support.test.runner.AndroidJUnit4;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FillResponseTest {

    private final RemoteViews mPresentation = mock(RemoteViews.class);
    private final IntentSender mIntentSender = mock(IntentSender.class);
    private final FillResponse.Builder mBuilder = new FillResponse.Builder();
    private final AutofillId[] mIds = new AutofillId[] { new AutofillId(42) };
    private final SaveInfo mSaveInfo = new SaveInfo.Builder(0, mIds).build();
    private final Bundle mClientState = new Bundle();
    private final Dataset mDataset = new Dataset.Builder()
            .setValue(new AutofillId(42), AutofillValue.forText("forty-two"))
            .build();
    private final FieldsDetection mFieldsDetection =
            new FieldsDetection(new AutofillId(42), "666", "108");
    private final long mDisableDuration = 666;

    @Test
    public void testBuilder_setAuthentication_invalid() {
        // null ids
        assertThrows(IllegalArgumentException.class,
                () -> mBuilder.setAuthentication(null, mIntentSender, mPresentation));
        // empty ids
        assertThrows(IllegalArgumentException.class,
                () -> mBuilder.setAuthentication(new AutofillId[] {}, mIntentSender,
                        mPresentation));
        // null intent sender
        assertThrows(IllegalArgumentException.class,
                () -> mBuilder.setAuthentication(mIds, null, mPresentation));
        // null presentation
        assertThrows(IllegalArgumentException.class,
                () -> mBuilder.setAuthentication(mIds, mIntentSender, null));
    }

    @Test
    public void testBuilder_setAuthentication_valid() {
        new FillResponse.Builder().setAuthentication(mIds, null, null);
        new FillResponse.Builder().setAuthentication(mIds, mIntentSender, mPresentation);
    }

    @Test
    public void testBuilder_setFlag_invalid() {
        assertThrows(IllegalArgumentException.class, () -> mBuilder.setFlags(-1));
    }

    @Test
    public void testBuilder_setFlag_valid() {
        mBuilder.setFlags(0);
        mBuilder.setFlags(FLAG_TRACK_CONTEXT_COMMITED);
        mBuilder.setFlags(FLAG_DISABLE_ACTIVITY_ONLY);
    }

    @Test
    public void testBuilder_disableAutofill_invalid() {
        assertThrows(IllegalArgumentException.class, () -> mBuilder.disableAutofill(0));
        assertThrows(IllegalArgumentException.class, () -> mBuilder.disableAutofill(-1));
    }

    @Test
    public void testBuilder_disableAutofill_valid() {
        mBuilder.disableAutofill(mDisableDuration);
        mBuilder.disableAutofill(Long.MAX_VALUE);
    }

    @Test
    public void testBuilder_disableAutofill_mustBeTheOnlyMethodCalled() {
        // No method can be called after disableAutofill()
        mBuilder.disableAutofill(mDisableDuration);
        assertThrows(IllegalStateException.class, () -> mBuilder.setSaveInfo(mSaveInfo));
        assertThrows(IllegalStateException.class, () -> mBuilder.addDataset(mDataset));
        assertThrows(IllegalStateException.class,
                () -> mBuilder.setAuthentication(mIds, mIntentSender, mPresentation));
        assertThrows(IllegalStateException.class,
                () -> mBuilder.setFieldsDetection(mFieldsDetection));

        // And vice-versa...
        final FillResponse.Builder builder1 = new FillResponse.Builder().setSaveInfo(mSaveInfo);
        assertThrows(IllegalStateException.class, () -> builder1.disableAutofill(mDisableDuration));
        final FillResponse.Builder builder2 = new FillResponse.Builder().addDataset(mDataset);
        assertThrows(IllegalStateException.class, () -> builder2.disableAutofill(mDisableDuration));
        final FillResponse.Builder builder3 =
                new FillResponse.Builder().setAuthentication(mIds, mIntentSender, mPresentation);
        assertThrows(IllegalStateException.class, () -> builder3.disableAutofill(mDisableDuration));
        final FillResponse.Builder builder4 =
                new FillResponse.Builder().setFieldsDetection(mFieldsDetection);
        assertThrows(IllegalStateException.class, () -> builder4.disableAutofill(mDisableDuration));
    }

    @Test
    public void testBuilder_setFieldsDetection_invalid() {
        assertThrows(NullPointerException.class, () -> mBuilder.setFieldsDetection(null));
    }

    @Test
    public void testBuilder_setFieldsDetection_valid() {
        mBuilder.setFieldsDetection(mFieldsDetection);
    }

    @Test
    public void testBuilder_build_invalid() {
        assertThrows(IllegalStateException.class, () -> mBuilder.build());
    }

    @Test
    public void testBuilder_build_valid() {
        // authentication only
        assertThat(new FillResponse.Builder().setAuthentication(mIds, mIntentSender, mPresentation)
                .build()).isNotNull();
        // save info only
        assertThat(new FillResponse.Builder().setSaveInfo(mSaveInfo).build()).isNotNull();
        // dataset only
        assertThat(new FillResponse.Builder().addDataset(mDataset).build()).isNotNull();
        // disable autofill only
        assertThat(new FillResponse.Builder().disableAutofill(mDisableDuration).build())
                .isNotNull();
        // fill detection only
        assertThat(new FillResponse.Builder().setFieldsDetection(mFieldsDetection).build())
                .isNotNull();
    }

    @Test
    public void testNoMoreInteractionsAfterBuild() {
        assertThat(mBuilder.setAuthentication(mIds, mIntentSender, mPresentation).build())
                .isNotNull();

        assertThrows(IllegalStateException.class, () -> mBuilder.build());
        assertThrows(IllegalStateException.class,
                () -> mBuilder.setAuthentication(mIds, mIntentSender, mPresentation).build());
        assertThrows(IllegalStateException.class, () -> mBuilder.setIgnoredIds(mIds));
        assertThrows(IllegalStateException.class, () -> mBuilder.addDataset(null));
        assertThrows(IllegalStateException.class, () -> mBuilder.setSaveInfo(mSaveInfo));
        assertThrows(IllegalStateException.class, () -> mBuilder.setClientState(mClientState));
        assertThrows(IllegalStateException.class, () -> mBuilder.setFlags(0));
        assertThrows(IllegalStateException.class,
                () -> mBuilder.setFieldsDetection(mFieldsDetection));
    }
}
