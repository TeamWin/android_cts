/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts.releaseparser;

import com.android.cts.releaseparser.ReleaseProto.*;
import com.google.protobuf.TextFormat;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class XmlParser extends FileParser {
    private XmlHandler mHandler;
    private HashMap<String, PermissionList> mPermissions;

    public XmlParser(File file) {
        super(file);
    }

    @Override
    public Entry.EntryType getType() {
        return Entry.EntryType.XML;
    }

    @Override
    public void setAdditionalInfo() {
        HashMap<String, PermissionList> permissions = getPermissions();
        if (permissions != null) {
            getFileEntryBuilder().putAllDevicePermissions(permissions);
        }
    }

    public HashMap<String, PermissionList> getPermissions() {
        if (mPermissions == null) {
            parse();
        }
        return mPermissions;
    }

    // Todo readPermissions() from frameworks/base/core/java/com/android/server/SystemConfig.java
    // for Feature set
    private void parse() {
        try {
            mHandler = new XmlHandler(getFileName());
            XMLReader xmlReader = XMLReaderFactory.createXMLReader();
            xmlReader.setContentHandler(mHandler);
            FileReader fileReader = new FileReader(getFile());
            xmlReader.parse(new InputSource(fileReader));
            mPermissions = mHandler.getPermissions();
            fileReader.close();
        } catch (Exception e) {
            // file is not a Test Module Config
            System.err.println("Fail to parse:" + getFileName() + "\n" + e.getMessage());
        }
    }

    private static final String USAGE_MESSAGE =
            "Usage: java -jar releaseparser.jar com.android.cts.releaseparser.XmlParser [-options] <path> [args...]\n"
                    + "           to prase an XML file \n"
                    + "Options:\n"
                    + "\t-i PATH\t XML path \n";

    /** Get the argument or print out the usage and exit. */
    private static void printUsage() {
        System.out.printf(USAGE_MESSAGE);
        System.exit(1);
    }

    /** Get the argument or print out the usage and exit. */
    private static String getExpectedArg(String[] args, int index) {
        if (index < args.length) {
            return args[index];
        } else {
            printUsage();
            return null; // Never will happen because printUsage will call exit(1)
        }
    }

    public static void main(String[] args) throws IOException {
        String fileName = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                if ("-i".equals(args[i])) {
                    fileName = getExpectedArg(args, ++i);
                }
            }
        }
        if (fileName == null) {
            printUsage();
        }
        File aFile = new File(fileName);
        XmlParser aParser = new XmlParser(aFile);
        HashMap<String, PermissionList> map = aParser.getPermissions();
        map.forEach(
                (key, value) -> System.out.println(key + "\n" + TextFormat.printToString(value)));
    }
}
