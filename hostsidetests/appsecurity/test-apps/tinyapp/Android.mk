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

LOCAL_PATH := $(call my-dir)

cert_dir := cts/hostsidetests/appsecurity/certs/pkgsigverify

# This is the default test package signed with the default key.
include $(LOCAL_PATH)/base.mk
LOCAL_PACKAGE_NAME := CtsPkgInstallTinyApp
include $(BUILD_CTS_SUPPORT_PACKAGE)

# This is the test package signed using the V1/V2 signature schemes with
# two signers.
include $(LOCAL_PATH)/base.mk
LOCAL_PACKAGE_NAME := v1v2-ec-p256-two-signers
LOCAL_CERTIFICATE := $(cert_dir)/ec-p256
LOCAL_ADDITIONAL_CERTIFICATES := $(cert_dir)/ec-p256_2
include $(BUILD_CTS_SUPPORT_PACKAGE)

# This is the test package signed using the V3 signature scheme with
# a rotated key and one signer in the lineage with default capabilities.
include $(LOCAL_PATH)/base.mk
LOCAL_PACKAGE_NAME := v3-ec-p256-with-por_1_2-default-caps
LOCAL_CERTIFICATE := $(cert_dir)/ec-p256_2
LOCAL_ADDITIONAL_CERTIFICATES := $(cert_dir)/ec-p256
LOCAL_CERTIFICATE_LINEAGE := $(cert_dir)/ec-p256-por_1_2-default-caps
include $(BUILD_CTS_SUPPORT_PACKAGE)

cert_dir :=

