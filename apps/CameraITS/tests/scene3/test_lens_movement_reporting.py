# Copyright 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os

import cv2
import its.caps
import its.device
import its.image
import its.objects
import numpy as np

NUM_IMGS = 12
NUM_TRYS = 8
FRAME_TIME_TOL = 10  # ms
SHARPNESS_TOL = 0.10  # percentage
POSITION_TOL = 0.10  # percentage
CHART_FILE = os.path.join(os.environ['CAMERA_ITS_TOP'], 'pymodules', 'its',
                          'test_images', 'ISO12233.png')
CHART_HEIGHT = 16.5  # cm
CHART_DISTANCE = 40.0  # cm
CHART_SCALE_START = 0.75
CHART_SCALE_STOP = 1.25
CHART_SCALE_STEP = 0.05
VGA_WIDTH = 640
VGA_HEIGHT = 480
NAME = os.path.basename(__file__).split('.')[0]


def normalize_img(img):
    """Normalize the image values to between 0 and 1.

    Args:
        img: 2-D numpy array of image values
    Returns:
        Normalized image
    """
    return (img - np.amin(img))/(np.amax(img) - np.amin(img))


def find_chart_bbox(img, chart, scale_factor):
    """Find the crop area for the chart."""
    scale_start = CHART_SCALE_START * scale_factor
    scale_stop = CHART_SCALE_STOP * scale_factor
    scale_step = CHART_SCALE_STEP * scale_factor
    bbox = its.image.find_chart(chart, img,
                                scale_start, scale_stop, scale_step)
    # convert bbox to (xnorm, ynorm, wnorm, hnorm)
    wnorm = float((bbox[1][0]) - bbox[0][0]) / img.shape[1]
    hnorm = float((bbox[1][1]) - bbox[0][1]) / img.shape[0]
    xnorm = float(bbox[0][0]) / img.shape[1]
    ynorm = float(bbox[0][1]) / img.shape[0]
    return xnorm, ynorm, wnorm, hnorm


def find_af_chart(cam, props, sensitivity, exp, af_fd):
    """Take an AF image to find the chart location.

    Args:
        cam:    An open device session.
        props:  Properties of cam
        sensitivity: Sensitivity for the AF request as defined in
                     android.sensor.sensitivity
        exp:    Exposure time for the AF request as defined in
                android.sensor.exposureTime
        af_fd:  float; autofocus lens position
    Returns:
        xnorm:  float; x location normalized to [0, 1]
        ynorm:  float; y location normalized to [0, 1]
        wnorm:  float; width normalized to [0, 1]
        hnorm:  float; height normalized to [0, 1]
    """
    # find maximum size 4:3 format
    fmts = props['android.scaler.streamConfigurationMap']['availableStreamConfigurations']
    fmts = [f for f in fmts if f['format'] == 256]
    fmt = {'format': 'yuv', 'width': fmts[0]['width'],
           'height': fmts[0]['height']}
    req = its.objects.manual_capture_request(sensitivity, exp)
    req['android.lens.focusDistance'] = af_fd
    trys = 0
    done = False
    while not done:
        print 'Waiting for lens to move to correct location...'
        cap_chart = cam.do_capture(req, fmt)
        done = (cap_chart['metadata']['android.lens.state'] == 0)
        print ' status: ', done
        trys += 1
        if trys == NUM_TRYS:
            raise its.error.Error('Error: cannot settle lens in %d trys!'
                                  % (trys))
    y, _, _ = its.image.convert_capture_to_planes(cap_chart, props)
    its.image.write_image(y, '%s_AF_image.jpg' % NAME)
    template = cv2.imread(CHART_FILE, cv2.IMREAD_ANYDEPTH)
    focal_l = cap_chart['metadata']['android.lens.focalLength']
    pixel_pitch = (props['android.sensor.info.physicalSize']['height'] /
                   y.shape[0])
    print ' Chart distance: %.2fcm' % CHART_DISTANCE
    print ' Chart height: %.2fcm' % CHART_HEIGHT
    print ' Focal length: %.2fmm' % focal_l
    print ' Pixel pitch: %.2fum' % (pixel_pitch*1E3)
    print ' Template height: %d' % template.shape[0]
    chart_pixel_h = CHART_HEIGHT * focal_l / (CHART_DISTANCE * pixel_pitch)
    scale_factor = template.shape[0] / chart_pixel_h
    print 'Chart/image scale factor = %.2f' % scale_factor
    xnorm, ynorm, wnorm, hnorm = find_chart_bbox(y, template, scale_factor)
    chart = its.image.get_image_patch(y, xnorm, ynorm, wnorm, hnorm)
    its.image.write_image(chart, '%s_AF_chart.jpg' % NAME)
    return xnorm, ynorm, wnorm, hnorm


