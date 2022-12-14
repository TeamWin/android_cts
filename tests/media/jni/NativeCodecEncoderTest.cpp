/*
 * Copyright (C) 2020 The Android Open Source Project
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
#define LOG_TAG "NativeCodecEncoderTest"
#include <log/log.h>

#include <jni.h>
#include <sys/stat.h>

#include "NativeCodecTestBase.h"
#include "NativeMediaCommon.h"

class CodecEncoderTest final : public CodecTestBase {
  private:
    uint8_t* mInputData;
    size_t mInputLength;
    int mInputBufferReadOffset;
    int mNumBytesSubmitted;
    int mLoopBackFrameLimit;
    bool mIsLoopBack;
    int64_t mInputOffsetPts;
    std::vector<AMediaFormat*> mFormats;
    int mNumSyncFramesReceived;
    std::vector<int> mSyncFramesPos;

    int32_t* mBitRates;
    int mLen0;
    int32_t* mEncParamList1;
    int mLen1;
    int32_t* mEncParamList2;
    int mLen2;

    int mWidth, mHeight;
    int mChannels;
    int mSampleRate;
    int mColorFormat;
    int mMaxBFrames;
    int mDefFrameRate;
    const int kInpFrmWidth = 352;
    const int kInpFrmHeight = 288;

    void convertyuv420ptoyuv420sp();
    void setUpSource(const char* srcPath);
    void deleteSource();
    void setUpParams(int limit);
    void deleteParams();
    bool configureCodec(AMediaFormat* format, bool isAsync, bool signalEOSWithLastFrame,
                        bool isEncoder) override;
    void resetContext(bool isAsync, bool signalEOSWithLastFrame) override;
    bool enqueueInput(size_t bufferIndex) override;
    bool dequeueOutput(size_t bufferIndex, AMediaCodecBufferInfo* bufferInfo) override;
    bool doWork(int frameLimit) override;
    bool isTestStateValid() override;
    void initFormat(AMediaFormat* format);
    bool encodeToMemory(const char* file, const char* encoder, int frameLimit, AMediaFormat* format,
                        OutputManager* ref);
    void fillByteBuffer(uint8_t* inputBuffer);
    void forceSyncFrame(AMediaFormat* format);
    void updateBitrate(AMediaFormat* format, int bitrate);

  public:
    CodecEncoderTest(const char* mime, int32_t* list0, int len0, int32_t* list1, int len1,
                     int32_t* list2, int len2, int colorFormat);
    ~CodecEncoderTest();

    bool testSimpleEncode(const char* encoder, const char* srcPath);
    bool testReconfigure(const char* encoder, const char* srcPath);
    bool testSetForceSyncFrame(const char* encoder, const char* srcPath);
    bool testAdaptiveBitRate(const char* encoder, const char* srcPath);
    bool testOnlyEos(const char* encoder);
};

CodecEncoderTest::CodecEncoderTest(const char* mime, int32_t* list0, int len0, int32_t* list1,
                                   int len1, int32_t* list2, int len2, int colorFormat)
    : CodecTestBase(mime),
      mBitRates{list0},
      mLen0{len0},
      mEncParamList1{list1},
      mLen1{len1},
      mEncParamList2{list2},
      mLen2{len2},
      mColorFormat{colorFormat} {
    mDefFrameRate = 30;
    if (!strcmp(mime, AMEDIA_MIMETYPE_VIDEO_H263)) mDefFrameRate = 12;
    else if (!strcmp(mime, AMEDIA_MIMETYPE_VIDEO_MPEG4)) mDefFrameRate = 12;
    mMaxBFrames = 0;
    mInputData = nullptr;
    mInputLength = 0;
    mInputBufferReadOffset = 0;
    mNumBytesSubmitted = 0;
    mLoopBackFrameLimit = 0;
    mIsLoopBack = false;
    mInputOffsetPts = 0;
}

CodecEncoderTest::~CodecEncoderTest() {
    deleteSource();
    deleteParams();
}

void CodecEncoderTest::convertyuv420ptoyuv420sp() {
    int ySize = kInpFrmWidth * kInpFrmHeight;
    int uSize = kInpFrmWidth * kInpFrmHeight / 4;
    int frameSize = ySize + uSize * 2;
    int totalFrames = mInputLength / frameSize;
    uint8_t* u = new uint8_t[uSize];
    uint8_t* v = new uint8_t[uSize];
    uint8_t* frameBase = mInputData;
    for (int i = 0; i < totalFrames; i++) {
        uint8_t* uvBase = frameBase + ySize;
        memcpy(u, uvBase, uSize);
        memcpy(v, uvBase + uSize, uSize);
        for (int j = 0, idx = 0; j < uSize; j++, idx += 2) {
            uvBase[idx] = u[j];
            uvBase[idx + 1] = v[j];
        }
        frameBase += frameSize;
    }
    delete[] u;
    delete[] v;
}

void CodecEncoderTest::setUpSource(const char* srcPath) {
    FILE* fp = fopen(srcPath, "rbe");
    struct stat buf {};
    if (fp && !fstat(fileno(fp), &buf)) {
        deleteSource();
        mInputLength = buf.st_size;
        mInputData = new uint8_t[mInputLength];
        fread(mInputData, sizeof(uint8_t), mInputLength, fp);
        if (mColorFormat == COLOR_FormatYUV420SemiPlanar) {
            convertyuv420ptoyuv420sp();
        }
    } else {
        ALOGE("unable to open input file %s", srcPath);
    }
    if (fp) fclose(fp);
}

void CodecEncoderTest::deleteSource() {
    if (mInputData) {
        delete[] mInputData;
        mInputData = nullptr;
    }
    mInputLength = 0;
}

void CodecEncoderTest::setUpParams(int limit) {
    int count = 0;
    for (int k = 0; k < mLen0; k++) {
        int bitrate = mBitRates[k];
        if (mIsAudio) {
            for (int j = 0; j < mLen1; j++) {
                int rate = mEncParamList1[j];
                for (int i = 0; i < mLen2; i++) {
                    int channels = mEncParamList2[i];
                    AMediaFormat* format = AMediaFormat_new();
                    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mMime);
                    if (!strcmp(mMime, AMEDIA_MIMETYPE_AUDIO_FLAC)) {
                        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FLAC_COMPRESSION_LEVEL,
                                              bitrate);
                    } else {
                        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, bitrate);
                    }
                    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, rate);
                    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, channels);
                    mFormats.push_back(format);
                    count++;
                    if (count >= limit) break;
                }
            }
        } else {
            for (int j = 0; j < mLen1; j++) {
                int width = mEncParamList1[j];
                int height = mEncParamList2[j];
                AMediaFormat* format = AMediaFormat_new();
                AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mMime);
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, bitrate);
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, width);
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, height);
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, mDefFrameRate);
                AMediaFormat_setInt32(format, TBD_AMEDIACODEC_PARAMETER_KEY_MAX_B_FRAMES,
                                      mMaxBFrames);
                AMediaFormat_setFloat(format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 1.0F);
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_FORMAT, mColorFormat);
                mFormats.push_back(format);
                count++;
                if (count >= limit) break;
            }
        }
    }
}

void CodecEncoderTest::deleteParams() {
    for (auto format : mFormats) AMediaFormat_delete(format);
    mFormats.clear();
}

bool CodecEncoderTest::configureCodec(AMediaFormat* format, bool isAsync,
                                      bool signalEOSWithLastFrame, bool isEncoder) {
    bool res = CodecTestBase::configureCodec(format, isAsync, signalEOSWithLastFrame, isEncoder);
    initFormat(format);
    return res;
}

void CodecEncoderTest::resetContext(bool isAsync, bool signalEOSWithLastFrame) {
    CodecTestBase::resetContext(isAsync, signalEOSWithLastFrame);
    mInputBufferReadOffset = 0;
    mNumBytesSubmitted = 0;
    mInputOffsetPts = 0;
    mNumSyncFramesReceived = 0;
    mSyncFramesPos.clear();
}

void CodecEncoderTest::fillByteBuffer(uint8_t* inputBuffer) {
    int width, height, tileWidth, tileHeight;
    int offset = 0, frmOffset = mInputBufferReadOffset;
    int numOfPlanes;
    if (mColorFormat == COLOR_FormatYUV420SemiPlanar) {
        numOfPlanes = 2;
    } else {
        numOfPlanes = 3;
    }
    for (int plane = 0; plane < numOfPlanes; plane++) {
        if (plane == 0) {
            width = mWidth;
            height = mHeight;
            tileWidth = kInpFrmWidth;
            tileHeight = kInpFrmHeight;
        } else {
            if (mColorFormat == COLOR_FormatYUV420SemiPlanar) {
                width = mWidth;
                tileWidth = kInpFrmWidth;
            } else {
                width = mWidth / 2;
                tileWidth = kInpFrmWidth / 2;
            }
            height = mHeight / 2;
            tileHeight = kInpFrmHeight / 2;
        }
        for (int k = 0; k < height; k += tileHeight) {
            int rowsToCopy = std::min(height - k, tileHeight);
            for (int j = 0; j < rowsToCopy; j++) {
                for (int i = 0; i < width; i += tileWidth) {
                    int colsToCopy = std::min(width - i, tileWidth);
                    memcpy(inputBuffer + (offset + (k + j) * width + i),
                           mInputData + (frmOffset + j * tileWidth), colsToCopy);
                }
            }
        }
        offset += width * height;
        frmOffset += tileWidth * tileHeight;
    }
}

bool CodecEncoderTest::enqueueInput(size_t bufferIndex) {
    if (mInputBufferReadOffset >= mInputLength) {
        if (!mIsLoopBack) return enqueueEOS(bufferIndex);
        mInputBufferReadOffset = 0; // loop back to beginning
    }
    {
        int size = 0;
        int flags = 0;
        int64_t pts = mInputOffsetPts;
        size_t buffSize;
        uint8_t* inputBuffer = AMediaCodec_getInputBuffer(mCodec, bufferIndex, &buffSize);
        RETURN_IF_TRUE(inputBuffer == nullptr,
                       std::string{"AMediaCodec_getInputBuffer returned nullptr"})
        if (mIsAudio) {
            pts += mNumBytesSubmitted * 1000000LL / (2 * mChannels * mSampleRate);
            size = std::min(buffSize, mInputLength - mInputBufferReadOffset);
            memcpy(inputBuffer, mInputData + mInputBufferReadOffset, size);
            if (mSignalEOSWithLastFrame) {
                if (mIsLoopBack ? (mInputCount + 1 >= mLoopBackFrameLimit)
                                : (mInputBufferReadOffset + size >= mInputLength)) {
                    flags |= AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM;
                    mSawInputEOS = true;
                }
            }
            mInputBufferReadOffset += size;
        } else {
            pts += mInputCount * 1000000LL / mDefFrameRate;
            size = mWidth * mHeight * 3 / 2;
            int frmSize = kInpFrmWidth * kInpFrmHeight * 3 / 2;
            RETURN_IF_TRUE(mInputBufferReadOffset + frmSize > mInputLength,
                           std::string{"received partial frame to encode"})
            RETURN_IF_TRUE(size > buffSize,
                           StringFormat("frame size exceeds buffer capacity of input buffer %d %zu",
                                        size, buffSize))
            if (mWidth == kInpFrmWidth && mHeight == kInpFrmHeight) {
                memcpy(inputBuffer, mInputData + mInputBufferReadOffset, size);
            } else {
                fillByteBuffer(inputBuffer);
            }
            if (mSignalEOSWithLastFrame) {
                if (mIsLoopBack ? (mInputCount + 1 >= mLoopBackFrameLimit)
                                : (mInputBufferReadOffset + frmSize >= mInputLength)) {
                    flags |= AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM;
                    mSawInputEOS = true;
                }
            }
            mInputBufferReadOffset += frmSize;
        }
        mNumBytesSubmitted += size;
        RETURN_IF_FAIL(AMediaCodec_queueInputBuffer(mCodec, bufferIndex, 0, size, pts, flags),
                       "AMediaCodec_queueInputBuffer failed")
        ALOGV("input: id: %zu  size: %d  pts: %" PRId64 "  flags: %d", bufferIndex, size, pts,
              flags);
        mOutputBuff->saveInPTS(pts);
        mInputCount++;
    }
    return !hasSeenError();
}

bool CodecEncoderTest::dequeueOutput(size_t bufferIndex, AMediaCodecBufferInfo* info) {
    if ((info->flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0) {
        mSawOutputEOS = true;
    }
    if (info->size > 0) {
        if (mSaveToMem) {
            size_t buffSize;
            uint8_t* buf = AMediaCodec_getOutputBuffer(mCodec, bufferIndex, &buffSize);
            RETURN_IF_TRUE(buf == nullptr,
                           std::string{"AMediaCodec_getOutputBuffer returned nullptr"})
            mOutputBuff->saveToMemory(buf, info);
        }
        if ((info->flags & TBD_AMEDIACODEC_BUFFER_FLAG_KEY_FRAME) != 0) {
            mNumSyncFramesReceived += 1;
            mSyncFramesPos.push_back(mOutputCount);
        }
        if ((info->flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff->saveOutPTS(info->presentationTimeUs);
            mOutputCount++;
        }
    }
    ALOGV("output: id: %zu  size: %d  pts: %" PRId64 "  flags: %d", bufferIndex, info->size,
          info->presentationTimeUs, info->flags);
    RETURN_IF_FAIL(AMediaCodec_releaseOutputBuffer(mCodec, bufferIndex, false),
                   "AMediaCodec_releaseOutputBuffer failed")
    return !hasSeenError();
}

bool CodecEncoderTest::doWork(int frameLimit) {
    mLoopBackFrameLimit = frameLimit;
    return CodecTestBase::doWork(frameLimit);
}

bool CodecEncoderTest::isTestStateValid() {
    if (!CodecTestBase::isTestStateValid()) return false;
    RETURN_IF_TRUE((mIsAudio || (mIsVideo && mMaxBFrames == 0)) &&
                           !mOutputBuff->isPtsStrictlyIncreasing(mPrevOutputPts),
                   std::string{"Output timestamps are not strictly increasing \n"}.append(
                           mOutputBuff->getErrorMsg()))
    RETURN_IF_TRUE(mIsVideo && !mOutputBuff->isOutPtsListIdenticalToInpPtsList(mMaxBFrames != 0),
                   std::string{"Input pts list and Output pts list are not identical \n"}.append(
                           mOutputBuff->getErrorMsg()))
    return true;
}

void CodecEncoderTest::initFormat(AMediaFormat* format) {
    if (mIsAudio) {
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &mSampleRate);
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &mChannels);
    } else {
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_WIDTH, &mWidth);
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_HEIGHT, &mHeight);
    }
}

bool CodecEncoderTest::encodeToMemory(const char* file, const char* encoder, int32_t frameLimit,
                                      AMediaFormat* format, OutputManager* ref) {
    /* TODO(b/149027258) */
    if (true) mSaveToMem = false;
    else mSaveToMem = true;
    mOutputBuff = ref;
    mCodec = AMediaCodec_createCodecByName(encoder);
    RETURN_IF_TRUE(!mCodec, StringFormat("unable to create codec by name %s \n", encoder))
    setUpSource(file);
    RETURN_IF_TRUE(!mInputData, StringFormat("unable to open input file %s", file))
    if (!configureCodec(format, false, true, true)) return false;
    RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
    if (!doWork(frameLimit)) return false;
    if (!queueEOS()) return false;
    if (!waitForAllOutputs()) return false;
    RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
    RETURN_IF_FAIL(AMediaCodec_delete(mCodec), "AMediaCodec_delete failed")
    mCodec = nullptr;
    mSaveToMem = false;
    return !hasSeenError();
}

