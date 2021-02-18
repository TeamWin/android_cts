# Copyright 2016 The Android Open Source Project
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


import logging
import os
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import cv2_image_processing_utils
import image_processing_utils
import its_session_utils


FRAME_ATOL_MS = 10
NAME = os.path.splitext(os.path.basename(__file__))[0]
NUM_FRAMES_PER_FD = 12
POSITION_RTOL = 0.10  # 10%
SHARPNESS_RTOL = 0.10  # 10%
VGA_WIDTH, VGA_HEIGHT = 640, 480


def take_caps_and_determine_sharpness(
    cam, props, fmt, gain, exp, af_fd, chart, log_path):
  """Return fd, sharpness, lens state of the output images.

  Args:
    cam: An open device session.
    props: Properties of cam
    fmt: dict; capture format
    gain: Sensitivity for the request as defined in android.sensor.sensitivity
    exp: Exposure time for the request as defined in
         android.sensor.exposureTime
    af_fd: Focus distance for the request as defined in
           android.lens.focusDistance
    chart: Object that contains chart information
    log_path: log_path to save the captured image

  Returns:
    Object containing reported sharpness of the output image, keyed by
    the following string:
        'sharpness'
  """

  # initialize variables and take data sets
  data_set = {}
  white_level = int(props['android.sensor.info.whiteLevel'])
  min_fd = props['android.lens.info.minimumFocusDistance']
  fds = [af_fd, min_fd]
  fds = sorted(fds * NUM_FRAMES_PER_FD)
  reqs = []
  for i, fd in enumerate(fds):
    reqs.append(capture_request_utils.manual_capture_request(gain, exp))
    reqs[i]['android.lens.focusDistance'] = fd
  caps = cam.do_capture(reqs, fmt)
  for i, cap in enumerate(caps):
    data = {'fd': fds[i]}
    data['loc'] = cap['metadata']['android.lens.focusDistance']
    data['lens_moving'] = (cap['metadata']['android.lens.state']
                           == 1)
    timestamp = cap['metadata']['android.sensor.timestamp'] * 1E-6
    if i == 0:
      timestamp_init = timestamp
    timestamp -= timestamp_init
    data['timestamp'] = timestamp
    y, _, _ = image_processing_utils.convert_capture_to_planes(cap, props)
    chart.img = image_processing_utils.normalize_img(
        image_processing_utils.get_image_patch(
            y, chart.xnorm, chart.ynorm, chart.wnorm, chart.hnorm))
    img_name = '%s_i=%d.jpg' % (os.path.join(log_path, NAME), i)
    image_processing_utils.write_image(chart.img, img_name)
    data['sharpness'] = (
        white_level * image_processing_utils.compute_image_sharpness(chart.img))
    data_set[i] = data
  return data_set


