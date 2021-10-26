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
import java.util.HashSet;
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
 * <p>For each entry in {@code FRAMEWORK_CLASSES} this will generate an interface including all public
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

    private static final String[] FRAMEWORK_CLASSES = {
            "android.app.admin.DevicePolicyManager",
            "android.net.wifi.WifiManager",
            "android.os.HardwarePropertiesManager",
            "android.os.UserManager",
            "android.content.pm.PackageManager",
            "android.content.pm.CrossProfileApps",
            "android.content.pm.LauncherApps",
            "android.accounts.AccountManager",
            "android.app.Activity"
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
                    + ".NameNotFoundException",

            // CrossProfileApps

            // Uses Drawable
            "public android.graphics.drawable.Drawable getProfileSwitchingIconDrawable("
                    + "android.os.UserHandle)",
            // Uses Activity
            "public void startActivity("
                    + "android.content.Intent, android.os.UserHandle, android.app.Activity)",
            "public void startActivity(android.content.Intent, android.os.UserHandle,"
                    + "android.app.Activity, android.os.Bundle)",

            // LauncherApps

            //Uses LauncherApps.Callback
            "public void registerCallback("
                    + "android.content.pm.LauncherApps.Callback, android.os.Handler)",
            "public void registerCallback("
                    + "android.content.pm.LauncherApps.Callback)",

            //Uses Drawable
            "public android.graphics.drawable.Drawable getShortcutBadgedIconDrawable("
            + "android.content.pm.ShortcutInfo, int)",
            "public android.graphics.drawable.Drawable getShortcutIconDrawable("
            + "android.content.pm.ShortcutInfo, int)",

            //Uses Executor
            "public void registerPackageInstallerSessionCallback("
                    + "@NonNull @CallbackExecutor java.util.concurrent.Executor,"
                    + "@NonNull android.content.pm.PackageInstaller.SessionCallback)",

            // Uses LauncherActivityInfo
            "public java.util.List<android.content.pm.LauncherActivityInfo> getActivityList("
                    + "String, android.os.UserHandle)",
            "public java.util.List<android.content.pm.LauncherActivityInfo> "
                    + "getShortcutConfigActivityList(String, android.os.UserHandle)",
            "public android.content.IntentSender getShortcutConfigActivityIntent("
                    + "@NonNull android.content.pm.LauncherActivityInfo)",
            "public android.content.pm.LauncherActivityInfo resolveActivity("
                    + "android.content.Intent, android.os.UserHandle)",

            //Uses LauncherApps.ShortcutQuery
            "public java.util.List<android.content.pm.ShortcutInfo> getShortcuts("
                    + "android.content.pm.LauncherApps.ShortcutQuery, android.os.UserHandle)",
            "public void unregisterCallback(android.content.pm.LauncherApps.Callback)",

            //Uses PackageInfo.SessionCallback
            "public void unregisterPackageInstallerSessionCallback("
                    + "android.content.pm.PackageInstaller.SessionCallback)",

            // AccountManager

            // Uses Activity
            "public android.accounts.AccountManagerFuture<android.os.Bundle> addAccount(String, String, String[], android.os.Bundle, android.app.Activity, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> finishSession(android.os.Bundle, android.app.Activity, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> editProperties(String, android.app.Activity, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> getAuthToken(android.accounts.Account, String, android.os.Bundle, android.app.Activity, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> getAuthTokenByFeatures(String, String, String[], android.app.Activity, android.os.Bundle, android.os.Bundle, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> removeAccount(android.accounts.Account, android.app.Activity, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> startAddAccountSession(String, String, String[], android.os.Bundle, android.app.Activity, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> startUpdateCredentialsSession(android.accounts.Account, String, android.os.Bundle, android.app.Activity, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> updateCredentials(android.accounts.Account, String, android.os.Bundle, android.app.Activity, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> confirmCredentials(android.accounts.Account, android.os.Bundle, android.app.Activity, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",

            // Uses OnAccountsUpdateListener
            "public void addOnAccountsUpdatedListener(android.accounts.OnAccountsUpdateListener, android.os.Handler, boolean)",
            "public void addOnAccountsUpdatedListener(android.accounts.OnAccountsUpdateListener, android.os.Handler, boolean, String[])",
            "public void removeOnAccountsUpdatedListener(android.accounts.OnAccountsUpdateListener)",

            // Uses AccountManagerCallback
            "public android.accounts.AccountManagerFuture<android.os.Bundle> getAuthToken(android.accounts.Account, String, boolean, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> getAuthToken(android.accounts.Account, String, android.os.Bundle, boolean, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> addAccount(String, String, String[], android.os.Bundle, android.app.Activity, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> getAuthToken(android.accounts.Account, String, boolean, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> getAuthToken(android.accounts.Account, String, android.os.Bundle, boolean, android.accounts.AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.os.Bundle hasFeatures(android.accounts.Account, String[], android.accounts.AccountManagerCallback<java.lang.Boolean>, android.os.Handler)",
            "public android.os.Bundle isCredentialsUpdateSuggested(android.accounts.Account, String, android.accounts.AccountManagerCallback<java.lang.Boolean>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.accounts.Account[]> getAccountsByTypeAndFeatures(String, String[], android.accounts.AccountManagerCallback<android.accounts.Account[]>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<java.lang.Boolean> hasFeatures(android.accounts.Account, String[], android.accounts.AccountManagerCallback<java.lang.Boolean>, android.os.Handler)",
            "public android.os.Bundle isCredentialsUpdateSuggested(android.accounts.AccountAuthenticatorResponse, android.accounts.Account, String) throws android.accounts.NetworkErrorException",
            "public android.accounts.AccountManagerFuture<java.lang.Boolean> isCredentialsUpdateSuggested(android.accounts.Account, String, android.accounts.AccountManagerCallback<java.lang.Boolean>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<java.lang.Boolean> removeAccount(android.accounts.Account, android.accounts.AccountManagerCallback<java.lang.Boolean>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.accounts.Account> renameAccount(android.accounts.Account, @Size(min=1) String, android.accounts.AccountManagerCallback<android.accounts.Account>, android.os.Handler)",

            // Activity

            // Uses android.view.View
            "public void addContentView(android.view.View, android.view.ViewGroup.LayoutParams)",
            "@Nullable public android.view.View getCurrentFocus()",
            "@Nullable public android.view.View onCreatePanelView(int)",
            "@Nullable public android.view.View onCreateView(@NonNull String, @NonNull android.content.Context, @NonNull android.util.AttributeSet)",
            "@Nullable public android.view.View onCreateView(@Nullable android.view.View, @NonNull String, @NonNull android.content.Context, @NonNull android.util.AttributeSet)",
            "public boolean onPreparePanel(int, @Nullable android.view.View, @NonNull android.view.Menu)",
            "public void openContextMenu(android.view.View)",
            "public void registerForContextMenu(android.view.View)",
            "public void setContentView(android.view.View)",
            "public void setContentView(android.view.View, android.view.ViewGroup.LayoutParams)",
            "public void unregisterForContextMenu(android.view.View)",

            // Uses java.io.FileDescriptor
            "public void dump(@NonNull String, @Nullable java.io.FileDescriptor, @NonNull java.io.PrintWriter, @Nullable String[])",

            // Uses android.app.Activity
            "@Deprecated public void finishActivityFromChild(@NonNull android.app.Activity, int)",
            "@Deprecated public void finishFromChild(android.app.Activity)",
            "public final android.app.Activity getParent()",
            "@Deprecated public boolean navigateUpToFromChild(android.app.Activity, android.content.Intent)",
            "protected void onChildTitleChanged(android.app.Activity, CharSequence)",
            "@Deprecated public boolean onNavigateUpFromChild(android.app.Activity)",
            "@Deprecated public void startActivityFromChild(@NonNull android.app.Activity, @RequiresPermission android.content.Intent, int)",
            "@Deprecated public void startActivityFromChild(@NonNull android.app.Activity, @RequiresPermission android.content.Intent, int, @Nullable android.os.Bundle)",
            "@Deprecated public void startIntentSenderFromChild(android.app.Activity, android.content.IntentSender, int, android.content.Intent, int, int, int) throws android.content.IntentSender.SendIntentException",
            "@Deprecated public void startIntentSenderFromChild(android.app.Activity, android.content.IntentSender, int, android.content.Intent, int, int, int, @Nullable android.os.Bundle) throws android.content.IntentSender.SendIntentException",

            // Uses android.app.ActionBar
            "@Nullable public android.app.ActionBar getActionBar()",

            // Uses android.app.Application
            "public final android.app.Application getApplication()",

            // Uses android.app.Fragment
            "@Deprecated public void onAttachFragment(android.app.Fragment)",
            "@Deprecated public void startActivityFromFragment(@NonNull android.app.Fragment, @RequiresPermission android.content.Intent, int)",
            "@Deprecated public void startActivityFromFragment(@NonNull android.app.Fragment, @RequiresPermission android.content.Intent, int, @Nullable android.os.Bundle)",

            // Uses android.app.FragmentManager
            "@Deprecated public android.app.FragmentManager getFragmentManager()",

            // Uses android.transition.Scene
            "public android.transition.Scene getContentScene()",

            // Uses android.transition.TransitionManager
            "public android.transition.TransitionManager getContentTransitionManager()",
            "public void setContentTransitionManager(android.transition.TransitionManager)",

            // Uses Object
            "@Nullable public Object getLastNonConfigurationInstance()",
            "public Object onRetainNonConfigurationInstance()",

            // Uses android.view.LayoutInflater
            "@NonNull public android.view.LayoutInflater getLayoutInflater()",

            // Uses android.view.MenuInflater
            "@NonNull public android.view.MenuInflater getMenuInflater()",

            // Uses android.app.LoaderManager
            "@Deprecated public android.app.LoaderManager getLoaderManager()",

            // Uses android.media.session.MediaController
            "public final android.media.session.MediaController getMediaController()",

            // Uses android.content.SharedPreferences
            "public android.content.SharedPreferences getPreferences(int)",

            // Uses android.view.SearchEvent
            "public final android.view.SearchEvent getSearchEvent()",

            // Uses android.window.SplashScreen
            "@NonNull public final android.window.SplashScreen getSplashScreen()",

            // Uses android.app.VoiceInteractor
            "public android.app.VoiceInteractor getVoiceInteractor()",

            // Uses android.view.Window
            "public android.view.Window getWindow()",

            // Uses android.view.WindowManager
            "public android.view.WindowManager getWindowManager()",

            // Uses android.database.Cursor
            "@Deprecated public final android.database.Cursor managedQuery(android.net.Uri, String[], String, String[], String)",
            "@Deprecated public void startManagingCursor(android.database.Cursor)",
            "@Deprecated public void stopManagingCursor(android.database.Cursor)",

            // Uses android.view.ActionMode
            "@CallSuper public void onActionModeFinished(android.view.ActionMode)",
            "@CallSuper public void onActionModeStarted(android.view.ActionMode)",
            "@Nullable public android.view.ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback)",
            "@Nullable public android.view.ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback, int)",
            "@Nullable public android.view.ActionMode startActionMode(android.view.ActionMode.Callback)",
            "@Nullable public android.view.ActionMode startActionMode(android.view.ActionMode.Callback, int)",

            // Uses android.view.MenuItem
            "public boolean onContextItemSelected(@NonNull android.view.MenuItem)",
            "public void onContextMenuClosed(@NonNull android.view.Menu)",
            "public boolean onMenuItemSelected(int, @NonNull android.view.MenuItem)",
            "public boolean onOptionsItemSelected(@NonNull android.view.MenuItem)",

            // Uses android.view.ContextMenu
            "public void onCreateContextMenu(android.view.ContextMenu, android.view.View, android.view.ContextMenu.ContextMenuInfo)",

            // Uses android.app.Dialog
            "@Deprecated protected android.app.Dialog onCreateDialog(int)",
            "@Deprecated @Nullable protected android.app.Dialog onCreateDialog(int, android.os.Bundle)",
            "@Deprecated protected void onPrepareDialog(int, android.app.Dialog)",
            "@Deprecated protected void onPrepareDialog(int, android.app.Dialog, android.os.Bundle)",

            // Uses android.app.TaskStackBuilder
            "public void onCreateNavigateUpTaskStack(android.app.TaskStackBuilder)",
            "public void onPrepareNavigateUpTaskStack(android.app.TaskStackBuilder)",

            // Uses android.view.Menu
            "public boolean onCreateOptionsMenu(android.view.Menu)",
            "public boolean onCreatePanelMenu(int, @NonNull android.view.Menu)",
            "public boolean onMenuOpened(int, @NonNull android.view.Menu)",
            "public void onOptionsMenuClosed(android.view.Menu)",
            "public void onPanelClosed(int, @NonNull android.view.Menu)",
            "public boolean onPrepareOptionsMenu(android.view.Menu)",

            // Uses android.graphics.Canvas
            "@Deprecated public boolean onCreateThumbnail(android.graphics.Bitmap, android.graphics.Canvas)",

            // Uses android.os.CancellationSignal
            "public void onGetDirectActions(@NonNull android.os.CancellationSignal, @NonNull java.util.function.Consumer<java.util.List<android.app.DirectAction>>)",
            "public void onPerformDirectAction(@NonNull String, @NonNull android.os.Bundle, @NonNull android.os.CancellationSignal, @NonNull java.util.function.Consumer<android.os.Bundle>)",

            // Uses android.view.SearchEvent
            "public boolean onSearchRequested(@Nullable android.view.SearchEvent)",

            // Uses android.app.Application.ActivityLifecycleCallbacks
            "public void registerActivityLifecycleCallbacks(@NonNull android.app.Application.ActivityLifecycleCallbacks)",
            "public void unregisterActivityLifecycleCallbacks(@NonNull android.app.Application.ActivityLifecycleCallbacks)",

            // Uses Runnable
            "public final void runOnUiThread(Runnable)",

            // Uses android.widget.Toolbar
            "public void setActionBar(@Nullable android.widget.Toolbar)",

            // Uses android.app.SharedElementCallback
            "public void setEnterSharedElementCallback(android.app.SharedElementCallback)",
            "public void setExitSharedElementCallback(android.app.SharedElementCallback)",

            // Uses Drawable
            "public final void setFeatureDrawable(int, android.graphics.drawable.Drawable)",

            // Uses android.media.session.MediaController
            "public final void setMediaController(android.media.session.MediaController)",

            // Context

            // Uses Object
            "public abstract Object getSystemService(@NonNull String)",

            // ContextThemeWrapper
            "protected void onApplyThemeResource(android.content.res.Resources.Theme, int, boolean)"
    );


    private static final ClassName NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteDevicePolicyManager");
    private static final ClassName COMPONENT_NAME_CLASSNAME =
            ClassName.get("android.content", "ComponentName");

    private static final ClassName ACCOUNT_MANAGE_FUTURE_WRAPPER_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.remoteframeworkclasses", "AccountManagerFutureWrapper");

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

            for (String systemService : FRAMEWORK_CLASSES) {
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
                .addMember("futureWrappers", "$T.class",
                        ACCOUNT_MANAGE_FUTURE_WRAPPER_CLASSNAME)
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
                        .addModifiers(Modifier.PUBLIC);

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
        Set<ExecutableElement> filteredMethods = new HashSet<>();

        for (ExecutableElement method : allMethods) {
            MethodSignature methodSignature = MethodSignature.forMethod(method, elements);
            if (validApis.methods().contains(methodSignature)) {
                if (method.getModifiers().contains(Modifier.PROTECTED)) {
                    System.out.println(methodSignature + " is protected. Dropping");
                } else {
                    filteredMethods.add(method);
                }
            } else {
                System.out.println("No matching public API for " + methodSignature);
            }
        }

        return filteredMethods;
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
