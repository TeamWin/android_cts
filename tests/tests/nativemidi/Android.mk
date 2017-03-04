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

LOCAL_PATH:= $(call my-dir)

#
# libnativemidi
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := libnativemidi
#LOCAL_SRC_FILES := out/target/product/marlin/obj_arm/STATIC_LIBRARIES/libnativemidi_intermediates/libnativemidi.a
#include $(PREBUILT_STATIC_LIBRARY)

#
# NativeMidiEchoTest
#
include $(CLEAR_VARS)

# Don't include this package in any target.
LOCAL_MODULE_TAGS := optional

# When built, explicitly put it in the data partition.
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts

LOCAL_SRC_FILES := $(call all-java-files-under, java)

LOCAL_STATIC_JAVA_LIBRARIES := compatibility-device-util ctstestrunner
#LOCAL_WHOLE_STATIC_LIBRARIES := libnativemidi
LOCAL_JNI_SHARED_LIBRARIES := libnativemidi_jni
#LOCAL_SHARED_LIBRARIES := libnativemidi_jni
# Must match the package name in CtsTestCaseList.mk
LOCAL_PACKAGE_NAME := CtsNativeMidiTestCases

#LOCAL_SDK_VERSION := current

include $(BUILD_CTS_PACKAGE)
