/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "StagefrightCodecTest-JNI"

#include <jni.h>
#include <binder/IServiceManager.h>
#include <media/IMediaPlayerService.h>
#include <media/IOMX.h>
#include <binder/MemoryDealer.h>

#include "OMX_Component.h"

using namespace android;

struct DeathRecipient : public IBinder::DeathRecipient
{
    DeathRecipient() : mDied(false) { }
    virtual void binderDied(const wp<IBinder>& who __unused) { mDied = true; }
    bool died() const { return mDied; }
    bool mDied;
};

struct DummyOMXObserver: public BnOMXObserver
{
public:
    DummyOMXObserver() { }

    virtual void onMessages(const std::list<omx_message> &messages) { }

protected:
    virtual ~DummyOMXObserver() { }
};

static jboolean android_security_cts_StagefrightCodecTest_doMP3DecodeTest(
    JNIEnv* env __unused, jobject thiz __unused)
{
    sp<IServiceManager> sm = defaultServiceManager();

    sp<IBinder> binder = sm->getService(String16("media.player"));
    sp<IMediaPlayerService> mps = interface_cast<
            IMediaPlayerService>(binder);

    if (mps == NULL) {
        ALOGE("get media player service failed");
        return JNI_TRUE;
    }

    sp<IOMX> service = mps->getOMX();
    if (service == NULL) {
        ALOGE("get omx failed");
        return JNI_TRUE;
    }

    sp<DeathRecipient> deathRecipient(new DeathRecipient());
    IInterface::asBinder(service)->linkToDeath(deathRecipient);

    status_t err;
    IOMX::node_id node = 0;
    int fenceFd = -1;

    sp<DummyOMXObserver> observer = new DummyOMXObserver();

    const char *name = "OMX.google.mp3.decoder";

    err = service->allocateNode(name, observer, &node);
    if (err != OK) {
        ALOGE("%s node allocation failed", name);
        return JNI_TRUE;
    }

    int paramsSize = sizeof(OMX_AUDIO_PARAM_PCMMODETYPE);
    OMX_AUDIO_PARAM_PCMMODETYPE *params = (OMX_AUDIO_PARAM_PCMMODETYPE *) malloc(
        sizeof(OMX_AUDIO_PARAM_PCMMODETYPE));

    params->nPortIndex = 1;
    params->nSize = sizeof(OMX_AUDIO_PARAM_PCMMODETYPE);
    params->nChannels = 32;
    params->nSamplingRate = 44100;

    err = service->setParameter(node, OMX_IndexParamAudioPcm, params, paramsSize);

    int inMemSize = 8;
    int outMemSize = 4608*4;
    int inBufferCnt = 4;
    int outBufferCnt = 4;

    int inBufferSize = inMemSize / inBufferCnt;
    int outBufferSize = outMemSize / outBufferCnt;

    IOMX::buffer_id *inBufferId = new IOMX::buffer_id[inBufferCnt];
    IOMX::buffer_id *outBufferId = new IOMX::buffer_id[outBufferCnt];

    sp<MemoryDealer> dealerIn = new MemoryDealer(inMemSize);
    sp<MemoryDealer> dealerOut = new MemoryDealer(outMemSize);

    for (int i = 0; i < inBufferCnt; i++) {
        sp<IMemory> memory = dealerIn->allocate(inBufferSize);
        memset(memory->pointer(), 0x01, inBufferSize);
        err = service->useBuffer(node, 0, memory, &inBufferId[i], inBufferSize);
    }

    for (int i = 0; i < outBufferCnt; i++) {
        sp<IMemory> memory = dealerOut->allocate(outBufferSize);
        memset(memory->pointer(), 0xff, inBufferSize);
        err = service->useBuffer(node, 1, memory, &outBufferId[i], outBufferSize);
    }

    err = service->sendCommand(node, OMX_CommandStateSet, 2);
    err = service->sendCommand(node, OMX_CommandStateSet, 3);

    for (int i = 0; i < inBufferCnt; i++) {
        err = service->emptyBuffer(node, inBufferId[i], 0, inBufferSize, 1, 0, fenceFd);
    }

    for (int i = 0; i < outBufferCnt; i++) {
        err = service->fillBuffer(node, outBufferId[i], fenceFd);
    }

    sleep(1);
    if (deathRecipient->died()) {
        ALOGE("binder died");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

int register_android_security_cts_StagefrightCodecTest(JNIEnv *env)
{
    static JNINativeMethod methods[] = {
        { "native_doMP3DecodeTest", "()Z",
                (void *) android_security_cts_StagefrightCodecTest_doMP3DecodeTest },
    };

    jclass clazz = env->FindClass("android/security/cts/StagefrightCodecTest");
    return env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
}

