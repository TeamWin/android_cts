/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.eventlib.queryhelpers;

import com.android.eventlib.EventLogsQuery;
import com.android.eventlib.info.ActivityInfo;

/** Implementation of {@link ActivityQuery}. */
public final class ActivityQueryHelper<E extends EventLogsQuery> implements ActivityQuery<E> {

    private final E mQuery;
    private final ClassQueryHelper<E> mClassQueryHelper;

    public ActivityQueryHelper(E query) {
        mQuery = query;
        mClassQueryHelper = new ClassQueryHelper<>(query);
    }

    @Override
    public E isSameClassAs(Class<?> clazz) {
        return mClassQueryHelper.isSameClassAs(clazz);
    }

    @Override
    public StringQuery<E> className() {
        return mClassQueryHelper.className();
    }

    @Override
    public StringQuery<E> simpleName() {
        return mClassQueryHelper.simpleName();
    }

    public boolean matches(ActivityInfo value) {
        return mClassQueryHelper.matches(value);
    }
}
