/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <fcntl.h>
#include <linux/fs.h>
#include <setjmp.h>
#include <signal.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <android-base/unique_fd.h>
#include <cutils/properties.h>
#include <gtest/gtest.h>

// Define the latest fscrypt definitions if needed.
// TODO: delete this once Bionic has updated its headers to Linux v5.4.
#ifndef FS_IOC_GET_ENCRYPTION_POLICY_EX

#define FSCRYPT_POLICY_FLAGS_PAD_4 0x00
#define FSCRYPT_POLICY_FLAGS_PAD_8 0x01
#define FSCRYPT_POLICY_FLAGS_PAD_16 0x02
#define FSCRYPT_POLICY_FLAGS_PAD_32 0x03
#define FSCRYPT_POLICY_FLAGS_PAD_MASK 0x03
#define FSCRYPT_POLICY_FLAG_DIRECT_KEY 0x04

#define FSCRYPT_MODE_AES_256_XTS 1
#define FSCRYPT_MODE_AES_256_CTS 4
#define FSCRYPT_MODE_AES_128_CBC 5
#define FSCRYPT_MODE_AES_128_CTS 6
#define FSCRYPT_MODE_ADIANTUM 9

#define FSCRYPT_POLICY_V1 0
#define FSCRYPT_KEY_DESCRIPTOR_SIZE 8
struct fscrypt_policy_v1 {
    __u8 version;
    __u8 contents_encryption_mode;
    __u8 filenames_encryption_mode;
    __u8 flags;
    __u8 master_key_descriptor[FSCRYPT_KEY_DESCRIPTOR_SIZE];
};

#define FSCRYPT_POLICY_V2 2
#define FSCRYPT_KEY_IDENTIFIER_SIZE 16
struct fscrypt_policy_v2 {
    __u8 version;
    __u8 contents_encryption_mode;
    __u8 filenames_encryption_mode;
    __u8 flags;
    __u8 __reserved[4];
    __u8 master_key_identifier[FSCRYPT_KEY_IDENTIFIER_SIZE];
};

#define FS_IOC_GET_ENCRYPTION_POLICY_EX _IOWR('f', 22, __u8[9])
struct fscrypt_get_policy_ex_arg {
    __u64 policy_size;
    union {
        __u8 version;
        struct fscrypt_policy_v1 v1;
        struct fscrypt_policy_v2 v2;
    } policy;
};
#endif  // FS_IOC_GET_ENCRYPTION_POLICY_EX

// Non-upstream encryption modes that are used on some devices.
#define FSCRYPT_MODE_AES_256_HEH 126
#define FSCRYPT_MODE_PRIVATE 127

// The relevant Android API levels
#define Q_API_LEVEL 29

static int getFirstApiLevel(void) {
    int level = property_get_int32("ro.product.first_api_level", 0);
    if (level == 0) {
        level = property_get_int32("ro.build.version.sdk", 0);
    }
    if (level == 0) {
        ADD_FAILURE() << "Failed to determine first API level";
    }
    return level;
}

#ifdef __arm__
// For ARM32, assemble the 'aese.8' instruction as a .word, since otherwise
// clang does not accept it.  It would be allowed in a separate file compiled
// with -march=armv8, but this way is much easier.  And it's not yet possible to
// use a target function attribute, because clang doesn't yet support
// target("fpu=crypto-neon-fp-armv8") like gcc does.
static void executeAESInstruction(void) {
    // aese.8  q0, q1
    asm volatile(".word  0xf3b00302" : : : "q0");
}
#elif defined(__aarch64__)
static void __attribute__((target("crypto"))) executeAESInstruction(void) {
    asm volatile("aese  v0.16b, v1.16b" : : : "v0");
}
#elif defined(__i386__) || defined(__x86_64__)
static void __attribute__((target("sse"))) executeAESInstruction(void) {
    asm volatile("aesenc %%xmm1, %%xmm0" : : : "xmm0");
}
#else
#warning "unknown architecture, assuming AES instructions are available"
static void executeAESInstruction(void) {}
#endif

static jmp_buf jump_buf;

static void handleSIGILL(int __attribute__((unused)) signum) {
    longjmp(jump_buf, 1);
}

// This function checks for the presence of AES instructions, e.g. ARMv8 crypto
// extensions for ARM, or AES-NI for x86.
//
// ARM processors don't have a standard way for user processes to determine CPU
// features.  On Linux it's possible to read the AT_HWCAP and AT_HWCAP2 values
// from /proc/self/auxv.  But, this relies on the kernel exposing the features
// correctly, which we don't want to rely on.  Instead we actually try to
// execute the instruction, and see whether SIGILL is raised or not.
//
// To keep things consistent we use the same approach on x86 to detect AES-NI,
// though in principle the 'cpuid' instruction could be used there.
static bool cpuHasAESInstructions(void) {
    struct sigaction act;
    struct sigaction oldact;
    bool result;

    memset(&act, 0, sizeof(act));
    act.sa_handler = handleSIGILL;

    EXPECT_EQ(0, sigaction(SIGILL, &act, &oldact));

    if (setjmp(jump_buf) != 0) {
        // SIGILL was received when executing the AES instruction.
        result = false;
    } else {
        executeAESInstruction();
        // Successfully executed the AES instruction.
        result = true;
    }

    EXPECT_EQ(0, sigaction(SIGILL, &oldact, NULL));

    return result;
}

