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
#include "omxUtils.h"

sp<IMediaPlayerService> mediaPlayerService = NULL;
IOMX::node_id node = 0;
sp<IOMX> service = NULL;
omx_message msg;
Mutex mLock;
Condition mMessageAddedCondition;
int32_t mLastMsgGeneration;
int32_t mCurGeneration;
List<omx_message> mMessageQueue;

struct CodecObserver : public BnOMXObserver {
 public:
    CodecObserver(int32_t gen)
            : mGeneration(gen) {
    }

    void onMessages(const std::list<omx_message> &messages) override;
    int32_t mGeneration;

 protected:
    virtual ~CodecObserver() {
    }
};
void handleMessages(int32_t gen, const std::list<omx_message> &messages) {
    Mutex::Autolock autoLock(mLock);
    for (std::list<omx_message>::const_iterator it = messages.cbegin();
            it != messages.cend();) {
        mMessageQueue.push_back(*it++);
        mLastMsgGeneration = gen;
    }
    mMessageAddedCondition.signal();
}
void CodecObserver::onMessages(const std::list<omx_message> &messages) {
    handleMessages(mGeneration, messages);
}

status_t dequeueMessageForNode(omx_message *msg, int64_t timeoutUs) {
    int64_t finishBy = ALooper::GetNowUs() + timeoutUs;
    status_t err = OK;

    while (err != TIMED_OUT) {
        Mutex::Autolock autoLock(mLock);
        if (mLastMsgGeneration < mCurGeneration) {
            mMessageQueue.clear();
        }
        // Messages are queued in batches, if the last batch queued is
        // from a node that already expired, discard those messages.
        List<omx_message>::iterator it = mMessageQueue.begin();
        while (it != mMessageQueue.end()) {
            if ((*it).node == node) {
                *msg = *it;
                mMessageQueue.erase(it);
                return OK;
            }
            ++it;
        }
        if (timeoutUs < 0) {
            err = mMessageAddedCondition.wait(mLock);
        } else {
            err = mMessageAddedCondition.waitRelative(
                    mLock, (finishBy - ALooper::GetNowUs()) * 1000);
        }
    }
    return err;
}
void omxUtilsCheckCmdExecution(char *name) {
    status_t err = dequeueMessageForNode(&msg, DEFAULT_TIMEOUT);
    if (err == TIMED_OUT) {
        ALOGE("[omxUtils] OMX command timed out for %s, exiting the app", name);
        exit (EXIT_FAILURE);
    }
}
void omxExitOnError(status_t ret) {
    if (ret != OK) {
        exit (EXIT_FAILURE);
    }
}
status_t omxUtilsInit(char *codecName) {
    android::ProcessState::self()->startThreadPool();
    sp<IServiceManager> sm = defaultServiceManager();
    sp<IBinder> binder = sm->getService(String16("media.player"));
    sp<IMediaPlayerService> mediaPlayerService = interface_cast
            <IMediaPlayerService> (binder);
    if (mediaPlayerService == NULL) {
        return NO_INIT;
    }
    service = mediaPlayerService->getOMX();
    if (service == NULL) {
        return NO_INIT;
    }
    sp<CodecObserver> observer = new CodecObserver(++mCurGeneration);
    return service->allocateNode(codecName, observer, NULL, &node);
}
status_t omxUtilsGetParameter(int portIndex,
                              OMX_PARAM_PORTDEFINITIONTYPE *params) {
    InitOMXParams(params);
    params->nPortIndex = portIndex;
    return service->getParameter(node, OMX_IndexParamPortDefinition, params,
                                 sizeof(OMX_PARAM_PORTDEFINITIONTYPE));
}
status_t omxUtilsSetParameter(int portIndex,
                              OMX_PARAM_PORTDEFINITIONTYPE *params) {
    InitOMXParams(params);
    params->nPortIndex = portIndex;
    return service->setParameter(node, OMX_IndexParamPortDefinition, params,
                                 sizeof(OMX_PARAM_PORTDEFINITIONTYPE));
}
status_t omxUtilsStoreMetaDataInBuffers(OMX_U32 portIndex, OMX_BOOL enable,
                                        MetadataBufferType *type) {
    return service->storeMetaDataInBuffers(node, portIndex, enable, type);
}

status_t omxUtilsUseBuffer(OMX_U32 portIndex, const sp<IMemory> &params,
                           android::BnOMX::buffer_id *buffer,
                           OMX_U32 allottedSize) {
    return service->useBuffer(node, portIndex, params, buffer, allottedSize);
}
status_t omxUtilsEnableNativeBuffers(OMX_U32 portIndex, OMX_BOOL graphic,
                                     OMX_BOOL enable) {
    return service->enableNativeBuffers(node, portIndex, graphic, enable);
}
status_t omxUtilsAllocateBufferWithBackup(OMX_U32 portIndex,
                                          const sp<IMemory> &params,
                                          android::BnOMX::buffer_id *buffer,
                                          OMX_U32 allottedSize) {
    return service->allocateBufferWithBackup(node, portIndex, params, buffer,
                                             allottedSize);
}
status_t omxUtilsUpdateGraphicBufferInMeta(OMX_U32 portIndex,
                                      const sp<GraphicBuffer> &graphicBuffer,
                                      android::BnOMX::buffer_id buffer) {
    return service->updateGraphicBufferInMeta(node, portIndex, graphicBuffer,
                                              buffer);
}
status_t omxUtilsSendCommand(OMX_COMMANDTYPE cmd, OMX_S32 param) {
    int ret = service->sendCommand(node, cmd, param);
    omxUtilsCheckCmdExecution((char *) __FUNCTION__);
    return ret;
}
status_t omxUtilsEmptyBuffer(android::BnOMX::buffer_id buffer,
                             OMX_U32 range_offset, OMX_U32 range_length,
                             OMX_U32 flags, OMX_TICKS timestamp, int fenceFd) {
    return service->emptyBuffer(node, buffer, range_offset, range_length, flags,
                                timestamp, fenceFd);
}
status_t omxUtilsFillBuffer(android::BnOMX::buffer_id buffer, int fenceFd) {
    return service->fillBuffer(node, buffer, fenceFd);
}
status_t omxUtilsFreeBuffer(OMX_U32 portIndex,
                            android::BnOMX::buffer_id buffer) {
    return service->freeBuffer(node, portIndex, buffer);
}
status_t omxUtilsFreeNode() {
    return service->freeNode(node);
}
