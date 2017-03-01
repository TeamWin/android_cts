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
 *
 */

#include "nativeTestHelper.h"
#include "SensorTest.h"

namespace {
using android::SensorTest::SensorTest;

jlong setUp(JNIEnv*, jclass) {
    SensorTest *test = new SensorTest();
    if (test != nullptr) {
        test->SetUp();
    }
    return reinterpret_cast<jlong>(test);
}

void tearDown(JNIEnv*, jclass, jlong instance) {
    delete reinterpret_cast<SensorTest *>(instance);
}

void test(JNIEnv* env, jclass, jlong instance) {
    SensorTest *test = reinterpret_cast<SensorTest *>(instance);
    ASSERT_NE(test, nullptr);

    // test if SensorTest is intialized
    test->testInitialized(env);

    // test gyro direct report using shared memory buffer
    test->testGyroscopeSharedMemoryDirectReport(env);
}

JNINativeMethod gMethods[] = {
    {  "nativeSetUp", "()J",
            (void *) setUp},
    {  "nativeTearDown", "(J)V",
            (void *) tearDown},
    {  "nativeTest", "(J)V",
            (void *) test},
};
} // unamed namespace

int register_android_view_cts_SensorNativeTest(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/hardware/cts/SensorNativeTest");
    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
