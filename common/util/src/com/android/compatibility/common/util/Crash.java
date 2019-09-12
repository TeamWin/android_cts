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

import java.util.Objects;
import java.io.Serializable;
import javax.annotation.Nullable;

public class Crash implements Serializable {

    public static final long serialVersionUID = 42L;
    public final int pid;
    public final int tid;
    @Nullable
    public final String name;
    @Nullable
    public final Long faultAddress;
    @Nullable
    public final String signal;

    public Crash(int pid, int tid, String name, Long faultAddress, String signal) {
        this.pid = pid;
        this.tid = tid;
        this.name = name;
        this.faultAddress = faultAddress;
        this.signal = signal;
    }

    @Override
    public String toString() {
        return "Crash{" +
            "pid=" + pid +
            ", tid=" + tid +
            ", name=" + name +
            ", faultAddress=" + faultAddress +
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
            Objects.equals(name, crash.name) &&
            Objects.equals(faultAddress, crash.faultAddress) &&
            Objects.equals(signal, crash.signal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid, tid, name, faultAddress, signal);
    }
}