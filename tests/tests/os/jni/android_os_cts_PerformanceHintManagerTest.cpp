/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <errno.h>
#include <jni.h>
#include <sys/types.h>
#include <unistd.h>

#include <vector>

#include <android/performance_hint.h>

static jstring toJString(JNIEnv *env, const char* c_str) {
    return env->NewStringUTF(c_str);
}

constexpr int64_t DEFAULT_TARGET_NS = 16666666L;

class SessionWrapper {
public:
    explicit SessionWrapper(APerformanceHintSession* session) : mSession(session) {}
    SessionWrapper(SessionWrapper&& other) : mSession(other.mSession) {
        other.mSession = nullptr;
    }
    ~SessionWrapper() {
        if (mSession) {
            APerformanceHint_closeSession(mSession);
        }
    }

    SessionWrapper(const SessionWrapper&) = delete;
    SessionWrapper& operator=(const SessionWrapper&) = delete;

    APerformanceHintSession* session() const { return mSession; }

private:
    APerformanceHintSession* mSession;
};

static SessionWrapper createSession(APerformanceHintManager* manager) {
    int32_t pid = getpid();
    return SessionWrapper(APerformanceHint_createSession(manager, &pid, 1u, DEFAULT_TARGET_NS));
}

static jstring nativeTestCreateHintSession(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper a = createSession(manager);
    SessionWrapper b = createSession(manager);
    if (a.session() == nullptr) {
        if (b.session() != nullptr) {
            return toJString(env, "b is not null");
        }
    } else if (b.session() == nullptr) {
        if (a.session() != nullptr) {
            return toJString(env, "a is not null");
        }
    } else if (a.session() == b.session()) {
        return toJString(env, "a and b matches");
    }
    return nullptr;
}

static jstring nativeTestGetPreferredUpdateRateNanos(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() != nullptr) {
        bool positive = APerformanceHint_getPreferredUpdateRateNanos(manager) > 0;
        if (!positive)
          return toJString(env, "preferred rate is not positive");
    } else {
        if (APerformanceHint_getPreferredUpdateRateNanos(manager) != -1)
          return toJString(env, "preferred rate is not -1");
    }
    return nullptr;
}

static jstring nativeUpdateTargetWorkDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_updateTargetWorkDuration(wrapper.session(), 100);
    if (result != 0) {
        return toJString(env, "updateTargetWorkDuration did not return 0");
    }
    return nullptr;
}

static jstring nativeUpdateTargetWorkDurationWithNegativeDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_updateTargetWorkDuration(wrapper.session(), -1);
    if (result != EINVAL) {
        return toJString(env, "updateTargetWorkDuration did not return EINVAL");
    }
    return nullptr;
}

static jstring nativeReportActualWorkDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_reportActualWorkDuration(wrapper.session(), 100);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(100) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(wrapper.session(), 1);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(1) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(wrapper.session(), 100);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(100) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(wrapper.session(), 1000);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(1000) did not return 0");
    }

    return nullptr;
}

static jstring nativeReportActualWorkDurationWithIllegalArgument(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_reportActualWorkDuration(wrapper.session(), -1);
    if (result != EINVAL) {
        return toJString(env, "reportActualWorkDuration did not return EINVAL");
    }
    return nullptr;
}

static jstring nativeTestSetThreadsWithInvalidTid(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) {
        return toJString(env, "null manager");
    }
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) {
        return nullptr;
    }

    std::vector<pid_t> tids;
    tids.push_back(2);
    int result = APerformanceHint_setThreads(wrapper.session(), tids.data(), 1);
    if (result != EPERM) {
        return toJString(env, "setThreads did not return EPERM");
    }
    return nullptr;
}


