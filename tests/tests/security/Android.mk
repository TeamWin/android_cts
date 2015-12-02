# Copyright (C) 2011 The Android Open Source Project
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

test_executable := CtsAslrMallocTest
list_executable := $(test_executable)_list

include $(CLEAR_VARS)
LOCAL_MODULE:= $(test_executable)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA)/nativetest
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := $(LOCAL_MODULE)32
LOCAL_MODULE_STEM_64 := $(LOCAL_MODULE)64

LOCAL_C_INCLUDES := \
    external/gtest/include

LOCAL_SRC_FILES := \
    src/AslrMallocTest.cpp

LOCAL_SHARED_LIBRARIES := \
  libbase \
  libutils \
  liblog \

LOCAL_STATIC_LIBRARIES := \
  libgtest

LOCAL_CTS_TEST_PACKAGE := android.security.cts

# Tag this module as a cts_v2 test artifact
LOCAL_COMPATIBILITY_SUITE := cts_v2

include $(BUILD_CTS_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk

LOCAL_MODULE := $(list_executable)
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    src/AslrMallocTest.cpp

LOCAL_CFLAGS := \
    -DBUILD_ONLY \

LOCAL_SHARED_LIBRARIES := \
    liblog

include $(BUILD_HOST_NATIVE_TEST)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

# Include both the 32 and 64 bit versions
LOCAL_MULTILIB := both

LOCAL_STATIC_JAVA_LIBRARIES := ctstestserver ctstestrunner ctsdeviceutil compatibility-device-util guava

LOCAL_JAVA_LIBRARIES := android.test.runner org.apache.http.legacy

LOCAL_JNI_SHARED_LIBRARIES := libctssecurity_jni libcts_jni

LOCAL_SRC_FILES := $(call all-java-files-under, src)\
                   src/android/security/cts/activity/ISecureRandomService.aidl

LOCAL_PACKAGE_NAME := CtsSecurityTestCases

LOCAL_SDK_VERSION := current

# Tag this module as a cts_v2 test artifact
LOCAL_COMPATIBILITY_SUITE := cts_v2

include $(BUILD_CTS_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
