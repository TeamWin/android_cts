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
#include <GLES3/gl3.h>

#include <iterator>
#include <set>
#include <sstream>
#include <string>
#include <vector>

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

void UploadData(const AHardwareBuffer_Desc& desc, GLenum format, GLenum type, const void* data) {
    if (desc.layers <= 1) {
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, desc.width, desc.height, format, type, data);
    } else {
        for (uint32_t layer = 0; layer < desc.layers; ++layer) {
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, desc.width, desc.height, 1,
                            format, type, data);
        }
    }
}

// Uploads opaque red to the currently bound texture.
void UploadRedPixels(const AHardwareBuffer_Desc& desc) {
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    switch (desc.format) {
        case AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM:
        case AHARDWAREBUFFER_FORMAT_R8G8B8_UNORM:
        case AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM: {
            // GL_RGB565 supports uploading GL_UNSIGNED_BYTE data.
            const int size = desc.width * desc.height * 3;
            std::unique_ptr<uint8_t[]> pixels(new uint8_t[size]);
            for (int i = 0; i < size; i += 3) {
                pixels[i] = 255;
                pixels[i + 1] = 0;
                pixels[i + 2] = 0;
            }
            UploadData(desc, GL_RGB, GL_UNSIGNED_BYTE, pixels.get());
            break;
        }
        case AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM: {
            const int size = desc.width * desc.height * 4;
            std::unique_ptr<uint8_t[]> pixels(new uint8_t[size]);
            for (int i = 0; i < size; i += 4) {
                pixels[i] = 255;
                pixels[i + 1] = 0;
                pixels[i + 2] = 0;
                pixels[i + 3] = 255;
            }
            UploadData(desc, GL_RGBA, GL_UNSIGNED_BYTE, pixels.get());
            break;
        }
        case AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT: {
            const int size = desc.width * desc.height * 4;
            std::unique_ptr<float[]> pixels(new float[size]);
            for (int i = 0; i < size; i += 4) {
                pixels[i] = 1.f;
                pixels[i + 1] = 0.f;
                pixels[i + 2] = 0.f;
                pixels[i + 3] = 1.f;
            }
            UploadData(desc, GL_RGBA, GL_FLOAT, pixels.get());
            break;
        }
        case AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM: {
            const int size = desc.width * desc.height;
            std::unique_ptr<uint32_t[]> pixels(new uint32_t[size]);
            for (int i = 0; i < size; ++i) {
                // Opaque red is top 2 bits and bottom 10 bits set.
                pixels[i] = 0xc00003ff;
            }
            UploadData(desc, GL_RGBA, GL_UNSIGNED_INT_2_10_10_10_REV_EXT, pixels.get());
            break;
        }
        default: FAIL() << "Unrecognized AHardwareBuffer format"; break;
    }
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
}

// Draws the following checkerboard pattern using glScissor and glClear:
//        +----+----+ (W, H)
//        | OR | Ob |
//        +----+----+  TB = transparent black
//        | TB | OR |  OR = opaque red
// (0, 0) +----+----+  Ob = opaque blue
void DrawCheckerboard(int width, int height) {
    glEnable(GL_SCISSOR_TEST);
    glClearColor(1.f, 0.f, 0.f, 1.f);
    glScissor(0, 0, width, height);
    glClear(GL_COLOR_BUFFER_BIT);
    glClearColor(0.f, 0.f, 0.f, 0.f);
    glScissor(0, 0, width / 2, height / 2);
    glClear(GL_COLOR_BUFFER_BIT);
    glClearColor(0.f, 0.f, 1.f, 1.f);
    glScissor(width / 2, height / 2, width / 2, height / 2);
    glClear(GL_COLOR_BUFFER_BIT);
}

