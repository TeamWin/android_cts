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

// Test if test environment is correctly initialized
void SensorTest::testInitialized(JNIEnv *env) {
    ASSERT_TRUE(mManager->isValid());
}

// Test if invalid parameter cases are handled correctly
void SensorTest::testInvalidParameter(JNIEnv *env) {
    ASensorList dummyList;
    ASSERT_EQ(ASensorManager_getSensorList(nullptr, nullptr), -EINVAL);
    ASSERT_EQ(ASensorManager_getSensorList(nullptr, &dummyList), -EINVAL);

    ASSERT_EQ(ASensorManager_getDefaultSensor(nullptr, ASENSOR_TYPE_ACCELEROMETER), nullptr);

    ASSERT_EQ(ASensorManager_getDefaultSensorEx(
            nullptr, ASENSOR_TYPE_ACCELEROMETER, false), nullptr);

    ALooper *nonNullLooper = reinterpret_cast<ALooper *>(1);
    ASensorManager *nonNullManager = reinterpret_cast<ASensorManager *>(1);
    ASSERT_EQ(ASensorManager_createEventQueue(nullptr, nullptr, 0, nullptr, nullptr), nullptr);
    ASSERT_EQ(ASensorManager_createEventQueue(
            nullptr, nonNullLooper, 0, nullptr, nullptr), nullptr);
    ASSERT_EQ(ASensorManager_createEventQueue(
            nonNullManager, nullptr, 0, nullptr, nullptr), nullptr);

    ASensorEventQueue *nonNullQueue = reinterpret_cast<ASensorEventQueue *>(1);
    ASSERT_EQ(ASensorManager_destroyEventQueue(nullptr, nullptr), -EINVAL);
    ASSERT_EQ(ASensorManager_destroyEventQueue(nullptr, nonNullQueue), -EINVAL);
    ASSERT_EQ(ASensorManager_destroyEventQueue(nonNullManager, nullptr), -EINVAL);

    int fakeValidFd = 1;
    int invalidFd = -1;
    ASSERT_EQ(ASensorManager_createSharedMemoryDirectChannel(
            nullptr, fakeValidFd, sizeof(ASensorEvent)), -EINVAL);
    ASSERT_EQ(ASensorManager_createSharedMemoryDirectChannel(
            nonNullManager, invalidFd, sizeof(ASensorEvent)), -EINVAL);
    ASSERT_EQ(ASensorManager_createSharedMemoryDirectChannel(
            nonNullManager, fakeValidFd, sizeof(ASensorEvent) - 1), -EINVAL);
    ASSERT_EQ(ASensorManager_createSharedMemoryDirectChannel(
            nonNullManager, fakeValidFd, 0), -EINVAL);

    AHardwareBuffer *nonNullHardwareBuffer = reinterpret_cast<AHardwareBuffer *>(1);
    ASSERT_EQ(ASensorManager_createHardwareBufferDirectChannel(
            nullptr, nonNullHardwareBuffer, sizeof(ASensorEvent)), -EINVAL);
    ASSERT_EQ(ASensorManager_createHardwareBufferDirectChannel(
            nonNullManager, nullptr, sizeof(ASensorEvent)), -EINVAL);
    ASSERT_EQ(ASensorManager_createHardwareBufferDirectChannel(
            nonNullManager, nonNullHardwareBuffer, sizeof(ASensorEvent) - 1), -EINVAL);
    ASSERT_EQ(ASensorManager_createHardwareBufferDirectChannel(
            nonNullManager, nonNullHardwareBuffer, 0), -EINVAL);

    // no return value to test, but call this to test if it will crash
    ASensorManager_destroyDirectChannel(nullptr, 1);

    ASensor *nonNullSensor = reinterpret_cast<ASensor *>(1);
    ASSERT_EQ(ASensorManager_configureDirectReport(
            nullptr, nullptr, 1, ASENSOR_DIRECT_RATE_NORMAL), -EINVAL);
    ASSERT_EQ(ASensorManager_configureDirectReport(
            nullptr, nonNullSensor, 1, ASENSOR_DIRECT_RATE_NORMAL), -EINVAL);
    ASSERT_EQ(ASensorManager_configureDirectReport(
            nullptr, nonNullSensor, 1, ASENSOR_DIRECT_RATE_STOP), -EINVAL);
    ASSERT_EQ(ASensorManager_configureDirectReport(
            nonNullManager, nullptr, 1, ASENSOR_DIRECT_RATE_NORMAL), -EINVAL);

    ASSERT_EQ(ASensorEventQueue_registerSensor(nullptr, nullptr, 1, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_registerSensor(nullptr, nonNullSensor, 1, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_registerSensor(nonNullQueue, nullptr, 1, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_registerSensor(nonNullQueue, nonNullSensor, -1, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_registerSensor(nonNullQueue, nonNullSensor, 1, -1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_registerSensor(nonNullQueue, nonNullSensor, -1, -1), -EINVAL);

    ASSERT_EQ(ASensorEventQueue_enableSensor(nullptr, nullptr), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_enableSensor(nullptr, nonNullSensor), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_enableSensor(nonNullQueue, nullptr), -EINVAL);

    ASSERT_EQ(ASensorEventQueue_disableSensor(nullptr, nullptr), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_disableSensor(nullptr, nonNullSensor), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_disableSensor(nonNullQueue, nullptr), -EINVAL);

    ASSERT_EQ(ASensorEventQueue_setEventRate(nullptr, nullptr, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_setEventRate(nullptr, nonNullSensor, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_setEventRate(nonNullQueue, nullptr, 1), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_setEventRate(nonNullQueue, nonNullSensor, -1), -EINVAL);

    ASSERT_EQ(ASensorEventQueue_hasEvents(nullptr), -EINVAL);

    ASensorEvent event;
    ASensorEvent *nonNullEvent = &event;
    ASSERT_EQ(ASensorEventQueue_getEvents(nullptr, nullptr, 1), -EINVAL)
    ASSERT_EQ(ASensorEventQueue_getEvents(nullptr, nullptr, 0), -EINVAL)
    ASSERT_EQ(ASensorEventQueue_getEvents(nullptr, nonNullEvent, 1), -EINVAL)
    ASSERT_EQ(ASensorEventQueue_getEvents(nullptr, nonNullEvent, 0), -EINVAL);
    ASSERT_EQ(ASensorEventQueue_getEvents(nonNullQueue, nullptr, 1), -EINVAL)
    ASSERT_EQ(ASensorEventQueue_getEvents(nonNullQueue, nullptr, 0), -EINVAL);

    ASSERT_EMPTY_CSTR(ASensor_getName(nullptr));
    ASSERT_EMPTY_CSTR(ASensor_getVendor(nullptr));
    ASSERT_EQ(ASensor_getType(nullptr), -1);
    ASSERT_EQ(ASensor_getResolution(nullptr), -1.f);
    ASSERT_EQ(ASensor_getMinDelay(nullptr), -1);
    ASSERT_EQ(ASensor_getFifoMaxEventCount(nullptr), -1);
    ASSERT_EQ(ASensor_getFifoReservedEventCount(nullptr), -1);
    ASSERT_EMPTY_CSTR(ASensor_getStringType(nullptr));
    ASSERT_EQ(ASensor_getReportingMode(nullptr), -1);
    ASSERT_EQ(ASensor_isWakeUpSensor(nullptr), false);
    ASSERT_EQ(ASensor_isDirectChannelTypeSupported(
            nullptr, ASENSOR_DIRECT_CHANNEL_TYPE_SHARED_MEMORY), false);
    ASSERT_EQ(ASensor_isDirectChannelTypeSupported(
            nullptr, ASENSOR_DIRECT_CHANNEL_TYPE_HARDWARE_BUFFER), false);
    ASSERT_EQ(ASensor_getHighestDirectReportRateLevel(nullptr), ASENSOR_DIRECT_RATE_STOP);
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
