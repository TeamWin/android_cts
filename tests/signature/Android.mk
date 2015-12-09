# Copyright (C) 2008 The Android Open Source Project
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

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# don't include this package in any target
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := CtsSignatureTestCases

# Tag this module as a cts_v2 test artifact
LOCAL_COMPATIBILITY_SUITE := cts_v2

cts_api_xml_rel := ../../../$(call intermediates-dir-for,APPS,CtsSignatureTestCases)/current.api
cts_api_xml := $(LOCAL_PATH)/$(cts_api_xml_rel)
$(cts_api_xml) : frameworks/base/api/current.txt | $(APICHECK)
	@echo "Convert API file $@"
	@mkdir -p $(dir $@)
	$(hide) $(APICHECK_COMMAND) -convert2xml $< $@

# Copy the current api file to CTS
LOCAL_COMPATIBILITY_SUPPORT_FILES += $(cts_api_xml_rel):current.api

# For CTS v1
LOCAL_CTS_MODULE_CONFIG := $(LOCAL_PATH)/Old$(CTS_MODULE_TEST_CONFIG)

cts_api_xml_v1 := $(CTS_TESTCASES_OUT)/current.api
$(cts_api_xml_v1):  $(cts_api_xml) | $(ACP)
	$(call copy-file-to-new-target)

$(CTS_TESTCASES_OUT)/$(LOCAL_PACKAGE_NAME).xml: $(cts_api_xml_v1)

LOCAL_SDK_VERSION := current

LOCAL_STATIC_JAVA_LIBRARIES := ctstestrunner

include $(BUILD_CTS_PACKAGE)

# signature-hostside java library (for testing)
# ============================================================

include $(CLEAR_VARS)

# These files are for device-side only, so filter-out for host library
LOCAL_DEVICE_ONLY_SOURCES := %/SignatureTest.java

LOCAL_SRC_FILES := $(filter-out $(LOCAL_DEVICE_ONLY_SOURCES), $(call all-java-files-under, src))

LOCAL_MODULE := signature-hostside

LOCAL_MODULE_TAGS := optional

include $(BUILD_HOST_JAVA_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
