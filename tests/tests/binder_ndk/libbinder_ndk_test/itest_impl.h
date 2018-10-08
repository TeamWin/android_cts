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

#include <test_package/BnTest.h>

#include "utilities.h"

using IEmpty = ::aidl::test_package::IEmpty;
using RegularPolygon = ::aidl::test_package::RegularPolygon;

class MyTest : public ::aidl::test_package::BnTest,
               public ThisShouldBeDestroyed {
 public:
  ::android::AutoAStatus TestVoidReturn() override {
    return ::android::AutoAStatus(AStatus_newOk());
  }
  ::android::AutoAStatus TestOneway() override {
    // This return code should be ignored since it is oneway.
    return ::android::AutoAStatus(AStatus_fromStatus(STATUS_UNKNOWN_ERROR));
  }
  ::android::AutoAStatus RepeatInt(int32_t in_value,
                                   int32_t* _aidl_return) override {
    *_aidl_return = in_value;
    return ::android::AutoAStatus(AStatus_newOk());
  }
  ::android::AutoAStatus RepeatLong(int64_t in_value,
                                    int64_t* _aidl_return) override {
    *_aidl_return = in_value;
    return ::android::AutoAStatus(AStatus_newOk());
  }
  ::android::AutoAStatus RepeatFloat(float in_value,
                                     float* _aidl_return) override {
    *_aidl_return = in_value;
    return ::android::AutoAStatus(AStatus_newOk());
  }
  ::android::AutoAStatus RepeatDouble(double in_value,
                                      double* _aidl_return) override {
    *_aidl_return = in_value;
    return ::android::AutoAStatus(AStatus_newOk());
  }
  ::android::AutoAStatus RepeatBoolean(bool in_value,
                                       bool* _aidl_return) override {
    *_aidl_return = in_value;
    return ::android::AutoAStatus(AStatus_newOk());
  }
  ::android::AutoAStatus RepeatChar(char16_t in_value,
                                    char16_t* _aidl_return) override {
    *_aidl_return = in_value;
    return ::android::AutoAStatus(AStatus_newOk());
  }
  ::android::AutoAStatus RepeatByte(int8_t in_value,
                                    int8_t* _aidl_return) override {
    *_aidl_return = in_value;
    return ::android::AutoAStatus(AStatus_newOk());
  }
  ::android::AutoAStatus RepeatBinder(
      const ::android::AutoAIBinder& in_value,
      ::android::AutoAIBinder* _aidl_return) override {
    *_aidl_return = in_value;
    return ::android::AutoAStatus(AStatus_newOk());
  }
  ::android::AutoAStatus RepeatInterface(
      const std::shared_ptr<IEmpty>& in_value,
      std::shared_ptr<IEmpty>* _aidl_return) override {
    *_aidl_return = in_value;
    return ::android::AutoAStatus(AStatus_newOk());
  }
  ::android::AutoAStatus RepeatString(const std::string& in_value,
                                      std::string* _aidl_return) override {
    *_aidl_return = in_value;
    return ::android::AutoAStatus(AStatus_newOk());
  }
  ::android::AutoAStatus RepeatPolygon(const RegularPolygon& in_value,
                                       RegularPolygon* _aidl_return) override {
    *_aidl_return = in_value;
    return ::android::AutoAStatus(AStatus_newOk());
  }
};