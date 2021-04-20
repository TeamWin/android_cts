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
import static android.compat.testing.Classpaths.ClasspathType.DEX2OATBOOTCLASSPATH;
import static android.compat.testing.Classpaths.ClasspathType.SYSTEMSERVERCLASSPATH;

import android.compat.testing.Classpaths;
import android.compat.testing.Classpaths.ClasspathType;

import com.android.compatibility.common.util.DeviceInfo;
import com.android.compatibility.common.util.HostInfoStore;
import com.android.tradefed.device.ITestDevice;

import com.google.common.collect.ImmutableList;

import org.jf.dexlib2.iface.ClassDef;

public class ClasspathDeviceInfo extends DeviceInfo {

    private ITestDevice mDevice;

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        mDevice = getDevice();

        store.startArray("jars");
        collectBootclasspathJars(store);
        store.endArray();
    }

    private void collectBootclasspathJars(HostInfoStore store) throws Exception {
        collectJarInfo(store, BOOTCLASSPATH);
        collectJarInfo(store, SYSTEMSERVERCLASSPATH);
        collectJarInfo(store, DEX2OATBOOTCLASSPATH);
    }

    private void collectJarInfo(HostInfoStore store, ClasspathType classpath) throws Exception {
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
