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
#define LOG_TAG "Cts-NdkBinderTest"

#include <aidl/test_package/BnEmpty.h>
#include <aidl/test_package/BpTest.h>
#include <aidl/test_package/RegularPolygon.h>
#include <android/binder_ibinder_jni.h>
#include <gtest/gtest.h>

#include "itest_impl.h"
#include "utilities.h"

using ::aidl::test_package::BpTest;
using ::aidl::test_package::ITest;
using ::aidl::test_package::RegularPolygon;
using ::ndk::ScopedAStatus;
using ::ndk::ScopedFileDescriptor;
using ::ndk::SharedRefBase;
using ::ndk::SpAIBinder;

struct Params {
  std::shared_ptr<ITest> iface;
  bool shouldBeRemote;
  std::string expectedName;
};

#define iface GetParam().iface
#define shouldBeRemote GetParam().shouldBeRemote

class NdkBinderTest_Aidl : public NdkBinderTest,
                           public ::testing::WithParamInterface<Params> {};

TEST_P(NdkBinderTest_Aidl, GotTest) { ASSERT_NE(nullptr, iface); }

TEST_P(NdkBinderTest_Aidl, SanityCheckSource) {
  std::string name;
  ASSERT_OK(iface->GetName(&name));
  EXPECT_EQ(GetParam().expectedName, name);
}

TEST_P(NdkBinderTest_Aidl, Remoteness) {
  ASSERT_EQ(shouldBeRemote, iface->isRemote());
}

TEST_P(NdkBinderTest_Aidl, UseBinder) {
  ASSERT_EQ(STATUS_OK, AIBinder_ping(iface->asBinder().get()));
}

TEST_P(NdkBinderTest_Aidl, Trivial) {
  ASSERT_OK(iface->TestVoidReturn());
  ASSERT_OK(iface->TestOneway());
}

TEST_P(NdkBinderTest_Aidl, CallingInfo) {
  EXPECT_OK(iface->CacheCallingInfoFromOneway());
  int32_t res;

  EXPECT_OK(iface->GiveMeMyCallingPid(&res));
  EXPECT_EQ(getpid(), res);

  EXPECT_OK(iface->GiveMeMyCallingUid(&res));
  EXPECT_EQ(getuid(), res);

  EXPECT_OK(iface->GiveMeMyCallingPidFromOneway(&res));
  if (shouldBeRemote) {
    // PID is hidden from oneway calls
    EXPECT_EQ(0, res);
  } else {
    EXPECT_EQ(getpid(), res);
  }

  EXPECT_OK(iface->GiveMeMyCallingUidFromOneway(&res));
  EXPECT_EQ(getuid(), res);
}

TEST_P(NdkBinderTest_Aidl, Constants) {
  ASSERT_EQ(0, ITest::kZero);
  ASSERT_EQ(1, ITest::kOne);
  ASSERT_EQ(0xffffffff, ITest::kOnes);
  ASSERT_EQ(std::string(""), ITest::kEmpty);
  ASSERT_EQ(std::string("foo"), ITest::kFoo);
}

TEST_P(NdkBinderTest_Aidl, RepeatPrimitives) {
  {
    int32_t out;
    ASSERT_OK(iface->RepeatInt(3, &out));
    EXPECT_EQ(3, out);
  }

  {
    int64_t out;
    ASSERT_OK(iface->RepeatLong(3, &out));
    EXPECT_EQ(3, out);
  }

  {
    float out;
    ASSERT_OK(iface->RepeatFloat(2.0f, &out));
    EXPECT_EQ(2.0f, out);
  }

  {
    double out;
    ASSERT_OK(iface->RepeatDouble(3.0, &out));
    EXPECT_EQ(3.0, out);
  }

  {
    bool out;
    ASSERT_OK(iface->RepeatBoolean(true, &out));
    EXPECT_EQ(true, out);
  }

  {
    char16_t out;
    ASSERT_OK(iface->RepeatChar(L'@', &out));
    EXPECT_EQ(L'@', out);
  }

  {
    int8_t out;
    ASSERT_OK(iface->RepeatByte(3, &out));
    EXPECT_EQ(3, out);
  }
}

