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
"""Verify jpeg capture latency for both front and back primary cameras.
"""

import its.caps
import its.device

JPEG_CAPTURE_S_PERFORMANCE_CLASS_THRESHOLD = 1000  # ms


def main():
    """Test jpeg capture latency for S performance class as specified in CDD.

    [7.5/H-1-6] MUST have camera2 JPEG capture latency < 1000ms for 1080p
    resolution as measured by the CTS camera PerformanceTest under ITS lighting
    conditions (3000K) for both primary cameras.
    """

    # Open camera with "with" semantics to check skip condition and save camera
    # id
    cam_id = ''
    with its.device.ItsSession() as cam:
        its.caps.skip_unless(
            cam.is_performance_class_primary_camera())
        cam_id = cam._camera_id

    # Create an Its session without opening the camera to test camera jpeg
    # capture latency because the test opens camera internally
    session = its.device.ItsSession(camera_id=cam_id)

    jpeg_capture_ms = session.measure_camera_1080p_jpeg_capture_ms()
    print '1080p jpeg capture time: %.1f ms' % jpeg_capture_ms

    msg = '1080p jpeg capture time: %.1f ms, THRESH: %.1f ms' \
        % (jpeg_capture_ms, JPEG_CAPTURE_S_PERFORMANCE_CLASS_THRESHOLD)
    assert jpeg_capture_ms < JPEG_CAPTURE_S_PERFORMANCE_CLASS_THRESHOLD, msg

if __name__ == '__main__':
    main()
