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
"""Verify IMU has stable output when device is stationary."""

import logging
import math
import os

import matplotlib
from matplotlib import pylab
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import its_session_utils

_NAME = os.path.basename(__file__).split('.')[0]
_NSEC_TO_SEC = 1E-9
_RAD_TO_DEG = 180/math.pi
_MEAN_THRESH = 0.01*_RAD_TO_DEG  # PASS/FAIL thresh for gyro mean drift
_SENSOR_EVENTS_WAIT_TIME = 30  # seconds
_VAR_THRESH = 0.001*_RAD_TO_DEG**2  # PASS/FAIL thresh for gyro variance drift


def convert_events_to_arrays(events, t_factor, xyz_factor):
  """Convert data from get_sensor_events() into x, y, z, t.

  Args:
    events: dict from cam.get_sensor_events()
    t_factor: time multiplication factor ie. NSEC_TO_SEC
    xyz_factor: xyz multiplicaiton factor ie. RAD_TO_DEG

  Returns:
    x, y, z, t numpy arrays
  """
  t = np.array([(e['time'] - events[0]['time'])*t_factor
                for e in events])
  x = np.array([e['x']*xyz_factor for e in events])
  y = np.array([e['y']*xyz_factor for e in events])
  z = np.array([e['z']*xyz_factor for e in events])

  return x, y, z, t


class ImuDriftTest(its_base_test.ItsBaseTest):
  """Test if the IMU has stable output when device is stationary."""

  def test_imu_drift(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.sensor_fusion(props) and
          cam.get_sensors().get('gyro'))

      # load scene
      its_session_utils.load_scene(cam, props, self.scene,
                                   self.tablet, self.chart_distance)

      # determine preview size
      supported_preview_sizes = cam.get_supported_preview_sizes(self.camera_id)
      logging.debug('Supported preview resolutions: %s',
                    supported_preview_sizes)
      preview_size = supported_preview_sizes[-1]
      logging.debug('Tested preview resolution: %s', preview_size)

      # start collecting gyro events
      logging.debug('Collecting gyro events')
      cam.start_sensor_events()

      # do preview recording
      cam.do_preview_recording(
          video_size=preview_size, duration=_SENSOR_EVENTS_WAIT_TIME,
          stabilize=True)

      # dump IMU events
      gyro_events = cam.get_sensor_events()['gyro']

    name_with_log_path = os.path.join(self.log_path, _NAME)
    xs, ys, zs, times = convert_events_to_arrays(
        gyro_events, _NSEC_TO_SEC, _RAD_TO_DEG)

    # add y limits so plot doesn't look like amplified noise
    y_min = min([np.amin(xs), np.amin(ys), np.amin(zs), -_MEAN_THRESH])
    y_max = max([np.amax(xs), np.amax(ys), np.amax(xs), _MEAN_THRESH])

    pylab.figure()
    pylab.plot(times, xs, 'r', label='x')
    pylab.plot(times, ys, 'g', label='y')
    pylab.plot(times, zs, 'b', label='z')
    pylab.title(_NAME)
    pylab.xlabel('Time (seconds)')
    pylab.ylabel(f'Gyro readings (degrees)')
    pylab.ylim([y_min, y_max])
    pylab.ticklabel_format(axis='y', style='sci', scilimits=(-3, -3))
    pylab.legend()
    logging.debug('Saving plot')
    matplotlib.pyplot.savefig(f'{name_with_log_path}_plot.png')

    for samples in [xs, ys, zs]:
      mean = samples.mean()
      var = np.var(samples)
      logging.debug('mean: %.3e', mean)
      logging.debug('var: %.3e', var)
      if mean >= _MEAN_THRESH:
        raise AssertionError(f'mean: {mean}.3e, TOL={_MEAN_THRESH}')
      if var >= _VAR_THRESH:
        raise AssertionError(f'var: {var}.3e, TOL={_VAR_THRESH}')


if __name__ == '__main__':
  test_runner.main()
