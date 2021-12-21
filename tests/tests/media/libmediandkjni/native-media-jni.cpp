/*
 * Copyright (C) 2014 The Android Open Source Project
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

/* Original code copied from NDK Native-media sample code */

//#define LOG_NDEBUG 0
#define LOG_TAG "NativeMedia"
#include <log/log.h>

#include <assert.h>
#include <jni.h>
#include <mutex>
#include <pthread.h>
#include <queue>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <semaphore.h>

#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>

#include "media/NdkMediaExtractor.h"
#include "media/NdkMediaCodec.h"
#include "media/NdkMediaCrypto.h"
#include "media/NdkMediaDataSource.h"
#include "media/NdkMediaFormat.h"
#include "media/NdkMediaMuxer.h"

template <class T>
class simplevector {
    T *storage;
    int capacity;
    int numfilled;
public:
    simplevector() {
        capacity = 16;
        numfilled = 0;
        storage = new T[capacity];
    }
    ~simplevector() {
        delete[] storage;
    }

    void add(T item) {
        if (numfilled == capacity) {
            T *old = storage;
            capacity *= 2;
            storage = new T[capacity];
            for (int i = 0; i < numfilled; i++) {
                storage[i] = old[i];
            }
            delete[] old;
        }
        storage[numfilled] = item;
        numfilled++;
    }

    int size() {
        return numfilled;
    }

    T* data() {
        return storage;
    }
};

static int adler32(const uint8_t *input, int len) {

    int a = 1;
    int b = 0;
    for (int i = 0; i < len; i++) {
        a += input[i];
        b += a;
        a = a % 65521;
        b = b % 65521;
    }
    int ret = b * 65536 + a;
    ALOGV("adler %d/%d", len, ret);
    return ret;
}

jobject testExtractor(AMediaExtractor *ex, JNIEnv *env) {

    simplevector<int> sizes;
    int numtracks = AMediaExtractor_getTrackCount(ex);
    sizes.add(numtracks);
    for (int i = 0; i < numtracks; i++) {
        AMediaFormat *format = AMediaExtractor_getTrackFormat(ex, i);
        const char *s = AMediaFormat_toString(format);
        ALOGI("track %d format: %s", i, s);
        const char *mime;
        if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            ALOGE("no mime type");
            return NULL;
        } else if (!strncmp(mime, "audio/", 6)) {
            sizes.add(0);
            int32_t val32;
            int64_t val64;
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &val32);
            sizes.add(val32);
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &val32);
            sizes.add(val32);
            AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &val64);
            sizes.add(val64);
        } else if (!strncmp(mime, "video/", 6)) {
            sizes.add(1);
            int32_t val32;
            int64_t val64;
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_WIDTH, &val32);
            sizes.add(val32);
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_HEIGHT, &val32);
            sizes.add(val32);
            AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &val64);
            sizes.add(val64);
        } else {
            ALOGE("expected audio or video mime type, got %s", mime);
        }
        AMediaFormat_delete(format);
        AMediaExtractor_selectTrack(ex, i);
    }
    int bufsize = 1024*1024;
    uint8_t *buf = new uint8_t[bufsize];
    while(true) {
        int n = AMediaExtractor_readSampleData(ex, buf, bufsize);
        ssize_t sampleSize = AMediaExtractor_getSampleSize(ex);
        if (n < 0 || n != sampleSize) {
            break;
        }
        sizes.add(n);
        sizes.add(AMediaExtractor_getSampleTrackIndex(ex));
        sizes.add(AMediaExtractor_getSampleFlags(ex));
        sizes.add(AMediaExtractor_getSampleTime(ex));
        sizes.add(adler32(buf, n));
        AMediaExtractor_advance(ex);
    }

    // allocate java int array for result and return it
    int *data = sizes.data();
    int numsamples = sizes.size();
    jintArray ret = env->NewIntArray(numsamples);
    jboolean isCopy;
    jint *dst = env->GetIntArrayElements(ret, &isCopy);
    for (int i = 0; i < numsamples; ++i) {
        dst[i] = data[i];
    }
    env->ReleaseIntArrayElements(ret, dst, 0);

    delete[] buf;
    AMediaExtractor_delete(ex);
    return ret;
}


