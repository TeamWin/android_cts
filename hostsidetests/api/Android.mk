# Copyright (C) 2018 The Android Open Source Project
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

LOCAL_MODULE := CtsUnofficialApisUsageTestCases
LOCAL_MODULE_TAGS := tests
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_SDK_VERSION := current
LOCAL_JAVA_LIBRARIES := cts-tradefed tradefed compatibility-host-util guava
LOCAL_STATIC_JAVA_LIBRARIES := dexlib2-no-guava doclava-no-guava jsilver antlr-runtime host-jdk-tools-prebuilt

# These are list of api txt files that are considered as approved APIs
api_files := $(addprefix frameworks/base/,\
  api/current.txt:current.txt \
  api/system-current.txt:system-current.txt \
  test-base/api/current.txt:android-test-base-current.txt \
  test-runner/api/current.txt:android-test-runner-current.txt \
  test-mock/api/current.txt:android-test-mock-current.txt)

# org.apache.http.legacy is considered as approved APIs
api_files += $(addprefix external/apache-http/,\
  api/current.txt:org-apache-http-legacy-current.txt \
  api/system-current.txt:org-apache-http-legacy-system-current.txt)

# API 27 is added since some support libraries are using old APIs
api_files += prebuilts/sdk/27/public/api/android.txt:android27.txt

define define-api-files
  $(eval tw := $(subst :, ,$(strip $(1)))) \
  $(eval $(call copy-one-file,$(word 1,$(tw)),$(2)/$(word 2,$(tw)))) \
  $(eval LOCAL_JAVA_RESOURCE_FILES += $(2)/$(word 2,$(tw)))
endef

dest := $(call intermediates-dir-for,JAVA_LIBRARIES,$(LOCAL_MODULE),true,COMMON)

$(foreach f,$(api_files),\
  $(call define-api-files,$(f),$(dest)))

api_files :=
dest :=

include $(BUILD_HOST_JAVA_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := host-jdk-tools-prebuilt:../../../$(HOST_JDK_TOOLS_JAR)
include $(BUILD_HOST_PREBUILT)