void CodecEncoderTest::forceSyncFrame(AMediaFormat* format) {
    AMediaFormat_setInt32(format, TBD_AMEDIACODEC_PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
    ALOGV("requesting key frame");
    AMediaCodec_setParameters(mCodec, format);
}

void CodecEncoderTest::updateBitrate(AMediaFormat* format, int bitrate) {
    AMediaFormat_setInt32(format, TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE, bitrate);
    ALOGV("requesting bitrate to be changed to %d", bitrate);
    AMediaCodec_setParameters(mCodec, format);
}

bool CodecEncoderTest::testSimpleEncode(const char* encoder, const char* srcPath) {
    setUpSource(srcPath);
    RETURN_IF_TRUE(!mInputData, StringFormat("unable to open input file %s", srcPath))
    setUpParams(1);
    /* TODO(b/149027258) */
    if (true) mSaveToMem = false;
    else mSaveToMem = true;
    auto ref = &mRefBuff;
    auto test = &mTestBuff;
    const bool boolStates[]{true, false};
    for (auto format : mFormats) {
        int loopCounter = 0;
        for (auto eosType : boolStates) {
            for (auto isAsync : boolStates) {
                mOutputBuff = loopCounter == 0 ? ref : test;
                mOutputBuff->reset();
                /* TODO(b/147348711) */
                /* Instead of create and delete codec at every iteration, we would like to create
                 * once and use it for all iterations and delete before exiting */
                mCodec = AMediaCodec_createCodecByName(encoder);
                RETURN_IF_TRUE(!mCodec, StringFormat("unable to create codec %s", encoder))
                char* name = nullptr;
                RETURN_IF_FAIL(AMediaCodec_getName(mCodec, &name), "AMediaCodec_getName failed")
                RETURN_IF_TRUE(!name, std::string{"AMediaCodec_getName returned null"})
                auto res = strcmp(name, encoder) != 0;
                AMediaCodec_releaseName(mCodec, name);
                RETURN_IF_TRUE(res,
                               StringFormat("Codec name mismatch act/got: %s/%s", encoder, name))
                if (!configureCodec(format, isAsync, eosType, true)) return false;
                RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
                if (!doWork(INT32_MAX)) return false;
                if (!queueEOS()) return false;
                if (!waitForAllOutputs()) return false;
                RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
                RETURN_IF_FAIL(AMediaCodec_delete(mCodec), "AMediaCodec_delete failed")
                mCodec = nullptr;
                RETURN_IF_TRUE((loopCounter != 0 && !ref->equals(test)),
                               std::string{"Encoder output is not consistent across runs \n"}
                                       .append(test->getErrorMsg()))
                loopCounter++;
            }
        }
    }
    return true;
}

bool CodecEncoderTest::testReconfigure(const char* encoder, const char* srcPath) {
    setUpSource(srcPath);
    RETURN_IF_TRUE(!mInputData, StringFormat("unable to open input file %s", srcPath))
    setUpParams(2);
    auto configRef = &mReconfBuff;
    if (mFormats.size() > 1) {
        auto format = mFormats[1];
        RETURN_IF_TRUE(!encodeToMemory(srcPath, encoder, INT32_MAX, format, configRef),
                       StringFormat("encodeToMemory failed for file: %s codec: %s \n format: %s",
                                    srcPath, encoder, AMediaFormat_toString(format)))
    }
    auto format = mFormats[0];
    auto ref = &mRefBuff;
    RETURN_IF_TRUE(!encodeToMemory(srcPath, encoder, INT32_MAX, format, ref),
                   StringFormat("encodeToMemory failed for file: %s codec: %s \n format: %s",
                                srcPath, encoder, AMediaFormat_toString(format)))

    auto test = &mTestBuff;
    mOutputBuff = test;
    const bool boolStates[]{true, false};
    for (auto isAsync : boolStates) {
        /* TODO(b/147348711) */
        /* Instead of create and delete codec at every iteration, we would like to create
         * once and use it for all iterations and delete before exiting */
        mCodec = AMediaCodec_createCodecByName(encoder);
        RETURN_IF_TRUE(!mCodec, StringFormat("unable to create codec %s", encoder))
        if (!configureCodec(format, isAsync, true, true)) return false;
        /* test reconfigure in init state */
        if (!reConfigureCodec(format, !isAsync, false, true)) return false;
        RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")

        /* test reconfigure in running state before queuing input */
        if (!reConfigureCodec(format, !isAsync, false, true)) return false;
        RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
        if (!doWork(23)) return false;

        /* test reconfigure codec in running state */
        if (!reConfigureCodec(format, isAsync, true, true)) return false;
        RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")

        /* TODO(b/149027258) */
        if (true) mSaveToMem = false;
        else mSaveToMem = true;
        test->reset();
        if (!doWork(INT32_MAX)) return false;
        if (!queueEOS()) return false;
        if (!waitForAllOutputs()) return false;
        RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
        RETURN_IF_TRUE(!ref->equals(test),
                       std::string{"Encoder output is not consistent across runs \n"}.append(
                               test->getErrorMsg()))

        /* test reconfigure codec at eos state */
        if (!reConfigureCodec(format, !isAsync, false, true)) return false;
        RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
        test->reset();
        if (!doWork(INT32_MAX)) return false;
        if (!queueEOS()) return false;
        if (!waitForAllOutputs()) return false;
        RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
        RETURN_IF_TRUE(!ref->equals(test),
                       std::string{"Encoder output is not consistent across runs \n"}.append(
                               test->getErrorMsg()))

        /* test reconfigure codec for new format */
        if (mFormats.size() > 1) {
            if (!reConfigureCodec(mFormats[1], isAsync, false, true)) return false;
            RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
            test->reset();
            if (!doWork(INT32_MAX)) return false;
            if (!queueEOS()) return false;
            if (!waitForAllOutputs()) return false;
            RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
            RETURN_IF_TRUE(!configRef->equals(test),
                           std::string{"Encoder output is not consistent across runs \n"}.append(
                                   test->getErrorMsg()))
        }
        mSaveToMem = false;
        RETURN_IF_FAIL(AMediaCodec_delete(mCodec), "AMediaCodec_delete failed")
        mCodec = nullptr;
    }
    return true;
}

bool CodecEncoderTest::testOnlyEos(const char* encoder) {
    setUpParams(1);
    /* TODO(b/149027258) */
    if (true) mSaveToMem = false;
    else mSaveToMem = true;
    auto ref = &mRefBuff;
    auto test = &mTestBuff;
    const bool boolStates[]{true, false};
    AMediaFormat* format = mFormats[0];
    int loopCounter = 0;
    for (auto isAsync : boolStates) {
        mOutputBuff = loopCounter == 0 ? ref : test;
        mOutputBuff->reset();
        /* TODO(b/147348711) */
        /* Instead of create and delete codec at every iteration, we would like to create
         * once and use it for all iterations and delete before exiting */
        mCodec = AMediaCodec_createCodecByName(encoder);
        RETURN_IF_TRUE(!mCodec, StringFormat("unable to create codec by name %s", encoder))
        if (!configureCodec(format, isAsync, false, true)) return false;
        RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
        if (!queueEOS()) return false;
        if (!waitForAllOutputs()) return false;
        RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
        RETURN_IF_FAIL(AMediaCodec_delete(mCodec), "AMediaCodec_delete failed")
        mCodec = nullptr;
        RETURN_IF_TRUE((loopCounter != 0 && !ref->equals(test)),
                       std::string{"Encoder output is not consistent across runs \n"}.append(
                               test->getErrorMsg()))
        loopCounter++;
    }
    return true;
}

bool CodecEncoderTest::testSetForceSyncFrame(const char* encoder, const char* srcPath) {
    setUpSource(srcPath);
    RETURN_IF_TRUE(!mInputData, StringFormat("unable to open input file %s", srcPath))
    setUpParams(1);
    AMediaFormat* format = mFormats[0];
    AMediaFormat_setFloat(format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 500.f);
    // Maximum allowed key frame interval variation from the target value.
    int kMaxKeyFrameIntervalVariation = 3;
    int kKeyFrameInterval = 2;  // force key frame every 2 seconds.
    int kKeyFramePos = mDefFrameRate * kKeyFrameInterval;
    int kNumKeyFrameRequests = 7;
    AMediaFormat* params = AMediaFormat_new();
    mFormats.push_back(params);
    mOutputBuff = &mTestBuff;
    const bool boolStates[]{true, false};
    for (auto isAsync : boolStates) {
        mOutputBuff->reset();
        /* TODO(b/147348711) */
        /* Instead of create and delete codec at every iteration, we would like to create
         * once and use it for all iterations and delete before exiting */
        mCodec = AMediaCodec_createCodecByName(encoder);
        RETURN_IF_TRUE(!mCodec, StringFormat("unable to create codec by name%s", encoder))
        if (!configureCodec(format, isAsync, false, true)) return false;
        RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
        for (int i = 0; i < kNumKeyFrameRequests; i++) {
            if (!doWork(kKeyFramePos)) return false;
            RETURN_IF_TRUE(mSawInputEOS,
                           StringFormat("Unable to encode %d frames as the input resource contains "
                                        "only %d frames \n",
                                        kKeyFramePos, mInputCount))
            forceSyncFrame(params);
            mInputBufferReadOffset = 0;
        }
        if (!queueEOS()) return false;
        if (!waitForAllOutputs()) return false;
        RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
        RETURN_IF_FAIL(AMediaCodec_delete(mCodec), "AMediaCodec_delete failed")
        mCodec = nullptr;
        RETURN_IF_TRUE((mNumSyncFramesReceived < kNumKeyFrameRequests),
                       StringFormat("Received only %d key frames for %d key frame requests \n",
                                    mNumSyncFramesReceived, kNumKeyFrameRequests))
        ALOGD("mNumSyncFramesReceived %d", mNumSyncFramesReceived);
        for (int i = 0, expPos = 0, index = 0; i < kNumKeyFrameRequests; i++) {
            int j = index;
            for (; j < mSyncFramesPos.size(); j++) {
                // Check key frame intervals:
                // key frame position should not be greater than target value + 3
                // key frame position should not be less than target value - 3
                if (abs(expPos - mSyncFramesPos.at(j)) <= kMaxKeyFrameIntervalVariation) {
                    index = j;
                    break;
                }
            }
            if (j == mSyncFramesPos.size()) {
                ALOGW("requested key frame at frame index %d none found near by", expPos);
            }
            expPos += kKeyFramePos;
        }
    }
    return true;
}

bool CodecEncoderTest::testAdaptiveBitRate(const char* encoder, const char* srcPath) {
    setUpSource(srcPath);
    RETURN_IF_TRUE(!mInputData, StringFormat("unable to open input file %s", srcPath))
    setUpParams(1);
    AMediaFormat* format = mFormats[0];
    int kAdaptiveBitrateInterval = 3;  // change bitrate every 3 seconds.
    int kAdaptiveBitrateDurationFrame = mDefFrameRate * kAdaptiveBitrateInterval;
    int kBitrateChangeRequests = 7;
    // TODO(b/251265293) Reduce the allowed deviation after improving the test conditions
    float kMaxBitrateDeviation = 60.0; // allowed bitrate deviation in %
    AMediaFormat* params = AMediaFormat_new();
    mFormats.push_back(params);
    // Setting in CBR Mode
    AMediaFormat_setInt32(format, TBD_AMEDIAFORMAT_KEY_BIT_RATE_MODE, kBitrateModeConstant);
    mOutputBuff = &mTestBuff;
    mSaveToMem = true;
    const bool boolStates[]{true, false};
    for (auto isAsync : boolStates) {
        mOutputBuff->reset();
        /* TODO(b/147348711) */
        /* Instead of create and delete codec at every iteration, we would like to create
         * once and use it for all iterations and delete before exiting */
        mCodec = AMediaCodec_createCodecByName(encoder);
        RETURN_IF_TRUE(!mCodec, StringFormat("unable to create codec by name %s", encoder))
        if (!configureCodec(format, isAsync, false, true)) return false;
        RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
        int expOutSize = 0;
        int bitrate;
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, &bitrate);
        for (int i = 0; i < kBitrateChangeRequests; i++) {
            if (!doWork(kAdaptiveBitrateDurationFrame)) return false;
            RETURN_IF_TRUE(mSawInputEOS,
                           StringFormat("Unable to encode %d frames as the input resource contains "
                                        "only %d frames \n",
                                        kAdaptiveBitrateDurationFrame, mInputCount))
            expOutSize += kAdaptiveBitrateInterval * bitrate;
            if ((i & 1) == 1) bitrate *= 2;
            else bitrate /= 2;
            updateBitrate(params, bitrate);
            mInputBufferReadOffset = 0;
        }
        if (!queueEOS()) return false;
        if (!waitForAllOutputs()) return false;
        RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
        RETURN_IF_FAIL(AMediaCodec_delete(mCodec), "AMediaCodec_delete failed")
        mCodec = nullptr;
        /* TODO: validate output br with sliding window constraints Sec 5.2 cdd */
        int outSize = mOutputBuff->getOutStreamSize() * 8;
        float brDev = abs(expOutSize - outSize) * 100.0f / expOutSize;
        RETURN_IF_TRUE(brDev > kMaxBitrateDeviation,
                       StringFormat("Relative Bitrate error is too large : %f %%\n", brDev))
    }
    return true;
}

