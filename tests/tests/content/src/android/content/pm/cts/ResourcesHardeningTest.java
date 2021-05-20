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

package android.content.pm.cts;

import static android.content.pm.cts.PackageManagerShellCommandIncrementalTest.checkIncrementalDeliveryFeature;
import static android.content.pm.cts.PackageManagerShellCommandIncrementalTest.isAppInstalled;
import static android.content.pm.cts.PackageManagerShellCommandIncrementalTest.uninstallPackageSilently;
import static android.content.pm.cts.PackageManagerShellCommandIncrementalTest.writeFullStream;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.content.Context;
import android.content.cts.util.XmlUtils;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.MatcherUtils;
import com.android.incfs.install.IBlockFilter;
import com.android.incfs.install.IncrementalInstallSession;
import com.android.incfs.install.PendingBlock;

import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
@AppModeFull
@LargeTest
public class ResourcesHardeningTest {
    private static final String TEST_APK_PATH = "/data/local/tmp/cts/content/";
    private static final String[] TEST_APKS = {"HelloWorld5.apk", "HelloWorld5_mdpi-v4.apk"};
    private static final String TEST_APP_PACKAGE = "com.example.helloworld";

    private static final String RES_IDENTIFIER = TEST_APP_PACKAGE + ":string/inc_string";
    private static final String RES_XML_PATH = "res/xml/test_xml.xml";
    private static final String RES_XML_LARGE_PATH = "res/xml/test_xml_attrs.xml";
    private static final String RES_DRAWABLE_PATH = "res/drawable-mdpi-v4/background.jpg";
    private static final int RES_STRING = 0x7f021000;
    private static final int RES_ARRAY = 0x7f051000;
    private static final int RES_STYLE = 0x7f061000;
    private static final int[] RES_STYLEABLE = {0x7f011000, 0x7f011001, 0x7f011002};

    @Before
    public void onBefore() throws Exception {
        checkIncrementalDeliveryFeature();
    }

    @Test
    public void checkGetIdentifier() throws Exception {
        testReadSuccessAndFailure(
                (res, filter) -> {
                    filter.stopSendingBlocks();
                    return res.getIdentifier(RES_IDENTIFIER, "", "");
                },
                not(equalTo(0)), equalTo(0));
    }

    @Test
    public void checkGetResourceName() throws Exception {
        testReadSuccessAndFailureException(
                (res, filter) -> {
                    filter.stopSendingBlocks();
                    return res.getResourceName(RES_STRING);
                },
                equalTo(RES_IDENTIFIER), instanceOf(Resources.NotFoundException.class));
    }

    @Test
    public void checkGetString() throws Exception {
        testReadSuccessAndFailureException(
                (res, filter) -> {
                    filter.stopSendingBlocks();
                    return res.getString(RES_STRING);
                },
                equalTo("true"), instanceOf(Resources.NotFoundException.class));
    }

    @Test
    public void checkGetStringArray() throws Exception {
        testReadSuccessAndFailureException(
                (res, filter) -> {
                    filter.stopSendingBlocks();
                    return res.getStringArray(RES_ARRAY);
                },
                equalTo(new String[]{"true"}), instanceOf(Resources.NotFoundException.class));
    }

    @Test
    public void checkOpenXmlResourceParser() throws Exception {
        testReadSuccessAndFailureException(
                (res, filter) -> {
                    filter.stopSendingBlocks();
                    final AssetManager assets = res.getAssets();
                    try (XmlResourceParser p = assets.openXmlResourceParser(RES_XML_PATH)) {
                        XmlUtils.beginDocument(p, "Test");
                        return p.nextText();
                    }
                },
                equalTo("true"), instanceOf(FileNotFoundException.class));
    }

    @Test
    public void checkApplyStyle() throws Exception {
        testReadSuccessAndFailure(
                (res, filter) -> {
                    filter.stopSendingBlocks();
                    final Resources.Theme theme = res.newTheme();
                    theme.applyStyle(RES_STYLE, true);
                    final TypedArray values = theme.obtainStyledAttributes(RES_STYLEABLE);
                    return new String[]{
                            values.getString(0),
                            values.getString(1),
                            values.getString(2),
                    };
                },
                equalTo(new String[]{"true", "true", "1"}),
                equalTo(new String[]{null, null, null}));
    }

    @Test
    public void checkXmlAttributes() throws Exception {
        testReadSuccessAndFailure(
                (res, filter) -> {
                    final AssetManager assets = res.getAssets();
                    try (XmlResourceParser p = assets.openXmlResourceParser(RES_XML_LARGE_PATH)) {
                        XmlUtils.beginDocument(p, "Test");
                        filter.stopSendingBlocks();
                        final TypedArray values = res.obtainAttributes(p, RES_STYLEABLE);
                        return new String[]{
                                values.getString(0),
                                values.getString(1),
                                values.getString(2),
                        };
                    }
                },
                equalTo(new String[]{"true", "true", "1"}),
                equalTo(new String[]{null, null, null}));
    }

    @Test
    public void checkOpen() throws Exception {
        testReadSuccessAndFailureException(
                (res, filter) -> {
                    final AssetManager assets = res.getAssets();
                    try (InputStream is = assets.openNonAsset(RES_DRAWABLE_PATH)) {
                        filter.stopSendingBlocks();
                        ByteArrayOutputStream result = new ByteArrayOutputStream();
                        writeFullStream(is, result, AssetFileDescriptor.UNKNOWN_LENGTH);
                        return true;
                    }
                }, equalTo(true), instanceOf(IOException.class));
    }

