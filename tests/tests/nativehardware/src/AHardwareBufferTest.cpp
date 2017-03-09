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

#define LOG_TAG "AHardwareBuffer_test"
//#define LOG_NDEBUG 0

#include <errno.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>

#include <android/hardware_buffer.h>
#include <gtest/gtest.h>

#define BAD_VALUE -EINVAL
#define NO_ERROR 0

static ::testing::AssertionResult BuildHexFailureMessage(uint64_t expected,
        uint64_t actual, const char* type) {
    std::ostringstream ss;
    ss << type << " 0x" << std::hex << actual
            << " does not match expected " << type << " 0x" << std::hex
            << expected;
    return ::testing::AssertionFailure() << ss.str();
}

static ::testing::AssertionResult BuildFailureMessage(uint32_t expected,
        uint32_t actual, const char* type) {
    return ::testing::AssertionFailure() << "Buffer " << type << " do not match"
            << ": " << actual << " != " << expected;
}

static ::testing::AssertionResult CheckAHardwareBufferMatchesDesc(
        AHardwareBuffer* abuffer, const AHardwareBuffer_Desc& desc) {
    AHardwareBuffer_Desc bufferDesc;
    AHardwareBuffer_describe(abuffer, &bufferDesc);
    if (bufferDesc.width != desc.width)
        return BuildFailureMessage(desc.width, bufferDesc.width, "widths");
    if (bufferDesc.height != desc.height)
        return BuildFailureMessage(desc.height, bufferDesc.height, "heights");
    if (bufferDesc.layers != desc.layers)
        return BuildFailureMessage(desc.layers, bufferDesc.layers, "layers");
    if (bufferDesc.usage0 != desc.usage0)
        return BuildHexFailureMessage(desc.usage0, bufferDesc.usage0, "usage0");
    if (bufferDesc.usage1 != desc.usage1)
        return BuildHexFailureMessage(desc.usage1, bufferDesc.usage1, "usage1");
    if (bufferDesc.format != desc.format)
        return BuildFailureMessage(desc.format, bufferDesc.format, "formats");
    return ::testing::AssertionSuccess();
}

// Test that passing in NULL values to allocate works as expected.
TEST(AHardwareBufferTest, AHardwareBuffer_allocate_FailsWithNullInput) {
    AHardwareBuffer* buffer;
    AHardwareBuffer_Desc desc;

    memset(&desc, 0, sizeof(AHardwareBuffer_Desc));

    int res = AHardwareBuffer_allocate(&desc, NULL);
    EXPECT_EQ(BAD_VALUE, res);
    res = AHardwareBuffer_allocate(NULL, &buffer);
    EXPECT_EQ(BAD_VALUE, res);
    res = AHardwareBuffer_allocate(NULL, NULL);
    EXPECT_EQ(BAD_VALUE, res);
}

// Test that passing in NULL values to allocate works as expected.
TEST(AHardwareBufferTest, AHardwareBuffer_allocate_BlobFormatRequiresHeight1) {
    AHardwareBuffer* buffer;
    AHardwareBuffer_Desc desc;

    desc.width = 2;
    desc.height = 4;
    desc.layers = 1;
    desc.usage0 = AHARDWAREBUFFER_USAGE0_CPU_READ;
    desc.usage1 = 0;
    desc.format = AHARDWAREBUFFER_FORMAT_BLOB;
    int res = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(BAD_VALUE, res);

    desc.height = 1;
    res = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(NO_ERROR, res);
    EXPECT_TRUE(CheckAHardwareBufferMatchesDesc(buffer, desc));
    AHardwareBuffer_release(buffer);
    buffer = NULL;
}

// Test that allocate can create an AHardwareBuffer correctly.
TEST(AHardwareBufferTest, AHardwareBuffer_allocate_Succeeds) {
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc;

    desc.width = 2;
    desc.height = 4;
    desc.layers = 1;
    desc.usage0 = AHARDWAREBUFFER_USAGE0_GPU_SAMPLED_IMAGE;
    desc.usage1 = 0;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    int res = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(NO_ERROR, res);
    EXPECT_TRUE(CheckAHardwareBufferMatchesDesc(buffer, desc));
    AHardwareBuffer_release(buffer);
    buffer = NULL;

    desc.width = 4;
    desc.height = 12;
    desc.layers = 1;
    desc.usage0 = AHARDWAREBUFFER_USAGE0_GPU_SAMPLED_IMAGE;
    desc.usage1 = 0;
    desc.format = AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM;
    res = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(NO_ERROR, res);
    EXPECT_TRUE(CheckAHardwareBufferMatchesDesc(buffer, desc));
    AHardwareBuffer_release(buffer);
}

TEST(AHardwareBufferTest, AHardwareBuffer_describe_Succeeds) {
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc;

    desc.width = 2;
    desc.height = 4;
    desc.layers = 1;
    desc.usage0 = AHARDWAREBUFFER_USAGE0_GPU_SAMPLED_IMAGE;
    desc.usage1 = 0;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    int res = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(NO_ERROR, res);

    AHardwareBuffer_Desc expected_desc;
    memset(&expected_desc, 0, sizeof(AHardwareBuffer_Desc));
    AHardwareBuffer_describe(NULL, &desc);
    EXPECT_EQ(0U, expected_desc.width);
    AHardwareBuffer_describe(buffer, NULL);
    EXPECT_EQ(0U, expected_desc.width);
    AHardwareBuffer_describe(buffer, &desc);
    EXPECT_TRUE(CheckAHardwareBufferMatchesDesc(buffer, desc));

    AHardwareBuffer_release(buffer);
}