void CompileProgram(const char* vertex_source, const char* fragment_source, GLuint* program_out) {
    GLint status = GL_FALSE;
    GLuint& program = *program_out;
    program = glCreateProgram();
    GLuint vertex_shader = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vertex_shader, 1, &vertex_source, nullptr);
    glCompileShader(vertex_shader);
    glGetShaderiv(vertex_shader, GL_COMPILE_STATUS, &status);
    ASSERT_EQ(GL_TRUE, status);
    GLuint fragment_shader = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fragment_shader, 1, &fragment_source, nullptr);
    glCompileShader(fragment_shader);
    glGetShaderiv(fragment_shader, GL_COMPILE_STATUS, &status);
    ASSERT_EQ(GL_TRUE, status);
    glAttachShader(program, vertex_shader);
    glAttachShader(program, fragment_shader);
    glLinkProgram(program);
    glGetProgramiv(program, GL_LINK_STATUS, &status);
    ASSERT_EQ(GL_TRUE, status);
    glDetachShader(program, vertex_shader);
    glDetachShader(program, fragment_shader);
    glDeleteShader(vertex_shader);
    glDeleteShader(fragment_shader);
}

enum GoldenColor {
    kZero,  // all zero, i.e., transparent black
    kBlack,  // opaque black
    kRed,  // opaque red
    kBlue,  // opaque blue
};

struct GoldenPixel {
    int x;
    int y;
    GoldenColor color;
};

void CheckGoldenPixels(const std::vector<GoldenPixel>& goldens, bool float_format, bool alpha_format) {
    for (const GoldenPixel& golden : goldens) {
        if (float_format) {
            float pixel[4] = {0.5f, 0.5f, 0.5f, 0.5f};
            glReadPixels(golden.x, golden.y, 1, 1, GL_RGBA, GL_FLOAT, pixel);
            EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
            EXPECT_EQ(golden.color == kRed ? 1.f : 0.f, pixel[0]);
            EXPECT_EQ(0.f, pixel[1]);
            EXPECT_EQ(golden.color == kBlue ? 1.f : 0.f, pixel[2]);
            // Formats without alpha should be read as opaque.
            EXPECT_EQ((golden.color != kZero || !alpha_format) ? 1.f : 0.f,
                      pixel[3]);
        } else {
            uint8_t pixel[4] = {127, 127, 127, 127};
            glReadPixels(golden.x, golden.y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
            EXPECT_EQ(golden.color == kRed ? 255 : 0, pixel[0]);
            EXPECT_EQ(0, pixel[1]);
            EXPECT_EQ(golden.color == kBlue ? 255 : 0, pixel[2]);
            // Formats without alpha should be read as opaque.
            EXPECT_EQ((golden.color != kZero || !alpha_format) ? 255 : 0,
                      pixel[3]);
        }
    }
}

// Vertex shader that draws a textured shape.
const char* kVertexShader = R"glsl(
    #version 100
    attribute vec2 aPosition;
    attribute float aDepth;
    varying mediump vec2 vTexCoords;
    void main() {
        vTexCoords = (vec2(1.0) + aPosition) * 0.5;
        gl_Position.xy = aPosition * 0.5;
        gl_Position.z = aDepth;
        gl_Position.w = 1.0;
    }
)glsl";

const char* kFragmentShader = R"glsl(
    #version 100
    precision mediump float;
    varying mediump vec2 vTexCoords;
    uniform lowp sampler2D uTexture;
    void main() {
        gl_FragColor = texture2D(uTexture, vTexCoords);
    }
)glsl";

const char* kVertexShaderEs3 = R"glsl(
    #version 300 es
    in vec2 aPosition;
    in float aDepth;
    out mediump vec2 vTexCoords;
    void main() {
        vTexCoords = (vec2(1.0) + aPosition) * 0.5;
        gl_Position.xy = aPosition * 0.5;
        gl_Position.z = aDepth;
        gl_Position.w = 1.0;
    }
)glsl";

const char* kArrayFragmentShaderEs3 = R"glsl(
    #version 300 es
    precision mediump float;
    in mediump vec2 vTexCoords;
    uniform lowp sampler2DArray uTexture;
    uniform mediump float uLayer;
    out lowp vec4 color;
    void main() {
        color = texture(uTexture, vec3(vTexCoords, uLayer));
    }
)glsl";

// Interleaved X and Y coordinates for 2 triangles forming a quad with CCW
// orientation.
const float kQuadPositions[] = {
    -1.f, -1.f, 1.f, 1.f, -1.f, 1.f,
    -1.f, -1.f, 1.f, -1.f, 1.f, 1.f,
};

