/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <iterator>
#include <set>
#include <sstream>
#include <string>

#include <android/hardware_buffer.h>
#include <gtest/gtest.h>

#define NO_ERROR 0

namespace android {
namespace {

bool FormatHasAlpha(uint32_t format) {
    switch (format) {
        case AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM:
        case AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT:
        case AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM:
            return true;
        default: return false;
    }
}

bool FormatIsFloat(uint32_t format) {
    return format == AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT;
}

enum GoldenColor { kBlack, kWhite };

struct GoldenPixel {
    int x;
    int y;
    GoldenColor color;
};

}  // namespace

class AHardwareBufferGLTest : public ::testing::Test {
public:
    void SetUp() override;
    void TearDown() override;

protected:
    std::set<std::string> mEGLExtensions;
    EGLDisplay mDisplay = EGL_NO_DISPLAY;
    EGLSurface mSurface = EGL_NO_SURFACE;
    EGLContext mContext[2];
};

void AHardwareBufferGLTest::SetUp() {
    mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(mDisplay, NULL, NULL);

    EGLConfig first_config;
    EGLint const config_attrib_list[] = {
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };
    EGLint num_config = 0;
    eglChooseConfig(mDisplay, config_attrib_list, &first_config, 1, &num_config);
    ASSERT_LT(0, num_config);

    // Try creating an OpenGL ES 3.x context and fall back to 2.x if that fails.
    // Create two contexts for cross-context image sharing tests.
    EGLint context_attrib_list[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE
    };
    mContext[0] = eglCreateContext(mDisplay, first_config, EGL_NO_CONTEXT, context_attrib_list);
    if (mContext == EGL_NO_CONTEXT) {
        context_attrib_list[1] = 2;
        mContext[0] = eglCreateContext(mDisplay, first_config, EGL_NO_CONTEXT, context_attrib_list);
        mContext[1] = eglCreateContext(mDisplay, first_config, EGL_NO_CONTEXT, context_attrib_list);
    } else {
        mContext[1] = eglCreateContext(mDisplay, first_config, EGL_NO_CONTEXT, context_attrib_list);
    }
    ASSERT_NE(EGL_NO_CONTEXT, mContext[0]);
    ASSERT_NE(EGL_NO_CONTEXT, mContext[1]);

    // Parse EGL extension strings into a set for easier processing.
    std::istringstream eglext_stream(eglQueryString(mDisplay, EGL_EXTENSIONS));
    mEGLExtensions = std::set<std::string>{
        std::istream_iterator<std::string>{eglext_stream},
        std::istream_iterator<std::string>{}
    };
    // Create a 1x1 pbuffer surface if surfaceless contexts are not supported.
    if (mEGLExtensions.find("EGL_KHR_surfaceless_context") == mEGLExtensions.end()) {
        EGLint const surface_attrib_list[] = {
            EGL_WIDTH, 1,
            EGL_HEIGHT, 1,
            EGL_NONE
        };
        mSurface = eglCreatePbufferSurface(mDisplay, first_config, surface_attrib_list);
    }
    EGLBoolean result = eglMakeCurrent(mDisplay, mSurface, mSurface, mContext[0]);
    ASSERT_EQ(EGLBoolean{EGL_TRUE}, result);
}

void AHardwareBufferGLTest::TearDown() {
    eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    for (int i = 0; i < 2; ++i) {
        eglDestroyContext(mDisplay, mContext[i]);
    }
    if (mSurface != EGL_NO_SURFACE) {
        eglDestroySurface(mDisplay, mSurface);
    }
    eglTerminate(mDisplay);
}

class AHardwareBufferColorFormatTest
    : public AHardwareBufferGLTest,
      public ::testing::WithParamInterface<uint64_t> {};

// Verify that when allocating an AHardwareBuffer succeeds with GPU_COLOR_OUTPUT,
// it can be bound as a framebuffer attachment, glClear'ed and then read from
// another context using glReadPixels.
TEST_P(AHardwareBufferColorFormatTest, GpuColorOutputIsRenderable) {
    const uint32_t format = GetParam();
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc = {};
    desc.width = 100;
    desc.height = 100;
    desc.layers = 1;
    desc.format = format;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT;

    int result = AHardwareBuffer_allocate(&desc, &buffer);
    // Skip if this format cannot be allocated.
    if (result != NO_ERROR) return;

    const EGLint attrib_list[] = { EGL_NONE };
    EGLImageKHR egl_image = eglCreateImageKHR(
        mDisplay, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID,
        eglGetNativeClientBufferANDROID(buffer), attrib_list);
    ASSERT_NE(EGL_NO_IMAGE_KHR, egl_image);

    // Bind the EGLImage to renderbuffers and framebuffers in both contexts.
    GLuint renderbuffer[2], fbo[2];
    for (int i = 0; i < 2; ++i) {
        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext[i]);
        glGenRenderbuffers(1, &renderbuffer[i]);
        glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer[i]);
        glEGLImageTargetRenderbufferStorageOES(GL_RENDERBUFFER, static_cast<GLeglImageOES>(egl_image));
        EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
        glGenFramebuffers(1, &fbo[i]);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo[i]);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, renderbuffer[i]);
        ASSERT_EQ(GLenum{GL_FRAMEBUFFER_COMPLETE},
                  glCheckFramebufferStatus(GL_FRAMEBUFFER));
    }

    // Draw a simple checkerboard pattern in the second context, which will
    // be current after the loop above, then read it in the first.
    //        +----+----+ (100, 100)
    //        | OW | TB |
    //        +----+----+  TB = transparent black
    //        | TB | OW |  OW = opaque white
    // (0, 0) +----+----+
    glEnable(GL_SCISSOR_TEST);
    glClearColor(1.f, 1.f, 1.f, 1.f);
    glScissor(0, 0, 100, 100);
    glClear(GL_COLOR_BUFFER_BIT);
    glClearColor(0.f, 0.f, 0.f, 0.f);
    glScissor(0, 0, 50, 50);
    glClear(GL_COLOR_BUFFER_BIT);
    glScissor(50, 50, 50, 50);
    glClear(GL_COLOR_BUFFER_BIT);
    glFinish();

    eglMakeCurrent(mDisplay, mSurface, mSurface, mContext[0]);
    const GoldenPixel goldens[] =
        {{10, 10, kBlack}, {10, 90, kWhite}, {90, 10, kWhite}, {90, 90, kBlack}};
    for (const GoldenPixel& golden : goldens) {
        if (FormatIsFloat(format)) {
            float pixel[4] = {0.5f, 0.5f, 0.5f, 0.5f};
            glReadPixels(golden.x, golden.y, 1, 1, GL_RGBA, GL_FLOAT, pixel);
            EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
            EXPECT_EQ(golden.color == kWhite ? 1.f : 0.f, pixel[0]);
            EXPECT_EQ(golden.color == kWhite ? 1.f : 0.f, pixel[1]);
            EXPECT_EQ(golden.color == kWhite ? 1.f : 0.f, pixel[2]);
            // Formats without alpha should be read as opaque.
            EXPECT_EQ((golden.color == kWhite || !FormatHasAlpha(format)) ? 1.f : 0.f,
                      pixel[3]);
        } else {
            uint8_t pixel[4] = {127, 127, 127, 127};
            glReadPixels(golden.x, golden.y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
            EXPECT_EQ(golden.color == kWhite ? 255 : 0, pixel[0]);
            EXPECT_EQ(golden.color == kWhite ? 255 : 0, pixel[1]);
            EXPECT_EQ(golden.color == kWhite ? 255 : 0, pixel[2]);
            // Formats without alpha should be read as opaque.
            EXPECT_EQ((golden.color == kWhite || !FormatHasAlpha(format)) ? 255 : 0,
                      pixel[3]);
        }
    }

    // Clean up GL objects
    for (int i = 0; i < 2; ++i) {
        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext[i]);
        glDeleteFramebuffers(1, &fbo[i]);
        glDeleteRenderbuffers(1, &renderbuffer[i]);
    }
    eglDestroyImageKHR(mDisplay, egl_image);
    AHardwareBuffer_release(buffer);
}

INSTANTIATE_TEST_CASE_P(
    AllColorFormats,
    AHardwareBufferColorFormatTest,
    ::testing::Values(AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM,
                      AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM,
                      AHARDWAREBUFFER_FORMAT_R8G8B8_UNORM,
                      AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM,
                      AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT,
                      AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM));

}  // namespace android