static jboolean nativeTestSimpleEncode(JNIEnv* env, jobject, jstring jEncoder, jstring jsrcPath,
                                       jstring jMime, jintArray jList0, jintArray jList1,
                                       jintArray jList2, jint colorFormat, jobject jRetMsg) {
    const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
    const char* cmime = env->GetStringUTFChars(jMime, nullptr);
    const char* cEncoder = env->GetStringUTFChars(jEncoder, nullptr);
    jsize cLen0 = env->GetArrayLength(jList0);
    jint* cList0 = env->GetIntArrayElements(jList0, nullptr);
    jsize cLen1 = env->GetArrayLength(jList1);
    jint* cList1 = env->GetIntArrayElements(jList1, nullptr);
    jsize cLen2 = env->GetArrayLength(jList2);
    jint* cList2 = env->GetIntArrayElements(jList2, nullptr);
    auto codecEncoderTest = new CodecEncoderTest(cmime, cList0, cLen0, cList1, cLen1, cList2, cLen2,
                                                 (int)colorFormat);
    bool isPass = codecEncoderTest->testSimpleEncode(cEncoder, csrcPath);
    std::string msg = isPass ? std::string{} : codecEncoderTest->getErrorMsg();
    delete codecEncoderTest;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    env->ReleaseIntArrayElements(jList0, cList0, 0);
    env->ReleaseIntArrayElements(jList1, cList1, 0);
    env->ReleaseIntArrayElements(jList2, cList2, 0);
    env->ReleaseStringUTFChars(jEncoder, cEncoder);
    env->ReleaseStringUTFChars(jMime, cmime);
    env->ReleaseStringUTFChars(jsrcPath, csrcPath);
    return static_cast<jboolean>(isPass);
}

