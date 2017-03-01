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

#include "SensorTest.h"
#include <errno.h>

namespace android {
namespace SensorTest {

// SensorTest container class
bool SensorTest::SetUp() {
    if (mManager == nullptr) {
        mManager.reset(
              TestSensorManager::getInstanceForPackage("android.hardware.cts.SensorNativeTest"));
    }
    return mManager == nullptr;
}

void SensorTest::TearDown() {
    if (mManager == nullptr) {
        mManager.reset(nullptr);
    }
}

void SensorTest::testInitialized(JNIEnv *env) {
    ASSERT_TRUE(mManager->isValid());
}

TestSensorManager::TestSensorManager(const char *package) {
    mManager = ASensorManager_getInstanceForPackage(package);
}

TestSensorManager::~TestSensorManager() {
    for (int channel : mSensorDirectChannel) {
        destroyDirectChannel(channel);
    }
    mSensorDirectChannel.clear();
}

TestSensorManager * TestSensorManager::getInstanceForPackage(const char *package) {
    return new TestSensorManager(package);
}

TestSensor TestSensorManager::getDefaultSensor(int type) {
    return TestSensor(ASensorManager_getDefaultSensor(mManager, type));
}

int TestSensorManager::createDirectChannel(const TestSharedMemory &mem) {
    if (!isValid()) {
        return -EINVAL;
    }
    switch (mem.getType()) {
        case ASENSOR_DIRECT_CHANNEL_TYPE_SHARED_MEMORY:
            return createSharedMemoryDirectChannel(
                    mem.getSharedMemoryFd(), mem.getSize());
        case ASENSOR_DIRECT_CHANNEL_TYPE_HARDWARE_BUFFER:
            return createHardwareBufferDirectChannel(
                    mem.getHardwareBuffer(), mem.getSize());
        default:
            return -1;
    }
}

int TestSensorManager::createSharedMemoryDirectChannel(int fd, size_t size) {
    int ret = ASensorManager_createSharedMemoryDirectChannel(mManager, fd, size);
    if (ret > 0) {
        mSensorDirectChannel.insert(ret);
    }
    return ret;
}

int TestSensorManager::createHardwareBufferDirectChannel(
        AHardwareBuffer const *buffer, size_t size) {
    int ret = ASensorManager_createHardwareBufferDirectChannel(mManager, buffer, size);
    if (ret > 0) {
        mSensorDirectChannel.insert(ret);
    }
    return ret;
}

void TestSensorManager::destroyDirectChannel(int channel) {
    if (!isValid()) {
        return;
    }
    ASensorManager_destroyDirectChannel(mManager, channel);
    mSensorDirectChannel.erase(channel);
    return;
}

int TestSensorManager::configureDirectReport(TestSensor sensor, int channel, int rate) {
    if (!isValid()) {
        return -EINVAL;
    }
    return ASensorManager_configureDirectReport(mManager, sensor, channel, rate);
}

char * TestSharedMemory::getBuffer() const {
    return mBuffer;
}

std::vector<ASensorEvent> TestSharedMemory::parseEvents(int64_t lastCounter, size_t offset) const {
    constexpr size_t kEventSize = sizeof(ASensorEvent);
    constexpr size_t kOffsetSize = offsetof(ASensorEvent, version);
    constexpr size_t kOffsetAtomicCounter = offsetof(ASensorEvent, reserved0);

    std::vector<ASensorEvent> events;
    while (offset + kEventSize <= mSize) {
        int64_t atomicCounter = *reinterpret_cast<uint32_t *>(mBuffer + offset + kOffsetAtomicCounter);
        if (atomicCounter <= lastCounter) {
            break;
        }

        int32_t size = *reinterpret_cast<int32_t *>(mBuffer + offset + kOffsetSize);
        if (size != kEventSize) {
            // unknown error, events parsed may be wrong, remove all
            events.clear();
            break;
        }

        events.push_back(*reinterpret_cast<ASensorEvent *>(mBuffer + offset));
        lastCounter = atomicCounter;
        offset += kEventSize;
    }

    return events;
}

TestSharedMemory::TestSharedMemory(int type, size_t size)
        : mType(type), mSize(0), mBuffer(nullptr),
            mSharedMemoryFd(-1), mHardwareBuffer(nullptr) {
    bool success = false;
    switch(type) {
        case ASENSOR_DIRECT_CHANNEL_TYPE_SHARED_MEMORY: {
            mSharedMemoryFd = ASharedMemory_create("TestSharedMemory", size);
            if (mSharedMemoryFd < 0
                    || ASharedMemory_getSize(mSharedMemoryFd) != size) {
                break;
            }

            mSize = size;
            mBuffer = reinterpret_cast<char *>(::mmap(
                    nullptr, mSize, PROT_READ | PROT_WRITE,
                    MAP_SHARED, mSharedMemoryFd, 0));

            if (mBuffer == MAP_FAILED) {
                mBuffer = nullptr;
                break;
            }
            success = true;
            break;
        }
        default:
            break;
    }

    if (!success) {
        release();
    }
}

TestSharedMemory::~TestSharedMemory() {
    release();
}

void TestSharedMemory::release() {
    switch(mType) {
        case ASENSOR_DIRECT_CHANNEL_TYPE_SHARED_MEMORY: {
            if (mBuffer != nullptr) {
                ::munmap(mBuffer, mSize);
                mBuffer = nullptr;
            }
            if (mSharedMemoryFd > 0) {
                ::close(mSharedMemoryFd);
                mSharedMemoryFd = -1;
            }
            mSize = 0;
            break;
        }
        default:
            break;
    }
    if (mSharedMemoryFd > 0 || mSize != 0 || mBuffer != nullptr) {
        ALOGE("TestSharedMemory %p not properly destructed: "
              "type %d, shared_memory_fd %d, hardware_buffer %p, size %zu, buffer %p",
              this, static_cast<int>(mType), mSharedMemoryFd, mHardwareBuffer, mSize, mBuffer);
    }
}

TestSharedMemory* TestSharedMemory::create(int type, size_t size) {
    constexpr size_t kMaxSize = 128*1024*1024; // sensor test should not need more than 128M
    if (size == 0 || size >= kMaxSize) {
        return nullptr;
    }

    auto m = new TestSharedMemory(type, size);
    if (m->mSize != size || m->mBuffer == nullptr) {
        delete m;
        m = nullptr;
    }
    return m;
}

// Test direct report of gyroscope at normal rate level through ashmem direct channel
void SensorTest::testGyroscopeSharedMemoryDirectReport(JNIEnv* env) {
    constexpr int type = ASENSOR_TYPE_GYROSCOPE;
    constexpr size_t kEventSize = sizeof(ASensorEvent);
    constexpr size_t kNEvent = 500;
    constexpr size_t kMemSize = kEventSize * kNEvent;

    TestSensor sensor = mManager->getDefaultSensor(type);

    if (sensor.getHighestDirectReportRateLevel() == ASENSOR_DIRECT_RATE_STOP
        || !sensor.isDirectChannelTypeSupported(ASENSOR_DIRECT_CHANNEL_TYPE_SHARED_MEMORY)) {
        // does not declare support
        return;
    }

    std::unique_ptr<TestSharedMemory>
            mem(TestSharedMemory::create(ASENSOR_DIRECT_CHANNEL_TYPE_SHARED_MEMORY, kMemSize));
    ASSERT_NE(mem, nullptr);
    ASSERT_NE(mem->getBuffer(), nullptr);
    ASSERT_GT(mem->getSharedMemoryFd(), 0);

    char* buffer = mem->getBuffer();
    // fill memory with data
    for (size_t i = 0; i < kMemSize; ++i) {
        buffer[i] = '\xcc';
    }

    int32_t channel;
    channel = mManager->createDirectChannel(*mem);
    ASSERT_GT(channel, 0);

    // check memory is zeroed
    for (size_t i = 0; i < kMemSize; ++i) {
        ASSERT_EQ(buffer[i], '\0');
    }

    int32_t eventToken;
    eventToken = mManager->configureDirectReport(sensor, channel, ASENSOR_DIRECT_RATE_NORMAL);
    usleep(1500000); // sleep 1 sec for data, plus 0.5 sec for initialization
    auto events = mem->parseEvents();

    // allowed to be between 55% and 220% of nominal freq (50Hz)
    ASSERT_GT(events.size(), 50 / 2);
    ASSERT_LT(events.size(), static_cast<size_t>(110*1.5));

    int64_t lastTimestamp = 0;
    for (auto &e : events) {
        ASSERT_EQ(e.type, type);
        ASSERT_EQ(e.sensor, eventToken);
        ASSERT_GT(e.timestamp, lastTimestamp);

        ASensorVector &gyro = e.vector;
        double gyroNorm = std::sqrt(gyro.x * gyro.x + gyro.y * gyro.y + gyro.z * gyro.z);
        // assert not drifting
        ASSERT_TRUE(gyroNorm < 0.1);  // < ~5 degree/sa

        lastTimestamp = e.timestamp;
    }

    // stop sensor and unregister channel
    mManager->configureDirectReport(sensor, channel, ASENSOR_DIRECT_RATE_STOP);
    mManager->destroyDirectChannel(channel);
}
} // namespace SensorTest
} // namespace android