// get the sample sizes for the file
extern "C" jobject Java_android_media_cts_NativeDecoderTest_getSampleSizesNative(JNIEnv *env,
        jclass /*clazz*/, int fd, jlong offset, jlong size)
{
    AMediaExtractor *ex = AMediaExtractor_new();
    int err = AMediaExtractor_setDataSourceFd(ex, fd, offset, size);
    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        return NULL;
    }
    return testExtractor(ex, env);
}

// get the sample sizes for the path
extern "C" jobject Java_android_media_cts_NativeDecoderTest_getSampleSizesNativePath(JNIEnv *env,
        jclass /*clazz*/, jstring jpath, jobjectArray jkeys, jobjectArray jvalues,
        jboolean testNativeSource)
{
    AMediaExtractor *ex = AMediaExtractor_new();

    const char *tmp = env->GetStringUTFChars(jpath, NULL);
    if (tmp == NULL) {  // Out of memory
        return NULL;
    }

    int numkeys = jkeys ? env->GetArrayLength(jkeys) : 0;
    int numvalues = jvalues ? env->GetArrayLength(jvalues) : 0;
    int numheaders = numkeys < numvalues ? numkeys : numvalues;
    const char **key_values = numheaders ? new const char *[numheaders * 2] : NULL;
    for (int i = 0; i < numheaders; i++) {
        jstring jkey = (jstring) (env->GetObjectArrayElement(jkeys, i));
        jstring jvalue = (jstring) (env->GetObjectArrayElement(jvalues, i));
        const char *key = env->GetStringUTFChars(jkey, NULL);
        const char *value = env->GetStringUTFChars(jvalue, NULL);
        key_values[i * 2] = key;
        key_values[i * 2 + 1] = value;
    }

    int err;
    AMediaDataSource *src = NULL;
    if (testNativeSource) {
        src = AMediaDataSource_newUri(tmp, numheaders, key_values);
        err = src ? AMediaExtractor_setDataSourceCustom(ex, src) : -1;
    } else {
        err = AMediaExtractor_setDataSource(ex, tmp);
    }

    for (int i = 0; i < numheaders; i++) {
        jstring jkey = (jstring) (env->GetObjectArrayElement(jkeys, i));
        jstring jvalue = (jstring) (env->GetObjectArrayElement(jvalues, i));
        env->ReleaseStringUTFChars(jkey, key_values[i * 2]);
        env->ReleaseStringUTFChars(jvalue, key_values[i * 2 + 1]);
    }

    env->ReleaseStringUTFChars(jpath, tmp);
    delete[] key_values;

    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        AMediaExtractor_delete(ex);
        AMediaDataSource_delete(src);
        return NULL;
    }

    jobject ret = testExtractor(ex, env);
    AMediaDataSource_delete(src);
    return ret;
}

extern "C" jlong Java_android_media_cts_NativeDecoderTest_getExtractorFileDurationNative(
        JNIEnv * /*env*/, jclass /*clazz*/, int fd, jlong offset, jlong size)
{
    AMediaExtractor *ex = AMediaExtractor_new();
    int err = AMediaExtractor_setDataSourceFd(ex, fd, offset, size);
    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        AMediaExtractor_delete(ex);
        return -1;
    }
    int64_t durationUs = -1;
    AMediaFormat *format = AMediaExtractor_getFileFormat(ex);
    AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &durationUs);
    AMediaFormat_delete(format);
    AMediaExtractor_delete(ex);
    return durationUs;
}

