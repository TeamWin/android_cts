# Lint as: python3
"""
Bluetooth multi devices tests.
"""
import sys
import os.path
import time
import re

import logging
logging.basicConfig(filename="/tmp/bluetooth_multi_devices_test_log.txt", level=logging.INFO)

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device

BLUETOOTH_MULTI_DEVICES_SNIPPET_PACKAGE = 'com.google.snippet.bluetooth'

GATT_PRIORITY_BALANCED = 0
GATT_PRIORITY_HIGH = 1
GATT_PRIORITY_LOW = 2
CONNECTION_PRIORITY_CCC = 3

class BluetoothMultiDevicesTest(base_test.BaseTestClass):

  def setup_class(self):
    # Declare that two Android devices are needed.
    self.client, self.server = self.register_controller(
      android_device, min_number=2)

    def setup_device(device):
      device.load_snippet('bluetooth_multi_devices_snippet', BLUETOOTH_MULTI_DEVICES_SNIPPET_PACKAGE)

    # Sets up devices in parallel to save time.
    utils.concurrent_exec(
        setup_device, ((self.client,), (self.server,)),
        max_workers=2,
        raise_on_exception=True)

  def test_gatt_connection_interval_test_case(self):

    if not self.server.bluetooth_multi_devices_snippet.isBluetoothOn() or\
        not self.client.bluetooth_multi_devices_snippet.isBluetoothOn():
      self.server.bluetooth_multi_devices_snippet.enableBluetooth()
      self.client.bluetooth_multi_devices_snippet.enableBluetooth()

      #TODO implement callback (b/266635827)
      time.sleep(3)

      asserts.assert_true(self.server.bluetooth_multi_devices_snippet.isBluetoothOn(), 'Server Bluetooth is OFF')
      asserts.assert_true(self.client.bluetooth_multi_devices_snippet.isBluetoothOn(), 'Client Bluetooth is OFF')


    # Start server, register callback, connect client, wait for connection
    self.server_name = self.server.bluetooth_multi_devices_snippet.createServer()
    asserts.assert_true(self.client.bluetooth_multi_devices_snippet.discoverServer(self.server_name), "Server not discovered");
    self.client.bluetooth_multi_devices_snippet.connectGatt(CONNECTION_PRIORITY_CCC)
    asserts.assert_true(self.server.bluetooth_multi_devices_snippet.waitConnection(), "Device not connected before timeout")

    used_interval = self.server.bluetooth_multi_devices_snippet.receivePriority()
    asserts.assert_true(used_interval is CONNECTION_PRIORITY_CCC, "Connection priority is not equal")

    self.client.bluetooth_multi_devices_snippet.updatePriority(GATT_PRIORITY_HIGH)
    used_interval = self.server.bluetooth_multi_devices_snippet.receivePriority()
    asserts.assert_true(used_interval is GATT_PRIORITY_HIGH, "Connection priority is not equal, expected: " + str(GATT_PRIORITY_HIGH) + " received: " + str(used_interval))

    self.client.bluetooth_multi_devices_snippet.disconnectGatt()
    self.client.bluetooth_multi_devices_snippet.clearClient()

    self.server.bluetooth_multi_devices_snippet.destroyServer()


if __name__ == '__main__':
  # Take test args
  index = sys.argv.index('--')
  sys.argv = sys.argv[:1] + sys.argv[index + 1:]
  test_runner.main()
