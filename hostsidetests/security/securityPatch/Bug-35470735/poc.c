/**
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

#define _GNU_SOURCE
#include <string.h>
#include <sys/types.h>

#include <asm/ioctl.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/wait.h>
#include <unistd.h>

//#define DEBUG
#ifdef DEBUG
#define LOG(fmt, ...)                                                  \
  do {                                                                 \
    printf("%s:%d: " fmt "\n", __FUNCTION__, __LINE__, ##__VA_ARGS__); \
  } while (0)
#else
#define LOG(fmt, ...)
#endif

int open_file(char* filename) {
  int fd;

  fd = open(filename, O_RDWR);
  if (fd < 0) {
    LOG("open %s fail %s\n", filename, strerror(errno));
    exit(1);
  }
  LOG("[%d] open %s succ return fd %d\n", gettid(), filename, fd);

  return fd;
}

int test_write(int fd, char* buf, int size) {
  int ret;

  ret = write(fd, buf, size);
  if (fd < 0) {
    LOG("write %d fail %s\n", fd, strerror(errno));
  } else
    LOG("[%d] write %s succ\n", gettid(), buf);

  return ret;
}

void prepare(void) {  // enable the log
  int enable_fd;
  char* str = "1";
  enable_fd = open_file("/proc/sys/ath_pktlog/cld/enable");
  test_write(enable_fd, str, strlen(str));
  close(enable_fd);
}

#define SIZE 16
void Thread1(void) {  // thread to read the log
  int cld_fd, ret;
  char buf[SIZE] = {0};
  cld_fd = open_file("/proc/ath_pktlog/cld");
  while (1) {
    ret = read(cld_fd, buf, SIZE);
    if (ret > 0) LOG("[%d] read succ %d\n", gettid(), ret);
    sleep(0.5);
  }
  close(cld_fd);
}

void Thread2(void) {  // thread to free pl_info->buf
  int size_fd;
  char* size1 = "1024";
  char* size2 = "2048";
  int index = 0;
  char buf[8] = {0};
  size_fd = open_file("/proc/sys/ath_pktlog/cld/size");
  while (1) {
    if (index++ % 2)
      test_write(size_fd, size1, strlen(size1));
    else
      test_write(size_fd, size2, strlen(size2));
    sleep(0.5);
  }
  close(size_fd);
}

#define TC 8
void trigger() {
  int i, ret;
  pthread_t tid1s[TC];
  pthread_t tid2s[TC];

  LOG("Try to trigger..\n");

  for (i = 0; i < TC; i++) {
    ret = pthread_create((pthread_t*)&tid1s[i], NULL, (void*)Thread1, NULL);
    sleep(1);
    ret = pthread_create((pthread_t*)&tid2s[i], NULL, (void*)Thread2, NULL);
  }

  for (i = 0; i < TC; i++) {
    pthread_join(tid1s[i], NULL);
    pthread_join(tid2s[i], NULL);
  }
}

int main(int argc, char* argv[]) {
  for (int i = 0; i < 1000; i++)
  {
    prepare();
    trigger();
  }
  return 0;
}
