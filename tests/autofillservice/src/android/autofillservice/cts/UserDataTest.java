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

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.service.autofill.InternalScorer;
import android.service.autofill.Scorer;
import android.service.autofill.UserData;

import com.google.common.base.Strings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UserDataTest {

    private final String mShortValue = Strings.repeat("k", UserData.getMinValueLength() - 1);
    private final String mLongValue = "LONG VALUE, Y U NO SHORTER"
            + Strings.repeat("?", UserData.getMaxValueLength());
    private final String mRemoteId = "id1";
    private final String mRemoteId2 = "id2";
    private final String mValue = mShortValue + "-1";
    private final String mValue2 = mShortValue + "-2";

    @Mock private InternalScorer mValidScorer;
    @Mock private Scorer mInvalidScorer;
    private UserData.Builder mBuilder;

    @Before
    public void setFixtures() {
        mBuilder = new UserData.Builder(mValidScorer, mRemoteId, mValue);
    }

    @Test
    public void testBuilder_invalid() {
        assertThrows(IllegalArgumentException.class,
                () -> new UserData.Builder(null, mRemoteId, mValue));
        assertThrows(IllegalArgumentException.class,
                () -> new UserData.Builder(mInvalidScorer, mRemoteId, mValue));
        assertThrows(NullPointerException.class,
                () -> new UserData.Builder(mValidScorer, mRemoteId, null));
        assertThrows(IllegalArgumentException.class,
                () -> new UserData.Builder(mValidScorer, mRemoteId, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new UserData.Builder(mValidScorer, mRemoteId, mShortValue));
        assertThrows(IllegalArgumentException.class,
                () -> new UserData.Builder(mValidScorer, mRemoteId, mLongValue));
        assertThrows(NullPointerException.class,
                () -> new UserData.Builder(mValidScorer, null, mValue));
        assertThrows(IllegalArgumentException.class,
                () -> new UserData.Builder(mValidScorer, "", mValue));
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
    public void testBuild_valid() {
        assertThat(mBuilder.build()).isNotNull();
    }

    @Test
    public void testNoMoreInteractionsAfterBuild() {
        testBuild_valid();

        assertThrows(IllegalStateException.class, () -> mBuilder.add(mRemoteId2, mValue));
        assertThrows(IllegalStateException.class, () -> mBuilder.build());
    }
}
