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

package com.android.bedstead.remoteframeworkclasses.processor;


import com.android.bedstead.remoteframeworkclasses.processor.annotations.RemoteFrameworkClasses;

import com.google.android.enterprise.connectedapps.annotations.CrossUser;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;

/**
 * Processor for generating {@code RemoteSystemService} classes.
 *
 * <p>This is started by including the {@link RemoteFrameworkClasses} annotation.
 *
 * <p>For each entry in {@code SYSTEM_SERVICES} this will generate an interface including all public
 * and test APIs with the {@code CrossUser} annotation. This interface will be named the same as the
 * framework class except with a prefix of "Remote", and will be in the same package.
 *
 * <p>This will also generate an implementation of the interface which takes an instance of the
 * framework class in the constructor, and each method proxying calls to the framework class.
 */
@SupportedAnnotationTypes({
        "com.android.bedstead.remoteframeworkclasses.processor.annotations.RemoteFrameworkClasses",
})
@AutoService(javax.annotation.processing.Processor.class)
public final class Processor extends AbstractProcessor {

    private static final String[] SYSTEM_SERVICES = {
            "android.app.admin.DevicePolicyManager",
            "android.net.wifi.WifiManager",
            "android.os.HardwarePropertiesManager",
            "android.os.UserManager",
            "android.content.pm.PackageManager"
    };

    private static final String PARENT_PROFILE_INSTANCE =
            "public android.app.admin.DevicePolicyManager getParentProfileInstance(android"
                    + ".content.ComponentName)";