static jboolean nativeTestReconfigure(JNIEnv* env, jobject, jstring jEncoder, jstring jsrcPath,
                                      jstring jMime, jintArray jList0, jintArray jList1,
                                      jintArray jList2, jint colorFormat, jobject jRetMsg) {
    bool isPass;
    const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
    const char* cmime = env->GetStringUTFChars(jMime, nullptr);
    const char* cEncoder = env->GetStringUTFChars(jEncoder, nullptr);
    jsize cLen0 = env->GetArrayLength(jList0);
    jint* cList0 = env->GetIntArrayElements(jList0, nullptr);
    jsize cLen1 = env->GetArrayLength(jList1);
    jint* cList1 = env->GetIntArrayElements(jList1, nullptr);
    jsize cLen2 = env->GetArrayLength(jList2);
    jint* cList2 = env->GetIntArrayElements(jList2, nullptr);
    auto codecEncoderTest = new CodecEncoderTest(cmime, cList0, cLen0, cList1, cLen1, cList2, cLen2,
                                                 (int)colorFormat);
    isPass = codecEncoderTest->testReconfigure(cEncoder, csrcPath);
    std::string msg = isPass ? std::string{} : codecEncoderTest->getErrorMsg();
    delete codecEncoderTest;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    env->ReleaseIntArrayElements(jList0, cList0, 0);
    env->ReleaseIntArrayElements(jList1, cList1, 0);
    env->ReleaseIntArrayElements(jList2, cList2, 0);
    env->ReleaseStringUTFChars(jEncoder, cEncoder);
    env->ReleaseStringUTFChars(jMime, cmime);
    env->ReleaseStringUTFChars(jsrcPath, csrcPath);
    return static_cast<jboolean>(isPass);
}

