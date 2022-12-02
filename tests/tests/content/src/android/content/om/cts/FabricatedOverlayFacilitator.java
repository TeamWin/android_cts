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

import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import android.util.TypedValue;

import java.util.List;

/** This class help tests to fill less information. */
public class FabricatedOverlayFacilitator {
    public static final String TARGET_COLOR_RES = "color/target_overlayable_color1";
    public static final String TARGET_STRING_RES = "string/target_overlayable_string1";
    public static final String OVERLAYABLE_NAME = "SelfTargetingOverlayable";

    private final Context mContext;
    private FabricatedOverlay.Builder mBuilder;

    public FabricatedOverlayFacilitator(Context context) {
        mContext = context;
        final String packageName = mContext.getPackageName();
        mBuilder =
                new FabricatedOverlay.Builder(packageName, "FabricatedOverlayHelper", packageName)
                        .setTargetOverlayable(OVERLAYABLE_NAME);
    }

    public void removeAllOverlays() throws Exception {
        final OverlayManager overlayManager = mContext.getSystemService(OverlayManager.class);
        final OverlayManagerTransaction.Builder transactionBuilder =
                overlayManager.beginTransaction();
        final List<OverlayInfo> overlayInfoList =
                overlayManager.getOverlayInfosForTarget(mContext.getPackageName());
        for (OverlayInfo overlayInfo : overlayInfoList) {
            transactionBuilder.unregisterFabricatedOverlay(overlayInfo.getOverlayIdentifier());
        }
        transactionBuilder.build().commit();
    }

    public FabricatedOverlay prepare(String overlayName, String overlayableName) {
        return prepare(overlayName, overlayableName, false);
    }

    public FabricatedOverlay prepare(String overlayName, String overlayableName,
            boolean isSelfTarget) {
        final String packageName = mContext.getPackageName();
        mBuilder = isSelfTarget
                ? new FabricatedOverlay.Builder(overlayName, packageName)
                : new FabricatedOverlay.Builder(packageName, overlayName, packageName);
        setOverlayable(overlayableName);
        set(TARGET_COLOR_RES, Color.WHITE);
        set(TARGET_STRING_RES, "HELLO");
        return get();
    }

    public FabricatedOverlayFacilitator setOverlayable(String overlayableName) {
        mBuilder.setTargetOverlayable(overlayableName);
        return this;
    }

    public FabricatedOverlayFacilitator set(String resourceName, int value) {
        mBuilder.setResourceValue(resourceName, TypedValue.TYPE_INT_COLOR_ARGB8, value);
        return this;
    }

    public FabricatedOverlayFacilitator set(String resourceName, String value) {
        mBuilder.setResourceValue(resourceName, TypedValue.TYPE_STRING, value);
        return this;
    }

    public FabricatedOverlayFacilitator set(String resourceName,
            ParcelFileDescriptor parcelFileDescriptor) {
        mBuilder.setResourceValue(resourceName, parcelFileDescriptor, null /* configuration */);
        return this;
    }

    public FabricatedOverlay get() {
        return mBuilder.build();
    }
}
