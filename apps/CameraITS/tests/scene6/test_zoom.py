# Copyright 2020 The Android Open Source Project
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
"""Verify zoom ratio scales circle sizes correctly."""


import logging
import math
import os.path

import cv2
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import opencv_processing_utils

_CIRCLE_COLOR = 0  # [0: black, 255: white]
_CIRCLE_AR_RTOL = 0.15  # contour width vs height (aspect ratio)
_CIRCLISH_RTOL = 0.05  # contour area vs ideal circle area pi*((w+h)/4)**2
_JPEG_STR = 'jpg'
_MIN_AREA_RATIO = 0.00015  # based on 2000/(4000x3000) pixels
_MIN_CIRCLE_PTS = 25
_MIN_FOCUS_DIST_TOL = 0.80  # allow charts a little closer than min
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 10
_OFFSET_ATOL = 10  # number of pixels
_OFFSET_RTOL = 0.15
_OFFSET_RTOL_MIN_FD = 0.30
_RADIUS_RTOL = 0.10
_RADIUS_RTOL_MIN_FD = 0.15
_TEST_FORMATS = ['yuv']  # list so can be appended for newer Android versions
_ZOOM_MAX_THRESH = 10.0
_ZOOM_MIN_THRESH = 2.0


def get_test_tols_and_cap_size(cam, props, chart_distance, debug):
  """Determine the tolerance per camera based on test rig and camera params.

  Cameras are pre-filtered to only include supportable cameras.
  Supportable cameras are: YUV(RGB)

  Args:
    cam: camera object
    props: dict; physical camera properties dictionary
    chart_distance: float; distance to chart in cm
    debug: boolean; log additional data

  Returns:
    dict of TOLs with camera focal length as key
    largest common size across all cameras
  """
  ids = camera_properties_utils.logical_multi_camera_physical_ids(props)
  physical_props = {}
  physical_ids = []
  for i in ids:
    physical_props[i] = cam.get_camera_properties_by_id(i)
    # find YUV capable physical cameras
    if camera_properties_utils.backward_compatible(physical_props[i]):
      physical_ids.append(i)

  # find physical camera focal lengths that work well with rig
  chart_distance_m = abs(chart_distance)/100  # convert CM to M
  test_tols = {}
  test_yuv_sizes = []
  for i in physical_ids:
    yuv_sizes = capture_request_utils.get_available_output_sizes(
        'yuv', physical_props[i])
    test_yuv_sizes.append(yuv_sizes)
    if debug:
      logging.debug('cam[%s] yuv sizes: %s', i, str(yuv_sizes))

    # determine if minimum focus distance is less than rig depth
    min_fd = physical_props[i]['android.lens.info.minimumFocusDistance']
    for fl in physical_props[i]['android.lens.info.availableFocalLengths']:
      logging.debug('cam[%s] min_fd: %.3f (diopters), fl: %.2f', i, min_fd, fl)
      if (math.isclose(min_fd, 0.0, rel_tol=1E-6) or  # fixed focus
          (1.0/min_fd < chart_distance_m*_MIN_FOCUS_DIST_TOL)):
        test_tols[fl] = (_RADIUS_RTOL, _OFFSET_RTOL)
      else:
        test_tols[fl] = (_RADIUS_RTOL_MIN_FD, _OFFSET_RTOL_MIN_FD)
        logging.debug('loosening RTOL for cam[%s]: '
                      'min focus distance too large.', i)
  # find intersection of formats for max common format
  common_sizes = list(set.intersection(*[set(list) for list in test_yuv_sizes]))
  if debug:
    logging.debug('common_fmt: %s', max(common_sizes))

  return test_tols, max(common_sizes)