static jboolean nativeTestSetForceSyncFrame(JNIEnv* env, jobject, jstring jEncoder,
                                            jstring jsrcPath, jstring jMime, jintArray jList0,
                                            jintArray jList1, jintArray jList2, jint colorFormat,
                                            jobject jRetMsg) {
    const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
    const char* cmime = env->GetStringUTFChars(jMime, nullptr);
    const char* cEncoder = env->GetStringUTFChars(jEncoder, nullptr);
    jsize cLen0 = env->GetArrayLength(jList0);
    jint* cList0 = env->GetIntArrayElements(jList0, nullptr);
    jsize cLen1 = env->GetArrayLength(jList1);
    jint* cList1 = env->GetIntArrayElements(jList1, nullptr);
    jsize cLen2 = env->GetArrayLength(jList2);
    jint* cList2 = env->GetIntArrayElements(jList2, nullptr);
    auto codecEncoderTest = new CodecEncoderTest(cmime, cList0, cLen0, cList1, cLen1, cList2, cLen2,
                                                 (int)colorFormat);
    bool isPass = codecEncoderTest->testSetForceSyncFrame(cEncoder, csrcPath);
    std::string msg = isPass ? std::string{} : codecEncoderTest->getErrorMsg();
    delete codecEncoderTest;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    env->ReleaseIntArrayElements(jList0, cList0, 0);
    env->ReleaseIntArrayElements(jList1, cList1, 0);
    env->ReleaseIntArrayElements(jList2, cList2, 0);
    env->ReleaseStringUTFChars(jEncoder, cEncoder);
    env->ReleaseStringUTFChars(jMime, cmime);
    env->ReleaseStringUTFChars(jsrcPath, csrcPath);
    return static_cast<jboolean>(isPass);
}

