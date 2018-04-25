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

#include <OMX_Component.h>
#include <binder/IServiceManager.h>
#include <binder/MemoryDealer.h>
#include <jni.h>
#include <media/IMediaPlayerService.h>
#include <media/IOMX.h>
#include <media/hardware/HardwareAPI.h>

#define LOG_TAG "StagefrightCodecTest-JNI"
#define MAX_COUNT 100

using namespace android;

class DeathNotifier : public IBinder::DeathRecipient {
public:
  DeathNotifier() : mDied(false) {}
  virtual void binderDied(const wp<IBinder> &who __unused) { mDied = true; }
  bool died() const { return mDied; }
  bool mDied;
};

struct DummyOMXObserver : public BnOMXObserver {
public:
  DummyOMXObserver() {}

  virtual void onMessages(const std::list<omx_message> &messages) {}

protected:
  virtual ~DummyOMXObserver() {}
};

template <class T> static void InitOMXParams(T *params) {
  params->nSize = sizeof(T);
  params->nVersion.s.nVersionMajor = 1;
  params->nVersion.s.nVersionMinor = 0;
  params->nVersion.s.nRevision = 0;
  params->nVersion.s.nStep = 0;
}

static jboolean android_security_cts_StagefrightCodecTest_doAVCDecodeTest(
    JNIEnv *env __unused, jobject thiz __unused) {
  sp<IServiceManager> sm = defaultServiceManager();

  sp<IBinder> binder = sm->getService(String16("media.player"));
  sp<DeathNotifier> deathNotifier = new DeathNotifier();
  sp<IMediaPlayerService> mps = interface_cast<IMediaPlayerService>(binder);
  int try_count = MAX_COUNT;

  if (mps == NULL) {
    ALOGI("get media player service failed");
    return JNI_TRUE;
  }

  while (try_count) {
    const char *codecName = "OMX.qcom.video.decoder.avc.secure";
    // connect to IOMX each time
    sp<IOMX> service = mps->getOMX();

    IInterface::asBinder(service)->linkToDeath(deathNotifier);
    IOMX::node_id node = 0;
    int fenceFd = -1;
    sp<DummyOMXObserver> observer = new DummyOMXObserver();
    status_t err = service->allocateNode(codecName, observer, &node);
    if (err != OK) {
      ALOGI("%s node allocation fails", codecName);
      return JNI_FALSE;
    }
    // get buffer parameters
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = 0;
    def.nBufferCountActual = 0;
    def.nBufferSize = 0;
    err = service->getParameter(node, OMX_IndexParamPortDefinition, &def,
                                sizeof(def));
    ALOGI("port 0: %u buffers of size %u", def.nBufferCountActual,
          def.nBufferSize);

    int inMemSize = def.nBufferCountActual * def.nBufferSize;
    int inBufferCnt = def.nBufferCountActual;
    int inBufferSize = inMemSize / inBufferCnt;

    sp<MemoryDealer> dealerIn = new MemoryDealer(inMemSize);
    IOMX::buffer_id *inBufferId = new IOMX::buffer_id[inBufferCnt];

    // allocate buffer
    void *buffer_data = NULL;
    err = service->allocateBuffer(node, 0, inBufferSize, &inBufferId[0],
                                  &buffer_data);
    ALOGI("allocateBuffer, port index 0, err: %d", err);

    // use buffer
    sp<IMemory> memory = dealerIn->allocate(inBufferSize);
    err = service->useBuffer(node, 0, memory, &inBufferId[1], inBufferSize);
    ALOGI("useBuffer, port index 0, err: %d", err);
    err = service->emptyBuffer(node, inBufferId[0], 0, inBufferSize, 0, 0,
                               fenceFd);
    ALOGI("emptyBuffer, err: %d", err);
    err = service->freeNode(node);
    ALOGI("freeNode, err: %d", err);
    sleep(1);
    try_count = try_count - 1;
    if (deathNotifier->died()) {
      ALOGE("binder died");
      return JNI_FALSE;
    }
  }

  return JNI_TRUE;
}

int register_android_security_cts_StagefrightCodecTest(JNIEnv *env) {
  static JNINativeMethod methods[] = {
      {"native_doAVCDecodeTest", "()Z",
       (void *)android_security_cts_StagefrightCodecTest_doAVCDecodeTest},
  };

  jclass clazz = env->FindClass("android/security/cts/StagefrightCodecTest");
  return env->RegisterNatives(clazz, methods,
                              sizeof(methods) / sizeof(methods[0]));
}