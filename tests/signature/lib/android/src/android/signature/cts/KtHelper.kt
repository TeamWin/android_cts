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

@file:JvmName("KtHelper")
package android.signature.cts

import com.android.tools.metalava.model.TypeItem

/**
 * Allows Java to call the TypeItem.toTypeString() without having to explicitly specify each named
 * parameter to its default. This allows additional parameters to be added to the method without
 * breaking the Java code.
 */
fun toDefaultTypeString(item: TypeItem): String {
    // Normalize the strings to contain , without a following space. This is needed because
    // different versions of the txt specification used different separators in generic types, some
    // used "," and some used ", " and metalava does not normalize them. e.g. some files will format
    // a Map from String to Integer as "java.util.Map<java.lang.String,java.lang.Integer>" and some
    // will format it as "java.util.Map<java.lang.String, java.lang.Integer>".
    //
    // Must match separator used in android.signature.cts.ReflectionHelper.typeToString.
    return item.toTypeString().replace(", ", ",")
}
