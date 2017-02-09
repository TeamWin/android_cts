/*
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

#include <atomic>
#include <inttypes.h>
#include <stdio.h>
#include <string>
#include <time.h>
#include <vector>

#include <jni.h>

#include <pthread.h>

#include <utils/Errors.h>
//#define LOG_NDEBUG 0
#define LOG_TAG "NativeMidiManager-JNI"
#include <utils/Log.h>

#include <midi/midi.h>

extern "C" {

/*
 * Structures for storing data flowing through the echo server.
 */
/*
 * Received Messages
 */
typedef struct {
    AMIDI_Message message;
    long timeReceived;
} ReceivedMessageRecord;

/*
 * Sent Messages
 */
typedef struct {
    uint8_t buffer[AMIDI_BUFFER_SIZE];
    size_t len;
    uint64_t timestamp;
    long timeSent;
} SentMessageRecord;

/*
 * Context
 * Holds the state of a given test and native MIDI I/O setup for that test.
 */
class TestContext {
private:
    // counters
    std::atomic<int> mNumSends;
    std::atomic<int> mNumBytesSent;
    std::atomic<int> mNumReceives;
    std::atomic<int> mNumBytesReceived;

    pthread_mutex_t mLock;
    std::vector<ReceivedMessageRecord> mReceivedMsgs;
    std::vector<SentMessageRecord> mSentMsgs;

    // Java NativeMidiMessage class stuff, for passing messages back out to the Java client.
    jclass mClsNativeMidiMessage;
    jmethodID mMidNativeMidiMessage_ctor;
    jfieldID mFid_opcode;
    jfieldID mFid_buffer;
    jfieldID mFid_len;
    jfieldID mFid_timestamp;
    jfieldID mFid_timeReceived;

public:
    // read Thread
    pthread_t mReadThread;
    std::atomic<bool> mReading;

    AMIDI_Device nativeReceiveDevice;
    std::atomic<AMIDI_OutputPort> midiOutputPort;

    AMIDI_Device nativeSendDevice;
    std::atomic<AMIDI_InputPort> midiInputPort;

    TestContext() :
        mNumSends(0),
        mNumBytesSent(0),
        mNumReceives(0),
        mNumBytesReceived(0),
        mClsNativeMidiMessage(0),
        mMidNativeMidiMessage_ctor(0),
        mFid_opcode(0),
        mFid_buffer(0),
        mFid_len(0),
        mFid_timestamp(0),
        mFid_timeReceived(0),
        mReadThread(0),
        mReading(false),
        nativeReceiveDevice(AMIDI_INVALID_HANDLE),
        nativeSendDevice(AMIDI_INVALID_HANDLE)
   {
        pthread_mutex_init(&mLock, (const pthread_mutexattr_t *) NULL);

        midiOutputPort.store(AMIDI_INVALID_HANDLE);
        midiInputPort.store(AMIDI_INVALID_HANDLE);
    }

    void clearCounters() {
        mNumSends = 0;
        mNumBytesSent = 0;
        mNumReceives = 0;
        mNumBytesReceived = 0;
    }

    int getNumSends() { return mNumSends; }
    void incNumSends() { mNumSends++; }

    int getNumBytesSent() { return mNumBytesSent; }
    void incNumBytesSent(int numBytes) { mNumBytesSent += numBytes; }

    int getNumReceives() { return mNumReceives; }
    void incNumReceives() { mNumReceives++; }

    int getNumBytesReceived() { return mNumBytesReceived; }
    void incNumBytesReceived(int numBytes) { mNumBytesReceived += numBytes; }

    void addSent(SentMessageRecord msg) {
        pthread_mutex_lock(&mLock);
        mSentMsgs.push_back(msg);
        pthread_mutex_unlock(&mLock);
    }
    size_t getNumSentMsgs() {
        pthread_mutex_lock(&mLock);
        size_t numMsgs = mSentMsgs.size();
        pthread_mutex_unlock(&mLock);
        return numMsgs;
    }

    bool initN(JNIEnv* j_env);

    void addRecieved(ReceivedMessageRecord msg) {
        pthread_mutex_lock(&mLock);
        mReceivedMsgs.push_back(msg);
        pthread_mutex_unlock(&mLock);
    }
    size_t getNumReceivedMsgs() {
        pthread_mutex_lock(&mLock);
        size_t numMsgs = mReceivedMsgs.size();
        pthread_mutex_unlock(&mLock);
        return numMsgs;
    }