extern "C" jlong Java_android_media_cts_NativeDecoderTest_getExtractorCachedDurationNative(
        JNIEnv * env, jclass /*clazz*/, jstring jpath, jboolean testNativeSource)
{
    AMediaExtractor *ex = AMediaExtractor_new();

    const char *tmp = env->GetStringUTFChars(jpath, NULL);
    if (tmp == NULL) {  // Out of memory
        AMediaExtractor_delete(ex);
        return -1;
    }

    int err;
    AMediaDataSource *src = NULL;
    if (testNativeSource) {
        src = AMediaDataSource_newUri(tmp, 0, NULL);
        err = src ? AMediaExtractor_setDataSourceCustom(ex, src) : -1;
    } else {
        err = AMediaExtractor_setDataSource(ex, tmp);
    }

    env->ReleaseStringUTFChars(jpath, tmp);

    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        AMediaExtractor_delete(ex);
        AMediaDataSource_delete(src);
        return -1;
    }

    int64_t cachedDurationUs = AMediaExtractor_getCachedDuration(ex);
    AMediaExtractor_delete(ex);
    AMediaDataSource_delete(src);
    return cachedDurationUs;

}


extern "C" jboolean Java_android_media_cts_NativeDecoderTest_testFormatNative(JNIEnv * /*env*/,
        jclass /*clazz*/) {
    AMediaFormat* format = AMediaFormat_new();
    if (!format) {
        return false;
    }

    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, 8000);
    int32_t bitrate = 0;
    if (!AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, &bitrate) || bitrate != 8000) {
        ALOGE("AMediaFormat_getInt32 fail: %d", bitrate);
        return false;
    }

    AMediaFormat_setInt64(format, AMEDIAFORMAT_KEY_DURATION, 123456789123456789LL);
    int64_t duration = 0;
    if (!AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &duration)
            || duration != 123456789123456789LL) {
        ALOGE("AMediaFormat_getInt64 fail: %lld", (long long) duration);
        return false;
    }

    AMediaFormat_setFloat(format, AMEDIAFORMAT_KEY_FRAME_RATE, 25.0f);
    float framerate = 0.0f;
    if (!AMediaFormat_getFloat(format, AMEDIAFORMAT_KEY_FRAME_RATE, &framerate)
            || framerate != 25.0f) {
        ALOGE("AMediaFormat_getFloat fail: %f", framerate);
        return false;
    }

    const char* value = "audio/mpeg";
    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, value);
    const char* readback = NULL;
    if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &readback)
            || strcmp(value, readback) || value == readback) {
        ALOGE("AMediaFormat_getString fail");
        return false;
    }

    uint32_t foo = 0xdeadbeef;
    AMediaFormat_setBuffer(format, "csd-0", &foo, sizeof(foo));
    foo = 0xabadcafe;
    void *bytes;
    size_t bytesize = 0;
    if(!AMediaFormat_getBuffer(format, "csd-0", &bytes, &bytesize)
            || bytesize != sizeof(foo) || *((uint32_t*)bytes) != 0xdeadbeef) {
        ALOGE("AMediaFormat_getBuffer fail");
        return false;
    }

    return true;
}


extern "C" jboolean Java_android_media_cts_NativeDecoderTest_testPsshNative(JNIEnv * /*env*/,
        jclass /*clazz*/, int fd, jlong offset, jlong size) {

    AMediaExtractor *ex = AMediaExtractor_new();
    int err = AMediaExtractor_setDataSourceFd(ex, fd, offset, size);
    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        return false;
    }

    PsshInfo* info = AMediaExtractor_getPsshInfo(ex);
    if (info == NULL) {
        ALOGI("null pssh");
        return false;
    }

    ALOGI("pssh has %zd entries", info->numentries);
    if (info->numentries != 2) {
        return false;
    }

    for (size_t i = 0; i < info->numentries; i++) {
        PsshEntry *entry = &info->entries[i];
        ALOGI("entry uuid %02x%02x..%02x%02x, data size %zd",
                entry->uuid[0],
                entry->uuid[1],
                entry->uuid[14],
                entry->uuid[15],
                entry->datalen);

        AMediaCrypto *crypto = AMediaCrypto_new(entry->uuid, entry->data, entry->datalen);
        if (crypto) {
            ALOGI("got crypto");
            AMediaCrypto_delete(crypto);
        } else {
            ALOGI("no crypto");
        }
    }
    return true;
}

