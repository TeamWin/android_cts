LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := hotspot
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice

LOCAL_SDK_VERSION := current

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4

LOCAL_COMPATIBILITY_SUITE := cts general-tests sts

include $(BUILD_CTS_PACKAGE)
