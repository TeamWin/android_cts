/**
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
#define _GNU_SOURCE
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/time.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifdef IOS_DISPLAY
#include "cast_types.h"
#else
#include "ihevc_typedefs.h"
#endif

#include "ihevcd_cxa.h"

#define COLORING_BYTE 0xBE
void write_to_callee_saved_registers(char write_array[8][8]);
void read_from_callee_saved_registers(char read_array[8][8]);
void check_read_values(char read_array[8][8], char write_arrayp[8][8]);
char write_array[8][8] __attribute__ ((aligned (16)));
char read_array[8][8] __attribute__ ((aligned (16)));

void check_read_values(char read_array[8][8], char write_array[8][8]) {
    if (0 != (memcmp(write_array, read_array, 64))) {
        abort();
    }
}
IV_API_CALL_STATUS_T ivd_cxa_api_function(iv_obj_t *ps_handle, void *pv_api_ip,
                                          void *pv_api_op) {
    memset(write_array, COLORING_BYTE, (8 * 8));
    memset(read_array, 0, (8 * 8));
    write_to_callee_saved_registers(write_array);

    IV_API_CALL_STATUS_T status = 0;
    status = ihevcd_cxa_api_function(ps_handle, pv_api_ip, pv_api_op);

    read_from_callee_saved_registers(read_array);
    check_read_values(read_array, write_array);
    return status;
}
