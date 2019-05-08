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
package android.signature.cts;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Workaround for the lack of a zip file system provider on Android.
 */
public abstract class VirtualPath {

    /**
     * Get a path to the local file system.
     */
    public static LocalFilePath get(String path) {
        return new LocalFilePath(path);
    }

    /**
     * Get a path to an entry in a zip file, i.e. a zip file system.
     */
    public static ZipEntryPath get(ZipFile zip, ZipEntry entry) {
        return new ZipEntryPath(zip, entry);
    }

    public abstract InputStream newInputStream() throws IOException;

    public static class LocalFilePath extends VirtualPath {
        private final String path;

        LocalFilePath(String path) {
            this.path = path;
        }

        public File toFile() {
            return new File(path);
        }

        public LocalFilePath resolve(String relative) {
            return new LocalFilePath(path + "/" + relative);
        }

        @Override
        public InputStream newInputStream() throws IOException {
            return new FileInputStream(path);
        }

        @Override
        public String toString() {
            return path;
        }
    }

    private static class ZipEntryPath extends VirtualPath {

        private final ZipFile zip;

        private final ZipEntry entry;

        private ZipEntryPath(ZipFile zip, ZipEntry entry) {
            this.zip = zip;
            this.entry = entry;
        }

        @Override
        public InputStream newInputStream() throws IOException {
            return zip.getInputStream(entry);
        }

        @Override
        public String toString() {
            return "zip:file:" + zip.getName() + "!/" + entry.getName();
        }
    }
}
