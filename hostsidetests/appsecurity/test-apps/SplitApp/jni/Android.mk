#
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
#

# Caution: This file is used by NDK to generate all platform library files.
#          Please don't change this file to Android.bp.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libsplitappjni
LOCAL_SRC_FILES := com_android_cts_splitapp_Native.cpp

LOCAL_LDLIBS += -llog

LOCAL_CFLAGS := -D__ANDROID_ARCH__=\"$(TARGET_ARCH_ABI)\"

# tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts general-tests

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE := libsplitappjni_revision
LOCAL_SRC_FILES := com_android_cts_splitapp_Native.cpp

LOCAL_LDLIBS += -llog

LOCAL_CFLAGS := -D__ANDROID_ARCH__=\"$(TARGET_ARCH_ABI)\" \
                -D__REVISION_HAVE_SUB__=1

# tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts general-tests

include $(BUILD_SHARED_LIBRARY)