static jboolean nativeTestAdaptiveBitRate(JNIEnv* env, jobject, jstring jEncoder, jstring jsrcPath,
                                          jstring jMime, jintArray jList0, jintArray jList1,
                                          jintArray jList2, jint colorFormat, jobject jRetMsg) {
    const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
    const char* cmime = env->GetStringUTFChars(jMime, nullptr);
    const char* cEncoder = env->GetStringUTFChars(jEncoder, nullptr);
    jsize cLen0 = env->GetArrayLength(jList0);
    jint* cList0 = env->GetIntArrayElements(jList0, nullptr);
    jsize cLen1 = env->GetArrayLength(jList1);
    jint* cList1 = env->GetIntArrayElements(jList1, nullptr);
    jsize cLen2 = env->GetArrayLength(jList2);
    jint* cList2 = env->GetIntArrayElements(jList2, nullptr);
    auto codecEncoderTest = new CodecEncoderTest(cmime, cList0, cLen0, cList1, cLen1, cList2, cLen2,
                                                 (int)colorFormat);
    bool isPass = codecEncoderTest->testAdaptiveBitRate(cEncoder, csrcPath);
    std::string msg = isPass ? std::string{} : codecEncoderTest->getErrorMsg();
    delete codecEncoderTest;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    env->ReleaseIntArrayElements(jList0, cList0, 0);
    env->ReleaseIntArrayElements(jList1, cList1, 0);
    env->ReleaseIntArrayElements(jList2, cList2, 0);
    env->ReleaseStringUTFChars(jEncoder, cEncoder);
    env->ReleaseStringUTFChars(jMime, cmime);
    env->ReleaseStringUTFChars(jsrcPath, csrcPath);
    return static_cast<jboolean>(isPass);
}