    private static final Set<String> BLOCKLISTED_METHODS = ImmutableSet.of(
            // DevicePolicyManager

            // Uses ServiceConnection
            "public boolean bindDeviceAdminServiceAsUser(android.content.ComponentName, android"
                    + ".content.Intent, android.content.ServiceConnection, int, android.os"
                    + ".UserHandle)",
            // Uses AttestedKeyPair
            "public android.security.AttestedKeyPair generateKeyPair(android.content"
                    + ".ComponentName, String, android.security.keystore.KeyGenParameterSpec, int)",
            // Uses Executor
            "public void installSystemUpdate(@NonNull android.content.ComponentName, android.net"
                    + ".Uri, java.util.concurrent.Executor, android.app.admin.DevicePolicyManager"
                    + ".InstallSystemUpdateCallback)",

            // WifiManager

            // Uses Executor
            "public void addSuggestionConnectionStatusListener(java.util.concurrent.Executor, "
                    + "android.net.wifi.WifiManager.SuggestionConnectionStatusListener)",
            "public void addSuggestionUserApprovalStatusListener(java.util.concurrent.Executor, "
                    + "android.net.wifi.WifiManager.SuggestionUserApprovalStatusListener)",
            "public void clearApplicationUserData(android.content.ComponentName, @NonNull String,"
                    + " @NonNull java.util.concurrent.Executor, android.app.admin"
                    + ".DevicePolicyManager.OnClearApplicationUserDataListener)",
            "public void registerScanResultsCallback(java.util.concurrent.Executor, android.net"
                    + ".wifi.WifiManager.ScanResultsCallback)",
            "public void registerSubsystemRestartTrackingCallback(java.util.concurrent.Executor, "
                    + "android.net.wifi.WifiManager.SubsystemRestartTrackingCallback)",
            // Uses WpsCallback
            "public void cancelWps(android.net.wifi.WifiManager.WpsCallback)",
            // Uses MulticastLock
            "public android.net.wifi.WifiManager.MulticastLock createMulticastLock(String)",
            // Uses WifiLock
            "public android.net.wifi.WifiManager.WifiLock createWifiLock(int, String)",
            "public android.net.wifi.WifiManager.WifiLock createWifiLock(String)",
            // Uses SuggestionConnectionStatusListener
            "public void removeSuggestionConnectionStatusListener(android.net.wifi.WifiManager"
                    + ".SuggestionConnectionStatusListener)",
            // Uses SuggestionUserApprovalStatusListener
            "public void removeSuggestionUserApprovalStatusListener(android.net.wifi.WifiManager"
                    + ".SuggestionUserApprovalStatusListener)",
            // Uses LocalOnlyHotspotCallback
            "public void startLocalOnlyHotspot(android.net.wifi.WifiManager"
                    + ".LocalOnlyHotspotCallback, android.os.Handler)",
            // Uses WpsCallback
            "public void startWps(android.net.wifi.WpsInfo, android.net.wifi.WifiManager"
                    + ".WpsCallback)",
            // Uses ScanResultsCallback
            "public void unregisterScanResultsCallback(@NonNull android.net.wifi.WifiManager"
                    + ".ScanResultsCallback)",
            // Uses SubsystemRestartTrackingCallback
            "public void unregisterSubsystemRestartTrackingCallback(android.net.wifi.WifiManager"
                    + ".SubsystemRestartTrackingCallback)",

            // PackageManager

            // Uses IBinder
            "public android.os.IBinder getHoldLockToken()",
            "public void holdLock(android.os.IBinder, int)",
            // Uses Drawable
            "public abstract android.graphics.drawable.Drawable getActivityBanner(@NonNull "
                    + "android.content.ComponentName) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getActivityBanner(@NonNull "
                    + "android.content.Intent) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getActivityIcon(@NonNull android"
                    + ".content.ComponentName) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getActivityIcon(@NonNull android"
                    + ".content.Intent) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getActivityLogo(@NonNull android"
                    + ".content.ComponentName) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getActivityLogo(@NonNull android"
                    + ".content.Intent) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getApplicationBanner(@NonNull "
                    + "android.content.pm.ApplicationInfo)",
            "public abstract android.graphics.drawable.Drawable getApplicationBanner(@NonNull "
                    + "String) throws android.content.pm.PackageManager.NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getApplicationIcon(@NonNull "
                    + "android.content.pm.ApplicationInfo)",
            "public abstract android.graphics.drawable.Drawable getApplicationIcon(@NonNull "
                    + "String) throws android.content.pm.PackageManager.NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getApplicationLogo(@NonNull "
                    + "android.content.pm.ApplicationInfo)",
            "public abstract android.graphics.drawable.Drawable getApplicationLogo(@NonNull "
                    + "String) throws android.content.pm.PackageManager.NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getDefaultActivityIcon()",
            "public abstract android.graphics.drawable.Drawable getDrawable(@NonNull String, "
                    + "@DrawableRes int, @Nullable android.content.pm.ApplicationInfo)",
            "public abstract android.graphics.drawable.Drawable getUserBadgedDrawableForDensity"
                    + "(@NonNull android.graphics.drawable.Drawable, @NonNull android.os"
                    + ".UserHandle, @Nullable android.graphics.Rect, int)",
            "public abstract android.graphics.drawable.Drawable getUserBadgedIcon(@NonNull "
                    + "android.graphics.drawable.Drawable, @NonNull android.os.UserHandle)",
            "public boolean isDefaultApplicationIcon(@NonNull android.graphics.drawable.Drawable)",
            // Uses Executor
            "public void getGroupOfPlatformPermission(@NonNull String, @NonNull java.util"
                    + ".concurrent.Executor, @NonNull java.util.function.Consumer<java.lang"
                    + ".String>)",
            "public void getPlatformPermissionsForGroup(@NonNull String, @NonNull java.util"
                    + ".concurrent.Executor, @NonNull java.util.function.Consumer<java.util"
                    + ".List<java.lang.String>>)",
            // Uses Resources
            "public abstract android.content.res.Resources getResourcesForActivity(@NonNull "
                    + "android.content.ComponentName) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.content.res.Resources getResourcesForApplication(@NonNull "
                    + "android.content.pm.ApplicationInfo) throws android.content.pm"
                    + ".PackageManager.NameNotFoundException",
            "public android.content.res.Resources getResourcesForApplication(@NonNull android"
                    + ".content.pm.ApplicationInfo, @Nullable android.content.res.Configuration) "
                    + "throws android.content.pm.PackageManager.NameNotFoundException",
            "public abstract android.content.res.Resources getResourcesForApplication(@NonNull "
                    + "String) throws android.content.pm.PackageManager.NameNotFoundException",
            // Uses PackageInstaller
            "public abstract android.content.pm.PackageInstaller getPackageInstaller()",
            // Uses XmlResourceParser
            "public abstract android.content.res.XmlResourceParser getXml(@NonNull String, "
                    + "@XmlRes int, @Nullable android.content.pm.ApplicationInfo)",
            // Uses OnChecksumsReadyListener
            "public void requestChecksums(@NonNull String, boolean, int, @NonNull java.util"
                    + ".List<java.security.cert.Certificate>, @NonNull android.content.pm"
                    + ".PackageManager.OnChecksumsReadyListener) throws java.security.cert"
                    + ".CertificateEncodingException, android.content.pm.PackageManager"
                    + ".NameNotFoundException"


    );


    private static final ClassName NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteDevicePolicyManager");
    private static final ClassName COMPONENT_NAME_CLASSNAME =
            ClassName.get("android.content", "ComponentName");

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        if (!roundEnv.getElementsAnnotatedWith(RemoteFrameworkClasses.class).isEmpty()) {
            Set<MethodSignature> blocklistedMethodSignatures = BLOCKLISTED_METHODS.stream()
                    .map(m -> MethodSignature.forApiString(
                            m, processingEnv.getTypeUtils(), processingEnv.getElementUtils()))
                    .collect(Collectors.toSet());

            for (String systemService : SYSTEM_SERVICES) {
                TypeElement typeElement =
                        processingEnv.getElementUtils().getTypeElement(systemService);
                generateRemoteSystemService(
                        typeElement, blocklistedMethodSignatures, processingEnv.getElementUtils());
            }

            generateWrappers();
        }