    jobject j_getReceiveMsgAt(JNIEnv* j_env, int index);

    static const int COMPARE_SUCCESS = 0;
    static const int COMPARE_COUNTMISSMATCH = 1;
    static const int COMPARE_DATALENMISMATCH = 2;
    static const int COMPARE_DATAMISMATCH = 3;
    static const int COMPARE_TIMESTAMPMISMATCH = 4;
    int compareInsAndOuts();

    static const int CHECKLATENCY_SUCCESS = 0;
    static const int CHECKLATENCY_COUNTMISSMATCH = 1;
    static const int CHECKLATENCY_LATENCYEXCEEDED = 2;
    int checkInOutLatency(long maxLatencyNanos);
};

//
// Helpers
//
static long System_nanoTime() {
    // this code is the implementation of System.nanoTime()
    // from system/code/ojluni/src/main/native/System.
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return now.tv_sec * 1000000000LL + now.tv_nsec;
}

bool TestContext::initN(JNIEnv* j_env) {
    static const char* clsSigNativeMidiMessage = "android/nativemidi/cts/NativeMidiMessage";

    mClsNativeMidiMessage =
            (jclass)j_env->NewGlobalRef(j_env->FindClass(clsSigNativeMidiMessage));
    if (mClsNativeMidiMessage == 0) {
        return false;
    }
    mMidNativeMidiMessage_ctor = j_env->GetMethodID(mClsNativeMidiMessage, "<init>", "()V");

    mFid_opcode = j_env->GetFieldID(mClsNativeMidiMessage, "opcode", "I");
    mFid_buffer = j_env->GetFieldID(mClsNativeMidiMessage, "buffer", "[B");
    mFid_len = j_env->GetFieldID(mClsNativeMidiMessage, "len", "I");
    mFid_timestamp = j_env->GetFieldID(mClsNativeMidiMessage, "timestamp", "J");
    mFid_timeReceived = j_env->GetFieldID(mClsNativeMidiMessage, "timeReceived", "J");
//    ALOGI("---- [%p, %p, %p, %p, %p]",
//        mFid_opcode, mFid_buffer, mFid_len, mFid_timestamp, mFid_timeReceived);
    return mMidNativeMidiMessage_ctor != 0 &&
            mFid_opcode != 0 &&
            mFid_buffer != 0 &&
            mFid_len != 0 &&
            mFid_timestamp != 0 &&
            mFid_timeReceived != 0;

    return false;
}

jobject TestContext::j_getReceiveMsgAt(JNIEnv* j_env, int index) {
    jobject msg = NULL;

    pthread_mutex_lock(&mLock);

    if (index < (int)mReceivedMsgs.size()) {
        ReceivedMessageRecord receiveRec = mReceivedMsgs.at(index);
        AMIDI_Message amidi_msg = receiveRec.message;

        msg = j_env->NewObject(mClsNativeMidiMessage, mMidNativeMidiMessage_ctor);

        j_env->SetIntField(msg, mFid_opcode, amidi_msg.opcode);
        j_env->SetIntField(msg, mFid_len, amidi_msg.len);
        jobject buffer_array = j_env->GetObjectField(msg, mFid_buffer);
        j_env->SetByteArrayRegion(reinterpret_cast<jbyteArray>(buffer_array), 0,
                sizeof(amidi_msg.buffer), (jbyte*)amidi_msg.buffer);
        j_env->SetLongField(msg, mFid_timestamp, amidi_msg.timestamp);
        j_env->SetLongField(msg, mFid_timeReceived, receiveRec.timeReceived);
    }

    pthread_mutex_unlock(&mLock);
    return msg;
}

