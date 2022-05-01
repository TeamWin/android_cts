/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.os.cts.companiontestapp;

import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.CompanionDeviceManager;
import android.content.IntentSender;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CDMController {

    private CompanionDeviceManager mCompanionDeviceManager;
    private ContextProvider mContextProvider;
    private AssociationCallback mAssociationCallback;

    private int associationId = -1;

    public CDMController(ContextProvider contextProvider) {
        mContextProvider = contextProvider;
        mCompanionDeviceManager =
                mContextProvider.getContext().getSystemService(CompanionDeviceManager.class);
        mAssociationCallback = new AssociationCallback();
    }

    public void associate() {
        mCompanionDeviceManager.associate(
                new AssociationRequest.Builder()
                        .setDisplayName("Test Device")
                        .build(), mAssociationCallback, null);
    }

    public void requestUserConsentForTransfer() {
        IntentSender intentSender = mCompanionDeviceManager.buildPermissionTransferUserConsentIntent(associationId);
        mContextProvider.processPermissionsSyncUserConsentIntentSender(intentSender);
    }

    public void beginDataTransfer() {
        mCompanionDeviceManager.startSystemDataTransfer(associationId);
    }

    public void disassociate() {
        Toast.makeText(mContextProvider.getContext(), "Disassociating...", Toast.LENGTH_SHORT).show();
        mCompanionDeviceManager.disassociate(associationId);
    }


    private class AssociationCallback extends CompanionDeviceManager.Callback {
        @Override
        public void onAssociationPending(@NonNull IntentSender intentSender) {
            Toast.makeText(mContextProvider.getContext(), "onAssociationPending", Toast.LENGTH_SHORT).show();
            mContextProvider.processAssociationIntentSender(intentSender);
        }

        @Override
        public void onAssociationCreated(@NonNull AssociationInfo associationInfo) {
            Toast.makeText(mContextProvider.getContext(), "onAssociationCreated: " + associationInfo.getId(), Toast.LENGTH_SHORT).show();
            associationId = associationInfo.getId();
            CommunicationManager.getInstance().onRemoteDeviceAssociated(associationInfo.getDeviceMacAddress());
        }

        @Override
        public void onFailure(@Nullable CharSequence error) {
            Toast.makeText(mContextProvider.getContext(), "onFailure: " + error, Toast.LENGTH_SHORT).show();
        }
    }
}