extern "C" jboolean Java_android_media_cts_NativeDecoderTest_testCryptoInfoNative(JNIEnv * /*env*/,
        jclass /*clazz*/) {

    size_t numsubsamples = 4;
    uint8_t key[16] = { 1,2,3,4,1,2,3,4,1,2,3,4,1,2,3,4 };
    uint8_t iv[16] = { 4,3,2,1,4,3,2,1,4,3,2,1,4,3,2,1 };
    size_t clearbytes[4] = { 5, 6, 7, 8 };
    size_t encryptedbytes[4] = { 8, 7, 6, 5 };

    AMediaCodecCryptoInfo *ci =
            AMediaCodecCryptoInfo_new(numsubsamples, key, iv, AMEDIACODECRYPTOINFO_MODE_CLEAR, clearbytes, encryptedbytes);

    if (AMediaCodecCryptoInfo_getNumSubSamples(ci) != 4) {
        ALOGE("numsubsamples mismatch");
        return false;
    }
    uint8_t bytes[16];
    AMediaCodecCryptoInfo_getKey(ci, bytes);
    if (memcmp(key, bytes, 16) != 0) {
        ALOGE("key mismatch");
        return false;
    }
    AMediaCodecCryptoInfo_getIV(ci, bytes);
    if (memcmp(iv, bytes, 16) != 0) {
        ALOGE("IV mismatch");
        return false;
    }
    if (AMediaCodecCryptoInfo_getMode(ci) != AMEDIACODECRYPTOINFO_MODE_CLEAR) {
        ALOGE("mode mismatch");
        return false;
    }
    size_t sizes[numsubsamples];
    AMediaCodecCryptoInfo_getClearBytes(ci, sizes);
    if (memcmp(clearbytes, sizes, sizeof(size_t) * numsubsamples)) {
        ALOGE("clear size mismatch");
        return false;
    }
    AMediaCodecCryptoInfo_getEncryptedBytes(ci, sizes);
    if (memcmp(encryptedbytes, sizes, sizeof(size_t) * numsubsamples)) {
        ALOGE("encrypted size mismatch");
        return false;
    }
    return true;
}

extern "C" jlong Java_android_media_cts_NativeDecoderTest_createAMediaExtractor(JNIEnv * /*env*/,
        jclass /*clazz*/) {
    AMediaExtractor *ex = AMediaExtractor_new();
    return reinterpret_cast<jlong>(ex);
}

extern "C" jlong Java_android_media_cts_NativeDecoderTest_createAMediaDataSource(JNIEnv * env,
        jclass /*clazz*/, jstring jurl) {
    const char *url = env->GetStringUTFChars(jurl, NULL);
    if (url == NULL) {
        ALOGE("GetStringUTFChars error");
        return 0;
    }

    AMediaDataSource *ds = AMediaDataSource_newUri(url, 0, NULL);
    env->ReleaseStringUTFChars(jurl, url);
    return reinterpret_cast<jlong>(ds);
}

extern "C" jint Java_android_media_cts_NativeDecoderTest_setAMediaExtractorDataSource(JNIEnv * /*env*/,
        jclass /*clazz*/, jlong jex, jlong jds) {
    AMediaExtractor *ex = reinterpret_cast<AMediaExtractor *>(jex);
    AMediaDataSource *ds = reinterpret_cast<AMediaDataSource *>(jds);
    return AMediaExtractor_setDataSourceCustom(ex, ds);
}

extern "C" void Java_android_media_cts_NativeDecoderTest_closeAMediaDataSource(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong ds) {
    AMediaDataSource_close(reinterpret_cast<AMediaDataSource *>(ds));
}

extern "C" void Java_android_media_cts_NativeDecoderTest_deleteAMediaExtractor(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong ex) {
    AMediaExtractor_delete(reinterpret_cast<AMediaExtractor *>(ex));
}

