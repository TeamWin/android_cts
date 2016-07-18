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
#define protected public

#include <jni.h>
#include <binder/IServiceManager.h>
#include <binder/Parcel.h>
#include <binder/ProcessState.h>
#include <media/AudioEffect.h>
#include <media/IAudioPolicyService.h>
#include <media/IMediaPlayerService.h>
#include <media/mediaplayer.h>
#include <system/audio.h>

using namespace android;

/**
 * struct which can be used to check whether a binder is dead or not
 */
struct DeathRecipient : public IBinder::DeathRecipient {
    DeathRecipient() : mDied(false) { }
    virtual void binderDied(const wp<IBinder>& who __unused) {
        mDied = true;
    }
    bool died() const {
        return mDied;
    }
    bool mDied;
};

struct MyClient : public BnMediaPlayerClient {
    MyClient()
        : mEOS(false) {
    }
    virtual void notify(int msg, int ext1 __unused, int ext2 __unused, const Parcel *obj __unused) {
        Mutex::Autolock autoLock(mLock);
        if (msg == MEDIA_ERROR || msg == MEDIA_PLAYBACK_COMPLETE) {
            mEOS = true;
            mCondition.signal();
        }
    }

    void waitForEOS() {
        Mutex::Autolock autoLock(mLock);
        while (!mEOS) {
            mCondition.wait(mLock);
        }
    }

protected:
    virtual ~MyClient() {}

private:
    Mutex mLock;
    Condition mCondition;
    bool mEOS;
    DISALLOW_EVIL_CONSTRUCTORS(MyClient);
};

static jboolean android_security_cts_AudioPolicyServiceTest_doHeapCorruptionTest(JNIEnv*, jobject) {
    effect_descriptor_t descriptors;
    uint32_t count = 0xfffffff;
    sp<MyClient> client = new MyClient;
    sp<IServiceManager> serviceManger = defaultServiceManager();
    // binder to get media.audio_policy
    sp<IBinder> binder = serviceManger->getService(String16("media.audio_policy"));
    sp<IAudioPolicyService> iPolicy = IAudioPolicyService::asInterface(binder);
    // binder to get media.player
    sp<IBinder> mediaBinder = serviceManger->getService(String16("media.player"));
    // For identifying media player died or not
    sp<IMediaPlayerService> service = interface_cast<IMediaPlayerService>(mediaBinder);
    sp<IMediaPlayer> player = service->create(client, AUDIO_SESSION_ALLOCATE);
    sp<DeathRecipient> deathRecipient(new DeathRecipient());
    player->onAsBinder()->linkToDeath(deathRecipient);
    /*
     Triggering queryDefaultPreProcessing() method which causes
     media player to die on unpatched device
    */
    iPolicy->queryDefaultPreProcessing(1, &descriptors, &count); // 1 for audioSession
    // wait to check media player died or not
    sleep(1);
    return(!deathRecipient->died());
}

static JNINativeMethod gMethods[] = {
    {"doHeapCorruptionTest", "()Z",
            (void *) android_security_cts_AudioPolicyServiceTest_doHeapCorruptionTest}
};

int register_android_security_cts_AudioPolicyServiceTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/security/cts/AudioPolicyServiceTest");
    return env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(JNINativeMethod));
}
