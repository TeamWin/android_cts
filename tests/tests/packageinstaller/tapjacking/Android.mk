# Copyright (C) 2017 Google Inc.
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

LOCAL_MODULE_TAGS := tests

LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_PACKAGE_NAME := GtsPackageInstallerTapjackingTestCases

LOCAL_STATIC_JAVA_LIBRARIES := \
    ub-uiautomator \
    android-support-test \
    compatibility-device-util \
    xts-device-util \


LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_SDK_VERSION := current

LOCAL_MIN_SDK_VERSION := 4

# Tag this module as test artifact for gts, ats
LOCAL_COMPATIBILITY_SUITE := gts ats

include $(BUILD_XTS_PACKAGE)
