#  Copyright (C) 2021 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import argparse
from pathlib import Path
import subprocess
from src.library.main.proto.testapp_protos_pb2 import TestAppIndex, AndroidApp

def main():
    args_parser = argparse.ArgumentParser(description='Generate index for test apps')
    args_parser.add_argument('--directory', help='Directory containing test apps')
    args_parser.add_argument('--aapt2', help='The path to aapt2')
    args = args_parser.parse_args()

    pathlist = Path(args.directory).rglob('*.apk')
    file_names = [p.name for p in pathlist]

    index = TestAppIndex()

    for file_name in file_names:
        manifest_content = str(subprocess.check_output(
            [args.aapt2, 'd', 'xmltree', '--file', 'AndroidManifest.xml', args.directory + "/" + file_name]))
        androidApp = AndroidApp()

        androidApp.apk_name = file_name
        # TODO(b/192330233): Replace with more complete parsing
        androidApp.package_name = manifest_content.split("package=\"")[1].split("\"")[0]
        index.apps.append(androidApp)

    with open(args.directory + "/index.txt", "wb") as fd:
        fd.write(index.SerializeToString())

if __name__ == "__main__":
    main()