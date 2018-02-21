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
package android.signature.cts;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.text.ParseException;

/**
 * Parses an API definition given as a text file with DEX signatures of class
 * members. Constructs a {@link DexApiDocumentParser.DexMember} for every class
 * member.
 *
 * <p>The definition file is converted into a {@link Stream} of
 * {@link DexApiDocumentParser.DexMember}.
 */
public class DexApiDocumentParser {

    public Stream<DexMember> parseAsStream(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return StreamSupport.stream(new DexApiSpliterator(reader), false);
    }

    private static class DexApiSpliterator implements Spliterator<DexMember> {
        private final BufferedReader mReader;
        private int mLineNum;

        private static final Pattern REGEX_CLASS = Pattern.compile("^L[^->]*;$");
        private static final Pattern REGEX_FIELD = Pattern.compile("^(L[^->]*;)->(.*):(.*)$");
        private static final Pattern REGEX_METHOD =
                Pattern.compile("^(L[^->]*;)->(.*)\\((.*)\\)(.*)$");

        DexApiSpliterator(BufferedReader reader) {
            mReader = reader;
            mLineNum = 0;
        }

        @Override
        public boolean tryAdvance(Consumer<? super DexMember> action) {
            DexMember nextMember = null;
            try {
                nextMember = next();
            } catch (IOException | ParseException ex) {
                throw new RuntimeException(ex);
            }
            if (nextMember == null) {
                return false;
            }
            action.accept(nextMember);
            return true;
        }

        @Override
        public Spliterator<DexMember> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return ORDERED | DISTINCT | NONNULL | IMMUTABLE;
        }

        /**
         * Parses lines of DEX signatures from `mReader`. The following three
         * line formats are supported:
         * 1) [class descriptor]
         *      - e.g. Lcom/example/MyClass;
         *      - these lines are ignored
         * 2) [class descriptor]->[field name]:[field type]
         *      - e.g. Lcom/example/MyClass;->myField:I
         *      - these lines are parsed as field signatures
         * 3) [class descriptor]->[method name]([method parameter types])[method return type]
         *      - e.g. Lcom/example/MyClass;->myMethod(Lfoo;Lbar;)J
         *      - these lines are parsed as method signatures
         */
        private DexMember next() throws IOException, ParseException {
            while (true) {
                // Read the next line from the input.
                String line = mReader.readLine();
                if (line == null) {
                    // End of stream.
                    return null;
                }

                // Increment the line number.
                mLineNum = mLineNum + 1;

                // Match line against regex patterns.
                Matcher matchClass = REGEX_CLASS.matcher(line);
                Matcher matchField = REGEX_FIELD.matcher(line);
                Matcher matchMethod = REGEX_METHOD.matcher(line);

                // Check that *exactly* one pattern matches.
                int matchCount = (matchClass.matches() ? 1 : 0) + (matchField.matches() ? 1 : 0) +
                        (matchMethod.matches() ? 1 : 0);
                if (matchCount == 0) {
                    throw new ParseException("Could not parse: \"" + line + "\"", mLineNum);
                } else if (matchCount > 1) {
                    throw new ParseException("Ambiguous parse: \"" + line + "\"", mLineNum);
                }

                // Extract information from the line.
                if (matchClass.matches()) {
                    // We ignore lines describing a class because classes are
                    // not being hidden.
                } else if (matchField.matches()) {
                    return new DexField(
                            matchField.group(1), matchField.group(2), matchField.group(3));
                } else if (matchMethod.matches()) {
                    return new DexMethod(
                            matchMethod.group(1),matchMethod.group(2),
                            parseDexTypeList(matchMethod.group(3)), matchMethod.group(4));
                } else {
                    throw new IllegalStateException();
                }
            }
        }

        private List<String> parseDexTypeList(String typeSequence) throws ParseException {
            List<String> list = new ArrayList<String>();
            while (!typeSequence.isEmpty()) {
                String type = firstDexTypeFromList(typeSequence);
                list.add(type);
                typeSequence = typeSequence.substring(type.length());
            }
            return list;
        }

