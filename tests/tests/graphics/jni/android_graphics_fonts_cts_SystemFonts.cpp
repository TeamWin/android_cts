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

class ScopedUtfChars {
public:
    ScopedUtfChars(JNIEnv* env, jstring s) : mEnv(env), mString(s) {
        if (s == nullptr) {
            mUtfChars = nullptr;
            mSize = 0;
        } else {
            mUtfChars = mEnv->GetStringUTFChars(mString, nullptr);
            mSize = mEnv->GetStringUTFLength(mString);
        }
    }

    ~ScopedUtfChars() {
        if (mUtfChars) {
            mEnv->ReleaseStringUTFChars(mString, mUtfChars);
        }
    }

    const char* c_str() const {
        return mUtfChars;
    }

    size_t size() const {
      return mSize;
    }

private:
    JNIEnv* mEnv;
    jstring mString;
    const char* mUtfChars;
    size_t mSize;
};

class ScopedStringChars {
public:
    ScopedStringChars(JNIEnv* env, jstring s) : mEnv(env), mString(s) {
        if (s == nullptr) {
            mChars = nullptr;
            mSize = 0;
        } else {
            mChars = mEnv->GetStringChars(mString, NULL);
            mSize = mEnv->GetStringLength(mString);
        }
    }

    ~ScopedStringChars() {
        if (mChars != nullptr) {
            mEnv->ReleaseStringChars(mString, mChars);
        }
    }

    const jchar* get() const {
        return mChars;
    }

    size_t size() const {
        return mSize;
    }


private:
    JNIEnv* const mEnv;
    const jstring mString;
    const jchar* mChars;
    size_t mSize;
};


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

jlong nMatchFamilyStyleCharacter(JNIEnv* env, jclass, jstring familyName, jint weight,
                                 jboolean italic, jstring langTags, jstring text) {
    ScopedUtfChars familyNameChars(env, familyName);
    ScopedUtfChars langTagsChars(env, langTags);
    ScopedStringChars textChars(env, text);
    return reinterpret_cast<jlong>(ASystemFont_matchFamilyStyleCharacter(
        familyNameChars.c_str(), weight, italic, langTagsChars.c_str(), textChars.get(),
        textChars.size(), nullptr));
}

jint nMatchFamilyStyleCharacter_runLength(JNIEnv* env, jclass, jstring familyName, jint weight,
                                          jboolean italic, jstring langTags, jstring text) {
    ScopedUtfChars familyNameChars(env, familyName);
    ScopedUtfChars langTagsChars(env, langTags);
    ScopedStringChars textChars(env, text);
    uint32_t runLength = 0;
    ASystemFont* ptr = ASystemFont_matchFamilyStyleCharacter(
            familyNameChars.c_str(), weight, italic, langTagsChars.c_str(), textChars.get(),
            textChars.size(), &runLength);
    ASystemFont_close(ptr);
    return runLength;
}

const std::array<JNINativeMethod, 14> JNI_METHODS = {{
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
    { "nMatchFamilyStyleCharacter",
          "(Ljava/lang/String;IZLjava/lang/String;Ljava/lang/String;)J",
          (void*) nMatchFamilyStyleCharacter },
    { "nMatchFamilyStyleCharacter_runLength",
          "(Ljava/lang/String;IZLjava/lang/String;Ljava/lang/String;)I",
          (void*) nMatchFamilyStyleCharacter_runLength },

}};

}

int register_android_graphics_fonts_cts_SystemFontTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/graphics/fonts/NativeSystemFontHelper");
    return env->RegisterNatives(clazz, JNI_METHODS.data(), JNI_METHODS.size());
}