int TestContext::compareInsAndOuts() {
    // Number of messages sent/received
    pthread_mutex_lock(&mLock);

    if (mReceivedMsgs.size() != mSentMsgs.size()) {
        ALOGE("---- COMPARE_COUNTMISSMATCH r:%zu s:%zu", mReceivedMsgs.size(), mSentMsgs.size());
        pthread_mutex_unlock(&mLock);
       return COMPARE_COUNTMISSMATCH;
    }

    // we know that both vectors have the same number of messages from the test above.
    size_t numMessages = mSentMsgs.size();
    for (size_t msgIndex = 0; msgIndex < numMessages; msgIndex++) {
        // Data Length?
        if (mReceivedMsgs[msgIndex].message.len != mSentMsgs[msgIndex].len) {
            ALOGE("---- COMPARE_DATALENMISMATCH r:%zu s:%zu",
                    mReceivedMsgs[msgIndex].message.len, mSentMsgs[msgIndex].len);
            pthread_mutex_unlock(&mLock);
            return COMPARE_DATALENMISMATCH;
        }

        // Timestamps
        if (mReceivedMsgs[msgIndex].message.timestamp != mSentMsgs[msgIndex].timestamp) {
            ALOGE("---- COMPARE_TIMESTAMPMISMATCH");
            pthread_mutex_unlock(&mLock);
            return COMPARE_TIMESTAMPMISMATCH;
        }

        // we know that the data in both messages have the same number of bytes from the test above.
        int dataLen = mReceivedMsgs[msgIndex].message.len;
        for (int dataIndex = 0; dataIndex < dataLen; dataIndex++) {
            // Data Values?
            if (mReceivedMsgs[msgIndex].message.buffer[dataIndex] !=
                    mSentMsgs[msgIndex].buffer[dataIndex]) {
                ALOGE("---- COMPARE_DATAMISMATCH r:%d s:%d",
                        (int)mReceivedMsgs[msgIndex].message.buffer[dataIndex],
                        (int)mSentMsgs[msgIndex].buffer[dataIndex]);
                pthread_mutex_unlock(&mLock);
                return COMPARE_DATAMISMATCH;
            }
        }
    }

    pthread_mutex_unlock(&mLock);
    return COMPARE_SUCCESS;
}

int TestContext::checkInOutLatency(long maxLatencyNanos) {
    pthread_mutex_lock(&mLock);
    if (mReceivedMsgs.size() != mSentMsgs.size()) {
        pthread_mutex_unlock(&mLock);
        return CHECKLATENCY_COUNTMISSMATCH;
    }

    // we know that both vectors have the same number of messages
    // from the test above.
    int numMessages = mSentMsgs.size();
    for (int msgIndex = 0; msgIndex < numMessages; msgIndex++) {
        long timeDelta =  mSentMsgs[msgIndex].timeSent - mReceivedMsgs[msgIndex].timeReceived;
        // ALOGI("---- timeDelta:%ld", timeDelta);
        if (timeDelta > maxLatencyNanos) {
            pthread_mutex_unlock(&mLock);
            return CHECKLATENCY_LATENCYEXCEEDED;
        }
    }

    pthread_mutex_unlock(&mLock);
    return CHECKLATENCY_SUCCESS;
}

JNIEXPORT jlong JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_allocTestContext(
        JNIEnv* j_env, jclass) {
    TestContext* context = new TestContext;
    if (!context->initN(j_env)) {
        delete context;
        context = NULL;
    }

    return (jlong)context;
}

JNIEXPORT void JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_freeTestContext(
        JNIEnv*, jclass, jlong context) {
    delete (TestContext*)context;
}

/*
 * Receiving API
 */
//static void DumpDataMessage(AMIDI_Message* msg) {
//    char midiDumpBuffer[AMIDI_BUFFER_SIZE * 4]; // more than enough
//    memset(midiDumpBuffer, 0, sizeof(midiDumpBuffer));
//    int pos = snprintf(midiDumpBuffer, sizeof(midiDumpBuffer),
//            "%" PRIx64 " ", msg->timestamp);
//    for (uint8_t *b = msg->buffer, *e = b + msg->len; b < e; ++b) {
//        pos += snprintf(midiDumpBuffer + pos, sizeof(midiDumpBuffer) - pos,
//                "%02x ", *b);
//    }
//    ALOGI("---- DUMP %s", midiDumpBuffer);
//}

