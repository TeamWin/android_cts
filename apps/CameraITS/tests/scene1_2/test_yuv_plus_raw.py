# Copyright 2014 The Android Open Source Project
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
"""Verifies RAW and YUV images are similar."""


import logging
import os.path
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

MAX_IMG_SIZE = (1920, 1080)
NAME = os.path.splitext(os.path.basename(__file__))[0]
PATCH_H = 0.1  # center 10%
PATCH_W = 0.1
PATCH_X = 0.5 - PATCH_W/2
PATCH_Y = 0.5 - PATCH_H/2
THRESHOLD_MAX_RMS_DIFF = 0.035


def convert_and_compare_captures(cap_raw, cap_yuv, props,
                                 log_path_with_name, raw_fmt):
  """Helper function to convert and compare RAW and YUV captures.

  Args:
   cap_raw: capture request object with RAW/RAW10/RAW12 format specified
   cap_yuv: capture capture request object with YUV format specified
   props: object from its_session_utils.get_camera_properties().
   log_path_with_name: logging path where artifacts should be stored.
   raw_fmt: string 'raw', 'raw10', or 'raw12' to include in file name
  Returns:
    string "PASS" if test passed, else message for AssertionError.
  """
  shading_mode = cap_raw['metadata']['android.shading.mode']
  control_af_mode = cap_raw['metadata']['android.control.afMode']
  focus_distance = cap_raw['metadata']['android.lens.focusDistance']
  logging.debug('%s capture AF mode: %s', raw_fmt, control_af_mode)
  logging.debug('%s capture focus distance: %s', raw_fmt, focus_distance)
  logging.debug('%s capture shading mode: %d', raw_fmt, shading_mode)

  img = image_processing_utils.convert_capture_to_rgb_image(cap_yuv)
  image_processing_utils.write_image(img, '%s_shading=%d_yuv.jpg' % (
      log_path_with_name, shading_mode), True)
  patch = image_processing_utils.get_image_patch(
      img, PATCH_X, PATCH_Y, PATCH_W, PATCH_H)
  rgb_means_yuv = image_processing_utils.compute_image_means(patch)

  # RAW shots are 1/2 x 1/2 smaller after conversion to RGB, but patch
  # cropping is relative.
  img = image_processing_utils.convert_capture_to_rgb_image(
      cap_raw, props=props)
  image_processing_utils.write_image(img, '%s_shading=%d_%s.jpg' % (
      log_path_with_name, shading_mode, raw_fmt), True)
  patch = image_processing_utils.get_image_patch(
      img, PATCH_X, PATCH_Y, PATCH_W, PATCH_H)
  rgb_means_raw = image_processing_utils.compute_image_means(patch)

  rms_diff = image_processing_utils.compute_image_rms_difference_1d(
      rgb_means_yuv, rgb_means_raw)
  msg = f'{raw_fmt} diff: {rms_diff:.4f}'
  logging.debug('%s', msg)
  if rms_diff >= THRESHOLD_MAX_RMS_DIFF:
    return f'{msg}, spec: {THRESHOLD_MAX_RMS_DIFF}'
  else:
    return 'PASS'


class YuvPlusRawTest(its_base_test.ItsBaseTest):
  """Test capturing a single frame as both YUV and various RAW formats.

  Tests RAW, RAW10 and RAW12 as available.
  """

  def test_yuv_plus_raw(self):
    failure_messages = []
    logging.debug('Starting %s', NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = os.path.join(self.log_path, NAME)

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.raw_output(props) and
          not camera_properties_utils.mono_camera(props))

      # Load chart for scene
      # TODO(jpliu): change to capture_request_utils.CHART_DISTANCE_NO_SCALING
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # determine compatible RAW formats
      raw_formats = []
      if camera_properties_utils.raw16(props):
        raw_formats.append('raw')
      else:
        logging.debug('Skipping test_yuv_plus_raw')
      if camera_properties_utils.raw10(props):
        raw_formats.append('raw10')
      else:
        logging.debug('Skipping test_yuv_plus_raw10')
      if camera_properties_utils.raw12(props):
        raw_formats.append('raw12')
      else:
        logging.debug('Skipping test_yuv_plus_raw12')

      for raw_fmt in raw_formats:
        req = capture_request_utils.auto_capture_request(
            linear_tonemap=True, props=props, do_af=False)
        max_raw_size = capture_request_utils.get_available_output_sizes(
            raw_fmt, props)[0]
        if capture_request_utils.is_common_aspect_ratio(max_raw_size):
          w, h = capture_request_utils.get_available_output_sizes(
              'yuv', props, MAX_IMG_SIZE, max_raw_size)[0]
        else:
          w, h = capture_request_utils.get_available_output_sizes(
              'yuv', props, max_size=MAX_IMG_SIZE)[0]
        out_surfaces = [{'format': raw_fmt},
                        {'format': 'yuv', 'width': w, 'height': h}]
        cap_raw, cap_yuv = cam.do_capture(req, out_surfaces)
        msg = convert_and_compare_captures(cap_raw, cap_yuv, props,
                                           log_path, raw_fmt)
        if msg != 'PASS':
          failure_messages.append(msg)

      if failure_messages:
        raise AssertionError('\n'.join(failure_messages))


if __name__ == '__main__':
  test_runner.main()

