# Copyright (C) 2017 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := android-support-test

LOCAL_RES_LIBRARIES := CtsStaticSharedLibProviderApp7

LOCAL_PACKAGE_NAME := CtsStaticSharedLibConsumerApp3
LOCAL_SDK_VERSION := current

LOCAL_COMPATIBILITY_SUITE := cts vts general-tests cts_instant

LOCAL_USE_AAPT2 := true
# Disable AAPT2 manifest checks to fix:
# cts/hostsidetests/os/test-apps/StaticSharedLibConsumerApp3/AndroidManifest.xml:28: error: unexpected element <additional-certificate> found in <manifest><application><uses-static-library>.
# TODO(b/79755007): Remove when AAPT2 recognizes the manifest elements.
LOCAL_AAPT_FLAGS += --warn-manifest-validation

include $(BUILD_CTS_SUPPORT_PACKAGE)
