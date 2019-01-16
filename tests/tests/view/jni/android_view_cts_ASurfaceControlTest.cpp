/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "ASurfaceControlTest"

#include <unistd.h>

#include <array>
#include <string>

#include <android/hardware_buffer.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <android/surface_control.h>

#include <jni.h>

namespace {

// Raises a java exception
static void fail(JNIEnv* env, const char* format, ...) {
    va_list args;

    va_start(args, format);
    char* msg;
    vasprintf(&msg, format, args);
    va_end(args);

    jclass exClass;
    const char* className = "java/lang/AssertionError";
    exClass = env->FindClass(className);
    env->ThrowNew(exClass, msg);
    free(msg);
}

#define ASSERT(condition, format, args...) \
    if (!(condition)) {                    \
        fail(env, format, ##args);         \
        return;                            \
    }

static AHardwareBuffer* allocateBuffer(int32_t width, int32_t height) {
    AHardwareBuffer* buffer = nullptr;
    AHardwareBuffer_Desc desc = {};
    desc.width = width;
    desc.height = height;
    desc.layers = 1;
    desc.usage = AHARDWAREBUFFER_USAGE_COMPOSER_OVERLAY | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;

    AHardwareBuffer_allocate(&desc, &buffer);

    return buffer;
}

static void fillRegion(void* data, int32_t left, int32_t top, int32_t right,
                       int32_t bottom, uint32_t color, uint32_t stride) {
    uint32_t* ptr = static_cast<uint32_t*>(data);

    ptr += stride * top;

    for (uint32_t y = top; y < bottom; y++) {
        for (uint32_t x = left; x < right; x++) {
            ptr[x] = color;
        }
        ptr += stride;
    }
}

static bool getSolidBuffer(int32_t width, int32_t height, uint32_t color,
                           AHardwareBuffer** outHardwareBuffer,
                           int* outFence) {
    AHardwareBuffer* buffer = allocateBuffer(width, height);
    if (!buffer) {
        return true;
    }

    AHardwareBuffer_Desc desc = {};
    AHardwareBuffer_describe(buffer, &desc);

    void* data = nullptr;
    const ARect rect{0, 0, width, height};
    AHardwareBuffer_lock(buffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, &rect,
                                             &data);
    if (!data) {
        return true;
    }

    fillRegion(data, 0, 0, width, height, color, desc.stride);

    AHardwareBuffer_unlock(buffer, outFence);

    *outHardwareBuffer = buffer;
    return false;
}

static bool getQuadrantBuffer(int32_t width, int32_t height, jint colorTopLeft,
                              jint colorTopRight, jint colorBottomRight,
                              jint colorBottomLeft,
                              AHardwareBuffer** outHardwareBuffer,
                              int* outFence) {
    AHardwareBuffer* buffer = allocateBuffer(width, height);
    if (!buffer) {
        return true;
    }

    AHardwareBuffer_Desc desc = {};
    AHardwareBuffer_describe(buffer, &desc);

    void* data = nullptr;
    const ARect rect{0, 0, width, height};
    AHardwareBuffer_lock(buffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, &rect,
                                             &data);
    if (!data) {
        return true;
    }

    fillRegion(data, 0, 0, width / 2, height / 2, colorTopLeft, desc.stride);
    fillRegion(data, width / 2, 0, width, height / 2, colorTopRight, desc.stride);
    fillRegion(data, 0, height / 2, width / 2, height, colorBottomLeft,
                         desc.stride);
    fillRegion(data, width / 2, height / 2, width, height, colorBottomRight,
                         desc.stride);

    AHardwareBuffer_unlock(buffer, outFence);

    *outHardwareBuffer = buffer;
    return false;
}

jlong SurfaceTransaction_create(JNIEnv* /*env*/, jclass) {
    return reinterpret_cast<jlong>(ASurfaceTransaction_create());
}

void SurfaceTransaction_delete(JNIEnv* /*env*/, jclass, jlong surfaceTransaction) {
    ASurfaceTransaction_delete(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction));
}

void SurfaceTransaction_apply(JNIEnv* /*env*/, jclass, jlong surfaceTransaction) {
    ASurfaceTransaction_apply(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction));
}

