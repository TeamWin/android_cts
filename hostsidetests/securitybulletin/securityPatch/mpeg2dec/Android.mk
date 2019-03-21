LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# mpeg2dec testbench
include $(LOCAL_PATH)/testmpeg2.mk
include $(LOCAL_PATH)/testmpeg2_mem1.mk
include $(LOCAL_PATH)/testmpeg2_mem2.mk
