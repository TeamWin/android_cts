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
import logging
import os.path
import subprocess
import time


ITS_SUPPORTED_QUALITIES = (
    'HIGH',
    '2160P',
    '1080P',
    '720P',
    '480P',
    'CIF',
    'QCIF',
    'QVGA',
    'LOW',
    'VGA'
)


def extract_key_frames_from_video(log_path, video_file_name):
  """
  Returns a list of extracted key frames.

  Ffmpeg tool is used to extract key frames from the video at path
  os.path.join(log_path, video_file_name).
  The extracted key frames will have the name video_file_name with "_key_frame"
  suffix to identify the frames for video of each quality.Since there can be
  multiple key frames, each key frame image will be differentiated with it's
  frame index.All the extracted key frames will be available in  jpeg format
  at the same path as the video file.

  Args:
    log_path: path for video file directory
    video_file_name: name of the video file.
    Ex: VID_20220325_050918_0_CIF_352x288.mp4
  Returns:
    key_frame_files: A list of paths for each key frame extracted from the
    video.
  """
  ffmpeg_image_name = f"{video_file_name.split('.')[0]}_key_frame"
  ffmpeg_image_file_path = os.path.join(log_path, ffmpeg_image_name + '_%02d.png')
  cmd = ['ffmpeg',
    '-skip_frame',
    'nokey',
    '-i',
    os.path.join(log_path, video_file_name),
    '-vsync',
    'vfr',
    '-frame_pts',
    'true' ,
    ffmpeg_image_file_path,
  ]
  logging.debug('Extracting key frames from: %s' % video_file_name)
  output = subprocess.call(cmd)
  arr = os.listdir(os.path.join(log_path))
  key_frame_files = []
  for file in arr:
    if '.png' in file and not os.path.isdir(file) and ffmpeg_image_name in file:
      key_frame_files.append(file)
  return key_frame_files


def get_key_frame_to_process(key_frame_files):
  """
  Returns the key frame file from the list of key_frame_files.

  If the size of the list is 1 then the file in the list will be returned else
  the file with highest frame_index will be returned for further processing.

  Args:
    key_frame_files: A list of key frame files.
  Returns:
    key_frame_file to be used for further processing.
  """
  key_frame_files.sort()
  return key_frame_files[-1]