static jstring nativeSetPreferPowerEfficiency(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_setPreferPowerEfficiency(wrapper.session(), false);
    if (result != 0) {
        return toJString(env, "setPreferPowerEfficiency(false) did not return 0");
    }

    result = APerformanceHint_setPreferPowerEfficiency(wrapper.session(), true);
    if (result != 0) {
        return toJString(env, "setPreferPowerEfficiency(true) did not return 0");
    }

    result = APerformanceHint_setPreferPowerEfficiency(wrapper.session(), true);
    if (result != 0) {
        return toJString(env, "setPreferPowerEfficiency(true) did not return 0");
    }
    return nullptr;
}

static jstring nativeTestReportActualWorkDuration2(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    AWorkDuration* workDuration1 = AWorkDuration_create();
    AWorkDuration_setWorkPeriodStartTimestampNanos(workDuration1, 1000);
    AWorkDuration_setActualTotalDurationNanos(workDuration1, 14);
    AWorkDuration_setActualCpuDurationNanos(workDuration1, 11);
    AWorkDuration_setActualGpuDurationNanos(workDuration1, 8);

    int result = APerformanceHint_reportActualWorkDuration2(wrapper.session(), workDuration1);
    if (result != 0) {
        return toJString(env,
                         "APerformanceHint_reportActualWorkDuration2("
                         "{workPeriodStartTimestampNanos = 1000, actualTotalDurationNanos = 14, "
                         "actualCpuDurationNanos = 11, actualGpuDurationNanos = 8}) did not "
                         "return 0");
    }
    AWorkDuration_release(workDuration1);

    AWorkDuration* workDuration2 = AWorkDuration_create();
    AWorkDuration_setWorkPeriodStartTimestampNanos(workDuration2, 1016);
    AWorkDuration_setActualTotalDurationNanos(workDuration2, 14);
    AWorkDuration_setActualCpuDurationNanos(workDuration2, 12);
    AWorkDuration_setActualGpuDurationNanos(workDuration2, 4);
    result = APerformanceHint_reportActualWorkDuration2(wrapper.session(), workDuration2);
    if (result != 0) {
        return toJString(env,
                         "APerformanceHint_reportActualWorkDuration2("
                         "{workPeriodStartTimestampNanos = 1016, actualTotalDurationNanos = 14, "
                         "actualCpuDurationNanos = 12, actualGpuDurationNanos = 4}) did not "
                         "return 0");
    }
    AWorkDuration_release(workDuration2);

    return nullptr;
}

