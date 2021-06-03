/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.testapp.processor;


import com.android.bedstead.testapp.processor.annotations.TestAppCommunication;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.annotations.CrossProfileConfiguration;
import com.google.android.enterprise.connectedapps.annotations.CrossProfileProvider;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;

/** Processor for generating TestApp API for remote execution. */
@SupportedAnnotationTypes({
        "com.android.bedstead.testapp.processor.annotations.TestAppCommunication",
})
@AutoService(javax.annotation.processing.Processor.class)
public final class Processor extends AbstractProcessor {
    // TODO(scottjonathan): Add more verification before generating - and add processor tests
    private static final ClassName CONTEXT_CLASSNAME =
            ClassName.get("android.content", "Context");
    private static final ClassName REMOTE_ACTIVITY_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "RemoteActivity");
    private static final ClassName REMOTE_ACTIVITY_IMPL_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "RemoteActivityImpl");
    private static final ClassName PROFILE_TARGETED_REMOTE_ACTIVITY_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "ProfileTargetedRemoteActivity");
    private static final ClassName TARGETED_REMOTE_ACTIVITY_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "TargetedRemoteActivity");
    private static final ClassName TARGETED_REMOTE_ACTIVITY_IMPL_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "TargetedRemoteActivityImpl");
    private static final ClassName TARGETED_REMOTE_ACTIVITY_WRAPPER_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "TargetedRemoteActivityWrapper");
    private static final ClassName CROSS_PROFILE_CONNECTOR_CLASSNAME =
            ClassName.get("com.google.android.enterprise.connectedapps",
                    "CrossProfileConnector");
    private static final ClassName UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME =
            ClassName.get(
                    "com.google.android.enterprise.connectedapps.exceptions",
                    "UnavailableProfileException");
    private static final ClassName PROFILE_RUNTIME_EXCEPTION_CLASSNAME =
            ClassName.get(
                    "com.google.android.enterprise.connectedapps.exceptions",
                    "ProfileRuntimeException");
    private static final ClassName NENE_EXCEPTION_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.nene.exceptions", "NeneException");
    public static final String PACKAGE_NAME = "com.android.bedstead.testapp";

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {

        if (roundEnv.getElementsAnnotatedWith(TestAppCommunication.class).isEmpty()) {
            return true;
        }

        TypeElement remoteActivityInterface =
                processingEnv.getElementUtils().getTypeElement(
                        REMOTE_ACTIVITY_CLASSNAME.canonicalName());

        generateRemoteActivityImpl(remoteActivityInterface);
        generateTargetedRemoteActivityInterface(remoteActivityInterface);
        generateTargetedRemoteActivityImpl(remoteActivityInterface);
        generateTargetedRemoteActivityWrapper(remoteActivityInterface);
        generateProvider();
        generateConfiguration();

        return true;
    }

    private void generateTargetedRemoteActivityImpl(TypeElement remoteActivityInterface) {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        TARGETED_REMOTE_ACTIVITY_IMPL_CLASSNAME)
                        .addSuperinterface(TARGETED_REMOTE_ACTIVITY_CLASSNAME)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        for (ExecutableElement method : getMethods(remoteActivityInterface)) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class);

            methodBuilder.addParameter(
                    ParameterSpec.builder(String.class, "activityClassName").build());

            List<String> paramNames = new ArrayList<>();

            for (VariableElement param : method.getParameters()) {
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()),
                                param.getSimpleName().toString()).build();

                paramNames.add(param.getSimpleName().toString());

                methodBuilder.addParameter(parameterSpec);
            }

            if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(
                        "BaseTestAppActivity.findActivity(activityClassName).$L($L)",
                        method.getSimpleName(), String.join(", ", paramNames));
            } else {
                methodBuilder.addStatement(
                        "return BaseTestAppActivity.findActivity(activityClassName).$L($L)",
                        method.getSimpleName(), String.join(", ", paramNames));
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(PACKAGE_NAME, classBuilder.build());
    }

    private void generateTargetedRemoteActivityWrapper(TypeElement remoteActivityInterface) {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        TARGETED_REMOTE_ACTIVITY_WRAPPER_CLASSNAME)
                        .addSuperinterface(TARGETED_REMOTE_ACTIVITY_CLASSNAME)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addField(
                FieldSpec.builder(PROFILE_TARGETED_REMOTE_ACTIVITY_CLASSNAME,
                        "mProfileTargetedRemoteActivity")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build());
        classBuilder.addField(
                FieldSpec.builder(CROSS_PROFILE_CONNECTOR_CLASSNAME, "mConnector")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build());

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addParameter(CROSS_PROFILE_CONNECTOR_CLASSNAME, "connector")
                .addStatement("mConnector = connector")
                .addStatement(
                        "mProfileTargetedRemoteActivity = $T.create(connector)",
                        PROFILE_TARGETED_REMOTE_ACTIVITY_CLASSNAME)
                .build());

        for (ExecutableElement method : getMethods(remoteActivityInterface)) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class);

            methodBuilder.addParameter(
                    ParameterSpec.builder(String.class, "activityClassName").build());

            String params = "activityClassName";

            for (VariableElement param : method.getParameters()) {
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()),
                                param.getSimpleName().toString()).build();

                params += ", " + param.getSimpleName().toString();

                methodBuilder.addParameter(parameterSpec);
            }

            methodBuilder.beginControlFlow("try")
                    .addStatement("mConnector.connect()");

            if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(
                        "mProfileTargetedRemoteActivity.other().$L($L)",
                        method.getSimpleName(), params);
            } else {
                methodBuilder.addStatement(
                        "return mProfileTargetedRemoteActivity.other().$L($L)",
                        method.getSimpleName(), params);
            }

            methodBuilder.nextControlFlow(
                    "catch ($T e)", UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
                    .addStatement(
                            "throw new $T($S, e)",
                            NENE_EXCEPTION_CLASSNAME, "Error connecting to test app")
                    .nextControlFlow("catch ($T e)", PROFILE_RUNTIME_EXCEPTION_CLASSNAME)
                    .addStatement("throw ($T) e.getCause()", RuntimeException.class)
                    .nextControlFlow("finally")
                    .addStatement("mConnector.stopManualConnectionManagement()")
                    .endControlFlow();

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(PACKAGE_NAME, classBuilder.build());
    }

    private void generateRemoteActivityImpl(TypeElement remoteActivityInterface) {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        REMOTE_ACTIVITY_IMPL_CLASSNAME)
                        .addSuperinterface(REMOTE_ACTIVITY_CLASSNAME)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addField(FieldSpec.builder(String.class, "mActivityClassName")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL).build());
        classBuilder.addField(FieldSpec.builder(
                TARGETED_REMOTE_ACTIVITY_CLASSNAME, "mTargetedRemoteActivity")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL).build());

        classBuilder.addMethod(
                MethodSpec.constructorBuilder()
                        .addParameter(String.class, "activityClassName")
                        .addParameter(
                                TARGETED_REMOTE_ACTIVITY_CLASSNAME, "targetedRemoteActivity")
                        .addStatement("mActivityClassName = activityClassName")
                        .addStatement("mTargetedRemoteActivity = targetedRemoteActivity")
                        .build());


        for (ExecutableElement method : getMethods(remoteActivityInterface)) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class);

            String params = "mActivityClassName";

            for (VariableElement param : method.getParameters()) {
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()),
                                param.getSimpleName().toString()).build();

                params += ", " + param.getSimpleName().toString();

                methodBuilder.addParameter(parameterSpec);
            }

            if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(
                        "mTargetedRemoteActivity.$L($L)", method.getSimpleName(), params);
            } else {
                methodBuilder.addStatement(
                        "return mTargetedRemoteActivity.$L($L)", method.getSimpleName(), params);
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(PACKAGE_NAME, classBuilder.build());
    }

    private void generateTargetedRemoteActivityInterface(TypeElement remoteActivityInterface) {
        TypeSpec.Builder classBuilder =
                TypeSpec.interfaceBuilder(
                        TARGETED_REMOTE_ACTIVITY_CLASSNAME)
                        .addModifiers(Modifier.PUBLIC);

        for (ExecutableElement method : getMethods(remoteActivityInterface)) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .addAnnotation(CrossProfile.class);

            methodBuilder.addParameter(
                    ParameterSpec.builder(String.class, "activityClassName").build());

            for (VariableElement param : method.getParameters()) {
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()),
                                param.getSimpleName().toString()).build();

                methodBuilder.addParameter(parameterSpec);
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(PACKAGE_NAME, classBuilder.build());
    }

    private void generateProvider() {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        "Provider")
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        MethodSpec.Builder methodBuilder =
                MethodSpec.methodBuilder("provideTargetedremoteActivity")
                        .returns(TARGETED_REMOTE_ACTIVITY_CLASSNAME)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(CONTEXT_CLASSNAME, "context")
                        .addAnnotation(CrossProfileProvider.class)
                        .addCode("return new $T();", TARGETED_REMOTE_ACTIVITY_IMPL_CLASSNAME);

        classBuilder.addMethod(methodBuilder.build());

        writeClassToFile(PACKAGE_NAME, classBuilder.build());
    }

    private void generateConfiguration() {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        "Configuration")
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                        .addAnnotation(AnnotationSpec.builder(CrossProfileConfiguration.class)
                                .addMember("providers", "Provider.class")
                                .build());

        writeClassToFile(PACKAGE_NAME, classBuilder.build());
    }

    private void writeClassToFile(String packageName, TypeSpec clazz) {
        String qualifiedClassName =
                packageName.isEmpty() ? clazz.name : packageName + "." + clazz.name;

        JavaFile javaFile = JavaFile.builder(packageName, clazz).build();
        try {
            JavaFileObject builderFile =
                    processingEnv.getFiler().createSourceFile(qualifiedClassName);
            try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
                javaFile.writeTo(out);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error writing " + qualifiedClassName + " to file", e);
        }
    }

    private Set<ExecutableElement> getMethods(TypeElement interfaceClass) {
        return interfaceClass.getEnclosedElements().stream()
                .filter(e -> e instanceof ExecutableElement)
                .map(e -> (ExecutableElement) e)
                .collect(Collectors.toSet());
    }
}
