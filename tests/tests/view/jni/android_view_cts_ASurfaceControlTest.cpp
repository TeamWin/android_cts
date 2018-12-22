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


#include <android/surface_control.h>

#include <array>

#include <jni.h>

namespace {

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

const std::array<JNINativeMethod, 3> JNI_METHODS = {{
    {"nSurfaceTransaction_create", "()J", (void*)SurfaceTransaction_create},
    {"nSurfaceTransaction_delete", "(J)V", (void*)SurfaceTransaction_delete},
    {"nSurfaceTransaction_apply", "(J)V", (void*)SurfaceTransaction_apply},
}};

}  // anonymous namespace

jint register_android_view_cts_ASurfaceControlTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/view/cts/ASurfaceControlTest");
    return env->RegisterNatives(clazz, JNI_METHODS.data(), JNI_METHODS.size());
}