        return true;
    }

    private void generateWrappers() {
        generateWrapper(NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME);
    }

    private void generateWrapper(ClassName className) {
        String contents = null;
        try {
            URL url = Processor.class.getResource(
                    "/parcelablewrappers/" + className.simpleName() + ".java.txt");
            contents = Resources.toString(url, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse wrapper " + className, e);
        }

        JavaFileObject builderFile;
        try {
            builderFile = processingEnv.getFiler()
                    .createSourceFile(className.packageName() + "." + className.simpleName());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not write parcelablewrapper for " + className, e);
        }

        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            out.write(contents);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not write parcelablewrapper for " + className, e);
        }
    }

    private void generateRemoteSystemService(
            TypeElement frameworkClass,
            Set<MethodSignature> blocklistedMethodSignatures,
            Elements elements) {
        Set<ExecutableElement> methods = filterMethods(getMethods(frameworkClass),
                Apis.forClass(frameworkClass.getQualifiedName().toString(),
                        processingEnv.getTypeUtils(), processingEnv.getElementUtils()), elements)
                .stream()
                .filter(t -> !blocklistedMethodSignatures.contains(
                        MethodSignature.forMethod(t, elements)))
                .collect(Collectors.toSet());

        generateFrameworkInterface(frameworkClass, methods);
        generateFrameworkImpl(frameworkClass, methods);

        if (frameworkClass.getSimpleName().contentEquals("DevicePolicyManager")) {
            // Special case, we need to support the .getParentProfileInstance method
            generateDpmParent(frameworkClass, methods);
        }
    }

    private void generateFrameworkInterface(
            TypeElement frameworkClass, Set<ExecutableElement> methods) {
        MethodSignature parentProfileInstanceSignature =
                MethodSignature.forApiString(PARENT_PROFILE_INSTANCE, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        String packageName = frameworkClass.getEnclosingElement().toString();
        ClassName className = ClassName.get(packageName,
                "Remote" + frameworkClass.getSimpleName().toString());
        ClassName implClassName = ClassName.get(packageName,
                "Remote" + frameworkClass.getSimpleName().toString() + "Impl");
        TypeSpec.Builder classBuilder =
                TypeSpec.interfaceBuilder(className)
                        .addModifiers(Modifier.PUBLIC);


        classBuilder.addJavadoc("Public, test, and system interface for {@link $T}.\n\n",
                frameworkClass);
        classBuilder.addJavadoc("<p>All methods are annotated {@link $T} for compatibility with the"
                + " Connected Apps SDK.\n\n", CrossUser.class);
        classBuilder.addJavadoc("<p>For implementation see {@link $T}.\n", implClassName);


        classBuilder.addAnnotation(AnnotationSpec.builder(CrossUser.class)
                .addMember("parcelableWrappers", "$T.class",
                        NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME)
                .build());

        for (ExecutableElement method : methods) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .addAnnotation(CrossUser.class);


            MethodSignature signature = MethodSignature.forMethod(method,
                    processingEnv.getElementUtils());
            if (signature.equals(parentProfileInstanceSignature)) {
                // Special case, we want to return a RemoteDevicePolicyManager instead
                methodBuilder.returns(ClassName.get(
                        "android.app.admin", "RemoteDevicePolicyManager"));
            }

            methodBuilder.addJavadoc("See {@link $T#$L}.",
                    ClassName.get(frameworkClass.asType()), method.getSimpleName());

            for (TypeMirror thrownType : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(thrownType));
            }

            for (VariableElement param : method.getParameters()) {
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()),
                                param.getSimpleName().toString()).build();

                methodBuilder.addParameter(parameterSpec);
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(packageName, classBuilder.build());
    }

    private void generateDpmParent(TypeElement frameworkClass, Set<ExecutableElement> methods) {
        MethodSignature parentProfileInstanceSignature = MethodSignature.forApiString(
                PARENT_PROFILE_INSTANCE, processingEnv.getTypeUtils(),
                processingEnv.getElementUtils());
        String packageName = frameworkClass.getEnclosingElement().toString();
        ClassName className =
                ClassName.get(packageName, "Remote" + frameworkClass.getSimpleName() + "Parent");
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(className).addModifiers(Modifier.FINAL, Modifier.PUBLIC);

        classBuilder.addAnnotation(AnnotationSpec.builder(CrossUser.class)
                .addMember("parcelableWrappers", "$T.class",
                        NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME)
                .build());

        classBuilder.addField(ClassName.get(frameworkClass),
                "mFrameworkClass", Modifier.PRIVATE, Modifier.FINAL);

        classBuilder.addMethod(
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ClassName.get(frameworkClass), "frameworkClass")
                        .addCode("mFrameworkClass = frameworkClass;")
                        .build()
        );

        for (ExecutableElement method : methods) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(CrossUser.class);

            MethodSignature signature = MethodSignature.forMethod(method,
                    processingEnv.getElementUtils());

            for (TypeMirror thrownType : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(thrownType));
            }

            methodBuilder.addParameter(COMPONENT_NAME_CLASSNAME, "profileOwnerComponentName");

            List<String> paramNames = new ArrayList<>();

            for (VariableElement param : method.getParameters()) {
                String paramName = param.getSimpleName().toString();
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()), paramName).build();

                paramNames.add(paramName);

                methodBuilder.addParameter(parameterSpec);
            }

            if (signature.equals(parentProfileInstanceSignature)) {
                // Special case, we want to return a RemoteDevicePolicyManager instead
                methodBuilder.returns(ClassName.get(
                        "android.app.admin", "RemoteDevicePolicyManager"));
                methodBuilder.addStatement(
                        "mFrameworkClass.getParentProfileInstance(profileOwnerComponentName).$L"
                                + "($L)",
                        method.getSimpleName(), String.join(", ", paramNames));
                methodBuilder.addStatement("throw new $T($S)", UnsupportedOperationException.class,
                        "TestApp does not support calling .getParentProfileInstance() on a parent"
                                + ".");
            } else if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(
                        "mFrameworkClass.getParentProfileInstance(profileOwnerComponentName).$L"
                                + "($L)",
                        method.getSimpleName(), String.join(", ", paramNames));
            } else {
                methodBuilder.addStatement(
                        "return mFrameworkClass.getParentProfileInstance"
                                + "(profileOwnerComponentName).$L($L)",
                        method.getSimpleName(), String.join(", ", paramNames));
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(packageName, classBuilder.build());
    }

    private void generateFrameworkImpl(TypeElement frameworkClass, Set<ExecutableElement> methods) {
        MethodSignature parentProfileInstanceSignature = MethodSignature.forApiString(
                PARENT_PROFILE_INSTANCE, processingEnv.getTypeUtils(),
                processingEnv.getElementUtils());
        String packageName = frameworkClass.getEnclosingElement().toString();
        ClassName interfaceClassName = ClassName.get(packageName,
                "Remote" + frameworkClass.getSimpleName().toString());
        ClassName className = ClassName.get(packageName,
                "Remote" + frameworkClass.getSimpleName().toString() + "Impl");
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        className)
                        .addSuperinterface(interfaceClassName)
                        .addModifiers(Modifier.FINAL, Modifier.PUBLIC);

        classBuilder.addField(ClassName.get(frameworkClass),
                "mFrameworkClass", Modifier.PRIVATE, Modifier.FINAL);

        classBuilder.addMethod(
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ClassName.get(frameworkClass), "frameworkClass")
                        .addCode("mFrameworkClass = frameworkClass;")
                        .build()
        );

        for (ExecutableElement method : methods) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class);

            MethodSignature signature = MethodSignature.forMethod(method,
                    processingEnv.getElementUtils());

            for (TypeMirror thrownType : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(thrownType));
            }

            List<String> paramNames = new ArrayList<>();

            for (VariableElement param : method.getParameters()) {
                String paramName = param.getSimpleName().toString();

                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()), paramName).build();

                paramNames.add(paramName);

                methodBuilder.addParameter(parameterSpec);
            }

            if (signature.equals(parentProfileInstanceSignature)) {
                // Special case, we want to return a RemoteDevicePolicyManager instead
                methodBuilder.returns(ClassName.get(
                        "android.app.admin", "RemoteDevicePolicyManager"));
                methodBuilder.addStatement(
                        "return new $T(mFrameworkClass.$L($L))",
                        className, method.getSimpleName(), String.join(", ", paramNames));
            } else if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(
                        "mFrameworkClass.$L($L)",
                        method.getSimpleName(), String.join(", ", paramNames));
            } else {
                methodBuilder.addStatement(
                        "return mFrameworkClass.$L($L)",
                        method.getSimpleName(), String.join(", ", paramNames));
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(packageName, classBuilder.build());
    }

    private Set<ExecutableElement> filterMethods(
            Set<ExecutableElement> allMethods, Apis validApis, Elements elements) {
        return allMethods.stream()
                .filter(m -> validApis.methods().contains(MethodSignature.forMethod(m, elements)))
                .collect(Collectors.toSet());
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
                .filter(e -> e.getKind().equals(ElementKind.METHOD))
                .collect(Collectors.toSet());
    }
}