    @Test
    public void checkOpenFd() throws Exception {
        testReadSuccessAndFailureException(
                (res, filter) -> {
                    final AssetManager assets = res.getAssets();
                    try (AssetFileDescriptor fd = assets.openNonAssetFd(RES_DRAWABLE_PATH)) {
                        filter.stopSendingBlocks();
                        final ByteArrayOutputStream result = new ByteArrayOutputStream();
                        writeFullStream(fd.createInputStream(), result,
                                AssetFileDescriptor.UNKNOWN_LENGTH);
                        return true;
                    }
                }, equalTo(true), instanceOf(IOException.class));
    }

    private interface ThrowingFunction<R> {
        R apply(Resources res, BlockFilterController filter) throws Exception;
    }

    /**
     * Runs the test twice to test resource resolution when all necessary blocks are available and
     * when some necessary blocks are missing due to incremental installation.
     *
     * During the test of the failure path {@link TestBlockFilter#stopSendingBlocks()} will be
     * invoked when {@link BlockFilterController#stopSendingBlocks()} is invoked; preventing any
     * blocks that have not been served from being served during the test of the failure path.
     *
     * @param getValue executes resource resolution and returns a T object
     * @param checkSuccess the matcher that represents the value when all pages are available
     * @param checkFailure the matcher that represents the value when all pages are missing
     * @param <T> the expected return type of the resource resolution
     */
    private <T> void testReadSuccessAndFailure(ThrowingFunction<T> getValue,
            Matcher<? super T> checkSuccess, Matcher<? super T> checkFailure) throws Exception {
        try (ShellInstallSession session = startInstallSession()) {
            final T value = getValue.apply(session.getPackageResources(),
                    BlockFilterController.noop());
            MatcherAssert.assertThat(value, checkSuccess);
        }
        try (ShellInstallSession session = startInstallSession()) {
            final T value = getValue.apply(session.getPackageResources(),
                    BlockFilterController.allowDisable(session));
            MatcherAssert.assertThat(value, checkFailure);
        }
    }

    /**
     * Variant of {@link #testReadSuccessAndFailure(ThrowingFunction, Matcher, Matcher)} that
     * expects an exception to be thrown during the failure path.
     */
    private <T> void testReadSuccessAndFailureException(ThrowingFunction<T> getValue,
            Matcher<? super T> checkSuccess, Matcher<Throwable> checkFailure)
            throws Exception {
        try (ShellInstallSession session = startInstallSession()) {
            final T value = getValue.apply(session.getPackageResources(),
                    BlockFilterController.noop());
            MatcherAssert.assertThat(value, checkSuccess);
        }
        try (ShellInstallSession session = startInstallSession()) {
            MatcherUtils.assertThrows(checkFailure,
                    () -> getValue.apply(session.getPackageResources(),
                            BlockFilterController.allowDisable(session)));
        }
    }

    private static ShellInstallSession startInstallSession() throws IOException,
            InterruptedException {
        return startInstallSession(TEST_APKS, TEST_APP_PACKAGE);
    }

    private static ShellInstallSession startInstallSession(String[] apks, String packageName)
            throws IOException, InterruptedException {
        final String v4SignatureSuffix = ".idsig";
        final TestBlockFilter filter = new TestBlockFilter();
        final IncrementalInstallSession.Builder builder = new IncrementalInstallSession.Builder()
                .addExtraArgs("-t", "-i", getContext().getPackageName())
                .setLogger(new IncrementalDeviceConnection.Logger())
                .setBlockFilter(filter);
        for (final String apk : apks) {
            final String path = TEST_APK_PATH + apk;
            builder.addApk(Paths.get(path), Paths.get(path + v4SignatureSuffix));
        }

        final ShellInstallSession session = new ShellInstallSession(
                builder.build(), filter, packageName);
        session.session.start(Executors.newSingleThreadExecutor(),
                IncrementalDeviceConnection.Factory.reliable());
        session.session.waitForInstallCompleted(10, TimeUnit.SECONDS);
        assertTrue(isAppInstalled(packageName));
        return session;
    }

    private static class BlockFilterController {
        private final ShellInstallSession mSession;
        BlockFilterController(ShellInstallSession session) {
            mSession = session;
        }
        public static BlockFilterController allowDisable(ShellInstallSession session) {
            return new BlockFilterController(session);
        }
        public static BlockFilterController noop() {
            return new BlockFilterController(null);
        }
        public void stopSendingBlocks() {
            if (mSession != null) {
                mSession.stopSendingBlocks();
            }
        }
    }

    /**
     * A wrapper for {@link IncrementalInstallSession} that uninstalls the installed package when
     * testing is finished.
     */
    private static class ShellInstallSession implements AutoCloseable {
        public final IncrementalInstallSession session;
        private final TestBlockFilter mFilter;
        private final String mPackageName;
        private ShellInstallSession(IncrementalInstallSession session,
                TestBlockFilter filter, String packageName) {
            this.session = session;
            this.mFilter = filter;
            this.mPackageName = packageName;
            getUiAutomation().adoptShellPermissionIdentity();
        }

        public void stopSendingBlocks() {
            mFilter.stopSendingBlocks();
        }

        public Resources getPackageResources() throws PackageManager.NameNotFoundException {
            return getContext().createPackageContext(mPackageName, 0).getResources();
        }

        @Override
        public void close() throws IOException {
            session.close();
            getUiAutomation().dropShellPermissionIdentity();
            uninstallPackageSilently(mPackageName);
        }
    }

    private static class TestBlockFilter implements IBlockFilter {
        private final AtomicBoolean mDisabled = new AtomicBoolean();
        @Override
        public boolean shouldServeBlock(PendingBlock block) {
            return !mDisabled.get();
        }
        public void stopSendingBlocks() {
            mDisabled.set(true);
        }
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static UiAutomation getUiAutomation() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }
}
