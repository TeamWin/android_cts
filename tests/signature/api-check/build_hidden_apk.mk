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

# Specify the following variables before including:
#
#     LOCAL_PACKAGE_NAME
#         the name of the package
#
#     LOCAL_SIGNATURE_API_FILES
#         the list of api files needed

# don't include this package in any target
LOCAL_MODULE_TAGS := tests

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_SDK_VERSION := current

LOCAL_STATIC_JAVA_LIBRARIES += cts-api-signature-test

LOCAL_JNI_SHARED_LIBRARIES += libclassdescriptors
LOCAL_MULTILIB := both

LOCAL_JAVA_RESOURCE_FILES += $(INTERNAL_PLATFORM_HIDDENAPI_FLAGS)

LOCAL_DEX_PREOPT := false
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_USE_EMBEDDED_NATIVE_LIBS := false

ifneq (,$(wildcard $(LOCAL_PATH)/src))
  LOCAL_SRC_FILES := $(call all-java-files-under, src)
endif

include $(BUILD_CTS_PACKAGE)
