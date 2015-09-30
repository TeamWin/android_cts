# Copyright (C) 2015 The Android Open Source Project
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

# Don't include this package in any target
LOCAL_MODULE_TAGS := tests
# When built explicitly put it in the data partition
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_DEX_PREOPT := false

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_SRC_FILES := $(call all-java-files-under, src)

# The aim of this package is to run tests against the default packaging of ICU as a standalone
# java library, and not as the implementation in use by the current android system. For this
# reason, all the required ICU resources are included into the APK by the following rules.
# icu4j contains ICU's implementation classes, icu4j-tests contains the test classes,
# and icudata/icutzdata contain data files and timezone data files respectively.
LOCAL_STATIC_JAVA_LIBRARIES := compatibility-device-util android-support-test icu4j icu4j-tests \
	icu4j-icudata icu4j-icutzdata icu4j-testdata

# Tag this module as a cts_v2 test artifact
LOCAL_COMPATIBILITY_SUITE := cts_v2

LOCAL_PACKAGE_NAME := CtsIcuTestCases

LOCAL_SDK_VERSION := current

include $(BUILD_CTS_PACKAGE)

# The CTS framework has it's own logic for generating XML files based on scanning the source
# for test methods and classes. Since the classes that we are testing are not actually in this
# package we must provide an alternative. Here we define a specially crafted XML file which
# conforms to what CTS and particularly, the cts-tradefed tool understands. This file contains
# lists of classes in ICU4J that we know are used in libcore and should be tested as part of CTS.
# The following rule uses the Android CoPy (ACP) tool to copy this file to where it is expected.

ifeq ($(TARGET_ARCH),arm64)
	LOCAL_ARCH := arm
else ifeq ($(TARGET_ARCH),mips64)
	LOCAL_ARCH := mips
else ifeq ($(TARGET_ARCH),x86_64)
	LOCAL_ARCH := x86
else
	LOCAL_ARCH := $(TARGET_ARCH)
endif
$(cts_package_xml): $(call intermediates-dir-for,APPS,$(LOCAL_PACKAGE_NAME))/package.apk | $(ACP)
	$(ACP) -fp cts/tests/tests/icu/CtsIcuTestCases_$(LOCAL_ARCH).xml \
	$(CTS_TESTCASES_OUT)/CtsIcuTestCases.xml