TEST_P(NdkBinderTest_Aidl, RepeatBinder) {
  SpAIBinder binder = iface->asBinder();
  SpAIBinder ret;

  ASSERT_OK(iface->RepeatBinder(binder, &ret));
  EXPECT_EQ(binder.get(), ret.get());

  ASSERT_OK(iface->RepeatNullableBinder(binder, &ret));
  EXPECT_EQ(binder.get(), ret.get());

  ASSERT_OK(iface->RepeatNullableBinder(nullptr, &ret));
  EXPECT_EQ(nullptr, ret.get());
}

TEST_P(NdkBinderTest_Aidl, RepeatInterface) {
  class MyEmpty : public ::aidl::test_package::BnEmpty {};

  std::shared_ptr<IEmpty> empty = SharedRefBase::make<MyEmpty>();

  std::shared_ptr<IEmpty> ret;
  ASSERT_OK(iface->RepeatInterface(empty, &ret));
  EXPECT_EQ(empty.get(), ret.get());

  ASSERT_OK(iface->RepeatNullableInterface(empty, &ret));
  EXPECT_EQ(empty.get(), ret.get());

  ASSERT_OK(iface->RepeatNullableInterface(nullptr, &ret));
  EXPECT_EQ(nullptr, ret.get());
}

static void checkInOut(const ScopedFileDescriptor& inFd,
                       const ScopedFileDescriptor& outFd) {
  static const std::string kContent = "asdf";

  ASSERT_EQ(static_cast<int>(kContent.size()),
            write(inFd.get(), kContent.data(), kContent.size()));

  std::string out;
  out.resize(kContent.size());
  ASSERT_EQ(static_cast<int>(kContent.size()),
            read(outFd.get(), &out[0], kContent.size()));

  EXPECT_EQ(kContent, out);
}

static void checkFdRepeat(
    const std::shared_ptr<ITest>& test,
    ScopedAStatus (ITest::*repeatFd)(const ScopedFileDescriptor&,
                                     ScopedFileDescriptor*)) {
  int fds[2];

  while (pipe(fds) == -1 && errno == EAGAIN)
    ;

  ScopedFileDescriptor readFd(fds[0]);
  ScopedFileDescriptor writeFd(fds[1]);

  ScopedFileDescriptor readOutFd;
  ASSERT_OK((test.get()->*repeatFd)(readFd, &readOutFd));

  checkInOut(writeFd, readOutFd);
}

TEST_P(NdkBinderTest_Aidl, RepeatFd) { checkFdRepeat(iface, &ITest::RepeatFd); }

TEST_P(NdkBinderTest_Aidl, RepeatNullableFd) {
  checkFdRepeat(iface, &ITest::RepeatNullableFd);

  ScopedFileDescriptor in;
  EXPECT_EQ(-1, in.get());

  ScopedFileDescriptor out;
  ASSERT_OK(iface->RepeatNullableFd(in, &out));

  EXPECT_EQ(-1, out.get());
}

TEST_P(NdkBinderTest_Aidl, RepeatString) {
  std::string res;

  EXPECT_OK(iface->RepeatString("", &res));
  EXPECT_EQ("", res);

  EXPECT_OK(iface->RepeatString("a", &res));
  EXPECT_EQ("a", res);

  EXPECT_OK(iface->RepeatString("say what?", &res));
  EXPECT_EQ("say what?", res);
}

TEST_P(NdkBinderTest_Aidl, RepeatNullableString) {
  std::optional<std::string> res;

  EXPECT_OK(iface->RepeatNullableString(std::nullopt, &res));
  EXPECT_EQ(std::nullopt, res);

  EXPECT_OK(iface->RepeatNullableString("", &res));
  EXPECT_EQ("", *res);

  EXPECT_OK(iface->RepeatNullableString("a", &res));
  EXPECT_EQ("a", *res);

  EXPECT_OK(iface->RepeatNullableString("say what?", &res));
  EXPECT_EQ("say what?", *res);
}

TEST_P(NdkBinderTest_Aidl, ParcelableDefaults) {
  RegularPolygon polygon;

  EXPECT_EQ("square", polygon.name);
  EXPECT_EQ(4, polygon.numSides);
  EXPECT_EQ(1.0f, polygon.sideLength);
}

TEST_P(NdkBinderTest_Aidl, RepeatPolygon) {
  RegularPolygon defaultPolygon = {"hexagon", 6, 2.0f};
  RegularPolygon outputPolygon;
  ASSERT_OK(iface->RepeatPolygon(defaultPolygon, &outputPolygon));
  EXPECT_EQ("hexagon", outputPolygon.name);
  EXPECT_EQ(defaultPolygon.numSides, outputPolygon.numSides);
  EXPECT_EQ(defaultPolygon.sideLength, outputPolygon.sideLength);
}

