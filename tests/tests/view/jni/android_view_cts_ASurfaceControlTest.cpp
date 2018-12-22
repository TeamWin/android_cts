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

void SurfaceTransaction_releaseBuffer(JNIEnv* /*env*/, jclass, jlong buffer) {
    AHardwareBuffer_release(reinterpret_cast<AHardwareBuffer*>(buffer));
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

const std::array<JNINativeMethod, 8> JNI_METHODS = {{
    {"nSurfaceTransaction_create", "()J", (void*)SurfaceTransaction_create},
    {"nSurfaceTransaction_delete", "(J)V", (void*)SurfaceTransaction_delete},
    {"nSurfaceTransaction_apply", "(J)V", (void*)SurfaceTransaction_apply},
    {"nSurfaceControl_createFromWindow", "(Landroid/view/Surface;)J",
                                            (void*)SurfaceControl_createFromWindow},
    {"nSurfaceControl_create", "(J)J", (void*)SurfaceControl_create},
    {"nSurfaceControl_destroy", "(J)V", (void*)SurfaceControl_destroy},
    {"nSurfaceTransaction_setSolidBuffer", "(JJIII)J", (void*)SurfaceTransaction_setSolidBuffer},
    {"nSurfaceTransaction_releaseBuffer", "(J)V", (void*)SurfaceTransaction_releaseBuffer},
}};

}  // anonymous namespace

jint register_android_view_cts_ASurfaceControlTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/view/cts/ASurfaceControlTest");
    return env->RegisterNatives(clazz, JNI_METHODS.data(), JNI_METHODS.size());
}
