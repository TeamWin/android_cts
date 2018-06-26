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

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.ReferenceType;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.DexBackedField;
import org.jf.dexlib2.dexbacked.DexBackedMethod;
import org.jf.dexlib2.dexbacked.reference.DexBackedFieldReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedMethodReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedTypeReference;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ApkParser extends FileParser {
    private DexBackedDexFile mDexFile;
    private Api.Builder mApiBuilder;

    public ApkParser(File file) {
        super(file);
    }

    @Override
    public Entry.EntryType getType() {
        return Entry.EntryType.APK;
    }

    public Api getApi() {
        if (mApiBuilder == null) {
            mApiBuilder = prase();
        }
        return mApiBuilder.build();
    }

    private boolean isInternal(DexBackedTypeReference t) {
        if (t.getType().length() == 1) {
            // primitive class
            return true;
        } else if (t.getType().charAt(0) == '[') {
            return true;
        }
        return false;
    }

    // [[Lcom/foo/bar/MyClass$Inner; becomes
    // com.foo.bar.MyClass.Inner[][]
    // and [[I becomes int[][]
    private static String toCanonicalName(String name) {
        int arrayDepth = 0;
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) == '[') {
                arrayDepth++;
            } else {
                break;
            }
        }

        if (name.length() == arrayDepth + 1) {
            // primitive types
            name = name.substring(arrayDepth);
        } else if (name.charAt(arrayDepth) == 'L') {
            // omit the leading 'L' and the trailing ';'
            name = name.substring(arrayDepth + 1, name.length() - 1);

            // replace '/' to '.'
            name = name.replace('/', '.');
        } else {
            throw new RuntimeException("Invalid type name " + name);
        }

        // add []'s, if any
        if (arrayDepth > 0) {
            for (int i = 0; i < arrayDepth; i++) {
                name += "[]";
            }
        }
        return name;
    }

    private String getSignature(DexBackedTypeReference f) {
        return toCanonicalName(f.getType());
    }

    private String getSignature(DexBackedClassDef c) {
        return toCanonicalName(c.getType());
    }

    private String getSignature(DexBackedMethod m) {
        return toCanonicalName(m.getDefiningClass())
                + "."
                + m.getName()
                + ","
                + toParametersString(m.getParameterTypes())
                + ","
                + toCanonicalName(m.getReturnType());
    }

    private String getSignature(DexBackedField f) {
        return toCanonicalName(f.getDefiningClass())
                + "."
                + f.getName()
                + "."
                + toCanonicalName(f.getType());
    }

    private String getRefName(DexBackedFieldReference f) {
        return toCanonicalName(f.getDefiningClass()) + "." + f.getName() + " : " + f.getType();
    }

    private String getRefName(DexBackedMethodReference m) {
        return toCanonicalName(m.getDefiningClass())
                + "."
                + m.getName()
                + " : ("
                + String.join("", m.getParameterTypes())
                + ")"
                + m.getReturnType();
    }

    private String toParametersString(List<String> pList) {
        return pList.stream().map(p -> toCanonicalName(p)).collect(Collectors.joining(":"));
    }

    private Api.Builder prase() {
        Api.Builder apiBuilder = Api.newBuilder();
        DexBackedDexFile dexFile = null;
        Map<String, DexBackedClassDef> definedClassesInDex = new HashMap<>();
        Map<String, DexBackedMethod> definedMethodsInDex = new HashMap<>();
        Map<String, DexBackedField> definedFieldsInDex = new HashMap<>();

        // Loads a Dex file
        System.out.println("dexFile: " + getFile().getName());
        try {
            dexFile = DexFileFactory.loadDexFile(getFile().getName(), Opcodes.getDefault());

            dexFile.getClasses().stream().forEach(c -> definedClassesInDex.put(c.getType(), c));

            for (DexBackedClassDef clazz : definedClassesInDex.values()) {
                for (DexBackedField dxField : clazz.getFields()) {
                    definedFieldsInDex.put(getSignature(dxField), dxField);
                }
                for (DexBackedMethod dxMethod : clazz.getMethods()) {
                    definedMethodsInDex.put(getSignature(dxMethod), dxMethod);
                }
            }

            System.out.println("Ext");
            System.out.println("Classes:");
            dexFile.getReferences(ReferenceType.TYPE)
                    .stream()
                    .map(t -> (DexBackedTypeReference) t)
                    .filter(t -> !isInternal(t))
                    .filter(t -> !definedClassesInDex.containsKey(t.getType()))
                    .forEach(ref -> System.out.println(getSignature(ref)));

            System.out.println("\nFields:");
            dexFile.getReferences(ReferenceType.FIELD)
                    .stream()
                    .map(f -> (DexBackedFieldReference) f)
                    .filter(f -> !definedClassesInDex.containsKey(f.getDefiningClass()))
                    .forEach(f -> System.out.println(getRefName(f)));

            System.out.println("\nMethods:");
            dexFile.getReferences(ReferenceType.METHOD)
                    .stream()
                    .map(m -> (DexBackedMethodReference) m)
                    .filter(m -> !definedClassesInDex.containsKey(m.getDefiningClass()))
                    .filter(
                            m ->
                                    !(m.getDefiningClass().startsWith("[")
                                            && m.getName().equals("clone")))
                    .forEach(m -> System.out.println(getRefName(m)));

        } catch (IOException | DexFileFactory.DexFileNotFoundException ex) {
            System.err.println("Unable to load dex file: " + getFile().getName());
            // ex.printStackTrace();
        }
        return apiBuilder;
    }

    private static final String USAGE_MESSAGE =
            "Usage: java -jar releaseparser.jar com.android.cts.releaseparser.ApkParser [-options] <path> [args...]\n"
                    + "           to prase an APK for API\n"
                    + "Options:\n"
                    + "\t-i PATH\t APK path \n";

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
        String apkFileName = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                if ("-i".equals(args[i])) {
                    apkFileName = getExpectedArg(args, ++i);
                }
            }
        }

        if (apkFileName == null) {
            printUsage();
        }

        File apkFile = new File(apkFileName);
        ApkParser apkParser = new ApkParser(apkFile);
        Api api = apkParser.getApi();
    }
}