class LensMovementReportingTest(its_base_test.ItsBaseTest):
  """Test if focus distance is properly reported.

  Do unit step of focus distance and check sharpness correlates.
  """

  def test_lens_movement_reporting(self):
    logging.debug('Starting %s', NAME)

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      chart_loc_arg = self.chart_loc_arg
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Check skip conditions
      camera_properties_utils.skip_unless(
          not camera_properties_utils.fixed_focus(props) and
          camera_properties_utils.read_3a(props) and
          camera_properties_utils.lens_approx_calibrated(props))

      # Calculate camera_fov and load scaled image on tablet.
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Initialize chart class and locate chart in scene
      chart = cv2_image_processing_utils.Chart(
          cam, props, self.log_path, chart_loc=chart_loc_arg)

      # Get proper sensitivity, exposure time, and focus distance with 3A.
      mono_camera = camera_properties_utils.mono_camera(props)
      s, e, _, _, fd = cam.do_3a(get_results=True, mono_camera=mono_camera)

      # Get sharpness for each focal distance
      fmt = {'format': 'yuv', 'width': VGA_WIDTH, 'height': VGA_HEIGHT}
      d = take_caps_and_determine_sharpness(
          cam, props, fmt, s, e, fd, chart, self.log_path)
      for k in sorted(d):
        logging.debug(
            'i: %d\tfd: %.3f\tlens location (diopters): %.3f \t'
            'sharpness: %.1f  \tlens_moving: %r \t'
            'timestamp: %.1fms', k, d[k]['fd'], d[k]['loc'], d[k]['sharpness'],
            d[k]['lens_moving'], d[k]['timestamp'])

      # Assert frames are consecutive
      frame_diffs = np.gradient([v['timestamp'] for v in d.values()])
      delta_diffs = np.amax(frame_diffs) - np.amin(frame_diffs)
      e_msg = 'Timestamp gradient(ms): %.1f, ATOL: %.f' % (
          delta_diffs, FRAME_ATOL_MS)
      assert np.isclose(delta_diffs, 0, atol=FRAME_ATOL_MS), e_msg

      # Remove data when lens is moving
      for k in sorted(d):
        if d[k]['lens_moving']:
          del d[k]

      # Split data into min_fd and af data for processing
      d_min_fd = {}
      d_af_fd = {}
      for k in sorted(d):
        if d[k]['fd'] == props['android.lens.info.minimumFocusDistance']:
          d_min_fd[k] = d[k]
        if d[k]['fd'] == fd:
          d_af_fd[k] = d[k]

      logging.debug('Assert reported locs are close for af_fd captures')
      min_loc = min([v['loc'] for v in d_af_fd.values()])
      max_loc = max([v['loc'] for v in d_af_fd.values()])
      e_msg = 'af_fd[loc] min: %.3f, max: %.3f, RTOL: %.2f' % (
          min_loc, max_loc, POSITION_RTOL)
      assert np.isclose(min_loc, max_loc, rtol=POSITION_RTOL), e_msg

      logging.debug('Assert reported sharpness is close at af_fd')
      min_sharp = min([v['sharpness'] for v in d_af_fd.values()])
      max_sharp = max([v['sharpness'] for v in d_af_fd.values()])
      e_msg = 'af_fd[sharpness] min: %.3f, max: %.3f, RTOL: %.2f' % (
          min_sharp, max_sharp, SHARPNESS_RTOL)
      assert np.isclose(min_sharp, max_sharp, rtol=SHARPNESS_RTOL), e_msg

      logging.debug('Assert reported loc is close to assign loc for af_fd')
      first_key = min(d_af_fd.keys())  # find 1st non-moving frame
      loc = d_af_fd[first_key]['loc']
      fd = d_af_fd[first_key]['fd']
      e_msg = 'af_fd[loc]: %.3f, af_fd[fd]: %.3f, RTOL: %.2f' % (
          loc, fd, POSITION_RTOL)
      assert np.isclose(loc, fd, rtol=POSITION_RTOL), e_msg

      logging.debug('Assert reported locs are close for min_fd captures')
      min_loc = min([v['loc'] for v in d_min_fd.values()])
      max_loc = max([v['loc'] for v in d_min_fd.values()])
      e_msg = 'min_fd[loc] min: %.3f, max: %.3f, RTOL: %.2f' % (
          min_loc, max_loc, POSITION_RTOL)
      assert np.isclose(min_loc, max_loc, rtol=POSITION_RTOL), e_msg

      logging.debug('Assert reported sharpness is close at min_fd')
      min_sharp = min([v['sharpness'] for v in d_min_fd.values()])
      max_sharp = max([v['sharpness'] for v in d_min_fd.values()])
      e_msg = 'min_fd[sharpness] min: %.3f, max: %.3f, RTOL: %.2f' % (
          min_sharp, max_sharp, SHARPNESS_RTOL)
      assert np.isclose(min_sharp, max_sharp, rtol=SHARPNESS_RTOL), e_msg

      logging.debug('Assert reported loc is close to assigned loc for min_fd')
      last_key = 2 * NUM_FRAMES_PER_FD - 1
      loc = d_min_fd[last_key]['loc']
      fd = d_min_fd[last_key]['fd']
      e_msg = 'min_fd[loc]: %.3f, min_fd[fd]: %.3f, RTOL: %.2f' % (
          loc, fd, POSITION_RTOL)
      assert np.isclose(loc, fd, rtol=POSITION_RTOL), e_msg

if __name__ == '__main__':
  test_runner.main()
