/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.json.stream.JsonReader;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * JsonReaderFuzzer contains fuzzerTestOneInput(...) method to fuzz JsonReader
 * using the jazzer fuzzing engine.
 */
public class JsonReaderFuzzer {
    /**
     * fuzzerTestOneInput(FuzzedDataProvider data) is called by the jazzer
     * fuzzing engine repeatedly with random inputs to try and crash the code
     * in JsonReader.
     * @param data
     * argument of type FuzzedDataProvider to provide easy access to various
     * data types to feed into the fuzzer program.
     */
    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        String initString = data.consumeRemainingAsString();
        Reader in = new StringReader(initString);
        JsonReader jsonReader = new JsonReader(in);
        boolean hasNext = true;
        while (hasNext) {
            try {
                hasNext = jsonReader.hasNext();
            } catch (IOException e) {
                break;
            }
            try {
                jsonReader.nextString();
            } catch (IOException | IllegalStateException e) {
                break;
            }
        }
    }
}