def test_lens_movement_reporting(cam, props, fmt, sensitivity, exp, af_fd):
    """Return fd, sharpness, lens state of the output images.

    Args:
        cam: An open device session.
        props: Properties of cam
        fmt: dict; capture format
        sensitivity: Sensitivity for the 3A request as defined in
            android.sensor.sensitivity
        exp: Exposure time for the 3A request as defined in
            android.sensor.exposureTime
        af_fd: Focus distance for the 3A request as defined in
            android.lens.focusDistance

    Returns:
        Object containing reported sharpness of the output image, keyed by
        the following string:
            'sharpness'
    """

    print 'Take AF image for chart locator.'
    xnorm, ynorm, wnorm, hnorm = find_af_chart(cam, props, sensitivity,
                                               exp, af_fd)
    data_set = {}
    white_level = int(props['android.sensor.info.whiteLevel'])
    min_fd = props['android.lens.info.minimumFocusDistance']
    fds = [af_fd, min_fd]
    fds = sorted(fds * NUM_IMGS)
    reqs = []
    for i, fd in enumerate(fds):
        reqs.append(its.objects.manual_capture_request(sensitivity, exp))
        reqs[i]['android.lens.focusDistance'] = fd
    cap = cam.do_capture(reqs, fmt)
    for i, _ in enumerate(reqs):
        data = {'fd': fds[i]}
        data['loc'] = cap[i]['metadata']['android.lens.focusDistance']
        data['lens_moving'] = (cap[i]['metadata']['android.lens.state']
                               == 1)
        timestamp = cap[i]['metadata']['android.sensor.timestamp']
        if i == 0:
            timestamp_init = timestamp
        timestamp -= timestamp_init
        timestamp *= 1E-6
        data['timestamp'] = timestamp
        print ' focus distance (diopters): %.3f' % data['fd']
        print ' current lens location (diopters): %.3f' % data['loc']
        print ' lens moving %r' % data['lens_moving']
        y, _, _ = its.image.convert_capture_to_planes(cap[i], props)
        chart = normalize_img(its.image.get_image_patch(y, xnorm, ynorm,
                                                        wnorm, hnorm))
        its.image.write_image(chart, '%s_i=%d_chart.jpg' % (NAME, i))
        data['sharpness'] = white_level*its.image.compute_image_sharpness(chart)
        print 'Chart sharpness: %.1f\n' % data['sharpness']
        data_set[i] = data
    return data_set


def main():
    """Test if focus distance is properly reported.

    Capture images at a variety of focus locations.
    """

    print '\nStarting test_lens_movement_reporting.py'
    with its.device.ItsSession() as cam:
        props = cam.get_camera_properties()
        its.caps.skip_unless(not its.caps.fixed_focus(props))
        its.caps.skip_unless(its.caps.lens_approx_calibrated(props))
        min_fd = props['android.lens.info.minimumFocusDistance']
        fmt = {'format': 'yuv', 'width': VGA_WIDTH, 'height': VGA_HEIGHT}

        # Get proper sensitivity, exposure time, and focus distance with 3A.
        s, e, _, _, fd = cam.do_3a(get_results=True)

        # Get sharpness for each focal distance
        d = test_lens_movement_reporting(cam, props, fmt, s, e, fd)
        for k in sorted(d):
            print ('i: %d\tfd: %.3f\tlens location (diopters): %.3f \t'
                   'sharpness: %.1f  \tlens_moving: %r \t'
                   'timestamp: %.1fms' % (k, d[k]['fd'], d[k]['loc'],
                                          d[k]['sharpness'],
                                          d[k]['lens_moving'],
                                          d[k]['timestamp']))

        # assert frames are consecutive
        print 'Asserting frames are consecutive'
        times = [v['timestamp'] for v in d.itervalues()]
        diffs = np.gradient(times)
        print diffs
        assert np.isclose(np.amax(diffs)-np.amax(diffs), 0, atol=FRAME_TIME_TOL)

        # remove data when lens is moving
        for k in sorted(d):
            if d[k]['lens_moving']:
                del d[k]

        # split data into min_fd and af data for processing
        d_min_fd = {}
        d_af_fd = {}
        for k in sorted(d):
            if d[k]['fd'] == min_fd:
                d_min_fd[k] = d[k]
            if d[k]['fd'] == fd:
                d_af_fd[k] = d[k]

        # assert reported locations are close at af_fd
        print 'Asserting lens location of af_fd data'
        min_loc = min([v['loc'] for v in d_af_fd.itervalues()])
        max_loc = max([v['loc'] for v in d_af_fd.itervalues()])
        assert np.isclose(min_loc, max_loc, rtol=POSITION_TOL)
        # assert reported sharpness is close at af_fd
        print 'Asserting sharpness of af_fd data'
        min_sharp = min([v['sharpness'] for v in d_af_fd.itervalues()])
        max_sharp = max([v['sharpness'] for v in d_af_fd.itervalues()])
        assert np.isclose(min_sharp, max_sharp, rtol=SHARPNESS_TOL)
        # assert reported location is close to assign location for af_fd
        print 'Asserting lens location close to assigned fd for af_fd data'
        assert np.isclose(d_af_fd[0]['loc'], d_af_fd[0]['fd'],
                          rtol=POSITION_TOL)

        # assert reported location is close for min_fd captures
        print 'Asserting lens location similar min_fd data'
        min_loc = min([v['loc'] for v in d_min_fd.itervalues()])
        max_loc = max([v['loc'] for v in d_min_fd.itervalues()])
        assert np.isclose(min_loc, max_loc, rtol=POSITION_TOL)
        # assert reported sharpness is close at min_fd
        print 'Asserting sharpness of min_fd data'
        min_sharp = min([v['sharpness'] for v in d_min_fd.itervalues()])
        max_sharp = max([v['sharpness'] for v in d_min_fd.itervalues()])
        assert np.isclose(min_sharp, max_sharp, rtol=SHARPNESS_TOL)
        # assert reported location is close to assign location for min_fd
        print 'Asserting lens location close to assigned fd for min_fd data'
        assert np.isclose(d_min_fd[NUM_IMGS*2-1]['loc'],
                          d_min_fd[NUM_IMGS*2-1]['fd'], rtol=POSITION_TOL)


if __name__ == '__main__':
    main()
