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

package test_package;

import test_package.IEmpty;
import test_package.RegularPolygon;

// This test interface is used in order to test the all of the things that AIDL can generate which
// build on top of the NDK.
//
// Repeat => return the same value. This is used to keep the clients/tests simple.
interface ITest {
    const int kZero = 0;
    const int kOne = 1;
    const int kOnes = 0xffffffff;
    const String kEmpty = "";
    const String kFoo = "foo";

    void TestVoidReturn();
    oneway void TestOneway();

    // Sending/receiving primitive types.
    int RepeatInt(int value);
    long RepeatLong(long value);
    float RepeatFloat(float value);
    double RepeatDouble(double value);
    boolean RepeatBoolean(boolean value);
    char RepeatChar(char value);
    byte RepeatByte(byte value);

    IBinder RepeatBinder(IBinder value);
    IEmpty RepeatInterface(IEmpty value);

    String RepeatString(String value);

    RegularPolygon RepeatPolygon(in RegularPolygon value);
}
