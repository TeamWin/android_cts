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

#include "jni.h"

class ScopedUtfChars {
 public:
  ScopedUtfChars(JNIEnv* env, jstring s) : env_(env), string_(s) {
    if (s == NULL) {
      utf_chars_ = NULL;
    } else {
      utf_chars_ = env->GetStringUTFChars(s, NULL);
    }
  }

  ~ScopedUtfChars() {
    if (utf_chars_) {
      env_->ReleaseStringUTFChars(string_, utf_chars_);
    }
  }

  const char* c_str() const {
    return utf_chars_;
  }

 private:
  JNIEnv* env_;
  jstring string_;
  const char* utf_chars_;
};

extern "C" JNIEXPORT void JNICALL
Java_android_signature_cts_DexMemberChecker_getField_1JNI(
    JNIEnv* env, jclass, jclass klass, jstring name, jstring type) {
  ScopedUtfChars utf_name(env, name);
  ScopedUtfChars utf_type(env, type);
  // Attempt to access the given instance field. It will succeed if it exists,
  // and throw NoSuchFieldError if not.
  env->GetFieldID(klass, utf_name.c_str(), utf_type.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_android_signature_cts_DexMemberChecker_getStaticField_1JNI(
    JNIEnv* env, jclass, jclass klass, jstring name, jstring type) {
  ScopedUtfChars utf_name(env, name);
  ScopedUtfChars utf_type(env, type);
  // Attempt to access the given static field. It will succeed if it exists,
  // and throw NoSuchFieldError if not.
  env->GetStaticFieldID(klass, utf_name.c_str(), utf_type.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_android_signature_cts_DexMemberChecker_getMethod_1JNI(
    JNIEnv* env, jclass, jclass klass, jstring name, jstring signature) {
  ScopedUtfChars utf_name(env, name);
  ScopedUtfChars utf_signature(env, signature);
  // Attempt to access the given instance method. It will succeed if it exists,
  // and throw NoSuchMethodError if not.
  env->GetMethodID(klass, utf_name.c_str(), utf_signature.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_android_signature_cts_DexMemberChecker_getStaticMethod_1JNI(
    JNIEnv* env, jclass, jclass klass, jstring name, jstring signature) {
  ScopedUtfChars utf_name(env, name);
  ScopedUtfChars utf_signature(env, signature);
  // Attempt to access the given static method. It will succeed if it exists,
  // and throw NoSuchMethodError if not.
  env->GetStaticMethodID(klass, utf_name.c_str(), utf_signature.c_str());
}
