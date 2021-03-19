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
"""CameraITS test to check solid color test pattern generation."""

import logging
import os

from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils


_CH_TOL = 4E-3  # ~1/255 DN in [0:1]
_OFF = 0x00000000
_SAT = 0xFFFFFFFF
_NAME = os.path.basename(__file__).split('.')[0]
_SHARPNESS_TOL = 0.1
_NUM_FRAMES = 4  # buffer a few frames to eliminate need for PER_FRAME_CONTROL
# frozendict not used below as it requires import on host after port to AOSP
_BLACK = {'color': 'BLACK', 'RGGB': (_OFF, _OFF, _OFF, _OFF), 'RGB': (0, 0, 0)}
_WHITE = {'color': 'WHITE', 'RGGB': (_SAT, _SAT, _SAT, _SAT), 'RGB': (1, 1, 1)}
_RED = {'color': 'RED', 'RGGB': (_SAT, _OFF, _OFF, _OFF), 'RGB': (1, 0, 0)}
_GREEN = {'color': 'GREEN', 'RGGB': (_OFF, _SAT, _SAT, _OFF), 'RGB': (0, 1, 0)}
_BLUE = {'color': 'BLUE', 'RGGB': (_OFF, _OFF, _OFF, _SAT), 'RGB': (0, 0, 1)}
_COLORS_CHECKED_RGB = (_BLACK, _WHITE, _RED, _GREEN, _BLUE)
_COLORS_CHECKED_MONO = (_BLACK, _WHITE)
_COLORS_CHECKED_UPGRADE = (_BLACK,)
_FULL_CHECK_FIRST_API_LEVEL = 31
_SOLID_COLOR_TEST_PATTERN = 1


def check_solid_color(img, exp_values):
  """Checks solid color test pattern image matches expected values.

  Args:
    img: capture converted to RGB image
    exp_values: list of RGB [0:1] expected values
  """
  logging.debug('Checking solid test pattern w/ RGB values %s', str(exp_values))
  rgb_means = image_processing_utils.compute_image_means(img)
  logging.debug('Captured frame averages: %s', str(rgb_means))
  rgb_vars = image_processing_utils.compute_image_variances(img)
  logging.debug('Capture frame variances: %s', str(rgb_vars))
  if not np.allclose(rgb_means, exp_values, atol=_CH_TOL):
    raise AssertionError('Image not expected value. '
                         f'RGB means: {rgb_means}, expected: {exp_values}, '
                         f'ATOL: {_CH_TOL}')
  if not all(i < _CH_TOL for i in rgb_vars):
    raise AssertionError(f'Image has too much variance. '
                         f'RGB variances: {rgb_vars}, ATOL: {_CH_TOL}')


class SolidColorTestPattern(its_base_test.ItsBaseTest):
  """Solid Color test pattern generation test.

    Test: Capture frame for the SOLID_COLOR test pattern with the values set
    and check YUV image matches request.

    android.sensor.testPatternMode
    0: OFF
    1: SOLID_COLOR
    2: COLOR_BARS
    3: COLOR_BARS_FADE_TO_GREY
    4: PN9
  """

  def test_solid_color_test_pattern(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Determine patterns to check based on API level
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      if first_api_level >= _FULL_CHECK_FIRST_API_LEVEL:
        if camera_properties_utils.mono_camera(props):
          colors_checked = _COLORS_CHECKED_MONO
        else:
          colors_checked = _COLORS_CHECKED_RGB
      else:
        colors_checked = _COLORS_CHECKED_UPGRADE

      # Determine if test is run or skipped
      available_patterns = props['android.sensor.availableTestPatternModes']
      if cam.is_camera_privacy_mode_supported():
        if _SOLID_COLOR_TEST_PATTERN not in available_patterns:
          raise AssertionError(
              'SOLID_COLOR not in android.sensor.availableTestPatternModes.')
      else:
        camera_properties_utils.skip_unless(
            _SOLID_COLOR_TEST_PATTERN in available_patterns)

      # Take extra frames if no per-frame control
      if camera_properties_utils.per_frame_control(props):
        num_frames = 1
      else:
        num_frames = _NUM_FRAMES

      # Start checking patterns
      for color in colors_checked:
        logging.debug('Assigned RGGB values %s',
                      str([int(c/_SAT) for c in color['RGGB']]))
        req = capture_request_utils.auto_capture_request()
        req['android.sensor.testPatternMode'] = camera_properties_utils.SOLID_COLOR_TEST_PATTERN
        req['android.sensor.testPatternData'] = color['RGGB']
        fmt = {'format': 'yuv'}
        caps = cam.do_capture([req]*num_frames, fmt)
        cap = caps[-1]
        logging.debug('Capture metadata RGGB testPatternData: %s',
                      str(cap['metadata']['android.sensor.testPatternData']))
        # Save test pattern image
        img = image_processing_utils.convert_capture_to_rgb_image(
            cap, props=props)
        image_processing_utils.write_image(
            img, f'{os.path.join(self.log_path, _NAME)}.jpg', True)

        # Check solid pattern for correctness
        check_solid_color(img, color['RGB'])
        logging.debug('Solid color test pattern %s is a PASS', color['color'])


if __name__ == '__main__':
  test_runner.main()
