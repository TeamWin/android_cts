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

package android.graphics.fonts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.text.FontConfig;
import android.text.TextUtils;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FontManagerTest {

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private HashSet<String> getFallbackNameSet(FontConfig config) {
        HashSet<String> fallbackNames = new HashSet<>();
        List<FontConfig.FontFamily> families = config.getFontFamilies();
        assertThat(families).isNotEmpty();
        for (FontConfig.FontFamily family : families) {
            if (family.getName() != null) {
                fallbackNames.add(family.getName());
            }
        }
        return fallbackNames;
    }

    @Test
    public void fontManager_getFontConfig_checkFamilies() {
        FontManager fm = getContext().getSystemService(FontManager.class);
        assertThat(fm).isNotNull();

        FontConfig config = fm.getFontConfig();
        // To expect name availability, collect all fallback names.
        Set<String> fallbackNames = getFallbackNameSet(config);

        List<FontConfig.FontFamily> families = config.getFontFamilies();
        assertThat(families).isNotEmpty();

        for (FontConfig.FontFamily family : families) {
            assertThat(family.getFontList()).isNotEmpty();

            if (family.getName() != null) {
                assertThat(family.getName()).isNotEmpty();
            }

            assertThat(family.getLocaleList()).isNotNull();
            assertThat(family.getVariant()).isAtLeast(0);
            assertThat(family.getVariant()).isAtMost(2);

            List<FontConfig.Font> fonts = family.getFontList();
            for (FontConfig.Font font : fonts) {
                // Provided font files must be readable.
                assertThat(font.getFile().canRead()).isTrue();

                assertThat(font.getTtcIndex()).isAtLeast(0);
                assertThat(font.getFontVariationSettings()).isNotNull();
                assertThat(font.getStyle()).isNotNull();
                if (font.getFontFamilyName() != null) {
                    assertThat(font.getFontFamilyName()).isIn(fallbackNames);
                }
            }
        }
    }

    @Test
    public void fontManager_getFontConfig_checkAlias() {
        FontManager fm = getContext().getSystemService(FontManager.class);
        assertThat(fm).isNotNull();

        FontConfig config = fm.getFontConfig();
        assertThat(config).isNotNull();
        // To expect name availability, collect all fallback names.
        Set<String> fallbackNames = getFallbackNameSet(config);

        List<FontConfig.Alias> aliases = config.getAliases();
        assertThat(aliases).isNotEmpty();
        for (FontConfig.Alias alias : aliases) {
            assertThat(alias.getName()).isNotEmpty();
            assertThat(alias.getOriginal()).isNotEmpty();
            assertThat(alias.getWeight()).isAtLeast(0);
            assertThat(alias.getWeight()).isAtMost(1000);

            // The alias must be in the existing fallback names
            assertThat(alias.getOriginal()).isIn(fallbackNames);
        }
    }

    private List<String> readAll(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        ArrayList<String> out = new ArrayList<>();
        String line = "";
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (!TextUtils.isEmpty(trimmed)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private void assertSecurityException(String command) throws Exception {
        Process proc = Runtime.getRuntime().exec(new String[] { "cmd", "font", command });

        // The shell command must not success.
        assertThat(proc.waitFor()).isNotEqualTo(0);

        // In case of calling from unauthorized UID, must not output anything.
        assertThat(readAll(proc.getInputStream())).isEmpty();

        // Any shell command is not allowed. Output error message and exit immediately.
        List<String> errors = readAll(proc.getErrorStream());
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).isEqualTo("Only shell or root user can execute font command.");
    }

    @Test
    public void fontManager_shellCommandPermissionTest() throws Exception {
        assertSecurityException("");
        assertSecurityException("update");
        assertSecurityException("clear");
        assertSecurityException("status");
        assertSecurityException("random_string");
    }
}
