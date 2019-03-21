/**
 * Copyright (C) 2018 The Android Open Source Project
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
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/mman.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include "memutils.h"

void sigsegv_handler(int signum) {
    size_t page_size = getpagesize();
    for (int i = 0; i < s_mem_map_index; i++) {
        if (NULL != s_mem_map[i].start_ptr) {
            ENABLE_MEM_ACCESS(s_mem_map[i].start_ptr,
                              (s_mem_map[i].num_pages * page_size));
        }
    }
    (*old_sa.sa_handler)(signum);
}

void sighandler_init(void) {
    sigemptyset(&new_sa.sa_mask);
    new_sa.sa_handler = sigsegv_handler;
    sigaction(SIGSEGV, &new_sa, &old_sa);
}

void memutils_init(void) {
    real_memalign = dlsym(RTLD_NEXT, "memalign");
    if (NULL == real_memalign) {
        return;
    }
    real_free = dlsym(RTLD_NEXT, "free");
    if (NULL == real_free) {
        return;
    }
    memset(&s_mem_map, 0, MAX_ENTRIES * sizeof(map_struct_t));
    sighandler_init();
    s_memutils_initialized = 1;
}

void *memalign(size_t alignment, size_t size) {
    if (s_memutils_initialized == 0) {
        memutils_init();
    }
    char* start_ptr;
    char* mem_ptr;
    size_t total_size;
    size_t aligned_size = size;
    size_t num_pages;
    size_t page_size = getpagesize();

    /* User specified alignment is not respected and is overridden by
     * "new_alignment". This is required to catch OOB read when read offset is
     * less than user specified alignment. "new_alignment" is derived based on
     * size_t, and helps to avoid bus errors due to non-aligned memory.
     * "new_alignment", whenever used, is checked to ensure sizeof(size_t)
     * has returned proper value                                            */
    size_t new_alignment = sizeof(size_t);

    if (s_mem_map_index == MAX_ENTRIES) {
        return real_memalign(alignment, size);
    }

    if (alignment > page_size) {
        return real_memalign(alignment, size);
    }

    if ((0 == page_size) || (0 == alignment) || (0 == size)
            || (0 == new_alignment)) {
        return real_memalign(alignment, size);
    }
#ifdef CHECK_OVERFLOW
    if (0 != (size % new_alignment)) {
        aligned_size = size + (new_alignment - (size % new_alignment));
    }
#endif

    if (0 != (aligned_size % page_size)) {
        num_pages = (aligned_size / page_size) + 2;
    } else {
        num_pages = (aligned_size / page_size) + 1;
    }

    total_size = (num_pages * page_size);
    start_ptr = (char *) real_memalign(page_size, total_size);
#ifdef CHECK_OVERFLOW
    mem_ptr = (char *) start_ptr + ((num_pages - 1) * page_size) - aligned_size;
    DISABLE_MEM_ACCESS((start_ptr + ((num_pages - 1) * page_size)), page_size);
#endif
#ifdef CHECK_UNDERFLOW
    mem_ptr = (char *) start_ptr + page_size;
    DISABLE_MEM_ACCESS(start_ptr, page_size);
#endif
    s_mem_map[s_mem_map_index].start_ptr = start_ptr;
    s_mem_map[s_mem_map_index].mem_ptr = mem_ptr;
    s_mem_map[s_mem_map_index].num_pages = num_pages;
    s_mem_map_index++;
    memset(mem_ptr, INITIAL_VAL, size);
    return mem_ptr;
}

void free(void *ptr) {
    if (s_memutils_initialized == 0) {
        memutils_init();
    }
    if (ptr != NULL) {
        int i = 0;
        size_t page_size = getpagesize();
        for (i = 0; i < s_mem_map_index; i++) {
            if (ptr == s_mem_map[i].mem_ptr) {
                ENABLE_MEM_ACCESS(s_mem_map[i].start_ptr,
                                  (s_mem_map[i].num_pages * page_size));
                real_free(s_mem_map[i].start_ptr);
                memset(&s_mem_map[i], 0, sizeof(map_struct_t));
                return;
            }
        }
    }
    real_free(ptr);
    return;
}
