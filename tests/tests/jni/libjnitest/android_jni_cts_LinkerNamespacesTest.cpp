/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Tests accessibility of platform native libraries
 */

#include <dirent.h>
#include <dlfcn.h>
#include <jni.h>
#include <JNIHelp.h>
#include <libgen.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>

#include <list>
#include <string>
#include <unordered_set>
#include <vector>

static std::vector<std::string> kDefaultLibraryPaths = {
#if defined(__LP64__)
    "/system/lib64",
    "/vendor/lib64",
#else
    "/system/lib",
    "/vendor/lib",
#endif
  };

static std::unordered_set<std::string> kPublicLibraries = {
    "libandroid.so",
    "libcamera2ndk.so",
    "libc.so",
    "libdl.so",
    "libEGL.so",
    "libGLESv1_CM.so",
    "libGLESv2.so",
    "libGLESv3.so",
    "libicui18n.so",
    "libicuuc.so",
    "libjnigraphics.so",
    "liblog.so",
    "libmediandk.so",
    "libm.so",
    "libOpenMAXAL.so",
    "libOpenSLES.so",
    "libRS.so",
    "libstdc++.so",
    "libvulkan.so",
    "libwebviewchromium_plat_support.so",
    "libz.so"
  };

template <typename F>
static bool for_each_file(const std::string& dir, F functor, std::string* error_msg) {
  auto dir_deleter = [](DIR* handle) { closedir(handle); };
  std::unique_ptr<DIR, decltype(dir_deleter)> dirp(opendir(dir.c_str()), dir_deleter);
  if (dirp == nullptr) {
    *error_msg = strerror(errno);
    return false;
  }

  dirent* dp;
  while ((dp = readdir(dirp.get())) != nullptr) {
    // skip "." and ".."
    if (strcmp(".", dp->d_name) == 0 ||
        strcmp("..", dp->d_name) == 0) {
      continue;
    }

    if (!functor(dp->d_name, error_msg)) {
      return false;
    }
  }

  return true;
}

static bool should_be_accessible(const std::string& path) {
  std::string name = basename(path.c_str());
  return (kPublicLibraries.find(name) != kPublicLibraries.end()) &&
         (kDefaultLibraryPaths.front() + "/" + name == path);
}

static bool is_directory(const std::string path) {
  struct stat sb;
  if (stat(path.c_str(), &sb) != -1) {
    return S_ISDIR(sb.st_mode);
  }

  return false;
}

static bool is_libdl(const std::string path) {
  return kDefaultLibraryPaths.front() + "/libdl.so" == path;
}

extern "C" JNIEXPORT jstring JNICALL Java_android_jni_cts_LinkerNamespacesHelper_runAccessibilityTest(
        JNIEnv* env, jclass clazz __attribute__((unused))) {
  std::list<std::string> dirs(kDefaultLibraryPaths.begin(), kDefaultLibraryPaths.end());
  while (!dirs.empty()) {
    const auto dir = dirs.front();
    dirs.pop_front();
    std::string error;
    bool success = for_each_file(dir, [&dirs,&dir](const char* name, std::string* error_msg) {
      std::string path = dir + "/" + name;
      if (is_directory(path)) {
        dirs.push_back(path);
        return true;
      }

      if (is_libdl(path)) {
        // TODO (dimitry): we skip check for libdl.so because
        // 1. Linker will fail to check accessibility because it imposes as libdl.so (see http://b/27106625)
        // 2. It is impractical to dlopen libdl.so because this library already depends
        //    on it in order to have dlopen()
        return true;
      }

      auto dlcloser = [](void* handle) { dlclose(handle); };
      std::unique_ptr<void, decltype(dlcloser)> handle(dlopen(path.c_str(), RTLD_NOW), dlcloser);
      if (should_be_accessible(path)) {
        if (handle.get() == nullptr) {
          *error_msg = "The library \"" + path + "\" should be accessible but isn't: " + dlerror();
          return false;
        }
      } else if (handle != nullptr) {
        *error_msg = "The library \"" + path + "\" should not be accessible";
        return false;
      } else { // (handle == nullptr && !shouldBeAccessible(path))
        // Check the error message
        std::string err = dlerror();

        if (err.find("dlopen failed: library \"" + path + "\"") != 0 ||
            err.find("is not accessible for the namespace \"classloader-namespace\"") == std::string::npos) {
          *error_msg = "unexpected dlerror: " + err;
          return false;
        }
      }
      return true;
    }, &error);

    if (!success) {
      return env->NewStringUTF(error.c_str());
    }
  }

  return nullptr;
}