extern "C" void Java_android_media_cts_NativeDecoderTest_deleteAMediaDataSource(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong ds) {
    AMediaDataSource_delete(reinterpret_cast<AMediaDataSource *>(ds));
}
//
// === NdkMediaCodec

extern "C" jlong Java_android_media_cts_NdkMediaCodec_AMediaCodecCreateCodecByName(
        JNIEnv *env, jclass /*clazz*/, jstring name) {

    if (name == NULL) {
        return 0;
    }

    const char *tmp = env->GetStringUTFChars(name, NULL);
    if (tmp == NULL) {
        return 0;
    }

    AMediaCodec *codec = AMediaCodec_createCodecByName(tmp);
    if (codec == NULL) {
        env->ReleaseStringUTFChars(name, tmp);
        return 0;
    }

    env->ReleaseStringUTFChars(name, tmp);
    return reinterpret_cast<jlong>(codec);

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecDelete(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong codec) {
    media_status_t err = AMediaCodec_delete(reinterpret_cast<AMediaCodec *>(codec));
    return err == AMEDIA_OK;
}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecStart(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong codec) {
    media_status_t err = AMediaCodec_start(reinterpret_cast<AMediaCodec *>(codec));
    return err == AMEDIA_OK;
}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecStop(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong codec) {
    media_status_t err = AMediaCodec_stop(reinterpret_cast<AMediaCodec *>(codec));
    return err == AMEDIA_OK;
}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecConfigure(
        JNIEnv *env,
        jclass /*clazz*/,
        jlong codec,
        jstring mime,
        jint width,
        jint height,
        jint colorFormat,
        jint bitRate,
        jint frameRate,
        jint iFrameInterval,
        jobject csd0,
        jobject csd1,
        jint flags,
        jint lowLatency,
        jobject surface,
        jint range,
        jint standard,
        jint transfer) {

    AMediaFormat* format = AMediaFormat_new();
    if (format == NULL) {
        return false;
    }

    const char *tmp = env->GetStringUTFChars(mime, NULL);
    if (tmp == NULL) {
        AMediaFormat_delete(format);
        return false;
    }

    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, tmp);
    env->ReleaseStringUTFChars(mime, tmp);

    const char *keys[] = {
            AMEDIAFORMAT_KEY_WIDTH,
            AMEDIAFORMAT_KEY_HEIGHT,
            AMEDIAFORMAT_KEY_COLOR_FORMAT,
            AMEDIAFORMAT_KEY_BIT_RATE,
            AMEDIAFORMAT_KEY_FRAME_RATE,
            AMEDIAFORMAT_KEY_I_FRAME_INTERVAL,
            // need to specify the actual string, since this test needs
            // to run on API 29, where the symbol doesn't exist
            "low-latency", // AMEDIAFORMAT_KEY_LOW_LATENCY
            AMEDIAFORMAT_KEY_COLOR_RANGE,
            AMEDIAFORMAT_KEY_COLOR_STANDARD,
            AMEDIAFORMAT_KEY_COLOR_TRANSFER,
    };

    jint values[] = {width, height, colorFormat, bitRate, frameRate, iFrameInterval, lowLatency,
                     range, standard, transfer};
    for (size_t i = 0; i < sizeof(values) / sizeof(values[0]); i++) {
        if (values[i] >= 0) {
            AMediaFormat_setInt32(format, keys[i], values[i]);
        }
    }

    if (csd0 != NULL) {
        void *csd0Ptr = env->GetDirectBufferAddress(csd0);
        jlong csd0Size = env->GetDirectBufferCapacity(csd0);
        AMediaFormat_setBuffer(format, "csd-0", csd0Ptr, csd0Size);
    }

    if (csd1 != NULL) {
        void *csd1Ptr = env->GetDirectBufferAddress(csd1);
        jlong csd1Size = env->GetDirectBufferCapacity(csd1);
        AMediaFormat_setBuffer(format, "csd-1", csd1Ptr, csd1Size);
    }

    media_status_t err = AMediaCodec_configure(
            reinterpret_cast<AMediaCodec *>(codec),
            format,
            surface == NULL ? NULL : ANativeWindow_fromSurface(env, surface),
            NULL,
            flags);

    AMediaFormat_delete(format);
    return err == AMEDIA_OK;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecSetInputSurface(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jobject surface) {

    media_status_t err = AMediaCodec_setInputSurface(
            reinterpret_cast<AMediaCodec *>(codec),
            ANativeWindow_fromSurface(env, surface));

    return err == AMEDIA_OK;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecSetNativeInputSurface(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec, jlong nativeWindow) {

    media_status_t err = AMediaCodec_setInputSurface(
            reinterpret_cast<AMediaCodec *>(codec),
            reinterpret_cast<ANativeWindow *>(nativeWindow));

    return err == AMEDIA_OK;

}

extern "C" jlong Java_android_media_cts_NdkMediaCodec_AMediaCodecCreateInputSurface(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec) {

    ANativeWindow *nativeWindow;
    media_status_t err = AMediaCodec_createInputSurface(
            reinterpret_cast<AMediaCodec *>(codec),
            &nativeWindow);

     if (err == AMEDIA_OK) {
         return reinterpret_cast<jlong>(nativeWindow);
     }

     return 0;

}

extern "C" jlong Java_android_media_cts_NdkMediaCodec_AMediaCodecCreatePersistentInputSurface(
        JNIEnv* /*env*/, jclass /*clazz*/) {

    ANativeWindow *nativeWindow;
    media_status_t err = AMediaCodec_createPersistentInputSurface(&nativeWindow);

     if (err == AMEDIA_OK) {
         return reinterpret_cast<jlong>(nativeWindow);
     }

     return 0;

}

extern "C" jstring Java_android_media_cts_NdkMediaCodec_AMediaCodecGetOutputFormatString(
        JNIEnv* env, jclass /*clazz*/, jlong codec) {

    AMediaFormat *format = AMediaCodec_getOutputFormat(reinterpret_cast<AMediaCodec *>(codec));
    const char *str = AMediaFormat_toString(format);
    jstring jstr = env->NewStringUTF(str);
    AMediaFormat_delete(format);
    return jstr;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecSignalEndOfInputStream(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec) {

    media_status_t err = AMediaCodec_signalEndOfInputStream(reinterpret_cast<AMediaCodec *>(codec));
    return err == AMEDIA_OK;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecReleaseOutputBuffer(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec, jint index, jboolean render) {

    media_status_t err = AMediaCodec_releaseOutputBuffer(
            reinterpret_cast<AMediaCodec *>(codec),
            index,
            render);

    return err == AMEDIA_OK;

}

static jobject AMediaCodecGetBuffer(
        JNIEnv* env,
        jlong codec,
        jint index,
        uint8_t *(*getBuffer)(AMediaCodec*, size_t, size_t*)) {

    size_t bufsize;
    uint8_t *buf = getBuffer(
            reinterpret_cast<AMediaCodec *>(codec),
            index,
            &bufsize);

    return env->NewDirectByteBuffer(buf, bufsize);

}

extern "C" jobject Java_android_media_cts_NdkMediaCodec_AMediaCodecGetOutputBuffer(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jint index) {

    return AMediaCodecGetBuffer(env, codec, index, AMediaCodec_getOutputBuffer);

}

extern "C" jlongArray Java_android_media_cts_NdkMediaCodec_AMediaCodecDequeueOutputBuffer(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jlong timeoutUs) {

    AMediaCodecBufferInfo info;
    memset(&info, 0, sizeof(info));
    int status = AMediaCodec_dequeueOutputBuffer(
        reinterpret_cast<AMediaCodec *>(codec),
        &info,
        timeoutUs);

    jlong ret[5] = {0};
    ret[0] = status;
    ret[1] = 0; // NdkMediaCodec calls ABuffer::data, which already adds offset
    ret[2] = info.size;
    ret[3] = info.presentationTimeUs;
    ret[4] = info.flags;

    jlongArray jret = env->NewLongArray(5);
    env->SetLongArrayRegion(jret, 0, 5, ret);
    return jret;

}

extern "C" jobject Java_android_media_cts_NdkMediaCodec_AMediaCodecGetInputBuffer(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jint index) {

    return AMediaCodecGetBuffer(env, codec, index, AMediaCodec_getInputBuffer);

}

extern "C" jint Java_android_media_cts_NdkMediaCodec_AMediaCodecDequeueInputBuffer(
        JNIEnv* /*env*/, jclass /*clazz*/, jlong codec, jlong timeoutUs) {

    return AMediaCodec_dequeueInputBuffer(
            reinterpret_cast<AMediaCodec *>(codec),
            timeoutUs);

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecQueueInputBuffer(
        JNIEnv* /*env*/,
        jclass /*clazz*/,
        jlong codec,
        jint index,
        jint offset,
        jint size,
        jlong presentationTimeUs,
        jint flags) {

    media_status_t err = AMediaCodec_queueInputBuffer(
            reinterpret_cast<AMediaCodec *>(codec),
            index,
            offset,
            size,
            presentationTimeUs,
            flags);

    return err == AMEDIA_OK;

}

extern "C" jboolean Java_android_media_cts_NdkMediaCodec_AMediaCodecSetParameter(
        JNIEnv* env, jclass /*clazz*/, jlong codec, jstring jkey, jint value) {

    AMediaFormat* params = AMediaFormat_new();
    if (params == NULL) {
        return false;
    }

    const char *key = env->GetStringUTFChars(jkey, NULL);
    if (key == NULL) {
        AMediaFormat_delete(params);
        return false;
    }

    AMediaFormat_setInt32(params, key, value);
    media_status_t err = AMediaCodec_setParameters(
            reinterpret_cast<AMediaCodec *>(codec),
            params);
    env->ReleaseStringUTFChars(jkey, key);
    AMediaFormat_delete(params);
    return err == AMEDIA_OK;

}

// === NdkInputSurface

extern "C" jlong Java_android_media_cts_NdkInputSurface_eglGetDisplay(JNIEnv * /*env*/, jclass /*clazz*/) {

    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) {
        return 0;
    }

    EGLint major, minor;
    if (!eglInitialize(eglDisplay, &major, &minor)) {
        return 0;
    }

    return reinterpret_cast<jlong>(eglDisplay);

}

extern "C" jlong Java_android_media_cts_NdkInputSurface_eglChooseConfig(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay) {

    // Configure EGL for recordable and OpenGL ES 2.0.  We want enough RGB bits
    // to minimize artifacts from possible YUV conversion.
    EGLint attribList[] = {
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL_NONE
    };

    EGLConfig configs[1];
    EGLint numConfigs[1];
    if (!eglChooseConfig(reinterpret_cast<EGLDisplay>(eglDisplay), attribList, configs, 1, numConfigs)) {
        return 0;
    }
    return reinterpret_cast<jlong>(configs[0]);

}

extern "C" jlong Java_android_media_cts_NdkInputSurface_eglCreateContext(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglConfig) {

    // Configure context for OpenGL ES 2.0.
    int attrib_list[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_NONE
    };

    EGLConfig eglContext = eglCreateContext(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLConfig>(eglConfig),
            EGL_NO_CONTEXT,
            attrib_list);

    if (eglGetError() != EGL_SUCCESS) {
        return 0;
    }

    return reinterpret_cast<jlong>(eglContext);

}

extern "C" jlong Java_android_media_cts_NdkInputSurface_createEGLSurface(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglConfig, jlong nativeWindow) {

    int surfaceAttribs[] = {EGL_NONE};
    EGLSurface eglSurface = eglCreateWindowSurface(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLConfig>(eglConfig),
            reinterpret_cast<EGLNativeWindowType>(nativeWindow),
            surfaceAttribs);

    if (eglGetError() != EGL_SUCCESS) {
        return 0;
    }

    return reinterpret_cast<jlong>(eglSurface);

}

extern "C" jboolean Java_android_media_cts_NdkInputSurface_eglMakeCurrent(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface, jlong eglContext) {

    return eglMakeCurrent(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface),
            reinterpret_cast<EGLSurface>(eglSurface),
            reinterpret_cast<EGLContext>(eglContext));

}

extern "C" jboolean Java_android_media_cts_NdkInputSurface_eglSwapBuffers(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface) {

    return eglSwapBuffers(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface));

}

extern "C" jboolean Java_android_media_cts_NdkInputSurface_eglPresentationTimeANDROID(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface, jlong nsecs) {

    return eglPresentationTimeANDROID(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface),
            reinterpret_cast<EGLnsecsANDROID>(nsecs));

}

extern "C" jint Java_android_media_cts_NdkInputSurface_eglGetWidth(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface) {

    EGLint width;
    eglQuerySurface(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface),
            EGL_WIDTH,
            &width);

    return width;

}

