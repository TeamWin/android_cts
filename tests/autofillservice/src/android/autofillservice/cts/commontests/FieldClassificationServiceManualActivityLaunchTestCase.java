/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.autofillservice.cts.commontests;

import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InstrumentedFieldClassificationService;

import org.junit.Before;

public abstract class FieldClassificationServiceManualActivityLaunchTestCase extends
        AutoFillServiceTestCase.ManualActivityLaunch {

    private static final String TAG = "FieldClassificationServiceManualActivityLaunchTestCase";

    protected static InstrumentedFieldClassificationService.Replier sClassificationReplier;

    private InstrumentedFieldClassificationService.ServiceWatcher mServiceWatcher;

    @Before
    public void setFixtures() throws Exception {
        sClassificationReplier = InstrumentedFieldClassificationService.getReplier();
        sClassificationReplier.reset();

        // Rest service
        Helper.resetAutofillDetectionService();
        if (mServiceWatcher != null) {
            mServiceWatcher.waitOnDisconnected();
            mServiceWatcher = null;
        }
    }

    protected InstrumentedFieldClassificationService enablePccDetectionService()
            throws InterruptedException {
        mServiceWatcher = InstrumentedFieldClassificationService.setServiceWatcher();
        Helper.setAutofillDetectionService(InstrumentedFieldClassificationService.SERVICE_NAME);
        InstrumentedFieldClassificationService service = mServiceWatcher.waitOnConnected();
        service.waitUntilConnected();
        return service;
    }
}