struct ClientData {
    const char* path;
    AHardwareBuffer* buffer;
    ClientData(const char* path_in, AHardwareBuffer* buffer_in)
            : path(path_in), buffer(buffer_in) {}
};

static void* clientFunction(void* data) {
    ClientData* pdata = reinterpret_cast<ClientData*>(data);

    int fd = socket(PF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) {
        GTEST_LOG_(ERROR) << "Client socket call failed: " << strerror(errno);
        return reinterpret_cast<void*>(-1);
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strcpy(addr.sun_path, pdata->path);

    if (connect(fd, reinterpret_cast<struct sockaddr*>(&addr),
            sizeof(addr)) < 0) {
        GTEST_LOG_(ERROR) << "Client connect call failed: " << strerror(errno);
        return reinterpret_cast<void*>(-1);
    }

    int err = AHardwareBuffer_sendHandleToUnixSocket(pdata->buffer, fd);
    EXPECT_EQ(NO_ERROR, err);
    close(fd);
    return 0;
}

TEST(AHardwareBufferTest, AHardwareBuffer_SendAndRecv_Succeeds) {
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc;

    desc.width = 2;
    desc.height = 4;
    desc.layers = 1;
    desc.usage0 = AHARDWAREBUFFER_USAGE0_GPU_SAMPLED_IMAGE;
    desc.usage1 = 0;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;

    // Test that an invalid buffer fails.
    int err = AHardwareBuffer_sendHandleToUnixSocket(NULL, 0);
    EXPECT_EQ(BAD_VALUE, err);
    err = 0;
    err = AHardwareBuffer_sendHandleToUnixSocket(buffer, 0);
    EXPECT_EQ(BAD_VALUE, err);

    // Allocate the buffer.
    err = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(NO_ERROR, err);

    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    ASSERT_LT(0, fd);

    std::string tempFile = "/data/local/tmp/ahardwarebuffer_test_XXXXXX";
    int tempFd = mkstemp(&tempFile[0]);
    unlink(&tempFile[0]);

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strcpy(addr.sun_path, &tempFile[0]);

    // Bind the server and listen on the socket.
    ASSERT_NE(-1, bind(fd, reinterpret_cast<struct sockaddr*>(&addr),
          sizeof(addr))) << strerror(errno);
    ASSERT_EQ(0, listen(fd, 1)) << strerror(errno);

    // Launch a client that will send the buffer back.
    ClientData data(&tempFile[0], buffer);
    pthread_t thread;
    ASSERT_EQ(0, pthread_create(&thread, NULL, clientFunction, &data));

    // Wait for the client to send the buffer.
    int acceptFd = accept(fd, NULL, NULL);
    ASSERT_LT(0, acceptFd) << strerror(errno);

    // Receive the buffer.
    err = AHardwareBuffer_recvHandleFromUnixSocket(acceptFd, NULL);
    EXPECT_EQ(BAD_VALUE, err);

    AHardwareBuffer* received = NULL;
    err = AHardwareBuffer_recvHandleFromUnixSocket(acceptFd, &received);
    EXPECT_EQ(NO_ERROR, err);
    ASSERT_TRUE(received != NULL);
    EXPECT_TRUE(CheckAHardwareBufferMatchesDesc(received, desc));

    void* ret_val;
    ASSERT_EQ(0, pthread_join(thread, &ret_val));
    ASSERT_EQ(NULL, ret_val);
    close(acceptFd);
    close(fd);

    AHardwareBuffer_release(buffer);
    AHardwareBuffer_release(received);
}

TEST(AHardwareBufferTest, AHardwareBuffer_Lock_and_Unlock_Succeed) {
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc;

    desc.width = 2;
    desc.height = 4;
    desc.layers = 1;
    desc.usage0 = AHARDWAREBUFFER_USAGE0_GPU_SAMPLED_IMAGE |
            AHARDWAREBUFFER_USAGE0_CPU_READ;
    desc.usage1 = 0;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;

    // Test that an invalid buffer fails.
    int err = AHardwareBuffer_lock(NULL, 0, -1, NULL, NULL);
    EXPECT_EQ(BAD_VALUE, err);
    err = 0;

    // Allocate the buffer.
    err = AHardwareBuffer_allocate(&desc, &buffer);
    EXPECT_EQ(NO_ERROR, err);
    void* bufferData = NULL;
    err = AHardwareBuffer_lock(buffer, AHARDWAREBUFFER_USAGE0_CPU_READ, -1,
          NULL, &bufferData);
    EXPECT_EQ(NO_ERROR, err);
    EXPECT_TRUE(bufferData != NULL);
    int32_t fence = -1;
    err = AHardwareBuffer_unlock(buffer, &fence);

    AHardwareBuffer_release(buffer);
}
