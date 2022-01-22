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
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "media/NdkMediaExtractor.h"
#include "media/NdkMediaCrypto.h"
#include "media/NdkMediaDataSource.h"
#include "media/NdkMediaFormat.h"

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