// CDD 9.9.3/C-1-5: must use AES-256-XTS or Adiantum contents encryption.
// CDD 9.9.3/C-1-6: must use AES-256-CTS or Adiantum filenames encryption.
// CDD 9.9.3/C-1-12: mustn't use Adiantum if the CPU has AES instructions.
static void validateEncryptionModes(int contents_mode, int filenames_mode) {
    switch (contents_mode) {
        case FSCRYPT_MODE_AES_256_XTS:
        case FSCRYPT_MODE_ADIANTUM:
        // Many existing devices shipped with custom kernel patches implementing
        // AES-256-XTS inline encryption behind "FSCRYPT_MODE_PRIVATE", so we
        // need to let it pass.  It's up to the vendor to ensure it's really
        // AES-256-XTS.
        case FSCRYPT_MODE_PRIVATE:
            break;
        default:
            ADD_FAILURE() << "Contents encryption mode not allowed: " << contents_mode;
            break;
    }

    switch (filenames_mode) {
        case FSCRYPT_MODE_AES_256_CTS:
        case FSCRYPT_MODE_ADIANTUM:
        // At least one existing device shipped with the experimental
        // AES-256-HEH filenames encryption, which was never added to the CDD.
        // It's cryptographically superior to AES-256-CTS for the use case,
        // though, so it's compliant in spirit; let it pass for now...
        case FSCRYPT_MODE_AES_256_HEH:
            break;
        default:
            ADD_FAILURE() << "Filenames encryption mode not allowed: " << filenames_mode;
            break;
    }

    if (contents_mode == FSCRYPT_MODE_ADIANTUM || filenames_mode == FSCRYPT_MODE_ADIANTUM) {
        // Adiantum encryption is only allowed if the CPU doesn't have AES instructions.
        EXPECT_FALSE(cpuHasAESInstructions());
    }
}

// We check the encryption policy of /data/local/tmp because it's one of the
// only encrypted directories the shell domain has permission to open.  Ideally
// we'd check the user's credential-encrypted storage (/data/user/0) instead.
// It shouldn't matter in practice though, since AOSP code doesn't provide any
// way to configure different directories to use different algorithms...
#define DIR_TO_CHECK "/data/local/tmp/"

// Test that the device is using appropriate encryption algorithms for
// file-based encryption.  If this test fails, you should ensure the device's
// fstab has the correct fileencryption= option for the userdata partition.  See
// https://source.android.com/security/encryption/file-based.html
TEST(FileBasedEncryptionPolicyTest, allowedPolicy) {
    int first_api_level = getFirstApiLevel();
    struct fscrypt_get_policy_ex_arg arg;
    int res;
    int contents_mode;
    int filenames_mode;

    android::base::unique_fd fd(open(DIR_TO_CHECK, O_RDONLY | O_CLOEXEC));
    if (fd < 0) {
        FAIL() << "Failed to open " DIR_TO_CHECK ": " << strerror(errno);
    }

    GTEST_LOG_(INFO) << "First API level is " << first_api_level;

    // Note: SELinux policy allows the shell domain to use these ioctls, but not
    // apps.  Therefore this test needs to be a real native test that's run
    // through the shell, not a JNI test run through an installed APK.
    arg.policy_size = sizeof(arg.policy);
    res = ioctl(fd, FS_IOC_GET_ENCRYPTION_POLICY_EX, &arg);
    if (res != 0 && errno == ENOTTY) {
        // Handle old kernels that don't support FS_IOC_GET_ENCRYPTION_POLICY_EX
        GTEST_LOG_(INFO) << "Old kernel, falling back to FS_IOC_GET_ENCRYPTION_POLICY";
        res = ioctl(fd, FS_IOC_GET_ENCRYPTION_POLICY, &arg.policy.v1);
    }
    if (res != 0) {
        if (errno == ENODATA || errno == ENOENT) {  // Directory is unencrypted
            // Starting with Android 10, file-based encryption is required on
            // new devices [CDD 9.9.2/C-0-3].
            if (first_api_level < Q_API_LEVEL) {
                GTEST_LOG_(INFO)
                        << "Exempt from file-based encryption due to old starting API level";
                return;
            }
            FAIL() << "Device isn't using file-based encryption";
        } else {
            FAIL() << "Failed to get encryption policy of " DIR_TO_CHECK ": " << strerror(errno);
        }
    }

    switch (arg.policy.version) {
        case FSCRYPT_POLICY_V1:
            GTEST_LOG_(INFO) << "Detected v1 encryption policy";
            contents_mode = arg.policy.v1.contents_encryption_mode;
            filenames_mode = arg.policy.v1.filenames_encryption_mode;
            break;
        case FSCRYPT_POLICY_V2:
            GTEST_LOG_(INFO) << "Detected v2 encryption policy";
            contents_mode = arg.policy.v2.contents_encryption_mode;
            filenames_mode = arg.policy.v2.filenames_encryption_mode;
            break;
        default:
            FAIL() << "Unknown encryption policy version: " << arg.policy.version;
    }

    GTEST_LOG_(INFO) << "Contents encryption mode: " << contents_mode;
    GTEST_LOG_(INFO) << "Filenames encryption mode: " << filenames_mode;

    validateEncryptionModes(contents_mode, filenames_mode);
}