long SurfaceControl_createFromWindow(JNIEnv* env, jclass, jobject jSurface) {
    if (!jSurface) {
        return 0;
    }

    ANativeWindow* window = ANativeWindow_fromSurface(env, jSurface);
    if (!window) {
        return 0;
    }

    const std::string debugName = "SurfaceControl_createFromWindowLayer";
    ASurfaceControl* surfaceControl =
            ASurfaceControl_createFromWindow(window, debugName.c_str());
    if (!surfaceControl) {
        return 0;
    }

    ANativeWindow_release(window);

    return reinterpret_cast<jlong>(surfaceControl);
}

jlong SurfaceControl_create(JNIEnv* /*env*/, jclass, jlong parentSurfaceControlId) {
    ASurfaceControl* surfaceControl = nullptr;
    const std::string debugName = "SurfaceControl_create";

    surfaceControl = ASurfaceControl_create(
            reinterpret_cast<ASurfaceControl*>(parentSurfaceControlId),
            debugName.c_str());

    return reinterpret_cast<jlong>(surfaceControl);
}

void SurfaceControl_destroy(JNIEnv* /*env*/, jclass, jlong surfaceControl) {
    ASurfaceControl_destroy(reinterpret_cast<ASurfaceControl*>(surfaceControl));
}

jlong SurfaceTransaction_setSolidBuffer(JNIEnv* /*env*/, jclass,
                                        jlong surfaceControl,
                                        jlong surfaceTransaction, jint width,
                                        jint height, jint color) {
    AHardwareBuffer* buffer = nullptr;
    int fence = -1;

    bool err = getSolidBuffer(width, height, color, &buffer, &fence);
    if (err) {
        return 0;
    }

    ASurfaceTransaction_setBuffer(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), buffer, fence);

    return reinterpret_cast<jlong>(buffer);
}

jlong SurfaceTransaction_setQuadrantBuffer(
        JNIEnv* /*env*/, jclass, jlong surfaceControl, jlong surfaceTransaction,
        jint width, jint height, jint colorTopLeft, jint colorTopRight,
        jint colorBottomRight, jint colorBottomLeft) {
    AHardwareBuffer* buffer = nullptr;
    int fence = -1;

    bool err =
            getQuadrantBuffer(width, height, colorTopLeft, colorTopRight,
                              colorBottomRight, colorBottomLeft, &buffer, &fence);
    if (err) {
        return 0;
    }

    ASurfaceTransaction_setBuffer(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), buffer, fence);

    return reinterpret_cast<jlong>(buffer);
}

void SurfaceTransaction_releaseBuffer(JNIEnv* /*env*/, jclass, jlong buffer) {
    AHardwareBuffer_release(reinterpret_cast<AHardwareBuffer*>(buffer));
}

void SurfaceTransaction_setVisibility(JNIEnv* /*env*/, jclass,
                                      jlong surfaceControl,
                                      jlong surfaceTransaction, jboolean show) {
    int8_t visibility = (show) ? ASURFACE_TRANSACTION_VISIBILITY_SHOW :
                                 ASURFACE_TRANSACTION_VISIBILITY_HIDE;
    ASurfaceTransaction_setVisibility(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), visibility);
}

void SurfaceTransaction_setBufferOpaque(JNIEnv* /*env*/, jclass,
                                        jlong surfaceControl,
                                        jlong surfaceTransaction,
                                        jboolean opaque) {
    int8_t transparency = (opaque) ? ASURFACE_TRANSACTION_TRANSPARENCY_OPAQUE :
                                   ASURFACE_TRANSACTION_TRANSPARENCY_TRANSPARENT;
    ASurfaceTransaction_setBufferTransparency(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), transparency);
}

void SurfaceTransaction_setGeometry(JNIEnv* /*env*/, jclass,
                                    jlong surfaceControl,
                                    jlong surfaceTransaction,
                                    jint srcLeft, jint srcTop, jint srcRight, jint srcBottom,
                                    jint dstLeft, jint dstTop, jint dstRight, jint dstBottom,
                                    jint transform) {
    const ARect src{srcLeft, srcTop, srcRight, srcBottom};
    const ARect dst{dstLeft, dstTop, dstRight, dstBottom};
    ASurfaceTransaction_setGeometry(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), src, dst, transform);
}

