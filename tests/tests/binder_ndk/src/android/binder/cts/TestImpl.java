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

package android.binder.cts;

import android.os.IBinder;

import test_package.IEmpty;
import test_package.ITest;
import test_package.RegularPolygon;

public class TestImpl extends ITest.Stub {
  @Override
  public void TestVoidReturn() {}

  @Override
  public void TestOneway() {}

  @Override
  public int RepeatInt(int in_value) {
      return in_value;
  }

  @Override
  public long RepeatLong(long in_value) {
      return in_value;
  }

  @Override
  public float RepeatFloat(float in_value) {
      return in_value;
  }

  @Override
  public double RepeatDouble(double in_value) {
      return in_value;
  }

  @Override
  public boolean RepeatBoolean(boolean in_value) {
      return in_value;
  }

  @Override
  public char RepeatChar(char in_value) {
      return in_value;
  }

  @Override
  public byte RepeatByte(byte in_value) {
      return in_value;
  }

  @Override
  public IBinder RepeatBinder(IBinder in_value) {
      return in_value;
  }

  @Override
  public IEmpty RepeatInterface(IEmpty in_value) {
      return in_value;
  }

  @Override
  public String RepeatString(String in_value) {
      return in_value;
  }

  @Override
  public RegularPolygon RepeatPolygon(RegularPolygon in_value) {
      return in_value;
  }

  @Override
  public void RenamePolygon(RegularPolygon value, String name) {
      value.name = name;
  }
}