void* readThreadRoutine(void * ctx) {
    TestContext* context  = (TestContext*)ctx;

    context->mReading = true;
    while (context->mReading) {
        AMIDI_OutputPort outputPort = context->midiOutputPort.load();
        if (outputPort != AMIDI_INVALID_HANDLE) {
            // Amount of messages we are ready to handle during one callback cycle.
            static const size_t MAX_INCOMING_MIDI_MESSAGES = 20;
            AMIDI_Message incomingMidiMessages[MAX_INCOMING_MIDI_MESSAGES];
            ssize_t midiReceived = AMIDI_receive(
                    outputPort, incomingMidiMessages, MAX_INCOMING_MIDI_MESSAGES);
            if (midiReceived >= 0) {
                for (ssize_t i = 0; i < midiReceived; ++i) {
                    AMIDI_Message* msg = &incomingMidiMessages[i];
                    if (msg->opcode == AMIDI_OPCODE_DATA) {
                        // DumpDataMessage(msg);
                        context->incNumReceives();
                        context->incNumBytesReceived(msg->len);
                        ReceivedMessageRecord receiveRec;
                        receiveRec.message = *msg;
                        receiveRec.timeReceived = System_nanoTime();
                        context->addRecieved(receiveRec);
                    } else if (msg->opcode == AMIDI_OPCODE_FLUSH) {
                        ALOGI("---- FLUSH %s", "MIDI flush");
                    }
                }
            } else {
                ALOGE("---- ! MIDI Receive error: %s !", strerror(-midiReceived));
            }
        }
    }

    return NULL;
}

static int commonDeviceOpen(int deviceId, AMIDI_Device* device) {
    int result = AMIDI_getDeviceByUid(deviceId, device);
    if (result == 0) {
        ALOGI("----   Obtained device token for uid %d: token %d", deviceId, *device);
    } else {
        ALOGE("----   Could not obtain device token for uid %d: result:%d", deviceId, result);
        return result;
    }

    AMIDI_DeviceInfo deviceInfo;
    result = AMIDI_getDeviceInfo(*device, &deviceInfo);
    if (result == 0) {
        ALOGI("----   Device info: uid %d, type %d, priv %d, ports %d I / %d O",
                deviceInfo.uid, deviceInfo.type, deviceInfo.is_private,
                (int)deviceInfo.input_port_count, (int)deviceInfo.output_port_count);
    } else {
        ALOGE("----   Could not obtain device info %d", result);
    }

    return result;
}

/*
 * Sending API
 */
JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_startWritingMidi(
        JNIEnv*, jobject, jlong ctx, jint deviceId, jint portNumber) {

    TestContext* context = (TestContext*)ctx;

    int result = commonDeviceOpen(deviceId, &context->nativeSendDevice);
    if (result != 0) {
        return result;
    }

    AMIDI_InputPort inputPort;
    result = AMIDI_openInputPort(context->nativeSendDevice, portNumber, &inputPort);
    if (result == 0) {
        ALOGI("---- Opened INPUT port %d: token %d", portNumber, inputPort);
        context->midiInputPort.store(inputPort);
    } else {
        ALOGE("---- Could not open INPUT port %d: %d", deviceId, result);
        return result;
    }

    return 0;
}

JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_stopWritingMidi(
        JNIEnv*, jobject, jlong ctx) {

    TestContext* context = (TestContext*)ctx;

    AMIDI_InputPort inputPort = context->midiInputPort.exchange(AMIDI_INVALID_HANDLE);
    if (inputPort == AMIDI_INVALID_HANDLE) {
        return android::BAD_VALUE;
    }

    int result = AMIDI_closeInputPort(inputPort);
    if (result == 0) {
        ALOGI("---- Closed port by token %d", inputPort);
    } else {
        ALOGE("---- Could not close port by token %d: %d", inputPort, result);
    }

    return result;
}

JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_getMaxWriteBufferSize(
        JNIEnv*, jobject, jlong ctx) {
    TestContext* context = (TestContext*)ctx;
    return AMIDI_getMaxMessageSizeInBytes(context->midiInputPort);
}

JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_writeMidiWithTimestamp(
        JNIEnv* j_env, jobject,
        jlong ctx, jbyteArray data, jint offset, jint numBytes, jlong timestamp) {

    TestContext* context = (TestContext*)ctx;
    context->incNumSends();
    context->incNumBytesSent(numBytes);

    jbyte* bufferPtr = j_env->GetByteArrayElements(data, NULL);
    if (bufferPtr == NULL) {
        return android::NO_MEMORY;
    }

    int numWritten = AMIDI_sendWithTimestamp(
            context->midiInputPort, (uint8_t*)bufferPtr + offset, numBytes, timestamp);
    if (numWritten > 0) {
        // Don't save a send record if we didn't send!
        SentMessageRecord sendRec;
        memcpy(sendRec.buffer, (uint8_t*)bufferPtr + offset, numBytes);
        sendRec.len = numBytes;
        sendRec.timestamp = timestamp;
        sendRec.timeSent = System_nanoTime();
        context->addSent(sendRec);
    }

    j_env->ReleaseByteArrayElements(data, bufferPtr, JNI_ABORT);

    return numWritten;
}

JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_writeMidi(
        JNIEnv* j_env, jobject j_object, jlong ctx, jbyteArray data, jint offset, jint numBytes) {
    return Java_android_nativemidi_cts_NativeMidiEchoTest_writeMidiWithTimestamp(
            j_env, j_object, ctx, data, offset, numBytes, 0L);
}

JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_flushSentMessages(
        JNIEnv*, jobject, jlong ctx) {
    TestContext* context = (TestContext*)ctx;
    return AMIDI_flush(context->midiInputPort);
}

JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_getNumSends(
        JNIEnv*, jobject, jlong ctx) {
    return ((TestContext*)ctx)->getNumSends();
}

JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_getNumBytesSent(
        JNIEnv*, jobject, jlong ctx) {
    return ((TestContext*)ctx)->getNumBytesSent();
}

JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_getNumReceives(
        JNIEnv*, jobject, jlong ctx) {
    return ((TestContext*)ctx)->getNumReceives();
}

JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_getNumBytesReceived(
        JNIEnv*, jobject, jlong ctx) {
    return ((TestContext*)ctx)->getNumBytesReceived();
}

JNIEXPORT void JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_clearCounters(
        JNIEnv*, jobject, jlong ctx) {
    ((TestContext*)ctx)->clearCounters();
}

JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_startReadingMidi(
        JNIEnv*, jobject, jlong ctx, jint deviceId, jint portNumber) {

    TestContext* context = (TestContext*)ctx;

    int result = commonDeviceOpen(deviceId, &context->nativeReceiveDevice);
    if (result != 0) {
        return result;
    }

    AMIDI_OutputPort outputPort;
    result = AMIDI_openOutputPort(context->nativeReceiveDevice, portNumber, &outputPort);
    if (result == 0) {
        ALOGI("---- Opened OUTPUT port %d: token %d", portNumber, outputPort);
        context->midiOutputPort.store(outputPort);
    } else {
        ALOGE("---- Could not open OUTPUT port %d: %d", deviceId, result);
        return result;
    }

    // Start read thread
    int pthread_result =
            pthread_create(&context->mReadThread, NULL, readThreadRoutine, (void*)context);
    if (pthread_result != 0) {
        ALOGE("---- pthread_create() Error %d, 0x%X", pthread_result, pthread_result);
    }

    return result;
}

JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_stopReadingMidi(
        JNIEnv*, jobject, jlong ctx) {

    TestContext* context = (TestContext*)ctx;
    context->mReading = false;

    AMIDI_OutputPort outputPort = context->midiOutputPort.exchange(AMIDI_INVALID_HANDLE);
    if (outputPort == AMIDI_INVALID_HANDLE) {
        return android::BAD_VALUE;
    }

    int result = AMIDI_closeOutputPort(outputPort);
    if (result == 0) {
        ALOGI("---- Closed OUTPUT port by token %d", outputPort);
    } else {
        ALOGI("---- Could not close port by token %d: %d", outputPort, result);
    }

    return result;
}

/*
 * Messages
 */
JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_getNumReceivedMessages(
        JNIEnv*, jobject, jlong ctx) {
    return ((TestContext*)ctx)->getNumReceivedMsgs();
}

JNIEXPORT jobject JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_getReceivedMessageAt(
        JNIEnv* j_env, jobject, jlong ctx, jint index) {
    return ((TestContext*)ctx)->j_getReceiveMsgAt(j_env, index);
}

JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_matchNativeMessages(
        JNIEnv*, jobject, jlong ctx) {
    return ((TestContext*)ctx)->compareInsAndOuts();
}

JNIEXPORT jint JNICALL Java_android_nativemidi_cts_NativeMidiEchoTest_checkNativeLatency(
        JNIEnv*, jobject, jlong ctx, jlong maxLatencyNanos) {
    return ((TestContext*)ctx)->checkInOutLatency(maxLatencyNanos);
}

} // extern "C"
