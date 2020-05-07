#
# Copyright (C) 2020 Google Inc.
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

LOCAL_PATH := $(my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := CtsCorruptApkTests_Unaligned_Q
LOCAL_SRC_FILES := unaligned_Q.apk
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := tests
LOCAL_MODULE_SUFFIX := .apk
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_REPLACE_PREBUILT_APK_INSTALLED := $(LOCAL_PATH)/unaligned_Q.apk
LOCAL_COMPATIBILITY_SUITE := cts vts10 general-tests
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := CtsCorruptApkTests_Unaligned_R
LOCAL_SRC_FILES := unaligned_R.apk
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := tests
LOCAL_MODULE_SUFFIX := .apk
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_REPLACE_PREBUILT_APK_INSTALLED := $(LOCAL_PATH)/unaligned_R.apk
LOCAL_COMPATIBILITY_SUITE := cts vts10 general-tests
include $(BUILD_PREBUILT)
