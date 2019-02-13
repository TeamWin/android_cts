/*
 * Copyright (C) 2009 The Android Open Source Project
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DocumentsContractTest {
    @Test
    public void testDocumentUri() {
        final String auth = "com.example";
        final String docId = "doc:12";

        final Uri uri = DocumentsContract.buildDocumentUri(auth, docId);
        assertEquals(auth, uri.getAuthority());
        assertEquals(docId, DocumentsContract.getDocumentId(uri));
        assertFalse(DocumentsContract.isTreeUri(uri));
    }

    @Test
    public void testTreeDocumentUri() {
        final String auth = "com.example";
        final String treeId = "doc:12";
        final String leafId = "doc:24";

        final Uri treeUri = DocumentsContract.buildTreeDocumentUri(auth, treeId);
        assertEquals(auth, treeUri.getAuthority());
        assertEquals(treeId, DocumentsContract.getTreeDocumentId(treeUri));
        assertTrue(DocumentsContract.isTreeUri(treeUri));

        final Uri leafUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, leafId);
        assertEquals(auth, leafUri.getAuthority());
        assertEquals(treeId, DocumentsContract.getTreeDocumentId(leafUri));
        assertEquals(leafId, DocumentsContract.getDocumentId(leafUri));
        assertTrue(DocumentsContract.isTreeUri(leafUri));
    }
}
