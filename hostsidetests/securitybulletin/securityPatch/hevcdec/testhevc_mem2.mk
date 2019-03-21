LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := testhevc_mem2
LOCAL_SRC_FILES += ../includes/memutils.c
LOCAL_CFLAGS += -DCHECK_UNDERFLOW
include $(LOCAL_PATH)/common.mk
