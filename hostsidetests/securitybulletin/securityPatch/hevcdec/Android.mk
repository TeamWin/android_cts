LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# hevcdec testbench
include $(LOCAL_PATH)/testhevc.mk
include $(LOCAL_PATH)/testhevc_mem1.mk
include $(LOCAL_PATH)/testhevc_mem2.mk
