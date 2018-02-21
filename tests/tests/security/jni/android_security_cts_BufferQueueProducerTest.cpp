#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <binder/Parcel.h>
#include <binder/ProcessState.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/TextOutput.h>
#include <cutils/ashmem.h>
#include <cutils/native_handle.h>
#include <utils/NativeHandle.h>
#include <media/IMediaPlayerService.h>
#include <media/IOMX.h>
#include <jni.h>

using namespace android;

#define MAX_TRY 5000 //based on experiments
int quit=1;

static void *start2(void* args){

    sp<IGraphicBufferProducer> bufferProducer = *(sp<IGraphicBufferProducer>*)args;
    while(quit){
        int buffer;
        sp<Fence> fence;
        bufferProducer->dequeueBuffer(&buffer,&fence,800,600,1,0);
    }
    return NULL;
}

static jboolean testBufferQueueProducer() {
    int count = MAX_TRY;
    jboolean result = JNI_TRUE;
    sp<IServiceManager> sm = defaultServiceManager();
    sp<IBinder> binder = sm->getService(String16("media.player"));
    sp<IMediaPlayerService> service = interface_cast<IMediaPlayerService>(binder);
    sp<IOMX> omx = service->getOMX();
    sp<IGraphicBufferProducer> bufferProducer = NULL;
    sp<IGraphicBufferConsumer> bufferConsumer = NULL;
    status_t status = omx->createPersistentInputSurface(&bufferProducer,&bufferConsumer);
    if(status!=OK){
        printf("createPersistentInputSurface failed\n");
        return JNI_FALSE;
    }

    pthread_t thread;
    pthread_create(&thread,NULL,start2,&bufferProducer);

    native_handle_t *nativeHandle = native_handle_create(0,20);
    sp<NativeHandle> nh = NativeHandle::create(nativeHandle,true);
    while(quit){
        bufferConsumer->setConsumerName(String8("dddddddddddddddd"));
        String8 str=bufferProducer->getConsumerName();
        if (count < 0) {
            quit = 0;
        }
        if (!strcmp("TransactFailed",str.string())) {
            result = JNI_FALSE;
            quit = 0;
        }
        count--;
    }
    pthread_join(thread, NULL);
    return result;
}

static jboolean android_security_cts_BufferQueueTest() {
    return testBufferQueueProducer();
}

//
// Checks that UAF case in bufferqueuesi.
//
int register_android_security_cts_BufferQueueProducerTest(JNIEnv *env) {
    static JNINativeMethod methods[] = {
            {"native_test_BufferQueue", "()Z",
                    (void *) android_security_cts_BufferQueueTest},
    };

    jclass clazz = env->FindClass("android/security/cts/BufferQueueProducerTest");
    return env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
}
