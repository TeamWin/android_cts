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

package android.view.textclassifier.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.LocaleList;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextSelection;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * TextClassifier value objects tests.
 *
 * <p>Contains unit tests for value objects passed to/from TextClassifier APIs.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextClassifierValueObjectsTest {

    private static final double ACCEPTED_DELTA = 0.0000001;
    private static final String TEXT = "abcdefghijklmnopqrstuvwxyz";
    private static final int START = 5;
    private static final int END = 20;
    private static final String SIGNATURE = "sig.na-ture";
    private static final LocaleList LOCALES = LocaleList.forLanguageTags("fr,en,de,es");

    @Test
    public void testTextSelection() {
        final float addressScore = 0.1f;
        final float emailScore = 0.9f;

        final TextSelection selection = new TextSelection.Builder(START, END)
                .setEntityType(TextClassifier.TYPE_ADDRESS, addressScore)
                .setEntityType(TextClassifier.TYPE_EMAIL, emailScore)
                .setSignature(SIGNATURE)
                .build();

        assertEquals(START, selection.getSelectionStartIndex());
        assertEquals(END, selection.getSelectionEndIndex());
        assertEquals(2, selection.getEntityCount());
        assertEquals(TextClassifier.TYPE_EMAIL, selection.getEntity(0));
        assertEquals(TextClassifier.TYPE_ADDRESS, selection.getEntity(1));
        assertEquals(addressScore, selection.getConfidenceScore(TextClassifier.TYPE_ADDRESS),
                ACCEPTED_DELTA);
        assertEquals(emailScore, selection.getConfidenceScore(TextClassifier.TYPE_EMAIL),
                ACCEPTED_DELTA);
        assertEquals(0, selection.getConfidenceScore("random_type"), ACCEPTED_DELTA);
        assertEquals(SIGNATURE, selection.getSignature());
    }

    @Test
    public void testTextSelection_differentParams() {
        final int start = 0;
        final int end = 1;
        final float confidenceScore = 0.5f;
        final String signature = "2hukwu3m3k44f1gb0";

        final TextSelection selection = new TextSelection.Builder(start, end)
                .setEntityType(TextClassifier.TYPE_URL, confidenceScore)
                .setSignature(signature)
                .build();

        assertEquals(start, selection.getSelectionStartIndex());
        assertEquals(end, selection.getSelectionEndIndex());
        assertEquals(1, selection.getEntityCount());
        assertEquals(TextClassifier.TYPE_URL, selection.getEntity(0));
        assertEquals(confidenceScore, selection.getConfidenceScore(TextClassifier.TYPE_URL),
                ACCEPTED_DELTA);
        assertEquals(0, selection.getConfidenceScore("random_type"), ACCEPTED_DELTA);
        assertEquals(signature, selection.getSignature());
    }

    @Test
    public void testTextSelection_defaultValues() {
        TextSelection selection = new TextSelection.Builder(START, END).build();
        assertEquals(0, selection.getEntityCount());
        assertEquals("", selection.getSignature());
    }

    @Test
    public void testTextSelection_prunedConfidenceScore() {
        final float phoneScore = -0.1f;
        final float prunedPhoneScore = 0f;
        final float otherScore = 1.5f;
        final float prunedOtherScore = 1.0f;

        final TextSelection selection = new TextSelection.Builder(START, END)
                .setEntityType(TextClassifier.TYPE_PHONE, phoneScore)
                .setEntityType(TextClassifier.TYPE_OTHER, otherScore)
                .build();

        assertEquals(prunedPhoneScore, selection.getConfidenceScore(TextClassifier.TYPE_PHONE),
                ACCEPTED_DELTA);
        assertEquals(prunedOtherScore, selection.getConfidenceScore(TextClassifier.TYPE_OTHER),
                ACCEPTED_DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTextSelection_invalidStartParams() {
        new TextSelection.Builder(-1 /* start */, END)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTextSelection_invalidEndParams() {
        new TextSelection.Builder(START, 0 /* end */)
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void testTextSelection_invalidSignature() {
        new TextSelection.Builder(START, END)
                .setSignature(null)
                .build();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testTextSelection_entityIndexOutOfBounds() {
        final TextSelection selection = new TextSelection.Builder(START, END).build();
        final int outOfBoundsIndex = selection.getEntityCount();
        selection.getEntity(outOfBoundsIndex);
    }

    @Test
    public void testTextSelectionOptions() {
        final TextSelection.Options options = new TextSelection.Options()
                .setDefaultLocales(LOCALES);
        assertEquals(LOCALES, options.getDefaultLocales());
    }

    @Test
    public void testTextSelectionOptions_nullValues() {
        final TextSelection.Options options = new TextSelection.Options()
                .setDefaultLocales(null);
        assertNull(options.getDefaultLocales());
    }

    @Test
    public void testTextSelectionOptions_defaultValues() {
        final TextSelection.Options options = new TextSelection.Options();
        assertNull(options.getDefaultLocales());
    }

    @Test
    public void testTextClassification() {
        final float addressScore = 0.1f;
        final float emailScore = 0.9f;
        final Intent intent = new Intent();
        final String label = "label";
        final Drawable icon = new ColorDrawable(Color.RED);
        final View.OnClickListener onClick = v -> { };
        final Intent intent1 = new Intent();
        final String label1 = "label1";
        final Drawable icon1 = new ColorDrawable(Color.GREEN);
        final View.OnClickListener onClick1 = v -> { };
        final Intent intent2 = new Intent();
        final String label2 = "label2";
        final Drawable icon2 = new ColorDrawable(Color.BLUE);
        final View.OnClickListener onClick2 = v -> { };

        final TextClassification classification = new TextClassification.Builder()
                .setText(TEXT)
                .setEntityType(TextClassifier.TYPE_ADDRESS, addressScore)
                .setEntityType(TextClassifier.TYPE_EMAIL, emailScore)
                .setPrimaryAction(intent, label, icon, onClick)
                .addSecondaryAction(intent1, label1, icon1, onClick1)
                .addSecondaryAction(intent2, label2, icon2, onClick2)
                .setSignature(SIGNATURE)
                .build();

        assertEquals(TEXT, classification.getText());
        assertEquals(2, classification.getEntityCount());
        assertEquals(TextClassifier.TYPE_EMAIL, classification.getEntity(0));
        assertEquals(TextClassifier.TYPE_ADDRESS, classification.getEntity(1));
        assertEquals(addressScore, classification.getConfidenceScore(TextClassifier.TYPE_ADDRESS),
                ACCEPTED_DELTA);
        assertEquals(emailScore, classification.getConfidenceScore(TextClassifier.TYPE_EMAIL),
                ACCEPTED_DELTA);
        assertEquals(0, classification.getConfidenceScore("random_type"), ACCEPTED_DELTA);

        assertEquals(intent, classification.getIntent());
        assertEquals(label, classification.getLabel());
        assertEquals(icon, classification.getIcon());
        assertEquals(onClick, classification.getOnClickListener());

        assertEquals(2, classification.getSecondaryActionsCount());
        assertEquals(intent1, classification.getSecondaryIntent(0));
        assertEquals(label1, classification.getSecondaryLabel(0));
        assertEquals(icon1, classification.getSecondaryIcon(0));
        assertEquals(onClick1, classification.getSecondaryOnClickListener(0));
        assertEquals(intent2, classification.getSecondaryIntent(1));
        assertEquals(label2, classification.getSecondaryLabel(1));
        assertEquals(icon2, classification.getSecondaryIcon(1));
        assertEquals(onClick2, classification.getSecondaryOnClickListener(1));

        assertEquals(SIGNATURE, classification.getSignature());
    }

    @Test
    public void testTextClassification_defaultValues() {
        final TextClassification classification = new TextClassification.Builder().build();

        assertEquals(null, classification.getText());
        assertEquals(0, classification.getEntityCount());
        assertEquals(null, classification.getIntent());
        assertEquals(null, classification.getLabel());
        assertEquals(null, classification.getIcon());
        assertEquals(null, classification.getOnClickListener());
        assertEquals(0, classification.getSecondaryActionsCount());
        assertEquals("", classification.getSignature());
    }

    @Test
    public void testTextClassificationOptions() {
        final TextClassification.Options options = new TextClassification.Options()
                .setDefaultLocales(LOCALES);
        assertEquals(LOCALES, options.getDefaultLocales());
    }

    @Test
    public void testTextClassificationOptions_nullValues() {
        final TextClassification.Options options = new TextClassification.Options()
                .setDefaultLocales(null);
        assertNull(options.getDefaultLocales());
    }

    @Test
    public void testTextClassificationOptions_defaultValues() {
        final TextClassification.Options options = new TextClassification.Options();
        assertNull(options.getDefaultLocales());
    }

    // TODO: Add more tests.
}