void SurfaceTransaction_setDamageRegion(JNIEnv* /*env*/, jclass,
                                        jlong surfaceControl,
                                        jlong surfaceTransaction, jint left,
                                        jint top, jint right, jint bottom) {
    const ARect rect[] = {{left, top, right, bottom}};
    ASurfaceTransaction_setDamageRegion(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), rect, 1);
}

void SurfaceTransaction_setZOrder(JNIEnv* /*env*/, jclass, jlong surfaceControl,
                                  jlong surfaceTransaction, jint z) {
    ASurfaceTransaction_setZOrder(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<ASurfaceControl*>(surfaceControl), z);
}

static void onComplete(void* context, int presentFence) {
    close(presentFence);

    if (!context) {
        return;
    }

    int* contextIntPtr = reinterpret_cast<int*>(context);
    (*contextIntPtr)++;
}

jlong SurfaceTransaction_setOnComplete(JNIEnv* /*env*/, jclass, jlong surfaceTransaction) {
    int* context = new int;
    *context = 0;

    ASurfaceTransaction_setOnComplete(
            reinterpret_cast<ASurfaceTransaction*>(surfaceTransaction),
            reinterpret_cast<void*>(context), onComplete);
    return reinterpret_cast<jlong>(context);
}

void SurfaceTransaction_checkOnComplete(JNIEnv* env, jclass, jlong context) {
    ASSERT(context != 0, "invalid context")

    int* contextPtr = reinterpret_cast<int*>(context);
    int data = *contextPtr;

    delete contextPtr;

    ASSERT(data >= 1, "did not receive a callback")
    ASSERT(data <= 1, "received too many callbacks")
}

const std::array<JNINativeMethod, 16> JNI_METHODS = {{
    {"nSurfaceTransaction_create", "()J", (void*)SurfaceTransaction_create},
    {"nSurfaceTransaction_delete", "(J)V", (void*)SurfaceTransaction_delete},
    {"nSurfaceTransaction_apply", "(J)V", (void*)SurfaceTransaction_apply},
    {"nSurfaceControl_createFromWindow", "(Landroid/view/Surface;)J",
                                            (void*)SurfaceControl_createFromWindow},
    {"nSurfaceControl_create", "(J)J", (void*)SurfaceControl_create},
    {"nSurfaceControl_destroy", "(J)V", (void*)SurfaceControl_destroy},
    {"nSurfaceTransaction_setSolidBuffer", "(JJIII)J", (void*)SurfaceTransaction_setSolidBuffer},
    {"nSurfaceTransaction_setQuadrantBuffer", "(JJIIIIII)J",
                                            (void*)SurfaceTransaction_setQuadrantBuffer},
    {"nSurfaceTransaction_releaseBuffer", "(J)V", (void*)SurfaceTransaction_releaseBuffer},
    {"nSurfaceTransaction_setVisibility", "(JJZ)V", (void*)SurfaceTransaction_setVisibility},
    {"nSurfaceTransaction_setBufferOpaque", "(JJZ)V", (void*)SurfaceTransaction_setBufferOpaque},
    {"nSurfaceTransaction_setGeometry", "(JJIIIIIIIII)V", (void*)SurfaceTransaction_setGeometry},
    {"nSurfaceTransaction_setDamageRegion", "(JJIIII)V", (void*)SurfaceTransaction_setDamageRegion},
    {"nSurfaceTransaction_setZOrder", "(JJI)V", (void*)SurfaceTransaction_setZOrder},
    {"nSurfaceTransaction_setOnComplete", "(J)J", (void*)SurfaceTransaction_setOnComplete},
    {"nSurfaceTransaction_checkOnComplete", "(J)V", (void*)SurfaceTransaction_checkOnComplete},
}};

}  // anonymous namespace

jint register_android_view_cts_ASurfaceControlTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/view/cts/ASurfaceControlTest");
    return env->RegisterNatives(clazz, JNI_METHODS.data(), JNI_METHODS.size());
}
