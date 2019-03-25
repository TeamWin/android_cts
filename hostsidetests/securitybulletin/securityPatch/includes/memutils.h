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
#define MAX_ENTRIES        (1024)
#define INITIAL_VAL        (0xBE)

#define DISABLE_MEM_ACCESS(mem, size)\
    mprotect((char *) mem, size, PROT_NONE);

#define ENABLE_MEM_ACCESS(mem, size)\
    mprotect((char *) mem, size, PROT_READ | PROT_WRITE);

typedef struct _map_struct_t {
    void *start_ptr;
    void *mem_ptr;
    int num_pages;
} map_struct_t;

static void* (*real_memalign)(size_t, size_t) = NULL;
static void (*real_free)(void *) = NULL;
static int s_memutils_initialized = 0;
static int s_mem_map_index = 0;
static struct sigaction new_sa, old_sa;
map_struct_t s_mem_map[MAX_ENTRIES];
#if (!(defined CHECK_OVERFLOW) && !(defined CHECK_UNDERFLOW))
    #error "CHECK MACROS NOT DEFINED"
#endif
