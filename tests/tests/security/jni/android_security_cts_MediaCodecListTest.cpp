/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <jni.h>
#include <cstring>
#include <cstdio>
#include <binder/IPCThreadState.h>
#include <binder/Parcel.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <media/IMediaCodecList.h>
#include <media/IMediaPlayerService.h>
#include <media/MediaCodecInfo.h>

using namespace android;

static jboolean android_security_cts_MediaCodecListTest_doCodecInfoTest
        (JNIEnv* env __unused, jobject thiz __unused) {
    sp<IMediaPlayerService> service =
            interface_cast<IMediaPlayerService>(defaultServiceManager()
            ->getService(String16("media.player")));
    sleep(1);
    sp<IMediaCodecList> list = service->getCodecList();
    sleep(1);
    list = service->getCodecList();
    size_t count = list->countCodecs();
    /*
     * Trying to access the index out of boundary
     */
    count = count + 5;
    sp<MediaCodecInfo> codecValue = list->getCodecInfo(count);
    /*
     * Fix was available in getCodecInfo()
     * checks for boundary condition and returns null if out of boundary
     */
    return (codecValue == NULL);
}

static JNINativeMethod gMethods[] = {
    {"doCodecInfoTest", "()Z",
        (void *) android_security_cts_MediaCodecListTest_doCodecInfoTest}
};

int register_android_security_cts_MediaCodecListTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/security/cts/MediaCodecListTest");
    return env->RegisterNatives(
            clazz, gMethods, sizeof(gMethods) / sizeof(JNINativeMethod));
}