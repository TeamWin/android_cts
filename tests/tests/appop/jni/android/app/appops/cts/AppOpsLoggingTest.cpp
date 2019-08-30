/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <jni.h>
#include <binder/AppOpsManager.h>
#include <utils/String16.h>

using namespace android;

#include "android/log.h"
#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "AppOpsLoggingTest"

// Note op from native code without supplying a message
extern "C" JNIEXPORT void JNICALL
Java_android_app_appops_cts_AppOpsLoggingTestKt_nativeNoteOp(JNIEnv* env, jobject obj,
        jint op, jint uid, jstring callingPackageName) {
    AppOpsManager appOpsManager;

    const char *nativeCallingPackageName = env->GetStringUTFChars(callingPackageName, 0);

    appOpsManager.noteOp(op, uid, String16(nativeCallingPackageName));

    env->ReleaseStringUTFChars(callingPackageName, nativeCallingPackageName);
}

// Note op from native code
extern "C" JNIEXPORT void JNICALL
Java_android_app_appops_cts_AppOpsLoggingTestKt_nativeNoteOpWithMessage(JNIEnv* env, jobject obj,
        jint op, jint uid, jstring callingPackageName, jstring message) {
    AppOpsManager appOpsManager;

    const char *nativeCallingPackageName = env->GetStringUTFChars(callingPackageName, 0);
    const char *nativeMessage = env->GetStringUTFChars(message, 0);

    appOpsManager.noteOp(op, uid, String16(nativeCallingPackageName), String16(nativeMessage));

    env->ReleaseStringUTFChars(callingPackageName, nativeCallingPackageName);
    env->ReleaseStringUTFChars(message, nativeMessage);
}
