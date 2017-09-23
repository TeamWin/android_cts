# Copyright (C) 2015 The Android Open Source Project
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

# We define this in a subdir so that it won't pick up the parent's Android.xml by default.

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# current api, in XML format.
# NOTE: the output XML file is also used
# in //cts/hostsidetests/devicepolicy/AndroidTest.xml
# by com.android.cts.managedprofile.CurrentApiHelper
# ============================================================
include $(CLEAR_VARS)

LOCAL_MODULE := cts-current-api
LOCAL_MODULE_STEM := current.api
LOCAL_SRC_FILES := frameworks/base/api/current.txt

include $(LOCAL_PATH)/build_xml_api_file.mk

# current system api, in XML format.
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := cts-system-current-api
LOCAL_MODULE_STEM := system-current.api
LOCAL_SRC_FILES := frameworks/base/api/system-current.txt

include $(LOCAL_PATH)/build_xml_api_file.mk

# removed system api, in XML format.
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := cts-system-removed-api
LOCAL_MODULE_STEM := system-removed.api
LOCAL_SRC_FILES := frameworks/base/api/system-removed.txt

include $(LOCAL_PATH)/build_xml_api_file.mk

# current legacy-test api, in XML format.
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := cts-legacy-test-current-api
LOCAL_MODULE_STEM := legacy-test-current.api
LOCAL_SRC_FILES := frameworks/base/legacy-test/api/legacy-test-current.txt

include $(LOCAL_PATH)/build_xml_api_file.mk

# current android-test-mock api, in XML format.
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := cts-android-test-mock-current-api
LOCAL_MODULE_STEM := android-test-mock-current.api
LOCAL_SRC_FILES := frameworks/base/test-runner/api/android-test-mock-current.txt

include $(LOCAL_PATH)/build_xml_api_file.mk

# current android-test-runner api, in XML format.
# ============================================================
include $(CLEAR_VARS)
LOCAL_MODULE := cts-android-test-runner-current-api
LOCAL_MODULE_STEM := android-test-runner-current.api
LOCAL_SRC_FILES := frameworks/base/test-runner/api/android-test-runner-current.txt

include $(LOCAL_PATH)/build_xml_api_file.mk

# current apache-http-legacy minus current api, in text format.
# =============================================================
# Removes any classes from the org.apache.http.legacy API description that are
# also part of the Android API description.
include $(CLEAR_VARS)
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_ETC)

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_MODULE := cts-apache-http-legacy-minus-current-api
LOCAL_MODULE_STEM := apache-http-legacy-minus-current.api

include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE) : \
        frameworks/base/api/current.txt \
        external/apache-http/api/apache-http-legacy-current.txt \
        | $(APICHECK)
	@echo "Generate unique Apache Http Legacy API file -> $@"
	@mkdir -p $(dir $@)
	$(hide) $(APICHECK_COMMAND) -new_api_no_strip \
	        frameworks/base/api/current.txt \
            external/apache-http/api/apache-http-legacy-current.txt \
            $@
