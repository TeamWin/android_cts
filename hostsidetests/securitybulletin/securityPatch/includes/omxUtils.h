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
#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <binder/IServiceManager.h>
#include <media/IMediaPlayerService.h>
#include <media/IOMX.h>
#include <binder/MemoryDealer.h>
#include "HardwareAPI.h"
#include "OMX_Component.h"
#include <media/stagefright/foundation/ALooper.h>
#include <utils/List.h>
#include <utils/Vector.h>
#include <utils/threads.h>
#include <binder/ProcessState.h>

#define DEFAULT_TIMEOUT 5000000
using namespace android;
template<class T>
static void InitOMXParams(T *params) {
    params->nSize = sizeof(T);
    params->nVersion.s.nVersionMajor = 1;
    params->nVersion.s.nVersionMinor = 0;
    params->nVersion.s.nRevision = 0;
    params->nVersion.s.nStep = 0;
}
using namespace android;
#define OMX_UTILS_IP_PORT 0
#define OMX_UTILS_OP_PORT 1

status_t omxUtilsInit(char *codec_name);
status_t omxUtilsGetParameter(int portIndex,
                              OMX_PARAM_PORTDEFINITIONTYPE *params);
status_t omxUtilsSetParameter(int portIndex,
                              OMX_PARAM_PORTDEFINITIONTYPE *params);
status_t omxUtilsStoreMetaDataInBuffers(OMX_U32 portIndex, OMX_BOOL enable,
                                        android::MetadataBufferType *type);
status_t omxUtilsEnableNativeBuffers(OMX_U32 portIndex, OMX_BOOL graphic,
                                     OMX_BOOL enable);
status_t omxUtilsAllocateBufferWithBackup(OMX_U32 portIndex,
                                          const sp<IMemory> &params,
                                          android::BnOMX::buffer_id *buffer,
                                          OMX_U32 allottedSize);
status_t omxUtilsUseBuffer(OMX_U32 portIndex, const sp<IMemory> &params,
                           android::BnOMX::buffer_id *buffer,
                           OMX_U32 allottedSize);
status_t omxUtilsUpdateGraphicBufferInMeta(
        OMX_U32 portIndex, const sp<GraphicBuffer> &graphicBuffer,
        android::BnOMX::buffer_id buffer);
status_t omxUtilsSendCommand(OMX_COMMANDTYPE cmd, OMX_S32 param);
status_t omxUtilsEmptyBuffer(android::BnOMX::buffer_id buffer,
                             OMX_U32 range_offset, OMX_U32 range_length,
                             OMX_U32 flags, OMX_TICKS timestamp, int fenceFd);
status_t omxUtilsFillBuffer(android::BnOMX::buffer_id buffer, int fenceFd);
status_t omxUtilsFreeBuffer(OMX_U32 portIndex,
                            android::BnOMX::buffer_id buffer);
status_t omxUtilsFreeNode();
status_t dequeueMessageForNode(omx_message *msg, int64_t timeoutUs);
void omxExitOnError(status_t ret);
