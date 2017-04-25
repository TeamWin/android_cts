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

package android.provider.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.pm.Signature;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageInfo;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.fonts.FontRequest;
import android.graphics.fonts.FontVariationAxis;
import android.provider.FontsContract;
import android.provider.FontsContract.FontFamilyResult;
import android.provider.FontsContract.FontInfo;
import android.provider.FontsContract.Columns;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FontsContractTest {
    private static final String AUTHORITY = "android.provider.fonts.cts.font";
    private static final String PACKAGE = "android.provider.cts";

    private static long TIMEOUT_MILLIS = 1000;

    // Signature to be used for authentication to access content provider.
    // In this test case, the content provider and consumer live in the same package, self package's
    // signature works.
    private static List<List<byte[]>> SIGNATURE;
    static {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SIGNATURES);
            ArrayList<byte[]> out = new ArrayList<>();
            for (Signature sig : info.signatures) {
                out.add(sig.toByteArray());
            }
            SIGNATURE = new ArrayList<>();
            SIGNATURE.add(out);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void querySingleFont() throws NameNotFoundException {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FontRequest request = new FontRequest(AUTHORITY, PACKAGE, "singleFontFamily", SIGNATURE);
        FontFamilyResult result = FontsContract.fetchFonts(
                ctx, null /* cancellation signal */, request);
        assertNotNull(result);
        assertEquals(FontFamilyResult.STATUS_OK, result.getStatusCode());

        FontInfo[] fonts = result.getFonts();
        assertEquals(1, fonts.length);
        FontInfo font = fonts[0];
        assertNotNull(font.getUri());
        assertEquals(Columns.RESULT_CODE_OK, font.getResultCode());
        // TODO: add more test cases for FontInfo members once the MockFontProvider becomes
        // configurable.
        assertNotNull(FontsContract.buildTypeface(ctx, null /* cancellation signal */, fonts));
    }

    @Test
    public void queryMultipleFont() throws NameNotFoundException {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FontRequest request = new FontRequest(AUTHORITY, PACKAGE, "multipleFontFamily", SIGNATURE);
        FontFamilyResult result = FontsContract.fetchFonts(
                ctx, null /* cancellation signal */, request);
        assertNotNull(result);
        assertEquals(FontFamilyResult.STATUS_OK, result.getStatusCode());

        FontInfo[] fonts = result.getFonts();
        assertEquals(4, fonts.length);
        for (FontInfo font: fonts) {
            assertNotNull(font.getUri());
            assertEquals(Columns.RESULT_CODE_OK, font.getResultCode());
        }
        // TODO: add more test cases for FontInfo members once the MockFontProvider becomes
        // configuarable.
        assertNotNull(FontsContract.buildTypeface(ctx, null /* cancellation signal */, fonts));
    }

    @Test
    public void restrictContextRejection() throws NameNotFoundException {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context restrictedContext = ctx.createPackageContext(PACKAGE, Context.CONTEXT_RESTRICTED);

        FontRequest request = new FontRequest(AUTHORITY, PACKAGE, "singleFontFamily", SIGNATURE);

        // Rejected if restricted context is used.
        FontFamilyResult result = FontsContract.fetchFonts(
                restrictedContext, null /* cancellation signal */, request);
        assertEquals(FontFamilyResult.STATUS_REJECTED, result.getStatusCode());

        // Even if you have a result, buildTypeface should fail with restricted context.
        result = FontsContract.fetchFonts(ctx, null /* cancellation signal */, request);
        assertEquals(FontFamilyResult.STATUS_OK, result.getStatusCode());
        assertNull(FontsContract.buildTypeface(
                restrictedContext, null /* cancellation signal */, result.getFonts()));
    }

    // TODO: Add more test case.
}
