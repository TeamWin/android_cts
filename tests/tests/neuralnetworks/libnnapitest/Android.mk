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

#
# This is the shared library included by the JNI test app.
#
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
LOCAL_CLANG := true
LOCAL_MODULE := libnnapitest_jni
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    test_generated.cpp

LOCAL_C_INCLUDES := $(JNI_H_INCLUDE)
LOCAL_C_INCLUDES += frameworks/ml/nn/runtime/include/
LOCAL_C_INCLUDES += frameworks/ml/nn/runtime/test/

LOCAL_CPPFLAGS := -std=c++11
LOCAL_CFLAGS := -Wno-unused-parameter

LOCAL_SHARED_LIBRARIES := libdl liblog libneuralnetworks

# TODO: specify LOCAL_SDK_VERSION when libneuralnetworks become available
# in NDK
#LOCAL_SDK_VERSION := 27

LOCAL_CXX_STL := libc++_static

include $(BUILD_SHARED_LIBRARY)



