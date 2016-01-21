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

//#define LOG_NDEBUG 0
#define LOG_TAG "GraphicBufferInfoLeakTest-JNI"

#include <jni.h>
#include <JNIHelp.h>

#include <binder/Parcel.h>
#include <binder/IServiceManager.h>

#include <gui/IGraphicBufferProducer.h>
#include <gui/IGraphicBufferConsumer.h>
#include <media/IMediaPlayerService.h>
#include <media/IMediaRecorder.h>
#include <media/IOMX.h>
#include <media/stagefright/MediaErrors.h>

#include <sys/stat.h>
#include <fcntl.h>

using namespace android;

static sp<IMediaPlayerService> getMediaPlayerService()
{
   sp<IServiceManager> sm = defaultServiceManager();
   sp<IBinder> mediaPlayerService = sm->checkService(String16("media.player"));

   sp<IMediaPlayerService> iMPService = IMediaPlayerService::asInterface(mediaPlayerService);
   return iMPService;
}

jint android_security_cts_GraphicBuffer_test_attachBufferInfoLeak(JNIEnv* env,
                                                           jobject thiz __unused)
{
    sp<IMediaPlayerService> iMPService = getMediaPlayerService();

    // get IOMX
    // Keep synchronized with IMediaPlayerService.cpp!
    enum {
        GET_OMX = 4,
    };

    status_t  err;
    Parcel data, reply;
    data.writeInterfaceToken(iMPService->getInterfaceDescriptor());
    err = IMediaPlayerService::asBinder(iMPService)->transact(GET_OMX, data, &reply);
    if (err != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException", "GET_OMX failed");
    }

    // get IGraphicBufferConsumer
    sp<IGraphicBufferProducer> iBufferProducer;
    sp<IGraphicBufferConsumer> iBufferConsumer;
    sp<IOMX> iOmx = interface_cast<IOMX>(reply.readStrongBinder());
    err = iOmx->createPersistentInputSurface(&iBufferProducer, &iBufferConsumer);
    if (err != NO_ERROR) {
        jniThrowException(env, "java/lang/RuntimeException", "createPersistentInputSurface failed");
        return err;
    }

    // Keep synchronized with IGraphicBufferConsumer.cpp!
    enum {
        ATTACH_BUFFER = 3,
    };

    for (;;) {
        Parcel data2, reply2;
        data2.writeInterfaceToken(iBufferConsumer->getInterfaceDescriptor());
        err = IGraphicBufferConsumer::asBinder(iBufferConsumer)->transact(ATTACH_BUFFER, data2, &reply2);
        if (err != NO_ERROR) {
            jniThrowException(env, "java/lang/RuntimeException", "ATTACH_BUFFER failed");
        }

        int32_t slot = reply2.readInt32();
        status_t result = reply2.readInt32();
        ALOGV("slot %d", slot);
        if (result != 0) {
            // only check for leaked data in error case
            return slot;
        }
    }
}

jint android_security_cts_GraphicBuffer_test_queueBufferInfoLeak(JNIEnv* env,
                                                           jobject thiz __unused)
{
    sp<IMediaPlayerService> iMPService = getMediaPlayerService();
    sp<IMediaRecorder> recorder = iMPService->createMediaRecorder(String16("GraphicBufferInfoLeakTest"));

    const char *fileName = "/dev/null";
    int fd = open(fileName, O_RDWR | O_CREAT, 0744);
    if (fd < 0) {
        jniThrowException(env, "java/lang/RuntimeException", "open output failed");
        return fd;
    }

    recorder->setVideoSource(2);
    recorder->setOutputFile(fd, 0, 0);
    recorder->setOutputFormat(0);
    recorder->init();
    recorder->prepare();
    recorder->start();

    //get IGraphicBufferProducer
    sp<IGraphicBufferProducer> iGBP = recorder->querySurfaceMediaSource();
    ALOGV("fd %d, Get iGBP instance, 0x%08x\n", fd, iGBP.get());

    // Keep synchronized with IGraphicBufferProducer.cpp!
    enum {
        QUEUE_BUFFER = 7,
    };

    for (;;) {
        status_t err;
        Parcel data, reply;
        data.writeInterfaceToken(iGBP->getInterfaceDescriptor());
        data.writeInt32(-1);
        err = IGraphicBufferProducer::asBinder(iGBP)->transact(QUEUE_BUFFER, data, &reply);
        if (err != NO_ERROR) {
            recorder->stop();
            recorder->release();
            jniThrowException(env, "java/lang/RuntimeException", "QUEUE_BUFFER failed");
            return err;
        }

        size_t len = reply.dataAvail();
        int32_t result; // last sizeof(int32_t) bytes of Parcel
        ALOGV("dataAvail = %zu\n", len);
        if (len < sizeof(result)) {
            // must contain result
            recorder->stop();
            recorder->release();
            jniThrowException(env, "java/lang/RuntimeException", "reply malformed");
            return ERROR_MALFORMED;
        }

        uint8_t *reply_data = (uint8_t *)reply.data();
        memcpy(&result, reply_data + len - sizeof(result), sizeof(result));
        if (result == NO_ERROR) {
            // only check for leaked data in error case
            continue;
        }

        uint8_t leaked_data = 0;
        for (size_t i = 0; i < len - sizeof(result); ++i) {
            ALOGV("IGraphicBufferProducer_InfoLeak reply_data[%d] = 0x%08x", i, reply_data[i]);
            if (reply_data[i]) {
                leaked_data = reply_data[i];
                break;
            }
        }

        recorder->stop();
        recorder->release();
        return leaked_data;
    }
}

static JNINativeMethod gMethods[] = {
    {  "native_test_attachBufferInfoLeak", "()I",
            (void *) android_security_cts_GraphicBuffer_test_attachBufferInfoLeak },
    {  "native_test_queueBufferInfoLeak", "()I",
            (void *) android_security_cts_GraphicBuffer_test_queueBufferInfoLeak },
};

int register_android_security_cts_GraphicBufferInfoLeakTest(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/security/cts/GraphicBufferInfoLeakTest");
    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
