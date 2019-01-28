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

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_JAVA_SDK_LIBRARIES := \
        android.test.base \
        android.test.mock \
        android.test.runner \
        com.android.future.usb.accessory \
        com.android.location.provider \
        com.android.mediadrm.signer \
        com.android.media.remotedisplay \
        com.android.media.tv.remoteprovider \
        com.android.nfc_extras \
        javax.obex \
        org.apache.http.legacy

$(foreach ver,28,\
  $(foreach api_level,public system,\
    $(foreach lib,$(LOCAL_JAVA_SDK_LIBRARIES),\
      $(eval all_shared_libs_files += $(lib)-$(ver)-$(api_level).api))))


LOCAL_PACKAGE_NAME := CtsSharedLibsApiSignatureTestCases

LOCAL_SIGNATURE_API_FILES := \
    $(all_shared_libs_files)

include $(LOCAL_PATH)/../build_signature_apk.mk

LOCAL_JAVA_SDK_LIBRARIES :=
all_shared_libs_files :=

