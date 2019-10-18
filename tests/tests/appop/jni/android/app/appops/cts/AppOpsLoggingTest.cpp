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

// Note op from native code
extern "C" JNIEXPORT void JNICALL
Java_android_app_appops_cts_AppOpsLoggingTestKt_nativeNoteOp(JNIEnv* env, jobject obj,
        jint op, jint uid, jstring jCallingPackageName, jstring jFeatureId, jstring jMessage) {
    AppOpsManager appOpsManager;

    const char *nativeCallingPackageName = env->GetStringUTFChars(jCallingPackageName, 0);
    String16 callingPackageName(nativeCallingPackageName);

    const char *nativeFeatureId;
    String16 *featureId;
    if (jFeatureId != nullptr) {
        nativeFeatureId = env->GetStringUTFChars(jFeatureId, 0);
        featureId = new String16(nativeFeatureId);
    } else {
        featureId = new String16();
    }

    const char *nativeMessage;
    String16 *message;
    if (jMessage != nullptr) {
        nativeMessage = env->GetStringUTFChars(jMessage, 0);
        message = new String16(nativeMessage);
    } else {
        message = new String16();
    }

    appOpsManager.noteOp(op, uid, callingPackageName, *featureId, *message);

    env->ReleaseStringUTFChars(jCallingPackageName, nativeCallingPackageName);

    if (jFeatureId != nullptr) {
        env->ReleaseStringUTFChars(jFeatureId, nativeFeatureId);
    }
    delete featureId;

    if (jMessage != nullptr) {
        env->ReleaseStringUTFChars(jMessage, nativeMessage);
    }
    delete message;
}
