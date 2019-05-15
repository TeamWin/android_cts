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
package android.signature.cts;

import java.util.stream.Stream;


/**
 * Parses an XML api definition file and constructs and populates an {@link JDiffClassDescription}
 * for every class.
 *
 * <p>The definition file is converted into a {@link Stream} of {@link JDiffClassDescription}.
 */
public class ApiDocumentParser {

    private final String tag;

    public ApiDocumentParser(String tag) {
        this.tag = tag;
    }

    private ApiParser getApiParser(VirtualPath path) {
        if (path.toString().endsWith(".api")) {
            return new XmlApiParser(tag);
        } else {
            throw new IllegalStateException("Unrecognized file type: " + path);
        }
    }

    public Stream<JDiffClassDescription> parseAsStream(VirtualPath path) {
        ApiParser parser = getApiParser(path);

        return parser.parseAsStream(path);
    }
}
