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

package android.hdmicec.cts.playback;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assume.assumeTrue;

import android.hdmicec.cts.CecDevice;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.HdmiCecClientWrapper;
import android.hdmicec.cts.HdmiCecConstants;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.Test;

import java.util.List;

/**
 * HDMI CEC test to verify physical address after device reboot (Section 10.2.3)
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecStartupTest extends BaseHostJUnit4Test {

  private static final CecDevice PLAYBACK_DEVICE = CecDevice.PLAYBACK_1;
  private static final ImmutableList<CecMessage> necessaryMessages =
      new ImmutableList.Builder<CecMessage>()
          .add(CecMessage.REPORT_PHYSICAL_ADDRESS, CecMessage.CEC_VERSION,
              CecMessage.DEVICE_VENDOR_ID, CecMessage.GIVE_POWER_STATUS).build();
  private static final ImmutableList<CecMessage> permissibleMessages =
      new ImmutableList.Builder<CecMessage>()
          .add(CecMessage.VENDOR_COMMAND, CecMessage.GIVE_DEVICE_VENDOR_ID,
              CecMessage.SET_OSD_NAME, CecMessage.GIVE_OSD_NAME).build();

  @Rule
  public HdmiCecClientWrapper hdmiCecClient = new HdmiCecClientWrapper(CecDevice.PLAYBACK_1, this);

  /**
   * Tests that the device sends all the messages that should be sent on startup. It also ensures
   * that only the device only sends messages which are allowed by the spec.
   */
  @Test
  public void cectVerifyStartupMessages() throws Exception {
    ITestDevice device = getDevice();

    /* Make sure device is playback only. Not applicable to playback/audio combinations */
    String deviceTypeCsv = device.executeShellCommand("getprop ro.hdmi.device_type").trim();
    assumeTrue(deviceTypeCsv.equals(CecDevice.PLAYBACK_1.getDeviceType()));

    device.executeShellCommand("reboot");
    device.waitForBootComplete(HdmiCecConstants.REBOOT_TIMEOUT);
    /* Monitor CEC messages for 20s after reboot */
    final List<CecMessage> messagesReceived = hdmiCecClient.getAllMessages(CecDevice.PLAYBACK_1, 20);

    /* Predicate to apply on necessaryMessages to ensure that all necessaryMessages are received. */
    final Predicate<CecMessage> notReceived = new Predicate<CecMessage>() {
      @Override
      public boolean apply(@Nullable CecMessage cecMessage) {
        return !messagesReceived.contains(cecMessage);
      }
    };

    /* Predicate to apply on messagesReceived to ensure all messages received are permissible. */
    final Predicate<CecMessage> notAllowed = new Predicate<CecMessage>() {
      @Override
      public boolean apply(@Nullable CecMessage cecMessage) {
        return !(permissibleMessages.contains(cecMessage) || necessaryMessages.contains(cecMessage));
      }
    };

    assertWithMessage("Some necessary messages are missing").
        that(filter(necessaryMessages, notReceived)).isEmpty();

    assertWithMessage("Some non-permissible messages received").
        that(filter(messagesReceived, notAllowed)).isEmpty();
  }
}
