LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := testhevc_mem1
LOCAL_SRC_FILES += ../includes/memutils.c
LOCAL_CFLAGS += -DCHECK_OVERFLOW
include $(LOCAL_PATH)/common.mk
