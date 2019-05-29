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

import android.signature.cts.JDiffClassDescription.JDiffConstructor;
import android.signature.cts.JDiffClassDescription.JDiffField;
import android.signature.cts.JDiffClassDescription.JDiffMethod;
import com.android.tools.metalava.doclava1.ApiFile;
import com.android.tools.metalava.doclava1.ApiParseException;
import com.android.tools.metalava.doclava1.TextCodebase;
import com.android.tools.metalava.model.ClassItem;
import com.android.tools.metalava.model.ConstructorItem;
import com.android.tools.metalava.model.FieldItem;
import com.android.tools.metalava.model.Item;
import com.android.tools.metalava.model.MethodItem;
import com.android.tools.metalava.model.ModifierList;
import com.android.tools.metalava.model.PackageItem;
import com.android.tools.metalava.model.ParameterItem;
import com.android.tools.metalava.model.TypeItem;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kotlin.streams.jdk8.StreamsKt;

/**
 * Parser for the text representation of an API specification.
 */
public class TextApiParser extends ApiParser {

    @Override
    public Stream<JDiffClassDescription> parseAsStream(VirtualPath path) {
        try {
            String content = new BufferedReader(new InputStreamReader(path.newInputStream()))
                    .lines()
                    .parallel()
                    .collect(Collectors.joining("\n"));
            TextCodebase codebase = ApiFile.parseApi(path.toString(), content, false);
            List<PackageItem> packages = codebase.getPackages().getPackages();
            return packages.stream()
                    // Map each package to the Sequence of ClassItems that it contains
                    .map(PackageItem::allClasses)
                    // Flatten the Sequences of ClassItems into one stream.
                    .flatMap(StreamsKt::asStream)
                    // Filter out TextClassItems that are used from but not defined in the source.
                    .filter(ClassItem::isDefined)
                    .map(TextApiParser::convertClass);
        } catch (IOException | ApiParseException e) {
            throw new RuntimeException("Could not parse " + path, e);
        }
    }

    private static JDiffClassDescription convertClass(ClassItem item) {
        String pkg = item.containingPackage().qualifiedName();

        JDiffClassDescription currentClass = new JDiffClassDescription(pkg, item.fullName());

        int modifiers = getModifiers(item);

        currentClass.setModifier(modifiers);
        currentClass.setType(item.isInterface() ? JDiffClassDescription.JDiffType.INTERFACE :
                JDiffClassDescription.JDiffType.CLASS);

        // Map the super class.
        ClassItem superClass = item.superClass();
        if (superClass != null) {
            String extendsClass = superClass.qualifiedName();
            if (item.isInterface()) {
                // TextCodebase treats an interface as if it extends java.lang.Object.
                if (!superClass.isJavaLangObject()) {
                    currentClass.addImplInterface(extendsClass);
                }
            } else {
                currentClass.setExtendsClass(extendsClass);
            }
        }

        // Map the interfaces.
        item.interfaceTypes().stream()
                .map(TypeItem::asClass)
                .filter(Objects::nonNull)
                .map(ClassItem::qualifiedName)
                .forEach(currentClass::addImplInterface);

        item.fields().stream().map(TextApiParser::convertField).forEach(currentClass::addField);

        item.constructors().stream()
                .map(TextApiParser::convertConstructor)
                .forEach(currentClass::addConstructor);

        item.methods().stream()
                .map(TextApiParser::convertMethod)
                .forEach(currentClass::addMethod);

        return currentClass;
    }

    private static int getModifiers(Item item) {
        ModifierList modifierList = item.getModifiers();
        int modifiers = 0;
        if (modifierList.isAbstract()) {
            modifiers |= Modifier.ABSTRACT;
        }
        if (modifierList.isFinal()) {
            modifiers |= Modifier.FINAL;
        }
        if (modifierList.isNative()) {
            modifiers |= Modifier.NATIVE;
        }
        if (modifierList.isStatic()) {
            modifiers |= Modifier.STATIC;
        }
        if (modifierList.isSynchronized()) {
            modifiers |= Modifier.SYNCHRONIZED;
        }
        if (modifierList.isTransient()) {
            modifiers |= Modifier.TRANSIENT;
        }
        if (modifierList.isVolatile()) {
            modifiers |= Modifier.VOLATILE;
        }
        if (modifierList.isPrivate()) {
            modifiers |= Modifier.PRIVATE;
        } else if (modifierList.isProtected()) {
            modifiers |= Modifier.PROTECTED;
        } else if (modifierList.isPublic()) {
            modifiers |= Modifier.PUBLIC;
        }
        return modifiers;
    }

    private static JDiffField convertField(FieldItem item) {
        int modifiers = getModifiers(item);
        Object value = item.initialValue(true);

        if (item.isEnumConstant()) {
            // Set the enum bit on the enum constant to match the modifiers returned by reflection.
            modifiers |= 0x00004000;
        }

        return new JDiffField(item.name(),
                KtHelper.toDefaultTypeString(item.type()), modifiers,
                value == null ? null : value.toString());
    }

    private static JDiffConstructor convertConstructor(ConstructorItem item) {
        JDiffConstructor constructor = new JDiffConstructor(item.name(), getModifiers(item));

        convertParameters(item, constructor);

        return constructor;
    }

    private static void convertParameters(MethodItem item, JDiffMethod method) {
        item.parameters().stream()
                .map(TextApiParser::convertParameter)
                .forEach(method::addParam);
    }

    private static JDiffMethod convertMethod(MethodItem item) {
        TypeItem returnType = item.returnType();
        String returnTypeAsString = returnType == null ? null
                : KtHelper.toDefaultTypeString(returnType);
        JDiffMethod method = new JDiffMethod(item.name(), getModifiers(item), returnTypeAsString);

        convertParameters(item, method);

        return method;
    }

    private static String convertParameter(ParameterItem item) {
        return KtHelper.toDefaultTypeString(item.type());
    }
}
