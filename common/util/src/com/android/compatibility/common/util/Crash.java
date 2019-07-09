/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.compatibility.common.util;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Objects;
import java.math.BigInteger;

public class Crash implements Serializable {

    public static final long serialVersionUID = 42L;

    public final int pid;
    public final int tid;
    public final String threadName;
    public final String process;
    @Nullable // the fault address is not always present in the log
    public final BigInteger faultAddress;
    public final String signal;
    @Nullable
    public final String crashString;

    public Crash(int pid, int tid, String threadName, String process,
            BigInteger faultAddress, String signal) {
        this(pid, tid, threadName, process, faultAddress, signal, null);
    }

    public Crash(int pid, int tid, String threadName, String process,
            BigInteger faultAddress, String signal, String crashString) {
        this.pid = pid;
        this.tid = tid;
        this.threadName = threadName;
        this.process = process;
        this.faultAddress = faultAddress;
        this.signal = signal;
        this.crashString = crashString;
    }

    @Override
    public String toString() {
        return "Crash{" +
            "pid=" + pid +
            ", tid=" + tid +
            ", threadName=" + threadName +
            ", process=" + process +
            ", faultAddress=" +
                    (faultAddress == null ? "--------" : "0x" + faultAddress.toString(16)) +
            ", signal=" + signal +
            '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Crash)) {
            return false;
        }
        Crash crash = (Crash) object;
        return pid == crash.pid &&
            tid == crash.tid &&
            Objects.equals(threadName, crash.threadName) &&
            Objects.equals(process, crash.process) &&
            Objects.equals(faultAddress, crash.faultAddress) &&
            Objects.equals(signal, crash.signal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid, tid, threadName, process, faultAddress, signal);
    }
}
