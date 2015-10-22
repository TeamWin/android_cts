/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.deviceinfo;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import java.lang.Integer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.android.compatibility.common.deviceinfo.DeviceInfo;

/**
 * Generic device info collector.
 */
public class GenericDeviceInfo extends DeviceInfo {

    public static final String DEVICE_INFO_GENERIC = "DEVICE_INFO_GENERIC_%s";
    public static final String BUILD_ID = "build_id";
    public static final String BUILD_PRODUCT = "build_product";
    public static final String BUILD_DEVICE = "build_device";
    public static final String BUILD_BOARD = "build_board";
    public static final String BUILD_MANUFACTURER = "build_manufacturer";
    public static final String BUILD_BRAND = "build_brand";
    public static final String BUILD_MODEL = "build_model";
    public static final String BUILD_TYPE = "build_type";
    public static final String BUILD_FINGERPRINT = "build_fingerprint";
    public static final String BUILD_ABI = "build_abi";
    public static final String BUILD_ABI2 = "build_abi2";
    public static final String BUILD_ABIS = "build_abis";
    public static final String BUILD_ABIS_32 = "build_abis_32";
    public static final String BUILD_ABIS_64 = "build_abis_64";
    public static final String BUILD_SERIAL = "build_serial";
    public static final String BUILD_VERSION_RELEASE = "build_version_release";
    public static final String BUILD_VERSION_SDK = "build_version_sdk";
    public static final String BUILD_VERSION_SDK_INT = "build_version_sdk_int";
    public static final String BUILD_VERSION_BASE_OS = "build_version_base_os";
    public static final String BUILD_VERSION_SECURITY_PATH = "build_version_security_patch";

    private final Map<String, String> mDeviceInfo = new HashMap<>();

    @Override
    protected void collectDeviceInfo() {
        addDeviceInfo(BUILD_ID, Build.ID);
        addDeviceInfo(BUILD_PRODUCT, Build.PRODUCT);
        addDeviceInfo(BUILD_DEVICE, Build.DEVICE);
        addDeviceInfo(BUILD_BOARD, Build.BOARD);
        addDeviceInfo(BUILD_MANUFACTURER, Build.MANUFACTURER);
        addDeviceInfo(BUILD_BRAND, Build.BRAND);
        addDeviceInfo(BUILD_MODEL, Build.MODEL);
        addDeviceInfo(BUILD_TYPE, Build.TYPE);
        addDeviceInfo(BUILD_FINGERPRINT, Build.FINGERPRINT);
        addDeviceInfo(BUILD_ABI, Build.CPU_ABI);
        addDeviceInfo(BUILD_ABI2, Build.CPU_ABI2);
        addDeviceInfo(BUILD_ABIS, TextUtils.join(",", Build.SUPPORTED_ABIS));
        addDeviceInfo(BUILD_ABIS_32, TextUtils.join(",", Build.SUPPORTED_32_BIT_ABIS));
        addDeviceInfo(BUILD_ABIS_64, TextUtils.join(",", Build.SUPPORTED_64_BIT_ABIS));
        addDeviceInfo(BUILD_SERIAL, Build.SERIAL);
        addDeviceInfo(BUILD_VERSION_RELEASE, Build.VERSION.RELEASE);
        addDeviceInfo(BUILD_VERSION_SDK, Build.VERSION.SDK);
        addDeviceInfo(BUILD_VERSION_SDK_INT, Integer.toString(Build.VERSION.SDK_INT));
        addDeviceInfo(BUILD_VERSION_BASE_OS, Build.VERSION.BASE_OS);
        addDeviceInfo(BUILD_VERSION_SECURITY_PATH, Build.VERSION.SECURITY_PATCH);
    }

    private void addDeviceInfo(String key, String value) {
        mDeviceInfo.put(key, value);
        addResult(key, value);
    }

    protected void putDeviceInfo(Bundle bundle) {
        for (Entry<String, String> entry : mDeviceInfo.entrySet()) {
            bundle.putString(String.format(DEVICE_INFO_GENERIC, entry.getKey()), entry.getValue());
        }
    }
}
