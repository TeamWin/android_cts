# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# TODO(b/140795853): Once fixed, remove make files.

LOCAL_PATH := $(call my-dir)

#################################################
# HelloWorld5

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests
LOCAL_STATIC_JAVA_LIBRARIES := androidx.test.rules

LOCAL_SRC_FILES := $(call all-java-files-under, src5)
LOCAL_MANIFEST_FILE := AndroidManifest.xml

LOCAL_PACKAGE_NAME := HelloWorld5
LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 24
LOCAL_PACKAGE_SPLITS := mdpi-v4 hdpi-v4 xhdpi-v4 xxhdpi-v4 xxxhdpi-v4

LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_ASSET_DIR := $(LOCAL_PATH)/res

LOCAL_JAVA_LIBRARIES := android.test.runner.stubs android.test.base.stubs androidx.appcompat_appcompat androidx-constraintlayout_constraintlayout com.google.android.material_material

include $(BUILD_CTS_SUPPORT_PACKAGE)

#################################################
# HelloWorld7

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests
LOCAL_STATIC_JAVA_LIBRARIES := androidx.test.rules

LOCAL_SRC_FILES := $(call all-java-files-under, src7)
LOCAL_MANIFEST_FILE := AndroidManifest.xml

LOCAL_PACKAGE_NAME := HelloWorld7
LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 24
LOCAL_PACKAGE_SPLITS := mdpi-v4 hdpi-v4 xhdpi-v4 xxhdpi-v4 xxxhdpi-v4

LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_ASSET_DIR := $(LOCAL_PATH)/res

LOCAL_JAVA_LIBRARIES := android.test.runner.stubs android.test.base.stubs androidx.appcompat_appcompat androidx-constraintlayout_constraintlayout com.google.android.material_material

include $(BUILD_CTS_SUPPORT_PACKAGE)
