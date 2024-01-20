# Copyright 2024 The Android Open Source Project
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
"""Tests for imu_processing_utils."""

import unittest

import imu_processing_utils


class ImuProcessingUtilsTest(unittest.TestCase):
  """Unit tests for this module."""

  def test_calc_rv_drift(self):
    """Unit test for rotation vector drift calculation method."""
    # Create test vectors
    a = [-1, 0, 1, -2]
    b = [-179, 180, 179, -180, -178]
    c = [179, 180, -179, -180, 178]

    a_drift = [0, 1, 2, -1]
    b_drift = [0, -1, -2, -1, 1]
    c_drift = [0, 1, 2, 1, -1]

    # Check drift
    self.assertEqual(imu_processing_utils.calc_rv_drift(a), a_drift,
                     'a_drift is incorrect')
    self.assertEqual(imu_processing_utils.calc_rv_drift(b), b_drift,
                     'b_drift is incorrect')


if __name__ == '__main__':
  unittest.main()
