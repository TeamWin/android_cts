/*
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

#include <utils/Log.h>
#include "system/camera_metadata.h"

static const uint32_t kPaddingByteOffset = 36;

int main() {
    camera_metadata_t *buffer = nullptr;
    buffer = allocate_camera_metadata(1, 100);
    if (buffer != nullptr) {
        uint8_t *byteBuffer = reinterpret_cast<uint8_t *> (buffer);
        if ((byteBuffer[kPaddingByteOffset] != 0) ||
                (byteBuffer[kPaddingByteOffset + 1] != 0) ||
                (byteBuffer[kPaddingByteOffset + 2] != 0) ||
                (byteBuffer[kPaddingByteOffset + 3] != 0)) {
            ALOGE("Metadata padding is not empty");
        }
        free_camera_metadata(buffer);
    }

    return 0;
}
