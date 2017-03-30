/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.cts.xmlgenerator;

public class Test implements Comparable<Test> {
    private String mName;
    private int mTimeout;
    private boolean mSecurity;

    public Test(String name, int timeout, boolean security) {
        mName = name;
        mTimeout = timeout;
        mSecurity = security;
    }

    public String getName() {
        return mName;
    }

    public int getTimeout() {
        return mTimeout;
    }

    public boolean isSecurity() {
        return mSecurity;
    }

    @Override
    public int compareTo(Test another) {
        return getName().compareTo(another.getName());
    }
}
