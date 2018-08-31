/*
 * Copyright 2018 The Android Open Source Project
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
 *
 */

#define LOG_TAG "SystemFonts"

#include <jni.h>
#include <android/system_fonts.h>

#include <array>
#include <android/log.h>

namespace {

jlong nOpenIterator(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(ASystemFontIterator_open());
}

void nCloseIterator(JNIEnv*, jclass, jlong ptr) {
    ASystemFontIterator_close(reinterpret_cast<ASystemFontIterator*>(ptr));
}

jlong nGetNext(JNIEnv*, jclass, jlong ptr) {
    return reinterpret_cast<jlong>(ASystemFontIterator_next(
            reinterpret_cast<ASystemFontIterator*>(ptr)));
}

void nCloseFont(JNIEnv*, jclass, jlong ptr) {
    return ASystemFont_close(reinterpret_cast<ASystemFont*>(ptr));
}

jstring nGetFilePath(JNIEnv* env, jclass, jlong ptr) {
    return env->NewStringUTF(ASystemFont_getFontFilePath(reinterpret_cast<ASystemFont*>(ptr)));
}

jint nGetWeight(JNIEnv*, jclass, jlong ptr) {
    return ASystemFont_getWeight(reinterpret_cast<ASystemFont*>(ptr));
}

jboolean nIsItalic(JNIEnv*, jclass, jlong ptr) {
    return ASystemFont_isItalic(reinterpret_cast<ASystemFont*>(ptr));
}

jstring nGetLocale(JNIEnv* env, jclass, jlong ptr) {
    return env->NewStringUTF(ASystemFont_getLocale(reinterpret_cast<ASystemFont*>(ptr)));
}

jint nGetCollectionIndex(JNIEnv*, jclass, jlong ptr) {
    return ASystemFont_getCollectionIndex(reinterpret_cast<ASystemFont*>(ptr));
}

jint nGetAxisCount(JNIEnv*, jclass, jlong ptr) {
    return ASystemFont_getAxisCount(reinterpret_cast<ASystemFont*>(ptr));
}

jint nGetAxisTag(JNIEnv*, jclass, jlong ptr, jint axisIndex) {
    return ASystemFont_getAxisTag(reinterpret_cast<ASystemFont*>(ptr), axisIndex);
}

jfloat nGetAxisValue(JNIEnv*, jclass, jlong ptr, jint axisIndex) {
    return ASystemFont_getAxisValue(reinterpret_cast<ASystemFont*>(ptr), axisIndex);
}

const std::array<JNINativeMethod, 12> JNI_METHODS = {{
    { "nOpenIterator", "()J", (void*) nOpenIterator },
    { "nCloseIterator", "(J)V", (void*) nCloseIterator },
    { "nNext", "(J)J", (void*) nGetNext },
    { "nCloseFont", "(J)V", (void*) nCloseFont },
    { "nGetFilePath", "(J)Ljava/lang/String;", (void*) nGetFilePath },
    { "nGetWeight", "(J)I", (void*) nGetWeight },
    { "nIsItalic", "(J)Z", (void*) nIsItalic },
    { "nGetLocale", "(J)Ljava/lang/String;", (void*) nGetLocale },
    { "nGetCollectionIndex", "(J)I", (void*) nGetCollectionIndex },
    { "nGetAxisCount", "(J)I", (void*) nGetAxisCount },
    { "nGetAxisTag", "(JI)I", (void*) nGetAxisTag },
    { "nGetAxisValue", "(JI)F", (void*) nGetAxisValue },
}};

}

int register_android_graphics_fonts_cts_SystemFontTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/graphics/fonts/NativeSystemFontHelper");
    return env->RegisterNatives(clazz, JNI_METHODS.data(), JNI_METHODS.size());
}
