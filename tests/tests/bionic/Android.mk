LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := CtsBionicTestCases
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA)/nativetest
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := $(LOCAL_MODULE)32
LOCAL_MODULE_STEM_64 := $(LOCAL_MODULE)64

LOCAL_SHARED_LIBRARIES += \
    libdl \

LOCAL_WHOLE_STATIC_LIBRARIES += \
    libBionicTests \
    libBionicCtsGtestMain \

LOCAL_STATIC_LIBRARIES += \
    libbase \
    libtinyxml2 \
    liblog \
    libgtest \

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts

LOCAL_CTS_TEST_PACKAGE := android.bionic
include $(BUILD_CTS_EXECUTABLE)
