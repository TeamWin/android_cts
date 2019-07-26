/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <proxy_resolver_js_bindings.h>
#include <proxy_resolver_v8.h>
#include <sys/types.h>
#include <utils/String16.h>
#include <utils/String8.h>

#include <fstream>
#include <iostream>

android::String16 url("");
android::String16 host("");

class MyErrorListener : public net::ProxyErrorListener {
 public:
  virtual void AlertMessage(android::String16 alert) {
    std::cout << "alert: " << android::String8(alert).string() << std::endl;
  }

  virtual void ErrorMessage(android::String16 error) {
    std::cout << "error: " << android::String8(error).string() << std::endl;
  }
};

int main(int argc, char *argv[]) {
  if (argc != 2) {
    std::cout << "incorrect number of arguments" << std::endl;
    std::cout << "usage: ./pacrunner mypac.pac" << std::endl;
    return EXIT_FAILURE;
  }
  net::ProxyResolverJSBindings *bindings =
      net::ProxyResolverJSBindings::CreateDefault();
  MyErrorListener errorListener;
  net::ProxyResolverV8 resolver(bindings, &errorListener);
  android::String16 results;

  std::ifstream t;
  t.open(argv[1]);
  if (t.rdstate() != std::ifstream::goodbit) {
    std::cout << "error opening file" << std::endl;
    return EXIT_FAILURE;
  }

  t.seekg(0, std::ios::end);
  size_t size = t.tellg();
  // allocate an extra byte for the null terminator
  char* raw = (char*)calloc(size + 1, sizeof(char));
  t.seekg(0);
  t.read(raw, size);
  android::String16 script(raw);

  resolver.SetPacScript(script);
  resolver.GetProxyForURL(url, host, &results);
  return EXIT_SUCCESS;
}
