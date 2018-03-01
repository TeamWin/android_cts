/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.signature.cts.api;

import android.provider.Settings;
import android.signature.cts.DexApiDocumentParser;
import android.signature.cts.DexMemberChecker;
import android.signature.cts.FailureType;

public class KillswitchTest extends AbstractApiTest {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        DexMemberChecker.init();
        // precondition check: make sure global setting has been configured properly.
        // This should be done via an adb command, configured in AndroidTest.xml.
        assertEquals("Global setting " + Settings.Global.HIDDEN_API_BLACKLIST_EXEMPTIONS,
                "*",
                Settings.Global.getString(
                        getInstrumentation().getContext().getContentResolver(),
                        Settings.Global.HIDDEN_API_BLACKLIST_EXEMPTIONS));
    }

    private static final String ERROR_MESSAGE_APPENDIX =
            " when global setting hidden_api_blacklist_exemptions is set to *";

    public void testKillswitch() {
        // for now, just verify that we can access something *not* on the light grey or white list
        DexApiDocumentParser.DexMember member = new DexApiDocumentParser.DexField(
                "Ldalvik/system/VMRuntime;", "THE_ONE", "Ldalvik/system/VMRuntime;");
        runWithTestResultObserver(resultObserver ->
                DexMemberChecker.checkSingleMember(member, new DexMemberChecker.Observer() {
                    @Override
                    public void classAccessible(boolean accessible,
                            DexApiDocumentParser.DexMember member) {
                        if (!accessible) {
                            resultObserver.notifyFailure(
                                    FailureType.MISSING_CLASS,
                                    member.toString(),
                                    "Class from boot classpath is not accessible"
                                            + ERROR_MESSAGE_APPENDIX);
                        }
                    }

                    @Override
                    public void fieldAccessibleViaReflection(boolean accessible,
                            DexApiDocumentParser.DexField field) {
                        if (!accessible) {
                            resultObserver.notifyFailure(
                                    FailureType.MISSING_FIELD,
                                    field.toString(),
                                    "Field from boot classpath is not accessible via reflection"
                                            + ERROR_MESSAGE_APPENDIX);
                        }
                    }

                    @Override
                    public void fieldAccessibleViaJni(boolean accessible,
                            DexApiDocumentParser.DexField field) {
                        if (!accessible) {
                            resultObserver.notifyFailure(
                                    FailureType.MISSING_FIELD,
                                    field.toString(),
                                    "Field from boot classpath is not accessible via JNI"
                                            + ERROR_MESSAGE_APPENDIX);
                        }
                    }

                    @Override
                    public void methodAccessibleViaReflection(boolean accessible,
                            DexApiDocumentParser.DexMethod method) {
                        if (!accessible) {
                            resultObserver.notifyFailure(
                                    FailureType.MISSING_METHOD,
                                    method.toString(),
                                    "Method from boot classpath is not accessible via reflection"
                                            + ERROR_MESSAGE_APPENDIX);
                        }
                    }

                    @Override
                    public void methodAccessibleViaJni(boolean accessible,
                            DexApiDocumentParser.DexMethod method) {
                        if (!accessible) {
                            resultObserver.notifyFailure(
                                    FailureType.MISSING_METHOD,
                                    method.toString(),
                                    "Method from boot classpath is not accessible via JNI"
                                            + ERROR_MESSAGE_APPENDIX);
                        }
                    }

                }));
    }
}
