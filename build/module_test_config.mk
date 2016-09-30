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

ifneq ($(LOCAL_CTS_MODULE_CONFIG),)
cts_module_test_config := $(CTS_TESTCASES_OUT)/$(LOCAL_MODULE).config
$(cts_module_test_config): $(LOCAL_CTS_MODULE_CONFIG) | $(ACP)
	$(call copy-file-to-target)
# Have the module name depend on the module test config, so it gets generated
# when you run mm/mmm/mma/mmma.
$(my_register_name) : $(cts_module_test_config)
endif
# clear var
LOCAL_CTS_MODULE_CONFIG :=