static jboolean nativeTestOnlyEos(JNIEnv* env, jobject, jstring jEncoder, jstring jMime,
                                  jintArray jList0, jintArray jList1, jintArray jList2,
                                  jint colorFormat, jobject jRetMsg) {
    const char* cmime = env->GetStringUTFChars(jMime, nullptr);
    const char* cEncoder = env->GetStringUTFChars(jEncoder, nullptr);
    jsize cLen0 = env->GetArrayLength(jList0);
    jint* cList0 = env->GetIntArrayElements(jList0, nullptr);
    jsize cLen1 = env->GetArrayLength(jList1);
    jint* cList1 = env->GetIntArrayElements(jList1, nullptr);
    jsize cLen2 = env->GetArrayLength(jList2);
    jint* cList2 = env->GetIntArrayElements(jList2, nullptr);
    auto codecEncoderTest = new CodecEncoderTest(cmime, cList0, cLen0, cList1, cLen1, cList2, cLen2,
                                                 (int)colorFormat);
    bool isPass = codecEncoderTest->testOnlyEos(cEncoder);
    std::string msg = isPass ? std::string{} : codecEncoderTest->getErrorMsg();
    delete codecEncoderTest;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    env->ReleaseIntArrayElements(jList0, cList0, 0);
    env->ReleaseIntArrayElements(jList1, cList1, 0);
    env->ReleaseIntArrayElements(jList2, cList2, 0);
    env->ReleaseStringUTFChars(jEncoder, cEncoder);
    env->ReleaseStringUTFChars(jMime, cmime);
    return static_cast<jboolean>(isPass);
}

