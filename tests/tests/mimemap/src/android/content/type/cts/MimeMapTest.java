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

package android.content.type.cts;

import android.content.type.cts.StockAndroidMimeMapFactory;

import org.junit.Before;
import org.junit.Test;

import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import libcore.net.MimeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests {@link MimeMap#getDefault()}.
 */
public class MimeMapTest {

    /** Stock Android's default MimeMap. */
    private MimeMap stockAndroidMimeMap;

    /** The platform's actual default MimeMap. */
    private MimeMap defaultMimeMap;

    @Before public void setUp() {
        defaultMimeMap = MimeMap.getDefault();
        // A copy of stock Android's MimeMap.getDefault() from when this test was built,
        // useful for comparing the platform's behavior vs. stock Android's.
        // The resources are placed into the testres/ path by the "mimemap-testing-res.jar" genrule.
        stockAndroidMimeMap = StockAndroidMimeMapFactory.create(
                s -> MimeMapTest.class.getResourceAsStream("/testres/" + s));
    }

    @Test
    public void defaultMap_15715370() {
        assertEquals("audio/flac", defaultMimeMap.guessMimeTypeFromExtension("flac"));
        assertEquals("flac", defaultMimeMap.guessExtensionFromMimeType("audio/flac"));
        assertEquals("flac", defaultMimeMap.guessExtensionFromMimeType("application/x-flac"));
    }

    // https://code.google.com/p/android/issues/detail?id=78909
    @Test public void defaultMap_78909() {
        assertEquals("mka", defaultMimeMap.guessExtensionFromMimeType("audio/x-matroska"));
        assertEquals("mkv", defaultMimeMap.guessExtensionFromMimeType("video/x-matroska"));
    }

    @Test public void defaultMap_16978217() {
        assertEquals("image/x-ms-bmp", defaultMimeMap.guessMimeTypeFromExtension("bmp"));
        assertEquals("image/x-icon", defaultMimeMap.guessMimeTypeFromExtension("ico"));
        assertEquals("video/mp2ts", defaultMimeMap.guessMimeTypeFromExtension("ts"));
    }

    @Test public void testCommon() {
        assertEquals("audio/mpeg", defaultMimeMap.guessMimeTypeFromExtension("mp3"));
        assertEquals("image/png", defaultMimeMap.guessMimeTypeFromExtension("png"));
        assertEquals("application/zip", defaultMimeMap.guessMimeTypeFromExtension("zip"));

        assertEquals("mp3", defaultMimeMap.guessExtensionFromMimeType("audio/mpeg"));
        assertEquals("png", defaultMimeMap.guessExtensionFromMimeType("image/png"));
        assertEquals("zip", defaultMimeMap.guessExtensionFromMimeType("application/zip"));
    }

    @Test public void defaultMap_18390752() {
        assertEquals("jpg", defaultMimeMap.guessExtensionFromMimeType("image/jpeg"));
    }

    @Test public void defaultMap_30207891() {
        assertTrue(defaultMimeMap.hasMimeType("IMAGE/PNG"));
        assertTrue(defaultMimeMap.hasMimeType("IMAGE/png"));
        assertFalse(defaultMimeMap.hasMimeType(""));
        assertEquals("png", defaultMimeMap.guessExtensionFromMimeType("IMAGE/PNG"));
        assertEquals("png", defaultMimeMap.guessExtensionFromMimeType("IMAGE/png"));
        assertNull(defaultMimeMap.guessMimeTypeFromExtension(""));
        assertNull(defaultMimeMap.guessMimeTypeFromExtension("doesnotexist"));
        assertTrue(defaultMimeMap.hasExtension("PNG"));
        assertTrue(defaultMimeMap.hasExtension("PnG"));
        assertFalse(defaultMimeMap.hasExtension(""));
        assertFalse(defaultMimeMap.hasExtension(".png"));
        assertEquals("image/png", defaultMimeMap.guessMimeTypeFromExtension("PNG"));
        assertEquals("image/png", defaultMimeMap.guessMimeTypeFromExtension("PnG"));
        assertNull(defaultMimeMap.guessMimeTypeFromExtension(".png"));
        assertNull(defaultMimeMap.guessMimeTypeFromExtension(""));
        assertNull(defaultMimeMap.guessExtensionFromMimeType("doesnotexist"));
    }

    @Test public void defaultMap_30793548() {
        assertEquals("video/3gpp", defaultMimeMap.guessMimeTypeFromExtension("3gpp"));
        assertEquals("video/3gpp", defaultMimeMap.guessMimeTypeFromExtension("3gp"));
        assertEquals("video/3gpp2", defaultMimeMap.guessMimeTypeFromExtension("3gpp2"));
        assertEquals("video/3gpp2", defaultMimeMap.guessMimeTypeFromExtension("3g2"));
    }

    @Test public void defaultMap_37167977() {
        // https://tools.ietf.org/html/rfc5334#section-10.1
        assertEquals("audio/ogg", defaultMimeMap.guessMimeTypeFromExtension("ogg"));
        assertEquals("audio/ogg", defaultMimeMap.guessMimeTypeFromExtension("oga"));
        assertEquals("audio/ogg", defaultMimeMap.guessMimeTypeFromExtension("spx"));
        assertEquals("video/ogg", defaultMimeMap.guessMimeTypeFromExtension("ogv"));
    }

    @Test public void defaultMap_70851634_mimeTypeFromExtension() {
        assertEquals("video/vnd.youtube.yt", defaultMimeMap.guessMimeTypeFromExtension("yt"));
    }

    @Test public void defaultMap_70851634_extensionFromMimeType() {
        assertEquals("yt", defaultMimeMap.guessExtensionFromMimeType("video/vnd.youtube.yt"));
        assertEquals("yt", defaultMimeMap.guessExtensionFromMimeType("application/vnd.youtube.yt"));
    }

    @Test public void defaultMap_112162449_audio() {
        // According to https://en.wikipedia.org/wiki/M3U#Internet_media_types
        // this is a giant mess, so we pick "audio/x-mpegurl" because a similar
        // playlist format uses "audio/x-scpls".
        assertMimeTypeFromExtension("audio/x-mpegurl", "m3u");
        assertMimeTypeFromExtension("audio/x-mpegurl", "m3u8");
        assertExtensionFromMimeType("m3u", "audio/x-mpegurl");

        assertExtensionFromMimeType("m4a", "audio/mp4");
        assertMimeTypeFromExtension("audio/mpeg", "m4a");

        assertBidirectional("audio/aac", "aac");
    }

    @Test public void defaultMap_112162449_video() {
        assertBidirectional("video/x-flv", "flv");
        assertBidirectional("video/quicktime", "mov");
        assertBidirectional("video/mpeg", "mpeg");
    }

    @Test public void defaultMap_112162449_image() {
        assertBidirectional("image/heif", "heif");
        assertBidirectional("image/heif-sequence", "heifs");
        assertBidirectional("image/heic", "heic");
        assertBidirectional("image/heic-sequence", "heics");
        assertMimeTypeFromExtension("image/heif", "hif");

        assertBidirectional("image/x-adobe-dng", "dng");
        assertBidirectional("image/x-photoshop", "psd");

        assertBidirectional("image/jp2", "jp2");
        assertMimeTypeFromExtension("image/jp2", "jpg2");
    }

    @Test public void defaultMap_120135571_audio() {
        assertMimeTypeFromExtension("audio/mpeg", "m4r");
    }

    @Test public void defaultMap_136096979_ota() {
        assertMimeTypeFromExtension("application/vnd.android.ota", "ota");
    }

    @Test public void defaultMap_wifiConfig_xml() {
        assertExtensionFromMimeType("xml", "application/x-wifi-config");
        assertMimeTypeFromExtension("text/xml", "xml");
    }

    // http://b/122734564
    @Test public void defaultMap_NonLowercaseMimeType() {
        // A mixed-case mimeType that appears in mime.types; we expect guessMimeTypeFromExtension()
        // to return it in lowercase because MimeMap considers lowercase to be the canonical form.
        String mimeType = "application/vnd.ms-word.document.macroEnabled.12".toLowerCase(Locale.US);
        assertBidirectional(mimeType, "docm");
    }

    // Check that the keys given for lookups in either direction are not case sensitive
    @Test public void defaultMap_CaseInsensitiveKeys() {
        String mimeType = defaultMimeMap.guessMimeTypeFromExtension("apk");
        assertNotNull(mimeType);

        assertEquals(mimeType, defaultMimeMap.guessMimeTypeFromExtension("APK"));
        assertEquals(mimeType, defaultMimeMap.guessMimeTypeFromExtension("aPk"));

        assertEquals("apk", defaultMimeMap.guessExtensionFromMimeType(mimeType));
        assertEquals("apk", defaultMimeMap.guessExtensionFromMimeType(
                mimeType.toUpperCase(Locale.US)));
        assertEquals("apk", defaultMimeMap.guessExtensionFromMimeType(
                mimeType.toLowerCase(Locale.US)));
    }

    @Test public void defaultMap_invalid_empty() {
        checkInvalidExtension("");
        checkInvalidMimeType("");
    }

    @Test public void defaultMap_invalid_null() {
        checkInvalidExtension(null);
        checkInvalidMimeType(null);
    }

    @Test public void defaultMap_invalid() {
        checkInvalidMimeType("invalid mime type");
        checkInvalidExtension("invalid extension");
    }

    @Test public void defaultMap_containsAllStockAndroidMappings_mimeToExt() {
        // The minimum expected mimeType -> extension mappings that should be present.
        TreeMap<String, String> expected = new TreeMap<>();
        // The extensions that these mimeTypes are actually mapped to.
        TreeMap<String, String> actual = new TreeMap<>();
        for (String mimeType : stockAndroidMimeMap.mimeTypes()) {
            expected.put(mimeType, stockAndroidMimeMap.guessExtensionFromMimeType(mimeType));
            actual.put(mimeType, defaultMimeMap.guessExtensionFromMimeType(mimeType));
        }
        assertEquals(expected, actual);
    }

    @Test public void defaultMap_containsAllExpectedMappings_extToMime() {
        // The minimum expected extension -> mimeType mappings that should be present.
        TreeMap<String, String> expected = new TreeMap<>();
        // The mimeTypes that these extensions are actually mapped to.
        TreeMap<String, String> actual = new TreeMap<>();
        for (String extension : stockAndroidMimeMap.extensions()) {
            expected.put(extension, stockAndroidMimeMap.guessMimeTypeFromExtension(extension));
            actual.put(extension, defaultMimeMap.guessMimeTypeFromExtension(extension));
        }
        assertEquals(expected, actual);
    }

    /**
     * Checks that MimeTypeMap and URLConnection.getFileNameMap()'s behavior is
     * consistent with MimeMap.getDefault(), i.e. that they are implemented on
     * top of MimeMap.getDefault().
     */
    @Test public void defaultMap_agreesWithPublicApi() {
        android.webkit.MimeTypeMap webkitMap = android.webkit.MimeTypeMap.getSingleton();
        FileNameMap urlConnectionMap = URLConnection.getFileNameMap();

        for (String extension : defaultMimeMap.extensions()) {
            String mimeType = defaultMimeMap.guessMimeTypeFromExtension(extension);
            Objects.requireNonNull(mimeType);
            assertEquals(mimeType, webkitMap.getMimeTypeFromExtension(extension));
            assertTrue(webkitMap.hasExtension(extension));

            // Extensions should never start with '.', make sure this is not the case ahead
            // of the subsequent check.
            assertFalse(extension.startsWith("."));
            // Relax this check for extensions that contain "." because of http://b/141880067
            if (!extension.contains(".")) {
                assertEquals(mimeType, urlConnectionMap.getContentTypeFor("filename." + extension));
            }
        }

        for (String mimeType : defaultMimeMap.mimeTypes()) {
            String extension = defaultMimeMap.guessExtensionFromMimeType(mimeType);
            Objects.requireNonNull(extension);
            assertEquals(extension, webkitMap.getExtensionFromMimeType(mimeType));
            assertTrue(webkitMap.hasMimeType(mimeType));
        }
    }

    private void checkInvalidExtension(String s) {
        assertFalse(defaultMimeMap.hasExtension(s));
        assertNull(defaultMimeMap.guessMimeTypeFromExtension(s));
    }

    private void checkInvalidMimeType(String s) {
        assertFalse(defaultMimeMap.hasMimeType(s));
        assertNull(defaultMimeMap.guessExtensionFromMimeType(s));
    }

    private void assertMimeTypeFromExtension(String mimeType, String extension) {
        final String actual = defaultMimeMap.guessMimeTypeFromExtension(extension);
        if (!Objects.equals(mimeType, actual)) {
            fail("Expected " + mimeType + " but was " + actual + " for extension " + extension);
        }
    }

    private void assertExtensionFromMimeType(String extension, String mimeType) {
        final String actual = defaultMimeMap.guessExtensionFromMimeType(mimeType);
        if (!Objects.equals(extension, actual)) {
            fail("Expected " + extension + " but was " + actual + " for type " + mimeType);
        }
    }

    private void assertBidirectional(String mimeType, String extension) {
        assertMimeTypeFromExtension(mimeType, extension);
        assertExtensionFromMimeType(extension, mimeType);
    }
}
