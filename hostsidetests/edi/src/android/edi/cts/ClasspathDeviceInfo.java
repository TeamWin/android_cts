/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.edi.cts;

import static android.compat.testing.Classpaths.ClasspathType.BOOTCLASSPATH;
import static android.compat.testing.Classpaths.ClasspathType.SYSTEMSERVERCLASSPATH;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.compat.testing.Classpaths;
import android.compat.testing.Classpaths.ClasspathType;
import android.compat.testing.SharedLibraryInfo;

import com.android.compatibility.common.util.DeviceInfo;
import com.android.compatibility.common.util.HostInfoStore;
import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.jf.dexlib2.iface.ClassDef;

/**
 * Collects information about Java classes present in *CLASSPATH variables and Java shared libraries
 * from the device.
 */
public class ClasspathDeviceInfo extends DeviceInfo {

    private ITestDevice mDevice;
    private DeviceSdkLevel deviceSdkLevel;

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        mDevice = getDevice();
        deviceSdkLevel = new DeviceSdkLevel(mDevice);

        store.startArray("jars");
        collectClasspathsJars(store);
        collectSharedLibraries(store);
        store.endArray();
    }

    private void collectClasspathsJars(HostInfoStore store) throws Exception {
        collectClasspathJarInfo(store, BOOTCLASSPATH);
        collectClasspathJarInfo(store, SYSTEMSERVERCLASSPATH);
        // No need to collect DEX2OATBOOTCLASSPATH, as it is just a subset of BOOTCLASSPATH
    }

    private void collectClasspathJarInfo(HostInfoStore store, ClasspathType classpath)
            throws Exception {
        ImmutableList<String> paths = Classpaths.getJarsOnClasspath(mDevice, classpath);
        for (int i = 0; i < paths.size(); i++) {
            store.startGroup();
            store.addResult("classpath", classpath.name());
            store.addResult("path", paths.get(i));
            store.addResult("index", i);
            collectClassInfo(store, paths.get(i));
            store.endGroup();
        }
    }

    private void collectSharedLibraries(HostInfoStore store) throws Exception {
        if (!deviceSdkLevel.isDeviceAtLeastS()) {
            return;
        }
        final ImmutableList<SharedLibraryInfo> sharedLibraries =
                Classpaths.getSharedLibraryInfos(mDevice, getBuild());
        for (SharedLibraryInfo libraryInfo : sharedLibraries) {
            for (int index = 0; index < libraryInfo.paths.size(); ++index) {
                final String path = libraryInfo.paths.get(index);
                if (!mDevice.doesFileExist(path)) {
                    CLog.w("Shared library is not present on device " + path);
                    continue;
                }
                store.startGroup();
                store.startGroup("shared_library");
                store.addResult("name", libraryInfo.name);
                store.addResult("type", libraryInfo.type);
                store.addResult("version", libraryInfo.version);
                store.endGroup(); // shared_library
                store.addResult("path", path);
                store.addResult("index", index);
                collectClassInfo(store, path);
                store.endGroup();
            }
        }
    }

    private void collectClassInfo(HostInfoStore store, String path) throws Exception {
        store.startArray("classes");
        for (ClassDef classDef : Classpaths.getClassDefsFromJar(mDevice, path)) {
            store.startGroup();
            store.addResult("name", classDef.getType());
            store.endGroup();
        }
        store.endArray();
    }
}