// Interleaved X, Y and Z coordinates for 4 triangles forming a "pyramid" as
// seen from above. The center vertex has Z=1, while the edge vertices have Z=-1.
// It looks like this:
//
//        +---+ 1, 1
//        |\ /| 
//        | x |
//        |/ \|
// -1, -1 +---+
/*const float kPyramidPositions[] = {
    -1.f, -1.f, -1.f, 0.f, 0.f, 1.f, -1.f, 1.f, -1.f,
    -1.f, 1.f, -1.f, 0.f, 0.f, 1.f, 1.f, 1.f, -1.f,
    1.f, 1.f, -1.f, 0.f, 0.f, 1.f, 1.f, -1.f, -1.f,
    1.f, -1.f, -1.f, 0.f, 0.f, 1.f, -1.f, -1.f, -1.f,
};*/

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
    int mGLVersion = 0;
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
    mGLVersion = context_attrib_list[1];

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
      public ::testing::WithParamInterface<AHardwareBuffer_Desc> {};

// Verify that when allocating an AHardwareBuffer succeeds with GPU_COLOR_OUTPUT,
// it can be bound as a framebuffer attachment, glClear'ed and then read from
// another context using glReadPixels.
TEST_P(AHardwareBufferColorFormatTest, GpuColorOutputIsRenderable) {
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc = GetParam();
    desc.width = 100;
    desc.height = 100;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT;
    // This test does not make sense for layered buffers - don't bother testing them.
    if (desc.layers > 1) return;

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
    DrawCheckerboard(desc.width, desc.height);
    glFinish();
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());

    eglMakeCurrent(mDisplay, mSurface, mSurface, mContext[0]);
    std::vector<GoldenPixel> goldens{
        {10, 90, kRed},  {40, 90, kRed},  {60, 90, kBlue}, {90, 90, kBlue},
        {10, 60, kRed},  {40, 60, kRed},  {60, 60, kBlue}, {90, 60, kBlue},
        {10, 40, kZero}, {40, 40, kZero}, {60, 40, kRed},  {90, 40, kRed},
        {10, 10, kZero}, {40, 10, kZero}, {60, 10, kRed},  {90, 10, kRed},
    };
    CheckGoldenPixels(goldens, FormatIsFloat(desc.format), FormatHasAlpha(desc.format));

    // Clean up GL objects
    for (int i = 0; i < 2; ++i) {
        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext[i]);
        glDeleteFramebuffers(1, &fbo[i]);
        glDeleteRenderbuffers(1, &renderbuffer[i]);
    }
    eglDestroyImageKHR(mDisplay, egl_image);
    AHardwareBuffer_release(buffer);
}

