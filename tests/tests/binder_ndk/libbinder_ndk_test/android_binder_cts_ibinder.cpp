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

#include <android/binder_ibinder.h>
#include <gtest/gtest.h>

#include "utilities.h"

class NdkBinderTest_AIBinder : public NdkBinderTest {};

TEST_F(NdkBinderTest_AIBinder, Destruction) {
  bool destroyed = false;
  AIBinder* binder =
      SampleData::newBinder(nullptr, [&](SampleData*) { destroyed = true; });
  EXPECT_FALSE(destroyed);
  AIBinder_incStrong(binder);  // 1 -> 2
  EXPECT_FALSE(destroyed);
  AIBinder_decStrong(binder);  // 2 -> 1
  EXPECT_FALSE(destroyed);
  AIBinder_decStrong(binder);  // 1 -> 0
  EXPECT_TRUE(destroyed);
}

TEST_F(NdkBinderTest_AIBinder, GetClass) {
  AIBinder* binder = SampleData::newBinder();
  // class is already set since this local binder is contructed with it
  EXPECT_EQ(SampleData::kClass, AIBinder_getClass(binder));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, AssociateClass) {
  AIBinder* binder = SampleData::newBinder();
  EXPECT_TRUE(AIBinder_associateClass(binder, SampleData::kClass));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, AssociateWrongClassFails) {
  AIBinder* binder = SampleData::newBinder();
  EXPECT_FALSE(AIBinder_associateClass(binder, SampleData::kAnotherClass));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, GetUserData) {
  // This test can't use the helper utility since SampleData isn't exposed
  SampleData* data = new SampleData;
  // Takes ownership of data
  AIBinder* binder = AIBinder_new(SampleData::kClass, static_cast<void*>(data));
  EXPECT_EQ(data, AIBinder_getUserData(binder));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, DestructionGivesUserData) {
  // This test can't use the helper utility since SampleData isn't exposed
  SampleData* destroyedPointer = nullptr;
  SampleData* data = new SampleData(
      nullptr, [&](SampleData* data) { destroyedPointer = data; });
  // Takes ownership of data
  AIBinder* binder = AIBinder_new(SampleData::kClass, static_cast<void*>(data));
  EXPECT_EQ(nullptr, destroyedPointer);
  AIBinder_decStrong(binder);

  // These pointers no longer reference valid memory locations, but the pointers
  // themselves are valid
  EXPECT_EQ(data, destroyedPointer);
}

TEST_F(NdkBinderTest_AIBinder, DebugRefCount) {
  AIBinder* binder = SampleData::newBinder();
  EXPECT_EQ(1, AIBinder_debugGetRefCount(binder));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, WeakPointerCanPromote) {
  AIBinder* binder = SampleData::newBinder();
  AIBinder_Weak* weak = AIBinder_Weak_new(binder);
  AIBinder* promoted = AIBinder_Weak_promote(weak);
  EXPECT_EQ(binder, promoted);
  AIBinder_Weak_delete(&weak);
  EXPECT_EQ(nullptr, weak);
  AIBinder_decStrong(binder);
  AIBinder_decStrong(promoted);
}

TEST_F(NdkBinderTest_AIBinder, WeakPointerCanNotPromote) {
  AIBinder* binder = SampleData::newBinder();
  AIBinder_Weak* weak = AIBinder_Weak_new(binder);
  AIBinder_decStrong(binder);

  AIBinder* promoted = AIBinder_Weak_promote(weak);
  EXPECT_EQ(nullptr, promoted);
}

TEST_F(NdkBinderTest_AIBinder, LocalIsLocal) {
  AIBinder* binder = SampleData::newBinder();
  EXPECT_FALSE(AIBinder_isRemote(binder));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, IsAlive) {
  AIBinder* binder = SampleData::newBinder();
  EXPECT_TRUE(AIBinder_isAlive(binder));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, CanPing) {
  AIBinder* binder = SampleData::newBinder();
  EXPECT_OK(AIBinder_ping(binder));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, TransactionHappens) {
  AIBinder* binder = SampleData::newBinder(TransactionsReturn(STATUS_OK),
                                           ExpectLifetimeTransactions(1));
  EXPECT_OK(SampleData::transact(binder, kCode));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, OnewayTransactionHappens) {
  AIBinder* binder = SampleData::newBinder(TransactionsReturn(STATUS_OK),
                                           ExpectLifetimeTransactions(1));
  EXPECT_OK(SampleData::transact(binder, kCode, WriteNothingToParcel,
                                 ReadNothingFromParcel, FLAG_ONEWAY));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, TransactionCodeMaintained) {
  AIBinder* binder = SampleData::newBinder(
      [&](transaction_code_t code, const AParcel*, AParcel*) {
        EXPECT_EQ(code, kCode);
        return STATUS_OK;
      },
      ExpectLifetimeTransactions(1));
  EXPECT_OK(SampleData::transact(binder, kCode));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, TransactionCodeRangeRespected) {
  AIBinder* binder = SampleData::newBinder(TransactionsReturn(STATUS_OK));
  EXPECT_OK(SampleData::transact(binder, FIRST_CALL_TRANSACTION));
  EXPECT_OK(SampleData::transact(binder, FIRST_CALL_TRANSACTION + 1));
  EXPECT_OK(SampleData::transact(binder, LAST_CALL_TRANSACTION - 1));
  EXPECT_OK(SampleData::transact(binder, LAST_CALL_TRANSACTION));

  EXPECT_EQ(STATUS_UNKNOWN_TRANSACTION,
            SampleData::transact(binder, FIRST_CALL_TRANSACTION - 1));
  EXPECT_EQ(STATUS_UNKNOWN_TRANSACTION,
            SampleData::transact(binder, LAST_CALL_TRANSACTION + 1));
  AIBinder_decStrong(binder);
}

TEST_F(NdkBinderTest_AIBinder, UnknownFlagsRejected) {
  AIBinder* binder =
      SampleData::newBinder(nullptr, ExpectLifetimeTransactions(0));
  EXPECT_EQ(STATUS_BAD_VALUE,
            SampleData::transact(binder, kCode, WriteNothingToParcel,
                                 ReadNothingFromParcel, +1 + 415));
  EXPECT_EQ(STATUS_BAD_VALUE,
            SampleData::transact(binder, kCode, WriteNothingToParcel,
                                 ReadNothingFromParcel, FLAG_ONEWAY + 1));
  EXPECT_EQ(STATUS_BAD_VALUE,
            SampleData::transact(binder, kCode, WriteNothingToParcel,
                                 ReadNothingFromParcel, ~0));
  AIBinder_decStrong(binder);
}