TEST_P(NdkBinderTest_Aidl, InsAndOuts) {
  RegularPolygon defaultPolygon;
  ASSERT_OK(iface->RenamePolygon(&defaultPolygon, "Jerry"));
  EXPECT_EQ("Jerry", defaultPolygon.name);
}

template <typename T>
using RepeatMethod = ScopedAStatus (ITest::*)(const std::vector<T>&,
                                              std::vector<T>*, std::vector<T>*);

template <typename T>
void testRepeat(const std::shared_ptr<ITest>& i, RepeatMethod<T> repeatMethod,
                std::vector<std::vector<T>> tests) {
  for (const auto& input : tests) {
    std::vector<T> out1;
    out1.resize(input.size());
    std::vector<T> out2;

    ASSERT_OK((i.get()->*repeatMethod)(input, &out1, &out2)) << input.size();
    EXPECT_EQ(input, out1);
    EXPECT_EQ(input, out2);
  }
}

TEST_P(NdkBinderTest_Aidl, Arrays) {
  testRepeat<bool>(iface, &ITest::RepeatBooleanArray,
                   {
                       {},
                       {true},
                       {false, true, false},
                   });
  testRepeat<int8_t>(iface, &ITest::RepeatByteArray,
                     {
                         {},
                         {1},
                         {1, 2, 3},
                     });
  testRepeat<char16_t>(iface, &ITest::RepeatCharArray,
                       {
                           {},
                           {L'@'},
                           {L'@', L'!', L'A'},
                       });
  testRepeat<int32_t>(iface, &ITest::RepeatIntArray,
                      {
                          {},
                          {1},
                          {1, 2, 3},
                      });
  testRepeat<int64_t>(iface, &ITest::RepeatLongArray,
                      {
                          {},
                          {1},
                          {1, 2, 3},
                      });
  testRepeat<float>(iface, &ITest::RepeatFloatArray,
                    {
                        {},
                        {1.0f},
                        {1.0f, 2.0f, 3.0f},
                    });
  testRepeat<double>(iface, &ITest::RepeatDoubleArray,
                     {
                         {},
                         {1.0},
                         {1.0, 2.0, 3.0},
                     });
  testRepeat<std::string>(iface, &ITest::RepeatStringArray,
                          {
                              {},
                              {"asdf"},
                              {"", "aoeu", "lol", "brb"},
                          });
}

template <typename T>
using RepeatNullableMethod = ScopedAStatus (ITest::*)(
    const std::optional<std::vector<std::optional<T>>>&,
    std::optional<std::vector<std::optional<T>>>*,
    std::optional<std::vector<std::optional<T>>>*);

template <typename T>
void testRepeat(
    const std::shared_ptr<ITest>& i, RepeatNullableMethod<T> repeatMethod,
    std::vector<std::optional<std::vector<std::optional<T>>>> tests) {
  for (const auto& input : tests) {
    std::optional<std::vector<std::optional<T>>> out1;
    if (input) {
      out1 = std::vector<std::optional<T>>{};
      out1->resize(input->size());
    }
    std::optional<std::vector<std::optional<T>>> out2;

    ASSERT_OK((i.get()->*repeatMethod)(input, &out1, &out2))
        << (input ? input->size() : -1);
    EXPECT_EQ(input, out1);
    EXPECT_EQ(input, out2);
  }
}

template <typename T>
using SingleRepeatNullableMethod = ScopedAStatus (ITest::*)(
    const std::optional<std::vector<T>>&, std::optional<std::vector<T>>*);

template <typename T>
void testRepeat(const std::shared_ptr<ITest>& i,
                SingleRepeatNullableMethod<T> repeatMethod,
                std::vector<std::optional<std::vector<T>>> tests) {
  for (const auto& input : tests) {
    std::optional<std::vector<T>> ret;
    ASSERT_OK((i.get()->*repeatMethod)(input, &ret))
        << (input ? input->size() : -1);
    EXPECT_EQ(input, ret);
  }
}

