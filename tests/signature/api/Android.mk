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

# $(1) name of the txt file to be created
# $(2) path to the api text file
define copy_api_txt_file
include $(CLEAR_VARS)
LOCAL_MODULE := cts-$(subst .,-,$(1))
LOCAL_MODULE_STEM := $(1)
LOCAL_MODULE_CLASS := ETC
LOCAL_COMPATIBILITY_SUITE := arcts cts vts general-tests
include $(BUILD_SYSTEM)/base_rules.mk
$$(LOCAL_BUILT_MODULE): $(2) | $(APICHECK)
	@echo "Copying API file $$< -> $$@"
	$$(copy-file-to-target)
endef

$(foreach ver,$(PLATFORM_SYSTEMSDK_VERSIONS),\
  $(if $(call math_is_number,$(ver)),\
    $(eval $(call copy_api_txt_file,system-$(ver).txt,prebuilts/sdk/$(ver)/system/api/android.txt))\
  )\
)

$(foreach ver,$(call int_range_list,28,$(PLATFORM_SDK_VERSION)),\
  $(foreach api_level,public system,\
    $(foreach lib,$(filter-out android,$(filter-out %removed,$(filter-out incompatibilities,\
      $(basename $(notdir $(wildcard $(HISTORICAL_SDK_VERSIONS_ROOT)/$(ver)/$(api_level)/api/*.txt)))))),\
        $(eval $(call copy_api_txt_file,$(lib)-$(ver)-$(api_level).txt,prebuilts/sdk/$(ver)/$(api_level)/api/$(lib).txt)))))