extern "C" jint Java_android_media_cts_NdkInputSurface_eglGetHeight(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface) {

    EGLint height;
    eglQuerySurface(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface),
            EGL_HEIGHT,
            &height);

    return height;

}

extern "C" jboolean Java_android_media_cts_NdkInputSurface_eglDestroySurface(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface) {

    return eglDestroySurface(
            reinterpret_cast<EGLDisplay>(eglDisplay),
            reinterpret_cast<EGLSurface>(eglSurface));

}

extern "C" void Java_android_media_cts_NdkInputSurface_nativeRelease(
        JNIEnv * /*env*/, jclass /*clazz*/, jlong eglDisplay, jlong eglSurface, jlong eglContext, jlong nativeWindow) {

    if (eglDisplay != 0) {

        EGLDisplay _eglDisplay = reinterpret_cast<EGLDisplay>(eglDisplay);
        EGLSurface _eglSurface = reinterpret_cast<EGLSurface>(eglSurface);
        EGLContext _eglContext = reinterpret_cast<EGLContext>(eglContext);

        eglDestroySurface(_eglDisplay, _eglSurface);
        eglDestroyContext(_eglDisplay, _eglContext);
        eglReleaseThread();
        eglTerminate(_eglDisplay);

    }

    ANativeWindow_release(reinterpret_cast<ANativeWindow *>(nativeWindow));

}

