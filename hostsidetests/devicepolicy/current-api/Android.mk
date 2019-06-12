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

LOCAL_PATH:= $(call my-dir)

# $(1) name of the xml file to be created
# $(2) path to the api text file
define build_xml_api_file
include $(CLEAR_VARS)
LOCAL_MODULE := cts-$(subst .,-,$(1))
LOCAL_MODULE_STEM := $(1)
LOCAL_MODULE_CLASS := ETC
LOCAL_COMPATIBILITY_SUITE := arcts cts vts general-tests
include $(BUILD_SYSTEM)/base_rules.mk
$$(LOCAL_BUILT_MODULE): $(2) | $(APICHECK)
	@echo "Convert API file $$< -> $$@"
	@mkdir -p $$(dir $$@)
	$(hide) $(APICHECK_COMMAND) -convert2xmlnostrip $$< $$@
endef

$(eval $(call build_xml_api_file,current.api,frameworks/base/api/current.txt))

include $(CLEAR_VARS)
