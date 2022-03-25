# Copyright 2022 The Android Open Source Project
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
"""Utility functions for processing video recordings.
"""
# Each item in this list corresponds to quality levels defined per
# CamcorderProfile. For Video ITS, we will currently test below qualities
# only if supported by the camera device.
_ITS_SUPPORTED_QUALITIES = (
    "HIGH",
    "2160P",
    "1080P",
    "720P",
    "480P",
    "CIF",
    "QCIF",
    "QVGA",
    "LOW",
    "VGA"
)


def create_test_format_list(qualities):
  """Returns the video quality levels to be tested.

  Args:
    qualities: List of all the quality levels supported by the camera device.
  Returns:
    test_qualities: Subset of test qualities to be tested from the
    supported qualities.
  """
  test_qualities = []
  for s in _ITS_SUPPORTED_QUALITIES:
    if s in qualities:
      test_qualities.append(s)
  return test_qualities
