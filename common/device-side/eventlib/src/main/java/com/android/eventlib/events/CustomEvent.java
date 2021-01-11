/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.eventlib.events;

import android.content.Context;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;

import java.io.Serializable;

/**
 * Implementation of {@link Event} which can be used for events not covered by other
 * {@link Event} subclasses.
 *
 * <p>To use, set custom data as {@link #tag()} and {@link #data()}.
 */
public final class CustomEvent extends Event {

    /** Begin a query for {@link CustomEvent} events. */
    public static CustomEventQuery queryPackage(String packageName) {
        return new CustomEventQuery(packageName);
    }

    public static final class CustomEventQuery
            extends EventLogsQuery<CustomEvent, CustomEventQuery> {
        String mTag = null;
        Serializable mData = null;

        private CustomEventQuery(String packageName) {
            super(CustomEvent.class, packageName);
        }

        /** Filter for a particular {@link CustomEvent#tag()}. */
        public CustomEventQuery withTag(String tag) {
            mTag = tag;
            return this;
        }

        /** Filter for a particular {@link CustomEvent#data()}. */
        public CustomEventQuery withData(Serializable data) {
            mData = data;
            return this;
        }

        @Override
        protected boolean filter(CustomEvent event) {
            if (mTag != null && !mTag.equals(event.mTag)) {
                return false;
            }
            if (mData != null && !mData.equals(event.mData)) {
                return false;
            }
            return true;
        }
    }

    /** Begin logging a {@link CustomEvent}. */
    public static CustomEventLogger logger(Context context) {
        return new CustomEventLogger(context);
    }

    public static final class CustomEventLogger extends EventLogger<CustomEvent> {
        private CustomEventLogger(Context context) {
            super(context, new CustomEvent());
        }

        /** Set the {@link CustomEvent#tag()}. */
        public CustomEventLogger setTag(String tag) {
            mEvent.mTag = tag;
            return this;
        }

        /** Set the {@link CustomEvent#data()}. */
        public CustomEventLogger setData(Serializable data) {
            mEvent.mData = data;
            return this;
        }
    }

    protected String mTag;
    protected Serializable mData;

    /** Get the tag set using {@link CustomEventLogger#setTag(String)}. */
    public String tag() {
        return mTag;
    }

    /** Get the tag set using {@link CustomEventLogger#setData(Serializable)}. */
    public Serializable data() {
        return mData;
    }

    @Override
    public String toString() {
        return "CustomEvent{" +
                "mTag='" + mTag + '\'' +
                ", mData=" + mData +
                ", mPackageName='" + mPackageName + '\'' +
                ", mTimestamp=" + mTimestamp +
                '}';
    }
}