// Verify that when allocating an AHardwareBuffer succeeds with GPU_SAMPLED_IMAGE,
// it can be bound as a texture, set to a color with glTexSubImage2D and sampled
// from in a fragment shader.
TEST_P(AHardwareBufferColorFormatTest, GpuSampledImageCanBeSampled) {
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc = GetParam();
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;

    // Skip texture arrays if the device doesn't support OpenGL ES 3.
    if (desc.layers > 1 && mGLVersion < 3) return;
    const GLenum textarget = desc.layers > 1 ? GL_TEXTURE_2D_ARRAY : GL_TEXTURE_2D;

    int result = AHardwareBuffer_allocate(&desc, &buffer);
    // Skip if this format cannot be allocated.
    if (result != NO_ERROR) return;

    const EGLint attrib_list[] = { EGL_NONE };
    EGLImageKHR egl_image = eglCreateImageKHR(
        mDisplay, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID,
        eglGetNativeClientBufferANDROID(buffer), attrib_list);

    // Bind the EGLImage to textures in both contexts.
    GLuint texture[2];
    for (int i = 0; i < 2; ++i) {
        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext[i]);
        glGenTextures(1, &texture[i]);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(textarget, texture[i]);
        glEGLImageTargetTexture2DOES(textarget, static_cast<GLeglImageOES>(egl_image));
        EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
    }
    // In the second context, upload opaque red to the texture.
    UploadRedPixels(desc);
    glFinish();

    // In the first context, draw a quad that samples from the texture.
    eglMakeCurrent(mDisplay, mSurface, mSurface, mContext[0]);
    GLuint fbo, renderbuffer;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glGenRenderbuffers(1, &renderbuffer);
    glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8_OES, 40, 40);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, renderbuffer);
    ASSERT_EQ(GLenum{GL_FRAMEBUFFER_COMPLETE}, glCheckFramebufferStatus(GL_FRAMEBUFFER));
    glClearColor(0.f, 0.f, 0.f, 0.f);
    glClear(GL_COLOR_BUFFER_BIT);

    // Compile the shader.
    GLuint program = 0;
    CompileProgram(desc.layers > 1 ? kVertexShaderEs3 : kVertexShader,
                   desc.layers > 1 ? kArrayFragmentShaderEs3 : kFragmentShader,
                   &program);
    glUseProgram(program);
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());

    // Draw a textured quad. Use a constant attribute for depth.
    GLint a_position_location = glGetAttribLocation(program, "aPosition");
    GLint a_depth_location = glGetAttribLocation(program, "aDepth");
    glVertexAttribPointer(a_position_location, 2, GL_FLOAT, GL_TRUE, 0, kQuadPositions);
    glVertexAttrib1f(a_depth_location, 0.f);
    glEnableVertexAttribArray(a_position_location);
    glUniform1i(glGetUniformLocation(program, "uTexture"), 1);
    if (desc.layers > 1) {
        glUniform1f(glGetUniformLocation(program, "uLayer"), static_cast<float>(desc.layers - 1));
    }
    glViewport(0, 0, 40, 40);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());

    // Check the rendered pixels. There should be a red square in the middle.
    std::vector<GoldenPixel> goldens{
        {5, 35, kZero}, {15, 35, kZero}, {25, 35, kZero}, {35, 35, kZero},
        {5, 25, kZero}, {15, 25, kRed},  {25, 25, kRed},  {35, 25, kZero},
        {5, 15, kZero}, {15, 15, kRed},  {25, 15, kRed},  {35, 15, kZero},
        {5,  5, kZero}, {15,  5, kZero}, {25, 5,  kZero}, {35, 5,  kZero},
    };
    CheckGoldenPixels(goldens, /*float_format=*/false, /*alpha_format=*/true);

    // Tear down the GL objects.
    glDeleteProgram(program);
    glDeleteFramebuffers(1, &fbo);
    glDeleteRenderbuffers(1, &renderbuffer);
    glDeleteTextures(1, &texture[0]);
    eglMakeCurrent(mDisplay, mSurface, mSurface, mContext[1]);
    glDeleteTextures(1, &texture[1]);
    eglDestroyImageKHR(mDisplay, egl_image);
    AHardwareBuffer_release(buffer);
}