class ZoomTest(its_base_test.ItsBaseTest):
  """Test the camera zoom behavior.
  """

  def test_zoom(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.zoom_ratio_range(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      z_range = props['android.control.zoomRatioRange']
      logging.debug('testing zoomRatioRange: %s', str(z_range))
      debug = self.debug_mode

      z_min, z_max = float(z_range[0]), float(z_range[1])
      camera_properties_utils.skip_unless(z_max >= z_min * _ZOOM_MIN_THRESH)
      z_list = np.arange(z_min, z_max, float(z_max - z_min) / (_NUM_STEPS - 1))
      z_list = np.append(z_list, z_max)

      # set TOLs based on camera and test rig params
      if camera_properties_utils.logical_multi_camera(props):
        test_tols, size = get_test_tols_and_cap_size(
            cam, props, self.chart_distance, debug)
      else:
        test_tols = {}
        fls = props['android.lens.info.availableFocalLengths']
        for fl in fls:
          test_tols[fl] = (_RADIUS_RTOL, _OFFSET_RTOL)
        yuv_size = capture_request_utils.get_largest_yuv_format(props)
        size = [yuv_size['width'], yuv_size['height']]
      logging.debug('capture size: %s', str(size))
      logging.debug('test TOLs: %s', str(test_tols))

      # determine vendor API level and test_formats to test
      test_formats = _TEST_FORMATS
      vendor_api_level = its_session_utils.get_vendor_api_level(self.dut.serial)
      if vendor_api_level >= its_session_utils.ANDROID14_API_LEVEL:
        test_formats.append(_JPEG_STR)

      # do captures over zoom range and find circles with cv2
      img_name_stem = f'{os.path.join(self.log_path, _NAME)}'
      if camera_properties_utils.manual_sensor(props):
        logging.debug('Manual sensor, using manual capture request')
        s, e, _, _, f_d = cam.do_3a(get_results=True)
        req = capture_request_utils.manual_capture_request(
            s, e, f_distance=f_d)
      else:
        logging.debug('Using auto capture request')
        cam.do_3a()
        req = capture_request_utils.auto_capture_request()
      test_failed = False
      for fmt in test_formats:
        logging.debug('testing %s format', fmt)
        test_data = {}
        for i, z in enumerate(z_list):
          logging.debug('zoom ratio: %.2f', z)
          req['android.control.zoomRatio'] = z
          cap = cam.do_capture(
              req, {'format': fmt, 'width': size[0], 'height': size[1]})
          img = image_processing_utils.convert_capture_to_rgb_image(
              cap, props=props)
          img_name = f'{img_name_stem}_{fmt}_{round(z, 2)}.{_JPEG_STR}'
          image_processing_utils.write_image(img, img_name)

          # determine radius tolerance of capture
          cap_fl = cap['metadata']['android.lens.focalLength']
          radius_tol, offset_tol = test_tols[cap_fl]

          # convert [0, 1] image to [0, 255] and cast as uint8
          img = image_processing_utils.convert_image_to_uint8(img)

          # Find the center circle in img
          try:
            circle = opencv_processing_utils.find_center_circle(
                img, img_name, _CIRCLE_COLOR, circle_ar_rtol=_CIRCLE_AR_RTOL,
                circlish_rtol=_CIRCLISH_RTOL,
                min_area=_MIN_AREA_RATIO * size[0] * size[1] * z * z,
                min_circle_pts=_MIN_CIRCLE_PTS, debug=debug)
            if opencv_processing_utils.is_circle_cropped(circle, size):
              logging.debug('zoom %.2f is too large! Skip further captures', z)
              break
          except AssertionError as e:
            if z/z_list[0] >= _ZOOM_MAX_THRESH:
              break
            else:
              raise AssertionError(
                  f'No circle detected for zoom ratio <= {_ZOOM_MAX_THRESH}. '
                  'Take pictures according to instructions carefully!') from e
          test_data[i] = {'z': z, 'circle': circle, 'r_tol': radius_tol,
                          'o_tol': offset_tol, 'fl': cap_fl}

        # assert some range is tested before circles get too big
        zoom_max_thresh = _ZOOM_MAX_THRESH
        z_max_ratio = z_max / z_min
        if z_max_ratio < _ZOOM_MAX_THRESH:
          zoom_max_thresh = z_max_ratio
        test_data_max_z = (test_data[max(test_data.keys())]['z'] /
                           test_data[min(test_data.keys())]['z'])
        logging.debug('test zoom ratio max: %.2f', test_data_max_z)
        if test_data_max_z < zoom_max_thresh:
          test_failed = True
          e_msg = (f'Max zoom ratio tested: {test_data_max_z:.4f}, '
                   f'range advertised min: {z_min}, max: {z_max} '
                   f'THRESH: {zoom_max_thresh}')
          logging.error(e_msg)

        # initialize relative size w/ zoom[0] for diff zoom ratio checks
        radius_0 = float(test_data[0]['circle'][2])
        z_0 = float(test_data[0]['z'])

        for i, data in test_data.items():
          logging.debug('Zoom: %.2f, fl: %.2f', data['z'], data['fl'])
          offset_xy = [(data['circle'][0] - size[0] // 2),
                       (data['circle'][1] - size[1] // 2)]
          logging.debug('Circle r: %.1f, center offset x, y: %d, %d',
                        data['circle'][2], offset_xy[0], offset_xy[1])
          z_ratio = data['z'] / z_0

          # check relative size against zoom[0]
          radius_ratio = data['circle'][2] / radius_0
          logging.debug('r ratio req: %.3f, measured: %.3f',
                        z_ratio, radius_ratio)
          if not math.isclose(z_ratio, radius_ratio, rel_tol=data['r_tol']):
            test_failed = True
            e_msg = (f'zoom: {z_ratio:.2f}, radius ratio:  {radius_ratio:.2f}, '
                     f"RTOL: {data['r_tol']}")
            logging.error(e_msg)

          # check relative offset against init vals w/ no focal length change
          if i == 0 or test_data[i-1]['fl'] != data['fl']:  # set init values
            z_init = float(data['z'])
            offset_hypot_init = math.hypot(offset_xy[0], offset_xy[1])
            logging.debug('offset_hypot_init: %.3f', offset_hypot_init)
          else:  # check
            z_ratio = data['z'] / z_init
            offset_hypot_rel = math.hypot(offset_xy[0], offset_xy[1]) / z_ratio
            logging.debug('offset_hypot_rel: %.3f', offset_hypot_rel)
            rel_tol = data['o_tol']
            if not math.isclose(offset_hypot_init, offset_hypot_rel,
                                rel_tol=rel_tol, abs_tol=_OFFSET_ATOL):
              test_failed = True
              e_msg = (f"zoom: {data['z']:.2f}, "
                       f'offset init: {offset_hypot_init:.4f}, '
                       f'offset rel: {offset_hypot_rel:.4f}, '
                       f'RTOL: {rel_tol}, ATOL: {_OFFSET_ATOL}')
              logging.error(e_msg)

    if test_failed:
      raise AssertionError(f'{_NAME} failed! Check logging for errors')

if __name__ == '__main__':
  test_runner.main()