int registerAndroidMediaV2CtsEncoderTest(JNIEnv* env) {
    const JNINativeMethod methodTable[] = {
            {"nativeTestSimpleEncode",
             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[I[I[IILjava/lang/"
             "StringBuilder;)Z",
             (void*)nativeTestSimpleEncode},
            {"nativeTestReconfigure",
             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[I[I[IILjava/lang/"
             "StringBuilder;)Z",
             (void*)nativeTestReconfigure},
            {"nativeTestSetForceSyncFrame",
             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[I[I[IILjava/lang/"
             "StringBuilder;)Z",
             (void*)nativeTestSetForceSyncFrame},
            {"nativeTestAdaptiveBitRate",
             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[I[I[IILjava/lang/"
             "StringBuilder;)Z",
             (void*)nativeTestAdaptiveBitRate},
            {"nativeTestOnlyEos",
             "(Ljava/lang/String;Ljava/lang/String;[I[I[IILjava/lang/StringBuilder;)Z",
             (void*)nativeTestOnlyEos},
    };
    jclass c = env->FindClass("android/mediav2/cts/CodecEncoderTest");
    return env->RegisterNatives(c, methodTable, sizeof(methodTable) / sizeof(JNINativeMethod));
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    if (registerAndroidMediaV2CtsEncoderTest(env) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
