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

import static android.provider.Settings.Secure.AUTOFILL_USER_DATA_MAX_FIELD_CLASSIFICATION_IDS_SIZE;
import static android.provider.Settings.Secure.AUTOFILL_USER_DATA_MAX_USER_DATA_SIZE;
import static android.provider.Settings.Secure.AUTOFILL_USER_DATA_MAX_VALUE_LENGTH;
import static android.provider.Settings.Secure.AUTOFILL_USER_DATA_MIN_VALUE_LENGTH;
import static com.google.common.truth.Truth.assertThat;
import static org.testng.Assert.assertThrows;

import android.autofillservice.cts.common.SettingsStateChangerRule;
import android.content.Context;
import android.service.autofill.UserData;
import android.support.test.InstrumentationRegistry;

import com.google.common.base.Strings;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UserDataTest {

    private static final Context sContext = InstrumentationRegistry.getContext();

    @ClassRule
    public static final SettingsStateChangerRule sUserDataMaxFcSizeChanger =
            new SettingsStateChangerRule(sContext,
                    AUTOFILL_USER_DATA_MAX_FIELD_CLASSIFICATION_IDS_SIZE, "10");

    @ClassRule
    public static final SettingsStateChangerRule sUserDataMaxUserSizeChanger =
            new SettingsStateChangerRule(sContext, AUTOFILL_USER_DATA_MAX_USER_DATA_SIZE, "10");

    @ClassRule
    public static final SettingsStateChangerRule sUserDataMinValueChanger =
            new SettingsStateChangerRule(sContext, AUTOFILL_USER_DATA_MIN_VALUE_LENGTH, "5");

    @ClassRule
    public static final SettingsStateChangerRule sUserDataMaxValueChanger =
            new SettingsStateChangerRule(sContext, AUTOFILL_USER_DATA_MAX_VALUE_LENGTH, "50");


    private final String mShortValue = Strings.repeat("k", UserData.getMinValueLength() - 1);
    private final String mLongValue = "LONG VALUE, Y U NO SHORTER"
            + Strings.repeat("?", UserData.getMaxValueLength());
    private final String mId = "4815162342";
    private final String mRemoteId = "id1";
    private final String mRemoteId2 = "id2";
    private final String mValue = mShortValue + "-1";
    private final String mValue2 = mShortValue + "-2";

    private UserData.Builder mBuilder;

    @Before
    public void setFixtures() {
        mBuilder = new UserData.Builder(mId, mRemoteId, mValue);
    }

    @Test
    public void testBuilder_invalid() {
        assertThrows(NullPointerException.class,
                () -> new UserData.Builder(null, mRemoteId, mValue));
        assertThrows(IllegalArgumentException.class,
                () -> new UserData.Builder("", mRemoteId, mValue));
        assertThrows(NullPointerException.class, () -> new UserData.Builder(mId, mRemoteId, null));
        assertThrows(IllegalArgumentException.class,
                () -> new UserData.Builder(mId, mRemoteId, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new UserData.Builder(mId, mRemoteId, mShortValue));
        assertThrows(IllegalArgumentException.class,
                () -> new UserData.Builder(mId, mRemoteId, mLongValue));
        assertThrows(NullPointerException.class, () -> new UserData.Builder(mId, null, mValue));
        assertThrows(IllegalArgumentException.class, () -> new UserData.Builder(mId, "", mValue));
    }

    @Test
    public void testAdd_invalid() {
        assertThrows(NullPointerException.class, () -> mBuilder.add(mRemoteId, null));
        assertThrows(IllegalArgumentException.class, () -> mBuilder.add(mRemoteId, ""));
        assertThrows(IllegalArgumentException.class, () -> mBuilder.add(mRemoteId, mShortValue));
        assertThrows(IllegalArgumentException.class, () -> mBuilder.add(mRemoteId, mLongValue));
        assertThrows(NullPointerException.class, () -> mBuilder.add(null, mValue));
        assertThrows(IllegalArgumentException.class, () -> mBuilder.add("", mValue));
    }

    @Test
    public void testAdd_duplicatedId() {
        assertThrows(IllegalStateException.class, () -> mBuilder.add(mRemoteId, mValue2));
    }

    @Test
    public void testAdd_duplicatedValue() {
        assertThrows(IllegalStateException.class, () -> mBuilder.add(mRemoteId2, mValue));
    }

    @Test
    public void testAdd_maximumReached() {
        // Must start from 1 because first is added on builder
        for (int i = 1; i < UserData.getMaxFieldClassificationIdsSize() - 1; i++) {
            mBuilder.add("ID" + i, mShortValue.toUpperCase() + i);
        }
        assertThrows(IllegalStateException.class, () -> mBuilder.add(mRemoteId, mValue));
    }

    @Test
    public void testSetFcAlgorithm() {
        final UserData userData = mBuilder.setFieldClassificationAlgorithm("algo_mas", null)
                .build();
        assertThat(userData.getFieldClassificationAlgorithm()).isEqualTo("algo_mas");
    }

    @Test
    public void testBuild_valid() {
        final UserData userData = mBuilder.build();
        assertThat(userData).isNotNull();
        assertThat(userData.getId()).isEqualTo(mId);
        assertThat(userData.getFieldClassificationAlgorithm()).isNull();
    }

    @Test
    public void testNoMoreInteractionsAfterBuild() {
        testBuild_valid();

        assertThrows(IllegalStateException.class, () -> mBuilder.add(mRemoteId2, mValue));
        assertThrows(IllegalStateException.class, () -> mBuilder.build());
    }
}
