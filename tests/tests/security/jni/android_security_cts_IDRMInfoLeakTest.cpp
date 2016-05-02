/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <media/IDrm.h>
#include <media/IMediaPlayer.h>
#include <media/IMediaPlayerService.h>

#define MAKE_DRM 6
#define GET_KEY_REQUEST 7

using namespace android;

static jboolean android_security_cts_IDRMInfoLeakTest_doIDRMInfoLeakTest
        (JNIEnv* env __unused, jobject thiz __unused) {
    Parcel data, reply, dataLeakInfo, replyLeakData;

    sp<IServiceManager> serviceManager = defaultServiceManager();
    sp<IBinder> mediaPlayerSevice = serviceManager->checkService(String16("media.player"));
    sp<IMediaPlayerService> iMediaPlayerService =
            IMediaPlayerService::asInterface(mediaPlayerSevice);
    data.writeInterfaceToken(iMediaPlayerService->getInterfaceDescriptor());

    //Trigger MAKE_DRM case
    IMediaPlayerService::asBinder(iMediaPlayerService)->transact(MAKE_DRM, data, &reply);
    sp<IDrm> iDrm = interface_cast<IDrm>(reply.readStrongBinder());
    dataLeakInfo.writeInterfaceToken(iDrm->getInterfaceDescriptor());

    //Trigger GET_KEY_REQUEST case
    IDrm::asBinder(iDrm)->transact(GET_KEY_REQUEST, dataLeakInfo, &replyLeakData);
    /**
     * The uninitialized 4 bytes stack data of keyRequestType starts from the 64th bit of
     * replyLeakData.
     */
    replyLeakData.readInt32();
    replyLeakData.readInt32();

    return (replyLeakData.readInt32() == 0);
}

static JNINativeMethod gMethods[] = {
    {"doIDRMInfoLeakTest", "()Z",
            (void *) android_security_cts_IDRMInfoLeakTest_doIDRMInfoLeakTest}
};

int register_android_security_cts_IDRMInfoLeakTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/security/cts/IDRMInfoLeakTest");
    return env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(JNINativeMethod));
}