/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telephony.ims.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.os.Parcel;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.PresenceBuilder;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class RcsContactUceCapabilityTest {

    private static final Uri TEST_CONTACT = Uri.fromParts("sip", "me.test", null);

    @Test
    @Ignore("RCS APIs not public yet")
    public void createParcelUnparcel() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        final boolean isAudioCapable = true;
        final boolean isVideoCapable = true;
        final String serviceVersion = "1.0";
        final String serviceDescription = "service description test";

        // Create the test capability
        ServiceCapabilities.Builder servCapsBuilder = new ServiceCapabilities.Builder(
                isAudioCapable, isVideoCapable);
        servCapsBuilder.addSupportedDuplexMode(ServiceCapabilities.DUPLEX_MODE_FULL);

        RcsContactPresenceTuple.Builder tupleBuilder = new RcsContactPresenceTuple.Builder(
                RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN,
                RcsContactPresenceTuple.SERVICE_ID_MMTEL, serviceVersion);
        tupleBuilder.addContactUri(TEST_CONTACT)
                .addDescription(serviceDescription)
                .addServiceCapabilities(servCapsBuilder.build());

        PresenceBuilder presenceBuilder = new PresenceBuilder(TEST_CONTACT,
                RcsContactUceCapability.SOURCE_TYPE_CACHED,
                RcsContactUceCapability.REQUEST_RESULT_FOUND);
        presenceBuilder.addCapabilityTuple(tupleBuilder.build());

        RcsContactUceCapability testCapability = presenceBuilder.build();

        // parcel and unparcel
        Parcel infoParceled = Parcel.obtain();
        testCapability.writeToParcel(infoParceled, 0);
        infoParceled.setDataPosition(0);
        RcsContactUceCapability unparceledCapability =
                RcsContactUceCapability.CREATOR.createFromParcel(infoParceled);
        infoParceled.recycle();

        boolean unparceledVolteCapable = false;
        boolean unparceledVtCapable = false;
        String unparceledTupleStatus = RcsContactPresenceTuple.TUPLE_BASIC_STATUS_CLOSED;
        String unparceledDuplexMode = ServiceCapabilities.DUPLEX_MODE_RECEIVE_ONLY;

        RcsContactPresenceTuple unparceledTuple =
                unparceledCapability.getPresenceTuple(RcsContactPresenceTuple.SERVICE_ID_MMTEL);

        if (unparceledTuple != null) {
            unparceledTupleStatus = unparceledTuple.getStatus();

            ServiceCapabilities serviceCaps = unparceledTuple.getServiceCapabilities();
            if (serviceCaps != null) {
                unparceledVolteCapable = serviceCaps.isAudioCapable();
                unparceledVtCapable = serviceCaps.isVideoCapable();
                List<String> duplexModes = serviceCaps.getSupportedDuplexModes();
                if (duplexModes != null && !duplexModes.isEmpty()) {
                    unparceledDuplexMode = duplexModes.get(0);
                }
            }
        }

        assertEquals(TEST_CONTACT, unparceledCapability.getContactUri());
        assertTrue(unparceledVolteCapable);
        assertTrue(unparceledVtCapable);
        assertEquals(RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN, unparceledTupleStatus);
        assertEquals(ServiceCapabilities.DUPLEX_MODE_FULL, unparceledDuplexMode);
        assertEquals(RcsContactPresenceTuple.SERVICE_ID_MMTEL, unparceledTuple.getServiceId());
        assertEquals(serviceVersion, unparceledTuple.getServiceVersion());
        assertEquals(serviceDescription, unparceledTuple.getServiceDescription());
    }
}
