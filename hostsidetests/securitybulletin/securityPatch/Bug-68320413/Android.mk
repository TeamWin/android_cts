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

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := Bug-68320413
LOCAL_SRC_FILES := poc.c
LOCAL_SRC_FILES_arm += poc_arm_neon_utils.c
LOCAL_SRC_FILES_arm += arm_neon_utils.s
LOCAL_MULTILIB := 32
LOCAL_MODULE_STEM_32 := $(LOCAL_MODULE)32
LOCAL_C_INCLUDES := external/libhevc/common
LOCAL_C_INCLUDES += external/libhevc/decoder
LOCAL_SHARED_LIBRARIES := liblog
LOCAL_SHARED_LIBRARIES += libstagefright_soft_hevcdec

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts sts
LOCAL_CTS_TEST_PACKAGE := android.security.cts

LOCAL_ARM_MODE := arm
LOCAL_CFLAGS += -Wall -Werror -Wno-uninitialized -Wno-unused-variable
LOCAL_CFLAGS += -fPIC -DMD5_DISABLE
LOCAL_CFLAGS_arm += -DCHECK_ARM_REGISTERS
include $(BUILD_CTS_EXECUTABLE)
