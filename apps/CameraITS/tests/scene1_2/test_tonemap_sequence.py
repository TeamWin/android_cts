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
"""Verifies shots with different tonemap curves."""


import logging
import os.path
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

_MAX_DELTA_SAME = 0.03  # match number in test_burst_sameness_manual
_MIN_DELTA_DIFF = 0.10
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_FRAMES = 3
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_RGB_G_CH = 1
_TMAP_NO_DELTA_FRAMES = list(range(_NUM_FRAMES-1)) + list(
    range(_NUM_FRAMES, 2*_NUM_FRAMES-1))


def do_captures_and_extract_means(cam, req, fmt, tonemap, log_path):
  """Do captures, save image and extract means from center patch.

  Args:
    cam: camera object.
    req: camera request.
    fmt: capture format.
    tonemap: string to determine 'linear' or 'default' tonemap.
    log_path: location to save images.

  Returns:
    appended means list.
  """
  green_means = []
  for i in range(_NUM_FRAMES):
    cap = cam.do_capture(req, fmt)
    img = image_processing_utils.convert_capture_to_rgb_image(cap)
    image_processing_utils.write_image(
        img, '%s_%s_%d.jpg' % (os.path.join(log_path, _NAME), tonemap, i))
    patch = image_processing_utils.get_image_patch(
        img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
    rgb_means = image_processing_utils.compute_image_means(patch)
    logging.debug('%s frame %d means: %s', tonemap, i, str(rgb_means))
    green_means.append(rgb_means[_RGB_G_CH])  # G, note python 2 version used R
  return green_means


class TonemapSequenceTest(its_base_test.ItsBaseTest):
  """Tests a sequence of shots with different tonemap curves.

  There should be _NUM_FRAMES with a linear tonemap followed by a second set of
  _NUM_FRAMES with the default tonemap.

  asserts the frames in each _NUM_FRAMES bunch are similar
  asserts the frames in the 2 _NUM_FRAMES bunches are different by >10%
  """

  def test_tonemap_sequence(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.manual_sensor(props) and
          camera_properties_utils.manual_post_proc(props) and
          camera_properties_utils.per_frame_control(props) and
          not camera_properties_utils.mono_camera(props))
      log_path = self.log_path

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      largest_yuv = capture_request_utils.get_largest_yuv_format(props)
      match_ar = (largest_yuv['width'], largest_yuv['height'])
      fmt = capture_request_utils.get_smallest_yuv_format(
          props, match_ar=match_ar)
      sens, exp, _, _, f_dist = cam.do_3a(do_af=True, get_results=True)
      means = []

      # linear tonemap req & captures
      req = capture_request_utils.manual_capture_request(
          sens, exp, f_dist, True, props)
      means.extend(do_captures_and_extract_means(
          cam, req, fmt, 'linear', log_path))

      # default tonemap req & captures
      req = capture_request_utils.manual_capture_request(
          sens, exp, f_dist, False)
      means.extend(do_captures_and_extract_means(
          cam, req, fmt, 'default', log_path))

      # Compute the delta between each consecutive frame pair
      deltas = [np.fabs(means[i+1]-means[i]) for i in range(2*_NUM_FRAMES-1)]
      logging.debug('Deltas between consecutive frames: %s', str(deltas))

      # assert frames similar with same tonemap
      if not all([deltas[i] < _MAX_DELTA_SAME for i in _TMAP_NO_DELTA_FRAMES]):
        raise AssertionError(
            f'deltas: {str(deltas)}, MAX_DELTA: {_MAX_DELTA_SAME}')

      # assert frames different with tonemap change
      if deltas[_NUM_FRAMES-1] <= _MIN_DELTA_DIFF:
        raise AssertionError(f'delta: {deltas[_NUM_FRAMES-1]:.5f}, '
                             f'THRESH: {_MIN_DELTA_DIFF}')

if __name__ == '__main__':
  test_runner.main()
