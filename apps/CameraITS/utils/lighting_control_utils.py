# Copyright 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Utility functions for sensor_fusion hardware rig."""


import logging
import struct
import time
import sensor_fusion_utils

# Constants for Arduino
ARDUINO_BRIGHTNESS_MAX = 255
ARDUINO_BRIGHTNESS_MIN = 0
ARDUINO_LIGHT_START_BYTE = 254


def set_light_brightness(ch, brightness, serial_port, delay=0):
  """Turn on light to specified brightness.

  Args:
    ch: str; light to turn on in ARDUINO_VALID_CH
    brightness: int value of brightness between 0 and 255.
    serial_port: object; serial port
    delay: int; time in seconds
  """
  if brightness < ARDUINO_BRIGHTNESS_MIN:
    logging.debug('Brightness must be >= %d.', ARDUINO_BRIGHTNESS_MIN)
    brightness = ARDUINO_BRIGHTNESS_MIN
  elif brightness > ARDUINO_BRIGHTNESS_MAX:
    logging.debug('Brightness must be <= %d.', ARDUINO_BRIGHTNESS_MAX)
    brightness = ARDUINO_BRIGHTNESS_MAX

  cmd = [struct.pack('B', i) for i in [
      ARDUINO_LIGHT_START_BYTE, int(ch), brightness]]
  sensor_fusion_utils.arduino_send_cmd(serial_port, cmd)
  time.sleep(delay)

