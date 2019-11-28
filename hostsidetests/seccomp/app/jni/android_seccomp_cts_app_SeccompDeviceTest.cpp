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

#define LOG_TAG "SeccompTest"

#include <cutils/log.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <err.h>
#include <sys/ptrace.h>
#include <stdlib.h>
#include <linux/elf.h>


#define EXIT_VULNERABLE 113

/*
 * Function: testSyscallBlocked
 * Purpose: test that the syscall listed is blocked by seccomp
 * Parameters:
 *        nr: syscall number
 * Returns:
 *        1 if blocked, else 0
 * Exceptions: None
 */
static jboolean testSyscallBlocked(JNIEnv *, jobject, int nr) {
    int pid = fork();
    if (pid == 0) {
        ALOGI("Calling syscall %d", nr);
        syscall(nr);
        return false;
    } else {
        int status;
        int ret = waitpid(pid, &status, 0);
        if (ret != pid) {
            ALOGE("Unexpected return result from waitpid");
            return false;
        }

        if (WIFEXITED(status)) {
            ALOGE("syscall was not blocked");
            return false;
        }

        if (WIFSIGNALED(status)) {
            int signal = WTERMSIG(status);
            if (signal == 31) {
                ALOGI("syscall caused process termination");
                return true;
            }

            ALOGE("Unexpected signal");
            return false;
        }

        ALOGE("Unexpected status from syscall_exists");
        return false;
    }
}

jboolean testPtrace_CVE_2019_2054(JNIEnv* env, jobject thiz) {
  (void) env;
  (void) thiz;
  pid_t my_pid = -1;
  pid_t child = fork();
  switch (child) {
    case -1:
      return true;
    case 0:
      ALOGE("child");
      my_pid = getpid();
      while (true) {
        errno = 0;
        int res = syscall(__NR_gettid, 0, 0);
        if (res != my_pid) {
          exit(EXIT_VULNERABLE);
        }
      }
      return true;
    default:
      sleep(1);
      if (ptrace(PTRACE_ATTACH, child, NULL, NULL)) {
        err(1, "main() : ptrace attach");
        return true;
      }
      int status;
      if (waitpid(child, &status, 0) != child) {
        err(1, "main() : wait for child");
        return true;
      }
      if (ptrace(PTRACE_SYSCALL, child, NULL, NULL)) {
        err(1, "main() : ptrace syscall entry");
        return true;
      }
      if (waitpid(child, &status, 0) != child) {
        err(1, "main() : wait for child");
        return true;
      }
      int syscallno;
      struct iovec iov = {.iov_base = &syscallno, .iov_len = sizeof(syscallno)};
      if (ptrace(PTRACE_GETREGSET, child, NT_ARM_SYSTEM_CALL, &iov)) {
        err(1, "main() : ptrace getregs");
        return true;
      }
      if (syscallno != __NR_gettid) {
        err(1, "main() : not gettid");
        return true;
      }
      syscallno = __NR_swapon;
      if (ptrace(PTRACE_SETREGSET, child, NT_ARM_SYSTEM_CALL, &iov)) {
        err(1, "main() : ptrace setregs");
        return true;
      }
      if (ptrace(PTRACE_DETACH, child, NULL, NULL)) {
        err(1, "main() : ptrace syscall");
        return true;
      }
      // kill child process
      int killRet = kill(child, SIGCONT);
      if (killRet == -1) {
        printf(
            "main() : killing child process(%d) with SIGCONT on error (%s)\n",
            child, strerror(errno));
      }
      // wait for child process stop
      int waitPid = waitpid(child, &status, 0);
      if (waitPid == -1) {
        perror("main() waitpid: waitpid = -1 and continue wait");
        return true;
      }
      if (WIFEXITED(status)) {
        //  detected vulnarable exit status of child process
        return WEXITSTATUS(status) != EXIT_VULNERABLE;
      }
      break;
  }
  return true;
}

static JNINativeMethod gMethods[] = {
    { "testSyscallBlocked", "(I)Z",
            (void*) testSyscallBlocked },
    {  "testPtrace_CVE_2019_2054", "()Z",
            (void *) testPtrace_CVE_2019_2054 },
};

int register_android_seccomp_cts_app_SeccompTest(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/seccomp/cts/app/SeccompDeviceTest");

    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