extern "C" jboolean Java_android_media_cts_NativeDecoderTest_testMediaFormatNative(
        JNIEnv * /*env*/, jclass /*clazz*/) {

    AMediaFormat *original = AMediaFormat_new();
    AMediaFormat *copy = AMediaFormat_new();
    jboolean ret = false;
    while (true) {
        AMediaFormat_setInt64(original, AMEDIAFORMAT_KEY_DURATION, 1234ll);
        int64_t value = 0;
        if (!AMediaFormat_getInt64(original, AMEDIAFORMAT_KEY_DURATION, &value) || value != 1234) {
            ALOGE("format missing expected entry");
            break;
        }
        AMediaFormat_copy(copy, original);
        value = 0;
        if (!AMediaFormat_getInt64(copy, AMEDIAFORMAT_KEY_DURATION, &value) || value != 1234) {
            ALOGE("copied format missing expected entry");
            break;
        }
        AMediaFormat_clear(original);
        if (AMediaFormat_getInt64(original, AMEDIAFORMAT_KEY_DURATION, &value)) {
            ALOGE("format still has entry after clear");
            break;
        }
        value = 0;
        if (!AMediaFormat_getInt64(copy, AMEDIAFORMAT_KEY_DURATION, &value) || value != 1234) {
            ALOGE("copied format missing expected entry");
            break;
        }
        ret = true;
        break;
    }
    AMediaFormat_delete(original);
    AMediaFormat_delete(copy);
    return ret;
}