static jstring nativeTestReportActualWorkDuration2WithIllegalArgument(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    AWorkDuration* workDuration0 = AWorkDuration_create();
    AWorkDuration_setWorkPeriodStartTimestampNanos(workDuration0, -1);
    AWorkDuration_setActualTotalDurationNanos(workDuration0, 14);
    AWorkDuration_setActualCpuDurationNanos(workDuration0, 11);
    AWorkDuration_setActualGpuDurationNanos(workDuration0, 8);

    int result = APerformanceHint_reportActualWorkDuration2(wrapper.session(), workDuration0);
    if (result != EINVAL) {
        return toJString(env,
                         "APerformanceHint_reportActualWorkDuration2("
                         "{workPeriodStartTimestampNanos = -1, actualTotalDurationNanos = 14, "
                         "actualCpuDurationNanos = 11, actualGpuDurationNanos = 8}) did not "
                         "return EINVAL");
    }
    AWorkDuration_release(workDuration0);

    AWorkDuration* workDuration1 = AWorkDuration_create();
    AWorkDuration_setWorkPeriodStartTimestampNanos(workDuration1, 1000);
    AWorkDuration_setActualTotalDurationNanos(workDuration1, -1);
    AWorkDuration_setActualCpuDurationNanos(workDuration1, 11);
    AWorkDuration_setActualGpuDurationNanos(workDuration1, 8);

    result = APerformanceHint_reportActualWorkDuration2(wrapper.session(), workDuration1);
    if (result != EINVAL) {
        return toJString(env,
                         "APerformanceHint_reportActualWorkDuration2("
                         "{workPeriodStartTimestampNanos = 1000, actualTotalDurationNanos = -1, "
                         "actualCpuDurationNanos = 11, actualGpuDurationNanos = 8}) did not "
                         "return EINVAL");
    }
    AWorkDuration_release(workDuration1);

    AWorkDuration* workDuration2 = AWorkDuration_create();
    AWorkDuration_setWorkPeriodStartTimestampNanos(workDuration2, 1000);
    AWorkDuration_setActualTotalDurationNanos(workDuration2, 14);
    AWorkDuration_setActualCpuDurationNanos(workDuration2, -1);
    AWorkDuration_setActualGpuDurationNanos(workDuration2, 8);
    result = APerformanceHint_reportActualWorkDuration2(wrapper.session(), workDuration2);
    if (result != EINVAL) {
        return toJString(env,
                         "APerformanceHint_reportActualWorkDuration2("
                         "{workPeriodStartTimestampNanos = 1000, actualTotalDurationNanos = 14, "
                         "actualCpuDurationNanos = -1, actualGpuDurationNanos = 8}) did not "
                         "return EINVAL");
    }
    AWorkDuration_release(workDuration2);

    AWorkDuration* workDuration3 = AWorkDuration_create();
    AWorkDuration_setWorkPeriodStartTimestampNanos(workDuration3, 1000);
    AWorkDuration_setActualTotalDurationNanos(workDuration3, 14);
    AWorkDuration_setActualCpuDurationNanos(workDuration3, 11);
    AWorkDuration_setActualGpuDurationNanos(workDuration3, -1);
    result = APerformanceHint_reportActualWorkDuration2(wrapper.session(), workDuration3);
    if (result != EINVAL) {
        return toJString(env,
                         "APerformanceHint_reportActualWorkDuration2("
                         "{workPeriodStartTimestampNanos = 1000, actualTotalDurationNanos = 14, "
                         "actualCpuDurationNanos = 11, actualGpuDurationNanos = -1}) did not "
                         "return EINVAL");
    }
    AWorkDuration_release(workDuration3);

    return nullptr;
}

static JNINativeMethod gMethods[] = {
    {"nativeTestCreateHintSession", "()Ljava/lang/String;",
     (void*)nativeTestCreateHintSession},
    {"nativeTestGetPreferredUpdateRateNanos", "()Ljava/lang/String;",
     (void*)nativeTestGetPreferredUpdateRateNanos},
    {"nativeUpdateTargetWorkDuration", "()Ljava/lang/String;",
     (void*)nativeUpdateTargetWorkDuration},
    {"nativeUpdateTargetWorkDurationWithNegativeDuration", "()Ljava/lang/String;",
     (void*)nativeUpdateTargetWorkDurationWithNegativeDuration},
    {"nativeReportActualWorkDuration", "()Ljava/lang/String;",
     (void*)nativeReportActualWorkDuration},
    {"nativeReportActualWorkDurationWithIllegalArgument", "()Ljava/lang/String;",
     (void*)nativeReportActualWorkDurationWithIllegalArgument},
    {"nativeTestSetThreadsWithInvalidTid", "()Ljava/lang/String;",
     (void*)nativeTestSetThreadsWithInvalidTid},
    {"nativeSetPreferPowerEfficiency", "()Ljava/lang/String;",
     (void*)nativeSetPreferPowerEfficiency},
    {"nativeTestReportActualWorkDuration2", "()Ljava/lang/String;",
     (void*)nativeTestReportActualWorkDuration2},
    {"nativeTestReportActualWorkDuration2WithIllegalArgument", "()Ljava/lang/String;",
     (void*)nativeTestReportActualWorkDuration2WithIllegalArgument},
};

int register_android_os_cts_PerformanceHintManagerTest(JNIEnv *env) {
    jclass clazz = env->FindClass("android/os/cts/PerformanceHintManagerTest");

    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