TEST_P(NdkBinderTest_Aidl, NullableArrays) {
  testRepeat<bool>(iface, &ITest::RepeatNullableBooleanArray,
                   {
                       std::nullopt,
                       {{}},
                       {{true}},
                       {{false, true, false}},
                   });
  testRepeat<int8_t>(iface, &ITest::RepeatNullableByteArray,
                     {
                         std::nullopt,
                         {{}},
                         {{1}},
                         {{1, 2, 3}},
                     });
  testRepeat<char16_t>(iface, &ITest::RepeatNullableCharArray,
                       {
                           std::nullopt,
                           {{}},
                           {{L'@'}},
                           {{L'@', L'!', L'A'}},
                       });
  testRepeat<int32_t>(iface, &ITest::RepeatNullableIntArray,
                      {
                          std::nullopt,
                          {{}},
                          {{1}},
                          {{1, 2, 3}},
                      });
  testRepeat<int64_t>(iface, &ITest::RepeatNullableLongArray,
                      {
                          std::nullopt,
                          {{}},
                          {{1}},
                          {{1, 2, 3}},
                      });
  testRepeat<float>(iface, &ITest::RepeatNullableFloatArray,
                    {
                        std::nullopt,
                        {{}},
                        {{1.0f}},
                        {{1.0f, 2.0f, 3.0f}},
                    });
  testRepeat<double>(iface, &ITest::RepeatNullableDoubleArray,
                     {
                         std::nullopt,
                         {{}},
                         {{1.0}},
                         {{1.0, 2.0, 3.0}},
                     });
  testRepeat<std::optional<std::string>>(
      iface, &ITest::RepeatNullableStringArray,
      {
          std::nullopt,
          {{}},
          {{"asdf"}},
          {{std::nullopt}},
          {{"aoeu", "lol", "brb"}},
          {{"", "aoeu", std::nullopt, "brb"}},
      });
  testRepeat<std::string>(iface, &ITest::DoubleRepeatNullableStringArray,
                          {
                              {{}},
                              {{"asdf"}},
                              {{std::nullopt}},
                              {{"aoeu", "lol", "brb"}},
                              {{"", "aoeu", std::nullopt, "brb"}},
                          });
}

std::shared_ptr<ITest> getLocalService() {
  // BpTest -> AIBinder -> test
  std::shared_ptr<MyTest> test = SharedRefBase::make<MyTest>();
  return BpTest::associate(test->asBinder());
}

std::shared_ptr<ITest> getNdkBinderTestJavaService(const std::string& method) {
  JNIEnv* env = GetEnv();
  if (env == nullptr) {
    std::cout << "No environment" << std::endl;
    return nullptr;
  }

  jclass cl = env->FindClass("android/binder/cts/NdkBinderTest");
  if (cl == nullptr) {
    std::cout << "No class" << std::endl;
    return nullptr;
  }

  jmethodID mid =
      env->GetStaticMethodID(cl, method.c_str(), "()Landroid/os/IBinder;");
  if (mid == nullptr) {
    std::cout << "No method id" << std::endl;
    return nullptr;
  }

  jobject object = env->CallStaticObjectMethod(cl, mid);
  if (object == nullptr) {
    std::cout << "Got null service from Java" << std::endl;
    return nullptr;
  }

  SpAIBinder binder = SpAIBinder(AIBinder_fromJavaBinder(env, object));

  return BpTest::associate(binder);
}

INSTANTIATE_TEST_CASE_P(LocalNative, NdkBinderTest_Aidl,
                        ::testing::Values(Params{getLocalService(),
                                                 false /*shouldBeRemote*/,
                                                 "CPP"}));
INSTANTIATE_TEST_CASE_P(
    LocalNativeFromJava, NdkBinderTest_Aidl,
    ::testing::Values(Params{
        getNdkBinderTestJavaService("getLocalNativeService"),
        false /*shouldBeRemote*/, "CPP"}));
INSTANTIATE_TEST_CASE_P(LocalJava, NdkBinderTest_Aidl,
                        ::testing::Values(Params{
                            getNdkBinderTestJavaService("getLocalJavaService"),
                            false /*shouldBeRemote*/, "JAVA"}));
INSTANTIATE_TEST_CASE_P(
    RemoteNative, NdkBinderTest_Aidl,
    ::testing::Values(Params{
        getNdkBinderTestJavaService("getRemoteNativeService"),
        true /*shouldBeRemote*/, "CPP"}));
INSTANTIATE_TEST_CASE_P(RemoteJava, NdkBinderTest_Aidl,
                        ::testing::Values(Params{
                            getNdkBinderTestJavaService("getRemoteJavaService"),
                            true /*shouldBeRemote*/, "JAVA"}));
