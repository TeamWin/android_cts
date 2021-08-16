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


import static java.util.stream.Collectors.toList;

import com.android.bedstead.testapp.processor.annotations.TestAppReceiver;
import com.android.bedstead.testapp.processor.annotations.TestAppSender;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.annotations.CrossProfileConfiguration;
import com.google.android.enterprise.connectedapps.annotations.CrossProfileProvider;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
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
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

/** Processor for generating TestApp API for remote execution. */
@SupportedAnnotationTypes({
        "com.android.bedstead.testapp.processor.annotations.TestAppSender",
        "com.android.bedstead.testapp.processor.annotations.TestAppReceiver",
})
@AutoService(javax.annotation.processing.Processor.class)
public final class Processor extends AbstractProcessor {
    public static final String PACKAGE_NAME = "com.android.bedstead.testapp";
    // TODO(scottjonathan): Add more verification before generating - and add processor tests
    private static final ClassName CONTEXT_CLASSNAME =
            ClassName.get("android.content", "Context");
    private static final ClassName NENE_ACTIVITY_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.nene.activities",
                    "NeneActivity");
    private static final ClassName TEST_APP_ACTIVITY_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "TestAppActivity");
    private static final ClassName TEST_APP_ACTIVITY_IMPL_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "TestAppActivityImpl");
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
    private static final ClassName TEST_APP_CONTROLLER_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "TestAppController");
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
                    "com.android.bedstead.nene.exceptions",
                    "NeneException");
    private static final ClassName TEST_APP_INSTANCE_REFERENCE_CLASSNAME =
            ClassName.get("com.android.bedstead.testapp",
                    "TestAppInstanceReference");
    private static final ClassName COMPONENT_REFERENCE_CLASSNAME =
            ClassName.get("com.android.bedstead.nene.packages",
                    "ComponentReference");
    private static final ClassName REMOTE_DEVICE_POLICY_MANAGER_PARENT_CLASSNAME =
            ClassName.get("android.app.admin", "RemoteDevicePolicyManagerParent");
    private static final ClassName DEVICE_POLICY_MANAGER_CLASSNAME =
            ClassName.get("android.app.admin", "DevicePolicyManager");
    private static final ClassName COMPONENT_NAME_CLASSNAME =
            ClassName.get("android.content", "ComponentName");
    private static final ClassName REMOTE_DEVICE_POLICY_MANAGER_PARENT_WRAPPER_CLASSNAME =
            ClassName.get("android.app.admin",
                    "RemoteDevicePolicyManagerParentWrapper");

    /**
     * Extract classes provided in an annotation.
     *
     * <p>The {@code runnable} should call the annotation method that the classes are being
     * extracted for.
     */
    public static List<TypeElement> extractClassesFromAnnotation(Types types, Runnable runnable) {
        // From https://docs.oracle.com/javase/8/docs/api/javax/lang/model/AnnotatedConstruct.html
        // "The annotation returned by this method could contain an element whose value is of type
        // Class. This value cannot be returned directly: information necessary to locate and load a
        // class (such as the class loader to use) is not available, and the class might not be
        // loadable at all. Attempting to read a Class object by invoking the relevant method on the
        // returned annotation will result in a MirroredTypeException, from which the corresponding
        // TypeMirror may be extracted."
        try {
            runnable.run();
        } catch (MirroredTypesException e) {
            return e.getTypeMirrors().stream()
                    .map(t -> (TypeElement) types.asElement(t))
                    .collect(toList());
        }
        throw new AssertionError("Could not extract classes from annotation");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {

        TypeElement neneActivityInterface =
                processingEnv.getElementUtils().getTypeElement(
                        NENE_ACTIVITY_CLASSNAME.canonicalName());

        Set<? extends Element> receiverAnnotatedElements =
                roundEnv.getElementsAnnotatedWith(TestAppReceiver.class);

        if (receiverAnnotatedElements.size() > 1) {
            throw new IllegalStateException(
                    "Cannot have more than one @TestAppReceiver annotation");
        }

        if (!receiverAnnotatedElements.isEmpty()) {
            TestAppReceiver testAppReceiver = receiverAnnotatedElements.iterator().next()
                    .getAnnotation(TestAppReceiver.class);


            List<TypeElement> systemServiceClasses =
                    extractClassesFromAnnotation(
                            processingEnv.getTypeUtils(), testAppReceiver::systemServiceClasses);


            generateTargetedRemoteActivityInterface(neneActivityInterface);
            generateTargetedRemoteActivityImpl(neneActivityInterface);
            generateTargetedRemoteActivityWrapper(neneActivityInterface);
            generateProvider(systemServiceClasses);
            generateConfiguration();

            generateDpmParentWrapper(processingEnv.getElementUtils());
            for (TypeElement systemServiceClass : systemServiceClasses) {
                generateRemoteFrameworkClassWrapper(
                        processingEnv.getElementUtils(), systemServiceClass);
            }
        }

        if (!roundEnv.getElementsAnnotatedWith(TestAppSender.class).isEmpty()) {
            generateTestAppActivityImpl(neneActivityInterface);
        }

        return true;
    }

    private void generateRemoteFrameworkClassWrapper(
            Elements elements, TypeElement systemServiceClass) {
        ClassName originalClassName = ClassName.get(systemServiceClass);
        ClassName interfaceClassName = ClassName.get(
                originalClassName.packageName(),
                "Remote" + originalClassName.simpleName());
        ClassName wrapperClassName = ClassName.get(
                originalClassName.packageName(),
                interfaceClassName.simpleName() + "Wrapper");
        ClassName profileClassName = ClassName.get(
                originalClassName.packageName(),
                "Profile" + interfaceClassName.simpleName());
        TypeElement interfaceElement = elements.getTypeElement(interfaceClassName.canonicalName());

        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        wrapperClassName)
                        .addSuperinterface(interfaceClassName)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addField(
                FieldSpec.builder(profileClassName,
                        "mProfileClass")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());
        classBuilder.addField(
                FieldSpec.builder(CROSS_PROFILE_CONNECTOR_CLASSNAME, "mConnector")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(CROSS_PROFILE_CONNECTOR_CLASSNAME, "connector")
                .addStatement("mConnector = connector")
                .addStatement(
                        "mProfileClass = $T.create(connector)",
                        profileClassName)
                .build());

        for (ExecutableElement method : getMethods(interfaceElement)) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class);

            for (TypeMirror m : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(m));
            }

            List<String> params = new ArrayList<>();

            for (VariableElement param : method.getParameters()) {
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()),
                                param.getSimpleName().toString()).build();

                params.add(param.getSimpleName().toString());

                methodBuilder.addParameter(parameterSpec);
            }

            methodBuilder.addStatement("int retries = 300") // 30 seconds of retries
                    .beginControlFlow("while (true)")
                    .beginControlFlow("try")
                    .addStatement("mConnector.connect()");

            if (method.getReturnType().toString().equals(
                    "android.app.admin.RemoteDevicePolicyManager")
                    && method.getSimpleName().contentEquals("getParentProfileInstance")) {
                // Special case, we want to return a new parent wrapper, but still call through to
                // the other side for exceptions, etc.
                methodBuilder.addStatement(
                        "mProfileClass.other().$L($L)",
                        method.getSimpleName(), String.join(", ", params));
                methodBuilder.addStatement("return new $T(mConnector, $L)",
                        REMOTE_DEVICE_POLICY_MANAGER_PARENT_WRAPPER_CLASSNAME,
                        String.join(", ", params));
            } else if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(
                        "mProfileClass.other().$L($L)",
                        method.getSimpleName(), String.join(", ", params));
                methodBuilder.addStatement("return");
            } else {
                methodBuilder.addStatement(
                        "return mProfileClass.other().$L($L)",
                        method.getSimpleName(), String.join(", ", params));
            }

            methodBuilder.nextControlFlow(
                    "catch ($T e)", UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
                    .beginControlFlow("if (retries-- <= 0)")
                    .addStatement(
                            "throw new $T($S, e)",
                            NENE_EXCEPTION_CLASSNAME, "Error connecting to test app")
                    .endControlFlow()
                    .beginControlFlow("try")
                    .addStatement("$T.sleep(100)", Thread.class)
                    .nextControlFlow("catch ($T e2)", InterruptedException.class)
                    .addStatement(
                            "throw new $T($S, e)",
                            NENE_EXCEPTION_CLASSNAME, "Error connecting to test app")
                    .endControlFlow()
                    .nextControlFlow("catch ($T e)", PROFILE_RUNTIME_EXCEPTION_CLASSNAME)
                    .addStatement("throw ($T) e.getCause()", RuntimeException.class)
                    .nextControlFlow("finally")
                    .addStatement("mConnector.stopManualConnectionManagement()")
                    .endControlFlow() // try
                    .endControlFlow(); // while(true)

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(originalClassName.packageName(), classBuilder.build());
    }

    private void generateDpmParentWrapper(Elements elements) {
        ClassName interfaceClassName = ClassName.get(
                "android.app.admin", "RemoteDevicePolicyManager");
        ClassName profileClassName = ClassName.get(
                "android.app.admin", "ProfileRemoteDevicePolicyManagerParent");
        TypeElement interfaceElement = elements.getTypeElement(interfaceClassName.canonicalName());

        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        REMOTE_DEVICE_POLICY_MANAGER_PARENT_WRAPPER_CLASSNAME)
                        .addSuperinterface(interfaceClassName)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addField(
                FieldSpec.builder(profileClassName,
                        "mProfileClass")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());
        classBuilder.addField(
                FieldSpec.builder(CROSS_PROFILE_CONNECTOR_CLASSNAME, "mConnector")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());
        classBuilder.addField(
                FieldSpec.builder(COMPONENT_NAME_CLASSNAME, "mProfileOwnerComponentName")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(CROSS_PROFILE_CONNECTOR_CLASSNAME, "connector")
                .addParameter(COMPONENT_NAME_CLASSNAME, "profileOwnerComponentName")
                .addStatement("mConnector = connector")
                .addStatement("mProfileOwnerComponentName = profileOwnerComponentName")
                .addStatement("mProfileClass = $T.create(connector)", profileClassName)
                .build());

        for (ExecutableElement method : getMethods(interfaceElement)) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class);

            for (TypeMirror m : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(m));
            }

            List<String> params = new ArrayList<>();

            params.add("mProfileOwnerComponentName");

            for (VariableElement param : method.getParameters()) {
                ParameterSpec parameterSpec = ParameterSpec.builder(ClassName.get(param.asType()),
                        param.getSimpleName().toString()).build();

                params.add(param.getSimpleName().toString());

                methodBuilder.addParameter(parameterSpec);
            }

            methodBuilder.beginControlFlow("try").addStatement("tryConnect(mConnector)");

            if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement("mProfileClass.other().$L($L)", method.getSimpleName(),
                        String.join(", ", params));
            } else {
                methodBuilder.addStatement("return mProfileClass.other().$L($L)",
                        method.getSimpleName(), String.join(", ", params));
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

        classBuilder.addMethod(
                MethodSpec.methodBuilder("tryConnect")
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(CROSS_PROFILE_CONNECTOR_CLASSNAME, "connector")
                        .addException(UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
                        .addStatement("int retries = 300") // 30 seconds of retries
                        .beginControlFlow("while (true)")
                        .beginControlFlow("try")
                        .addStatement("connector.connect()")
                        .addStatement("return")
                        .nextControlFlow("catch ($T e)", UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
                        .beginControlFlow("if (retries-- <= 0)")
                        .addStatement("throw e")
                        .endControlFlow()
                        .beginControlFlow("try")
                        .addStatement("$T.sleep(100)", Thread.class)
                        .nextControlFlow("catch ($T e2)", InterruptedException.class)
                        .addStatement("throw e")
                        .endControlFlow()
                        .endControlFlow()
                        .endControlFlow()
                        .build()
        );

        writeClassToFile("android.app.admin", classBuilder.build());
    }

    private void generateTargetedRemoteActivityImpl(TypeElement neneActivityInterface) {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        TARGETED_REMOTE_ACTIVITY_IMPL_CLASSNAME)
                        .addSuperinterface(TARGETED_REMOTE_ACTIVITY_CLASSNAME)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        for (ExecutableElement method : getMethods(neneActivityInterface)) {
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

    private void generateTargetedRemoteActivityWrapper(TypeElement neneActivityInterface) {
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

        for (ExecutableElement method : getMethods(neneActivityInterface)) {
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

            methodBuilder.addStatement("int retries = 300") // 30 seconds of retries
                    .beginControlFlow("while (true)")
                    .beginControlFlow("try")
                    .addStatement("mConnector.connect()");

            if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(
                        "mProfileTargetedRemoteActivity.other().$L($L)",
                        method.getSimpleName(), params);
                methodBuilder.addStatement("return");
            } else {
                methodBuilder.addStatement(
                        "return mProfileTargetedRemoteActivity.other().$L($L)",
                        method.getSimpleName(), params);
            }

            methodBuilder.nextControlFlow(
                    "catch ($T e)", UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME)
                    .beginControlFlow("if (retries-- <= 0)")
                    .addStatement(
                            "throw new $T($S, e)",
                            NENE_EXCEPTION_CLASSNAME, "Error connecting to test app")
                    .endControlFlow()
                    .beginControlFlow("try")
                    .addStatement("$T.sleep(100)", Thread.class)
                    .nextControlFlow("catch ($T e2)", InterruptedException.class)
                    .addStatement(
                            "throw new $T($S, e)",
                            NENE_EXCEPTION_CLASSNAME, "Error connecting to test app")
                    .endControlFlow()
                    .nextControlFlow("catch ($T e)", PROFILE_RUNTIME_EXCEPTION_CLASSNAME)
                    .addStatement("throw ($T) e.getCause()", RuntimeException.class)
                    .nextControlFlow("finally")
                    .addStatement("mConnector.stopManualConnectionManagement()")
                    .endControlFlow() // try
                    .endControlFlow(); // while(true)

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(PACKAGE_NAME, classBuilder.build());
    }

    private void generateTestAppActivityImpl(TypeElement neneActivityInterface) {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        TEST_APP_ACTIVITY_IMPL_CLASSNAME)
                        .superclass(TEST_APP_ACTIVITY_CLASSNAME)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addField(FieldSpec.builder(String.class, "mActivityClassName")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL).build());
        classBuilder.addField(FieldSpec.builder(
                TARGETED_REMOTE_ACTIVITY_CLASSNAME, "mTargetedRemoteActivity")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL).build());

        classBuilder.addMethod(
                MethodSpec.constructorBuilder()
                        .addParameter(TEST_APP_INSTANCE_REFERENCE_CLASSNAME, "instance")
                        .addParameter(
                                COMPONENT_REFERENCE_CLASSNAME, "component")
                        .addStatement("super(instance, component)")
                        .addStatement("mActivityClassName = component.className()")
                        .addStatement("mTargetedRemoteActivity = new $T(mInstance.connector())",
                                TARGETED_REMOTE_ACTIVITY_WRAPPER_CLASSNAME)
                        .build());


        for (ExecutableElement method : getMethods(neneActivityInterface)) {
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

    private void generateTargetedRemoteActivityInterface(TypeElement neneActivityInterface) {
        TypeSpec.Builder classBuilder =
                TypeSpec.interfaceBuilder(
                        TARGETED_REMOTE_ACTIVITY_CLASSNAME)
                        .addModifiers(Modifier.PUBLIC);

        for (ExecutableElement method : getMethods(neneActivityInterface)) {
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

    private void generateProvider(List<TypeElement> systemServiceClasses) {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        "Provider")
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addMethod(MethodSpec.methodBuilder("provideTargetedRemoteActivity")
                .returns(TARGETED_REMOTE_ACTIVITY_CLASSNAME)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CrossProfileProvider.class)
                .addCode("return new $T();", TARGETED_REMOTE_ACTIVITY_IMPL_CLASSNAME)
                .build());

        classBuilder.addMethod(MethodSpec.methodBuilder("provideTestAppController")
                .returns(TEST_APP_CONTROLLER_CLASSNAME)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CrossProfileProvider.class)
                .addCode("return new $T();", TEST_APP_CONTROLLER_CLASSNAME)
                .build());

        classBuilder.addMethod(MethodSpec.methodBuilder(
                "provideRemoteDevicePolicyManagerParent")
                .returns(REMOTE_DEVICE_POLICY_MANAGER_PARENT_CLASSNAME)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CrossProfileProvider.class)
                .addParameter(CONTEXT_CLASSNAME, "context")
                .addCode("return new $T(context.getSystemService($T.class));",
                        REMOTE_DEVICE_POLICY_MANAGER_PARENT_CLASSNAME,
                        DEVICE_POLICY_MANAGER_CLASSNAME)
                .build());

        for (TypeElement systemServiceClass : systemServiceClasses) {
            ClassName originalClassName = ClassName.get(systemServiceClass);
            ClassName interfaceClassName = ClassName.get(
                    originalClassName.packageName(), "Remote" + originalClassName.simpleName());
            ClassName implClassName = ClassName.get(
                    originalClassName.packageName(), interfaceClassName.simpleName() + "Impl");

            CodeBlock systemServiceGetterCode = CodeBlock.of(
                    "context.getSystemService($T.class)", originalClassName);

            if (systemServiceClass.asType().toString().equals(
                    "android.content.pm.PackageManager")) {
                // Special case - getSystemService will return null
                systemServiceGetterCode = CodeBlock.of("context.getPackageManager()");
            }

            classBuilder.addMethod(
                    MethodSpec.methodBuilder("provide" + interfaceClassName.simpleName())
                            .returns(interfaceClassName)
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(CrossProfileProvider.class)
                            .addParameter(CONTEXT_CLASSNAME, "context")
                            .addCode("return new $T($L);",
                                    implClassName, systemServiceGetterCode)
                            .build());
        }

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
