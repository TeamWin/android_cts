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

package android.provider.cts.simphonebook;

import static android.provider.SimPhonebookContract.ElementaryFiles.EF_ADN;

import static com.android.internal.telephony.testing.CursorSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.SimPhonebookContract.ElementaryFiles;
import android.provider.SimPhonebookContract.SimRecords;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Objects;

@RunWith(AndroidJUnit4.class)
public class SimPhonebookContract_ElementaryFilesTest {

    // The minimum value for the ElementaryFiles.NAME_MAX_LENGTH column.
    private static final int NAME_MAX_LENGTH_MINIMUM = 11;
    // The minimum value for the ElementaryFiles.PHONE_NUMBER_MAX_LENGTH column.
    private static final int PHONE_NUMBER_MAX_LENGTH_MINIMUM = 20;

    private ContentResolver mResolver;
    private SubscriptionManager mSubscriptionManager;
    private int mValidSubscriptionId;

    @Rule
    public final TestRule mRule = RuleChain
            .outerRule(new RequiredFeatureRule(PackageManager.FEATURE_TELEPHONY))
            .around(new SimPhonebookRequirementsRule())
            .around(new SimCleanupRule(ElementaryFiles.EF_ADN));

    @Before
    public void setUp() {
        mResolver = ApplicationProvider.getApplicationContext().getContentResolver();
        mSubscriptionManager = ApplicationProvider.getApplicationContext().getSystemService(
                SubscriptionManager.class);
        mValidSubscriptionId = SubscriptionManager.getDefaultSubscriptionId();
    }

    @Test
    public void query_includesRowForEachElementaryFileOnEachSim() throws Exception {
        List<SubscriptionInfo> subscriptionInfos = SystemUtil.callWithShellPermissionIdentity(
                () -> mSubscriptionManager.getActiveSubscriptionInfoList(),
                Manifest.permission.READ_PHONE_STATE);
        try (Cursor cursor = query(ElementaryFiles.CONTENT_URI,
                new String[]{
                        ElementaryFiles.SLOT_INDEX,
                        ElementaryFiles.SUBSCRIPTION_ID,
                        ElementaryFiles.EF_TYPE,
                        ElementaryFiles.MAX_RECORDS,
                        ElementaryFiles.NAME_MAX_LENGTH,
                        ElementaryFiles.PHONE_NUMBER_MAX_LENGTH
                })) {

            assertThat(cursor).hasCount(subscriptionInfos.size() * 3);
            cursor.moveToPosition(-1);
            for (SubscriptionInfo info : subscriptionInfos) {
                for (int efType : new int[]{
                        ElementaryFiles.EF_ADN, ElementaryFiles.EF_FDN, ElementaryFiles.EF_SDN
                }) {
                    assertThat(cursor.moveToNext()).isTrue();
                    assertThat(cursor)
                            .hasRowValue(ElementaryFiles.SLOT_INDEX, info.getSimSlotIndex())
                            .hasRowValue(ElementaryFiles.SUBSCRIPTION_ID, info.getSubscriptionId())
                            .hasRowValue(ElementaryFiles.EF_TYPE, efType);
                    assertThat(cursor).intField(ElementaryFiles.MAX_RECORDS).isAtLeast(1);
                    assertThat(cursor).intField(ElementaryFiles.NAME_MAX_LENGTH)
                            .isAtLeast(NAME_MAX_LENGTH_MINIMUM);
                    assertThat(cursor).intField(ElementaryFiles.PHONE_NUMBER_MAX_LENGTH)
                            .isAtLeast(PHONE_NUMBER_MAX_LENGTH_MINIMUM);
                }
            }
        }
    }

    @Test
    public void query_nonEmptySim_countIsCorrect() {
        String[] projection = {
                ElementaryFiles.SUBSCRIPTION_ID,
                ElementaryFiles.EF_TYPE,
                ElementaryFiles.RECORD_COUNT
        };
        ContentValues values = new ContentValues();
        values.put(SimRecords.PHONE_NUMBER, "5550101");
        Uri itemUri = mResolver.insert(SimRecords.getContentUri(mValidSubscriptionId, EF_ADN),
                values);
        mResolver.insert(SimRecords.getContentUri(mValidSubscriptionId, EF_ADN), values);
        mResolver.insert(SimRecords.getContentUri(mValidSubscriptionId, EF_ADN), values);

        try (Cursor adn = query(
                ElementaryFiles.getItemUri(mValidSubscriptionId, ElementaryFiles.EF_ADN),
                projection)) {
            assertThat(adn).hasSingleRow(mValidSubscriptionId, ElementaryFiles.EF_ADN, 3);
        }

        mResolver.delete(itemUri, null);

        try (Cursor adn = query(
                ElementaryFiles.getItemUri(mValidSubscriptionId, ElementaryFiles.EF_ADN),
                projection)) {
            assertThat(adn).hasSingleRow(mValidSubscriptionId, ElementaryFiles.EF_ADN, 2);
        }
    }

    @Test
    public void query_itemUri_returnsCorrectRow() {
        String[] projection = {
                ElementaryFiles.SUBSCRIPTION_ID,
                ElementaryFiles.EF_TYPE
        };
        try (Cursor adn = query(
                ElementaryFiles.getItemUri(mValidSubscriptionId, ElementaryFiles.EF_ADN),
                projection);
             Cursor fdn = query(
                     ElementaryFiles.getItemUri(mValidSubscriptionId, ElementaryFiles.EF_FDN),
                     projection);
             Cursor sdn = query(
                     ElementaryFiles.getItemUri(mValidSubscriptionId, ElementaryFiles.EF_SDN),
                     projection)
        ) {
            assertThat(adn).hasSingleRow(mValidSubscriptionId, ElementaryFiles.EF_ADN);
            assertThat(fdn).hasSingleRow(mValidSubscriptionId, ElementaryFiles.EF_FDN);
            assertThat(sdn).hasSingleRow(mValidSubscriptionId, ElementaryFiles.EF_SDN);
        }
    }

    @NonNull
    private Cursor query(Uri uri, String[] projection) {
        return Objects.requireNonNull(mResolver.query(uri, projection, null, null));
    }
}
