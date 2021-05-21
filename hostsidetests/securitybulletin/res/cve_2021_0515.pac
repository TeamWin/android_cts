/*
 * Copyright (C) 2021 The Android Open Source Project
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

function gc() {
    for (var i = 0; i < 0x10000; ++i) {
        new String();
    }
}

function gcc() {
    var temp = [];
    for (var i = 0; i < 0x100000; ++i) {
        temp.push(new Set());
    }
}

function SDD() {
    class H {
        ['h']() {
        }
    }
    let h = H.prototype.h;
    h[1024] = {};
    h["a"] = {};
    h["a"] = {};
    h["b"] = {};
    return h;
}

function FindProxyForURL(url, host) {
    SDD();
    gc();
    gc();
    gcc();
    h = SDD();
    return "DIRECT";
}
