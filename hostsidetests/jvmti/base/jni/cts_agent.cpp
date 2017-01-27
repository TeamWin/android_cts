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
#include <jvmti.h>

#include "android-base/logging.h"
#include "common.h"
#include "jni_binder.h"
#include "jvmti_helper.h"

namespace cts {
namespace jvmti {

static jvmtiEnv* jvmti_env;

jvmtiEnv* GetJvmtiEnv() {
  return jvmti_env;
}

int JniThrowNullPointerException(JNIEnv* env, const char* msg) {
  JNIEnv* e = reinterpret_cast<JNIEnv*>(env);

  if (env->ExceptionCheck()) {
    env->ExceptionClear();
  }

  jclass exc_class = env->FindClass("java/lang/NullPointerException");
  if (exc_class == nullptr) {
    return -1;
  }

  bool ok = env->ThrowNew(exc_class, msg) == JNI_OK;

  env->DeleteLocalRef(exc_class);

  return ok ? 0 : -1;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* vm,
                                               char* options ATTRIBUTE_UNUSED,
                                               void* reserved ATTRIBUTE_UNUSED) {
  BindOnLoad(vm);

  if (vm->GetEnv(reinterpret_cast<void**>(&jvmti_env), JVMTI_VERSION_1_0) != 0) {
    LOG(FATAL) << "Could not get shared jvmtiEnv";
  }

  SetAllCapabilities(jvmti_env);
  return 0;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm,
                                                 char* options ATTRIBUTE_UNUSED,
                                                 void* reserved ATTRIBUTE_UNUSED) {
  BindOnAttach(vm);

  if (vm->GetEnv(reinterpret_cast<void**>(&jvmti_env), JVMTI_VERSION_1_0) != 0) {
    LOG(FATAL) << "Could not get shared jvmtiEnv";
  }

  SetAllCapabilities(jvmti_env);
  return 0;
}

}
}
