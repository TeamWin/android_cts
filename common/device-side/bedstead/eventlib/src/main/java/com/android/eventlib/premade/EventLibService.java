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

package com.android.eventlib.premade;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;

import com.android.eventlib.events.services.ServiceBoundEvent;
import com.android.eventlib.events.services.ServiceCreatedEvent;
import com.android.eventlib.events.services.ServiceDestroyedEvent;
import com.android.eventlib.events.services.ServiceStartedEvent;
import com.android.eventlib.events.services.ServiceUnboundEvent;

/**
 * An {@link Service} which logs events for all lifecycle events.
 */
public class EventLibService extends Service {

    private String mOverrideServiceClassName;
    private final IBinder mBinder = new Binder();

    public void setOverrideServiceClassName(String overrideServiceClassName) {
        mOverrideServiceClassName = overrideServiceClassName;
    }

    /**
     * Gets the class name of this service.
     *
     * <p>If the class name has been overridden, that will be returned instead.
     */
    public String getClassName() {
        if (mOverrideServiceClassName != null) {
            return mOverrideServiceClassName;
        }

        return EventLibService.class.getName();
    }

    public ComponentName getComponentName() {
        return new ComponentName(getApplication().getPackageName(), getClassName());
    }

    private ServiceInfo mServiceInfo = null;

    private ServiceInfo serviceInfo() {
        if (mServiceInfo != null) {
            return mServiceInfo;
        }

        PackageManager packageManager = getPackageManager();
        try {
            mServiceInfo = packageManager.getServiceInfo(getComponentName(), /* flags= */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new AssertionError("Cannot find service", e);
        }

        return mServiceInfo;
    }

    @Override
    public void onCreate() {
        ServiceCreatedEvent.logger(this, serviceInfo()).log();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ServiceStartedEvent.logger(this, serviceInfo(), intent, flags, startId).log();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        ServiceDestroyedEvent.logger(this, serviceInfo()).log();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }

    @Override
    public void onLowMemory() {
    }

    @Override
    public void onTrimMemory(int level) {
    }

    @Override
    public IBinder onBind(Intent intent) {
        ServiceBoundEvent.logger(this, serviceInfo(), intent).log();
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        ServiceUnboundEvent.logger(this, serviceInfo(), intent).log();
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
    }
}
