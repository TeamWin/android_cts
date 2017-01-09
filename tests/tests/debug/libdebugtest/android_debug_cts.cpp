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

#include <jni.h>
#include <android/log.h>

#include <sys/ptrace.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#define LOG_TAG "Cts-DebugTest"

#define assert_or_exit(x)                                                                         \
    do {                                                                                          \
        if(x) break;                                                                              \
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Assertion " #x " failed. errno(%d): %s", \
                errno, strerror(errno));                                                          \
        _exit(1);                                                                                 \
    } while (0)
#define assert_or_return(x)                                                                       \
    do {                                                                                          \
        if(x) break;                                                                              \
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Assertion " #x " failed. errno(%d): %s", \
                errno, strerror(errno));                                                          \
        return false;                                                                             \
    } while (0)

static bool parent(pid_t child) {
    int status;
    int wpid = waitpid(child, &status, 0);
    assert_or_return(wpid == child);
    assert_or_return(WIFEXITED(status));
    assert_or_return(WEXITSTATUS(status ) == 0);
    return true;
}

static bool child(pid_t parent) __attribute__((noreturn));
static bool child(pid_t parent) {
    assert_or_exit(ptrace(PTRACE_ATTACH, parent, nullptr, nullptr) == 0);
    int status;
    assert_or_exit(waitpid(parent, &status, __WALL) == parent);
    assert_or_exit(WIFSTOPPED(status));
    assert_or_exit(WSTOPSIG(status) == SIGSTOP);

    assert_or_exit(ptrace(PTRACE_DETACH, parent, nullptr, nullptr) == 0);
    _exit(0);
}

// public static native boolean ptraceAttach();
extern "C" jboolean Java_android_debug_cts_DebugTest_ptraceAttach(JNIEnv *, jclass) {
    pid_t pid = fork();
    assert_or_return(pid >= 0);
    if (pid != 0)
        return parent(pid);
    else
        child(getppid());
}
