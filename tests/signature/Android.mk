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

LOCAL_PATH:= $(call my-dir)

# cts-signature-common java library
# =================================

include $(CLEAR_VARS)

# don't include this package in any target
LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := signature-android-javalib

LOCAL_JNI_SHARED_LIBRARIES := libcts_dexchecker

LOCAL_MODULE := cts-signature-common

LOCAL_SDK_VERSION := current

include $(BUILD_STATIC_JAVA_LIBRARY)


include $(call all-makefiles-under,$(LOCAL_PATH))