// Verify that buffers which have both GPU_SAMPLED_IMAGE and GPU_COLOR_OUTPUT
// can be both rendered and sampled as a texture.
TEST_P(AHardwareBufferColorFormatTest, GpuColorOutputAndSampledImage) {
    AHardwareBuffer* buffer = NULL;
    AHardwareBuffer_Desc desc = GetParam();
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;

    // Skip texture arrays if the device doesn't support OpenGL ES 3.
    if (desc.layers > 1 && mGLVersion < 3) return;
    const GLenum textarget = desc.layers > 1 ? GL_TEXTURE_2D_ARRAY : GL_TEXTURE_2D;

    int result = AHardwareBuffer_allocate(&desc, &buffer);
    // Skip if this format cannot be allocated.
    if (result != NO_ERROR) return;

    const EGLint attrib_list[] = { EGL_NONE };
    EGLImageKHR egl_image = eglCreateImageKHR(
        mDisplay, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID,
        eglGetNativeClientBufferANDROID(buffer), attrib_list);
    ASSERT_NE(EGL_NO_IMAGE_KHR, egl_image);

    // Bind the EGLImage to textures in both contexts.
    GLuint texture[2];
    for (int i = 0; i < 2; ++i) {
        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext[i]);
        glGenTextures(1, &texture[i]);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(textarget, texture[i]);
        glEGLImageTargetTexture2DOES(textarget, static_cast<GLeglImageOES>(egl_image));
        EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
    }

    // In the second context, draw a checkerboard pattern.
    GLuint texture_fbo;
    glGenFramebuffers(1, &texture_fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, texture_fbo);
    if (desc.layers > 1) {
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, texture[1], 0, desc.layers - 1);
    } else {
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture[1], 0);
    }
    ASSERT_EQ(GLenum{GL_FRAMEBUFFER_COMPLETE},
              glCheckFramebufferStatus(GL_FRAMEBUFFER));

    DrawCheckerboard(desc.width, desc.height);
    glFinish();
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());

    // In the first context, draw a quad that samples from the texture.
    eglMakeCurrent(mDisplay, mSurface, mSurface, mContext[0]);
    GLuint fbo, renderbuffer;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glGenRenderbuffers(1, &renderbuffer);
    glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8_OES, 40, 40);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, renderbuffer);
    ASSERT_EQ(GLenum{GL_FRAMEBUFFER_COMPLETE}, glCheckFramebufferStatus(GL_FRAMEBUFFER));
    glClearColor(0.f, 0.f, 0.f, 0.f);
    glClear(GL_COLOR_BUFFER_BIT);

    // Compile the shader.
    GLuint program = 0;
    CompileProgram(desc.layers > 1 ? kVertexShaderEs3 : kVertexShader,
                   desc.layers > 1 ? kArrayFragmentShaderEs3 : kFragmentShader,
                   &program);
    glUseProgram(program);
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());

    // Draw a textured quad. Use a constant attribute for depth.
    GLint a_position_location = glGetAttribLocation(program, "aPosition");
    GLint a_depth_location = glGetAttribLocation(program, "aDepth");
    glVertexAttribPointer(a_position_location, 2, GL_FLOAT, GL_TRUE, 0, kQuadPositions);
    glVertexAttrib1f(a_depth_location, 0.f);
    glEnableVertexAttribArray(a_position_location);
    glUniform1i(glGetUniformLocation(program, "uTexture"), 1);
    if (desc.layers > 1) {
        glUniform1f(glGetUniformLocation(program, "uLayer"), static_cast<float>(desc.layers - 1));
    }
    glViewport(0, 0, 40, 40);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());

    // Check the rendered pixels. The lower left area of the checkerboard will
    // be either transparent or opaque black depending on whether the texture
    // format has an alpha channel.
    const GoldenColor kCBBlack = FormatHasAlpha(desc.format) ? kZero : kBlack;
    std::vector<GoldenPixel> goldens{
        {5, 35, kZero}, {15, 35, kZero},    {25, 35, kZero}, {35, 35, kZero},
        {5, 25, kZero}, {15, 25, kRed},     {25, 25, kBlue}, {35, 25, kZero},
        {5, 15, kZero}, {15, 15, kCBBlack}, {25, 15, kRed},  {35, 15, kZero},
        {5, 5,  kZero}, {15, 5,  kZero},    {25, 5,  kZero}, {35, 5,  kZero},
    };
    CheckGoldenPixels(goldens, /*float_format=*/false, /*alpha_format=*/true);

    // Tear down the GL objects.
    glDeleteProgram(program);
    glDeleteFramebuffers(1, &fbo);
    glDeleteRenderbuffers(1, &renderbuffer);
    glDeleteTextures(1, &texture[0]);
    eglMakeCurrent(mDisplay, mSurface, mSurface, mContext[1]);
    glDeleteFramebuffers(1, &texture_fbo);
    glDeleteTextures(1, &texture[1]);
    eglDestroyImageKHR(mDisplay, egl_image);
    AHardwareBuffer_release(buffer);
}

INSTANTIATE_TEST_CASE_P(
    SingleLayer,
    AHardwareBufferColorFormatTest,
    ::testing::Values(
        AHardwareBuffer_Desc{10, 20, 1, AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{20, 10, 1, AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{16, 20, 1, AHARDWAREBUFFER_FORMAT_R8G8B8_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{10, 20, 1, AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{10, 20, 1, AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT, 0, 0, 0, 0},
        AHardwareBuffer_Desc{10, 20, 1, AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM, 0, 0, 0, 0}));

INSTANTIATE_TEST_CASE_P(
    MultipleLayers,
    AHardwareBufferColorFormatTest,
    ::testing::Values(
        AHardwareBuffer_Desc{25, 16, 7, AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{32, 32, 4, AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{30, 30, 3, AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{50, 50, 4, AHARDWAREBUFFER_FORMAT_R8G8B8_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{20, 10, 2, AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{20, 20, 4, AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT, 0, 0, 0, 0},
        AHardwareBuffer_Desc{30, 20, 16, AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM, 0, 0, 0, 0}));

}  // namespace android
