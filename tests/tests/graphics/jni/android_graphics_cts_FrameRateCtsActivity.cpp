/*
 * Copyright 2020 The Android Open Source Project
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
 *
 */

#define LOG_TAG "FrameRateCtsActivity"

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/surface_control.h>
#include <jni.h>

#include <array>

namespace {

jint nativeWindowSetFrameRate(JNIEnv* env, jclass, jobject jSurface, jfloat frameRate) {
    ANativeWindow* window = nullptr;
    if (jSurface) {
        window = ANativeWindow_fromSurface(env, jSurface);
    }
    return ANativeWindow_setFrameRate(window, frameRate);
}

jlong surfaceControlCreate(JNIEnv* env, jclass, jobject jParentSurface) {
    ANativeWindow* window = nullptr;
    if (jParentSurface) {
        window = ANativeWindow_fromSurface(env, jParentSurface);
    }
    if (!window) {
        return 0;
    }
    return reinterpret_cast<jlong>(
            ASurfaceControl_createFromWindow(window, "SetFrameRateTestSurface"));
}

void surfaceControlDestroy(JNIEnv*, jclass, jlong surfaceControlLong) {
    if (surfaceControlLong == 0) {
        return;
    }
    ASurfaceControl* surfaceControl = reinterpret_cast<ASurfaceControl*>(surfaceControlLong);
    ASurfaceTransaction* transaction = ASurfaceTransaction_create();
    ASurfaceTransaction_reparent(transaction, surfaceControl, nullptr);
    ASurfaceTransaction_apply(transaction);
    ASurfaceTransaction_delete(transaction);
    ASurfaceControl_release(surfaceControl);
}

void surfaceControlSetFrameRate(JNIEnv*, jclass, jlong surfaceControlLong, jfloat frameRate) {
    ASurfaceControl* surfaceControl = reinterpret_cast<ASurfaceControl*>(surfaceControlLong);
    ASurfaceTransaction* transaction = ASurfaceTransaction_create();
    ASurfaceTransaction_setFrameRate(transaction, surfaceControl, frameRate);
    ASurfaceTransaction_apply(transaction);
    ASurfaceTransaction_delete(transaction);
}

const std::array<JNINativeMethod, 4> JNI_METHODS = {{
        {"nativeWindowSetFrameRate", "(Landroid/view/Surface;F)I", (void*)nativeWindowSetFrameRate},
        {"nativeSurfaceControlCreate", "(Landroid/view/Surface;)J", (void*)surfaceControlCreate},
        {"nativeSurfaceControlDestroy", "(J)V", (void*)surfaceControlDestroy},
        {"nativeSurfaceControlSetFrameRate", "(JF)V", (void*)surfaceControlSetFrameRate},
}};

} // namespace

int register_android_graphics_cts_FrameRateCtsActivity(JNIEnv* env) {
    jclass clazz = env->FindClass("android/graphics/cts/FrameRateCtsActivity");
    return env->RegisterNatives(clazz, JNI_METHODS.data(), JNI_METHODS.size());
}
