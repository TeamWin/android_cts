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

import static android.content.om.cts.FabricatedOverlayFacilitator.OVERLAYABLE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.content.pm.PackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class OverlayManagerTransactionTest {
    private Context mContext;
    private OverlayManager mOverlayManager;
    private String mOverlayName;
    private FabricatedOverlayFacilitator mFacilitator;

    @Rule public Expect expect = Expect.create();

    @Rule public TestName testName = new TestName();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mOverlayManager = mContext.getSystemService(OverlayManager.class);

        mOverlayName = testName.getMethodName();
        mFacilitator = new FabricatedOverlayFacilitator(mContext);
        mFacilitator.removeAllOverlays();
    }

    @After
    public void tearDown() throws Exception {
        mFacilitator.removeAllOverlays();
    }

    @Test
    public void registerFabricatedOverlay_withNull_shouldFail() {
        final OverlayManagerTransaction.Builder builder = mOverlayManager.beginTransaction();

        assertThrows(NullPointerException.class, () -> builder.registerFabricatedOverlay(null));
    }

    @Test
    public void unregisterFabricatedOverlay_withNull_shouldFail() {
        final OverlayManagerTransaction.Builder builder = mOverlayManager.beginTransaction();

        assertThrows(NullPointerException.class, () -> builder.unregisterFabricatedOverlay(null));
    }

    @Test
    public void commit_withNullOverlayable_shouldFail() {
        final OverlayManagerTransaction transaction =
                mOverlayManager
                        .beginTransaction()
                        .registerFabricatedOverlay(
                                mFacilitator.prepare("hello_overlay1", null /* overlayableName */))
                        .build();

        assertThrows(IllegalArgumentException.class, transaction::commit);
    }

    @Test
    public void commit_withEmptyOverlayable_shouldFail() {
        final OverlayManagerTransaction transaction =
                mOverlayManager
                        .beginTransaction()
                        .registerFabricatedOverlay(
                                mFacilitator.prepare("hello_overlay1", "" /* overlayableName */))
                        .build();

        assertThrows(IllegalArgumentException.class, transaction::commit);
    }

    @Test
    public void commit_withNonExistOverlayable_shouldFail() {
        final OverlayManagerTransaction transaction =
                mOverlayManager
                        .beginTransaction()
                        .registerFabricatedOverlay(
                                mFacilitator.prepare("hello_overlay1", "not_exist"))
                        .build();

        assertThrows(IOException.class, transaction::commit);
    }

    @Test
    public void commit_withValidOverlayable_shouldSucceed()
            throws PackageManager.NameNotFoundException, IOException {
        final OverlayManagerTransaction transaction =
                mOverlayManager
                        .beginTransaction()
                        .registerFabricatedOverlay(
                                mFacilitator.prepare("hello_overlay1", OVERLAYABLE_NAME))
                        .build();

        transaction.commit();

        final List<OverlayInfo> overlayInfoList =
                mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName());
        expect.that(overlayInfoList.size()).isEqualTo(1);
    }

    @Test
    public void commit_multipleRequests_shouldSucceed()
            throws PackageManager.NameNotFoundException, IOException {
        mOverlayManager
                .beginTransaction()
                .registerFabricatedOverlay(mFacilitator.prepare("hello_overlay1", OVERLAYABLE_NAME))
                .registerFabricatedOverlay(mFacilitator.prepare("hello_overlay2", OVERLAYABLE_NAME))
                .registerFabricatedOverlay(mFacilitator.prepare("hello_overlay3", OVERLAYABLE_NAME))
                .build()
                .commit();
        List<OverlayInfo> overlayInfos =
                mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName());
        final int numberOfOverlaysWhenStart = overlayInfos.size();

        final OverlayManagerTransaction.Builder builder = mOverlayManager.beginTransaction();
        for (OverlayInfo overlayInfo : overlayInfos) {
            builder.unregisterFabricatedOverlay(overlayInfo.getOverlayIdentifier());
        }
        builder.registerFabricatedOverlay(mFacilitator.prepare("hello_overlay", OVERLAYABLE_NAME))
                .build()
                .commit();

        overlayInfos = mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName());
        expect.that(numberOfOverlaysWhenStart).isEqualTo(3);
        expect.that(overlayInfos.size()).isEqualTo(1);
        List<String> overlays =
                overlayInfos.stream()
                        .map(OverlayInfo::getOverlayName)
                        .collect(Collectors.toList());
        expect.that(overlays).containsExactly("hello_overlay");
    }

    @Test
    public void commit_notSelfTargetOverlay_beginTransaction_shouldSucceed()
            throws PackageManager.NameNotFoundException, IOException {
        final FabricatedOverlay fabricatedOverlay =
                mFacilitator.prepare(mOverlayName, OVERLAYABLE_NAME, false /* isSelfTarget */);
        final OverlayManagerTransaction transaction =
                mContext.getSystemService(OverlayManager.class).beginTransaction()
                                .registerFabricatedOverlay(fabricatedOverlay).build();

        transaction.commit();

        List<OverlayInfo> overlayInfoList =
                mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName());
        assertThat(overlayInfoList.size()).isEqualTo(1);
    }

    @Test
    public void commit_selfTargetOverlay_beginTransaction_shouldSucceed()
            throws PackageManager.NameNotFoundException, IOException {
        final FabricatedOverlay fabricatedOverlay =
                mFacilitator.prepare(mOverlayName, OVERLAYABLE_NAME, true /* isSelfTarget */);
        final OverlayManagerTransaction transaction =
                mContext.getSystemService(OverlayManager.class).beginTransaction()
                        .registerFabricatedOverlay(fabricatedOverlay).build();

        transaction.commit();

        List<OverlayInfo> overlayInfoList =
                mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName());
        assertThat(overlayInfoList.size()).isEqualTo(1);
    }

    @Test
    public void commit_notSelfTargetOverlay_notBeginTransaction_shouldFail() {
        final FabricatedOverlay fabricatedOverlay =
                mFacilitator.prepare(mOverlayName, OVERLAYABLE_NAME, false /* isSelfTarget */);
        final OverlayManagerTransaction transaction = new OverlayManagerTransaction.Builder()
                .registerFabricatedOverlay(fabricatedOverlay).build();

        assertThrows(NullPointerException.class, transaction::commit);
    }

    @Test
    public void commit_selfTargetOverlay_notBeginTransaction_shouldFail() {
        final FabricatedOverlay fabricatedOverlay =
                mFacilitator.prepare(mOverlayName, OVERLAYABLE_NAME, false /* isSelfTarget */);
        final OverlayManagerTransaction transaction = new OverlayManagerTransaction.Builder()
                .registerFabricatedOverlay(fabricatedOverlay).build();

        assertThrows(NullPointerException.class, transaction::commit);
    }
}