        /**
         * Returns the first dex type in `typeList` or throws a ParserException
         * if a dex type is not recognized. The input is not changed.
         */
        private String firstDexTypeFromList(String typeList) throws ParseException {
            String dexDimension = "";
            while (typeList.startsWith("[")) {
                dexDimension += "[";
                typeList = typeList.substring(1);
            }

            String type = null;
            if (typeList.startsWith("V")
                    || typeList.startsWith("Z")
                    || typeList.startsWith("B")
                    || typeList.startsWith("C")
                    || typeList.startsWith("S")
                    || typeList.startsWith("I")
                    || typeList.startsWith("J")
                    || typeList.startsWith("F")
                    || typeList.startsWith("D")) {
                type = typeList.substring(0, 1);
            } else if (typeList.startsWith("L") && typeList.indexOf(";") > 0) {
                type = typeList.substring(0, typeList.indexOf(";") + 1);
            } else {
                throw new ParseException(
                        "Unexpected dex type in \"" + typeList + "\"", mLineNum);
            }

            return dexDimension + type;
        }
    }

    /**
     * Represents one class member parsed from the reader of dex signatures.
     */
    public static abstract class DexMember {
        private final String mName;
        private final String mClassDescriptor;
        private final String mType;

        protected DexMember(String className, String name, String type) {
            mName = name;
            mClassDescriptor = className;
            mType = type;
        }

        public String getName() {
            return mName;
        }

        public String getDexClassName() {
            return mClassDescriptor;
        }

        public String getJavaClassName() {
            return dexToJavaType(mClassDescriptor);
        }

        public String getDexType() {
            return mType;
        }

        public String getJavaType() {
            return dexToJavaType(mType);
        }

        /**
         * Converts `type` to a Java type.
         */
        protected static String dexToJavaType(String type) {
            String javaDimension = "";
            while (type.startsWith("[")) {
                javaDimension += "[]";
                type = type.substring(1);
            }

            String javaType = null;
            if ("V".equals(type)) {
                javaType = "void";
            } else if ("Z".equals(type)) {
                javaType = "boolean";
            } else if ("B".equals(type)) {
                javaType = "byte";
            } else if ("C".equals(type)) {
                javaType = "char";
            } else if ("S".equals(type)) {
                javaType = "short";
            } else if ("I".equals(type)) {
                javaType = "int";
            } else if ("J".equals(type)) {
                javaType = "long";
            } else if ("F".equals(type)) {
                javaType = "float";
            } else if ("D".equals(type)) {
                javaType = "double";
            } else if (type.startsWith("L") && type.endsWith(";")) {
                javaType = type.substring(1, type.length() - 1).replace('/', '.');
            } else {
                throw new IllegalStateException("Unexpected type " + type);
            }

            return javaType + javaDimension;
        }
    }

    public static class DexField extends DexMember {
        public DexField(String className, String name, String type) {
            super(className, name, type);
        }

        @Override
        public String toString() {
            return getJavaType() + " " + getJavaClassName() + "." + getName();
        }
    }

    public static class DexMethod extends DexMember {
        private final List<String> mParamTypeList;

        public DexMethod(String className, String name, List<String> paramTypeList,
                String dexReturnType) {
            super(className, name, dexReturnType);
            mParamTypeList = paramTypeList;
        }

        public String getDexSignature() {
            return "(" + String.join("", mParamTypeList) + ")" + getDexType();
        }

        public List<String> getJavaParameterTypes() {
            return mParamTypeList
                    .stream()
                    .map(DexMember::dexToJavaType)
                    .collect(Collectors.toList());
        }

        public boolean isConstructor() {
            return "<init>".equals(getName()) && "V".equals(getDexType());
        }

        @Override
        public String toString() {
            return getJavaType() + " " + getJavaClassName() + "." + getName()
                    + "(" + String.join(", ", getJavaParameterTypes()) + ")";
        }
    }
}
