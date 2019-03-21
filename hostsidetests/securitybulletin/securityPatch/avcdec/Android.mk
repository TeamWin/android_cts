LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# avcdec testbench
include $(LOCAL_PATH)/testavc.mk
include $(LOCAL_PATH)/testavc_mem1.mk
include $(LOCAL_PATH)/testavc_mem2.mk
