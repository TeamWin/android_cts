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
#include <GLES3/gl31.h>

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

union IntFloat {
    uint32_t i;
    float f;
};

// Copied from android.util.Half
float FloatFromHalf(uint16_t bits) {
    uint32_t s = bits & 0x8000;
    uint32_t e = (bits & 0x7C00) >> 10;
    uint32_t m = bits & 0x3FF;
    uint32_t outE = 0;
    uint32_t outM = 0;
    if (e == 0) { // Denormal or 0
        if (m != 0) {
            // Convert denorm fp16 into normalized fp32
            IntFloat uif;
            uif.i = (126 << 23);
            float denormal = uif.f;
            uif.i += m;
            float o = uif.f - denormal;
            return s == 0 ? o : -o;
        }
    } else {
        outM = m << 13;
        if (e == 0x1f) { // Infinite or NaN
            outE = 0xff;
        } else {
            outE = e - 15 + 127;
        }
    }
    IntFloat result;
    result.i = (s << 16) | (outE << 23) | outM;
    return result.f;
}

bool FormatHasAlpha(uint32_t format) {
    switch (format) {
        case AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM:
        case AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT:
        case AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM:
        // This may look scary, but fortunately AHardwareBuffer formats and GL pixel formats
        // do not overlap.
        case GL_RGBA8:
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
        case AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM:
        case GL_RGB8: {
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
        case AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM:
        case GL_RGBA8: {
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

// Draws the following checkerboard pattern using glScissor and glClear.
// The number after the color is the stencil value and the floating point number is the depth value.
//        +-----+-----+ (W, H)
//        | OR1 | Ob2 |
//        | 0.5 | 0.0 |
//        +-----+-----+  TB = transparent black
//        | TB0 | OR1 |  OR = opaque red
//        | 1.0 | 0.5 |  Ob = opaque blue
// (0, 0) +-----+-----+
//
void DrawCheckerboard(int width, int height) {
    glEnable(GL_SCISSOR_TEST);
    const GLbitfield all_bits = GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT;

    glClearColor(1.f, 0.f, 0.f, 1.f);
    glClearDepthf(0.5f);
    glClearStencil(1);
    glScissor(0, 0, width, height);
    glClear(all_bits);

    glClearColor(0.f, 0.f, 0.f, 0.f);
    glClearDepthf(1.0f);
    glClearStencil(0);
    glScissor(0, 0, width / 2, height / 2);
    glClear(all_bits);

    glClearColor(0.f, 0.f, 1.f, 1.f);
    glClearDepthf(0.f);
    glClearStencil(2);
    glScissor(width / 2, height / 2, width / 2, height / 2);
    glClear(all_bits);

    glDisable(GL_SCISSOR_TEST);
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
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

void CheckGoldenPixel(const GoldenPixel& golden, uint8_t* pixel, bool alpha_format) {
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
    EXPECT_EQ(golden.color == kRed ? 255 : 0, pixel[0])
        << "Red doesn't match at X=" << golden.x << ", Y=" << golden.y;
    EXPECT_EQ(0, pixel[1])
        << "Green doesn't match at X=" << golden.x << ", Y=" << golden.y;
    EXPECT_EQ(golden.color == kBlue ? 255 : 0, pixel[2])
        << "Blue doesn't match at X=" << golden.x << ", Y=" << golden.y;
    // Formats without alpha should be read as opaque.
    EXPECT_EQ((golden.color != kZero || !alpha_format) ? 255 : 0, pixel[3])
        << "Alpha doesn't match at X=" << golden.x << ", Y=" << golden.y;
}

void CheckGoldenPixel(const GoldenPixel& golden, float* pixel, bool alpha_format) {
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
    EXPECT_EQ(golden.color == kRed ? 1.f : 0.f, pixel[0])
        << "Red doesn't match at X=" << golden.x << ", Y=" << golden.y;
    EXPECT_EQ(0.f, pixel[1])
        << "Green doesn't match at X=" << golden.x << ", Y=" << golden.y;
    EXPECT_EQ(golden.color == kBlue ? 1.f : 0.f, pixel[2])
        << "Blue doesn't match at X=" << golden.x << ", Y=" << golden.y;
    // Formats without alpha should be read as opaque.
    EXPECT_EQ((golden.color != kZero || !alpha_format) ? 1.f : 0.f, pixel[3])
        << "Alpha doesn't match at X=" << golden.x << ", Y=" << golden.y;
}

void CheckGoldenPixels(const std::vector<GoldenPixel>& goldens, bool float_format, bool alpha_format) {
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    // In OpenGL, Y axis grows up, so bottom = minimum Y coordinate.
    int bottom = INT_MAX, left = INT_MAX, right = 0, top = 0;
    for (const GoldenPixel& golden : goldens) {
        left = std::min(left, golden.x);
        right = std::max(right, golden.x);
        bottom = std::min(bottom, golden.y);
        top = std::max(top, golden.y);
        if (float_format) {
            float pixel[4] = {0.5f, 0.5f, 0.5f, 0.5f};
            glReadPixels(golden.x, golden.y, 1, 1, GL_RGBA, GL_FLOAT, pixel);
            CheckGoldenPixel(golden, pixel, alpha_format);
        } else {
            uint8_t pixel[4] = {127, 127, 127, 127};
            glReadPixels(golden.x, golden.y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            CheckGoldenPixel(golden, pixel, alpha_format);
        }
    }
    // Repeat the test, but read back all the necessary pixels in a single glReadPixels call.
    const int width = right - left + 1;
    const int height = top - bottom + 1;
    if (float_format) {
        std::unique_ptr<float[]> pixels(new float[width * height * 4]);
        glReadPixels(left, bottom, width, height, GL_RGBA, GL_FLOAT, pixels.get());
        EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
        for (const GoldenPixel& golden : goldens) {
            float* pixel = pixels.get() + ((golden.y - bottom) * width + golden.x - left) * 4;
            CheckGoldenPixel(golden, pixel, alpha_format);
        }
    } else {
        std::unique_ptr<uint8_t[]> pixels(new uint8_t[width * height * 4]);
        glReadPixels(left, bottom, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels.get());
        EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
        for (const GoldenPixel& golden : goldens) {
            uint8_t* pixel = pixels.get() + ((golden.y - bottom) * width + golden.x - left) * 4;
            CheckGoldenPixel(golden, pixel, alpha_format);
        }
    }
}

// Vertex shader that draws a textured shape.
const char* kVertexShader = R"glsl(#version 100
    attribute vec2 aPosition;
    attribute float aDepth;
    uniform mediump float uScale;
    varying mediump vec2 vTexCoords;
    void main() {
        vTexCoords = (vec2(1.0) + aPosition) * 0.5;
        gl_Position.xy = aPosition * uScale;
        gl_Position.z = aDepth;
        gl_Position.w = 1.0;
    }
)glsl";

const char* kTextureFragmentShader = R"glsl(#version 100
    precision mediump float;
    varying mediump vec2 vTexCoords;
    uniform lowp sampler2D uTexture;
    void main() {
        gl_FragColor = texture2D(uTexture, vTexCoords);
    }
)glsl";

const char* kColorFragmentShader = R"glsl(#version 100
    precision mediump float;
    uniform lowp vec4 uColor;
    void main() {
        gl_FragColor = uColor;
    }
)glsl";

const char* kVertexShaderEs3 = R"glsl(#version 300 es
    in vec2 aPosition;
    in float aDepth;
    uniform mediump float uScale;
    out mediump vec2 vTexCoords;
    void main() {
        vTexCoords = (vec2(1.0) + aPosition) * 0.5;
        gl_Position.xy = aPosition * uScale;
        gl_Position.z = aDepth;
        gl_Position.w = 1.0;
    }
)glsl";

const char* kSsboVertexShaderEs3 = R"glsl(#version 310 es
    in vec2 aPosition;
    in float aDepth;
    uniform mediump float uScale;
    layout(std430, binding=0) buffer Output {
        vec2 data[];
    } bOutput;
    out mediump vec2 vTexCoords;
    void main() {
        bOutput.data[gl_VertexID] = aPosition;
        vTexCoords = (vec2(1.0) + aPosition) * 0.5;
        gl_Position.xy = aPosition * uScale;
        gl_Position.z = aDepth;
        gl_Position.w = 1.0;
    }
)glsl";

const char* kColorFragmentShaderEs3 = R"glsl(#version 300 es
    precision mediump float;
    uniform lowp vec4 uColor;
    out mediump vec4 color;
    void main() {
        color = uColor;
    }
)glsl";

const char* kArrayFragmentShaderEs3 = R"glsl(#version 300 es
    precision mediump float;
    in mediump vec2 vTexCoords;
    uniform lowp sampler2DArray uTexture;
    uniform mediump float uLayer;
    out mediump vec4 color;
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
const GLsizei kQuadVertexCount = 6;

// Interleaved X, Y and Z coordinates for 4 triangles forming a "pyramid" as
// seen from above. The center vertex has Z=1, while the edge vertices have Z=-1.
// It looks like this:
//
//        +---+ 1, 1
//        |\ /|
//        | x |
//        |/ \|
// -1, -1 +---+
const float kPyramidPositions[] = {
    -1.f, -1.f, -1.f, 0.f, 0.f, 1.f, -1.f, 1.f, -1.f,
    -1.f, 1.f, -1.f, 0.f, 0.f, 1.f, 1.f, 1.f, -1.f,
    1.f, 1.f, -1.f, 0.f, 0.f, 1.f, 1.f, -1.f, -1.f,
    1.f, -1.f, -1.f, 0.f, 0.f, 1.f, -1.f, -1.f, -1.f,
};
const GLsizei kPyramidVertexCount = 12;

}  // namespace

class AHardwareBufferGLTest : public ::testing::TestWithParam<AHardwareBuffer_Desc> {
public:
    enum AttachmentType {
        kNone,
        kBufferAsTexture,
        kBufferAsRenderbuffer,
        kRenderbuffer,
    };

    void SetUp() override;
    virtual bool SetUpBuffer(const AHardwareBuffer_Desc& desc);
    void SetUpProgram(const char* vertex_source, const char* fragment_source,
                      const float* mesh, float scale, int texture_unit = 0);
    void SetUpTexture(const AHardwareBuffer_Desc& desc, int unit);
    void SetUpBufferObject(uint32_t size, GLenum target, GLbitfield flags);
    void SetUpFramebuffer(int width, int height, AttachmentType color,
                          AttachmentType depth = kNone, AttachmentType stencil = kNone,
                          AttachmentType depth_stencil = kNone);
    void TearDown() override;

    void MakeCurrent(int which) {
        if (GetParam().stride != 0) return;
        mWhich = which;
        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext[mWhich]);
    }
    bool HasGLExtension(const std::string& s) {
        return mGLExtensions.find(s) != mGLExtensions.end();
    }

protected:
    std::set<std::string> mEGLExtensions;
    std::set<std::string> mGLExtensions;
    EGLDisplay mDisplay = EGL_NO_DISPLAY;
    EGLSurface mSurface = EGL_NO_SURFACE;
    EGLContext mContext[2] = { EGL_NO_CONTEXT, EGL_NO_CONTEXT };
    int mWhich = 0;  // Which of the two EGL contexts is current.
    int mContextCount = 2;  // Will be 2 in AHB test cases and 1 in pure GL test cases.
    int mGLVersion = 0;  // major_version * 10 + minor_version

    AHardwareBuffer* mBuffer = nullptr;
    EGLImageKHR mEGLImage = EGL_NO_IMAGE_KHR;
    GLenum mTexTarget = GL_NONE;
    GLuint mProgram = 0;
    GLuint mTextures[2] = { 0, 0 };
    GLuint mBufferObjects[2] = { 0, 0 };
    GLuint mFramebuffers[2] = { 0, 0 };
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

    // Parse GL extension strings into a set for easier processing.
    std::istringstream glext_stream(reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS)));
    mGLExtensions = std::set<std::string>{
        std::istream_iterator<std::string>{glext_stream},
        std::istream_iterator<std::string>{}
    };
    // Parse GL version. Find the first dot, then treat the digit before it as the major version
    // and the digit after it as the minor version.
    std::string version = reinterpret_cast<const char*>(glGetString(GL_VERSION));
    std::size_t dot_pos = version.find('.');
    ASSERT_TRUE(dot_pos > 0 && dot_pos < version.size() - 1);
    mGLVersion = (version[dot_pos - 1] - '0') * 10 + (version[dot_pos + 1] - '0');
    ASSERT_GE(mGLVersion, 20);
}

bool AHardwareBufferGLTest::SetUpBuffer(const AHardwareBuffer_Desc& desc) {
    mTexTarget = desc.layers > 1 ? GL_TEXTURE_2D_ARRAY : GL_TEXTURE_2D;
    if (desc.layers > 1 && mGLVersion < 30) return false;
    // Nonzero stride indicates that desc.format should be interpreted as a GL format
    // and the test should be run in a single context, without using AHardwareBuffer.
    // This simplifies verifying that the test behaves as expected even if the
    // AHardwareBuffer format under test is not supported.
    if (desc.stride != 0) {
        mContextCount = 1;
        return true;
    }

    int result = AHardwareBuffer_allocate(&desc, &mBuffer);
    // Skip if this format cannot be allocated.
    if (result != NO_ERROR) return false;
    // Do not create the EGLImage if this is a blob format.
    if (desc.format == AHARDWAREBUFFER_FORMAT_BLOB) return true;

    const EGLint attrib_list[] = { EGL_NONE };
    mEGLImage = eglCreateImageKHR(
        mDisplay, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID,
        eglGetNativeClientBufferANDROID(mBuffer), attrib_list);
    EXPECT_NE(EGL_NO_IMAGE_KHR, mEGLImage);
    return mEGLImage != EGL_NO_IMAGE_KHR;
}

void AHardwareBufferGLTest::SetUpProgram(const char* vertex_source, const char* fragment_source,
                                         const float* mesh, float scale, int texture_unit) {
    ASSERT_EQ(0U, mProgram);
    GLint status = GL_FALSE;
    mProgram = glCreateProgram();
    GLuint vertex_shader = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vertex_shader, 1, &vertex_source, nullptr);
    glCompileShader(vertex_shader);
    glGetShaderiv(vertex_shader, GL_COMPILE_STATUS, &status);
    ASSERT_EQ(GL_TRUE, status) << "Vertex shader compilation failed";
    GLuint fragment_shader = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fragment_shader, 1, &fragment_source, nullptr);
    glCompileShader(fragment_shader);
    glGetShaderiv(fragment_shader, GL_COMPILE_STATUS, &status);
    ASSERT_EQ(GL_TRUE, status) << "Fragment shader compilation failed";
    glAttachShader(mProgram, vertex_shader);
    glAttachShader(mProgram, fragment_shader);
    glLinkProgram(mProgram);
    glGetProgramiv(mProgram, GL_LINK_STATUS, &status);
    ASSERT_EQ(GL_TRUE, status) << "Shader program linking failed";
    glDetachShader(mProgram, vertex_shader);
    glDetachShader(mProgram, fragment_shader);
    glDeleteShader(vertex_shader);
    glDeleteShader(fragment_shader);
    glUseProgram(mProgram);
    ASSERT_EQ(GLenum{GL_NO_ERROR}, glGetError());

    GLint a_position_location = glGetAttribLocation(mProgram, "aPosition");
    GLint a_depth_location = glGetAttribLocation(mProgram, "aDepth");
    if (mesh == kQuadPositions) {
        glVertexAttribPointer(a_position_location, 2, GL_FLOAT, GL_TRUE, 0, kQuadPositions);
        glVertexAttrib1f(a_depth_location, 0.f);
        glEnableVertexAttribArray(a_position_location);
    } else if (mesh == kPyramidPositions) {
        glVertexAttribPointer(a_position_location, 2, GL_FLOAT, GL_TRUE, 3 * sizeof(float),
                              kPyramidPositions);
        glVertexAttribPointer(a_depth_location, 1, GL_FLOAT, GL_TRUE, 3 * sizeof(float),
                              kPyramidPositions + 2);
        glEnableVertexAttribArray(a_position_location);
        glEnableVertexAttribArray(a_depth_location);
    } else {
        FAIL() << "Unknown mesh";
    }
    glUniform1f(glGetUniformLocation(mProgram, "uScale"), scale);
    GLint u_color_location = glGetUniformLocation(mProgram, "uColor");
    if (u_color_location >= 0) {
        glUniform4f(u_color_location, 1.f, 0.f, 0.f, 1.f);
    }
    GLint u_texture_location = glGetUniformLocation(mProgram, "uTexture");
    if (u_texture_location >= 0) {
        glUniform1i(u_texture_location, texture_unit);
    }
    GLint u_layer_location = glGetUniformLocation(mProgram, "uLayer");
    if (u_layer_location >= 0) {
        glUniform1f(u_layer_location, static_cast<float>(GetParam().layers));
    }
}

void AHardwareBufferGLTest::SetUpTexture(const AHardwareBuffer_Desc& desc, int unit) {
    GLuint& texture = mTextures[mWhich];
    glGenTextures(1, &texture);
    glActiveTexture(GL_TEXTURE0 + unit);
    glBindTexture(mTexTarget, texture);
    if (desc.stride == 0) {
        glEGLImageTargetTexture2DOES(mTexTarget, static_cast<GLeglImageOES>(mEGLImage));
    } else {
        // Stride is nonzero, so interpret desc.format as a GL format.
        if (desc.layers > 1) {
            glTexStorage3D(mTexTarget, 1, desc.format, desc.width, desc.height, desc.layers);
        } else if (mGLVersion >= 30) {
            glTexStorage2D(mTexTarget, 1, desc.format, desc.width, desc.height);
        } else {
            GLenum format = 0, type = 0;
            switch (desc.format) {
                case GL_RGB8:
                    format = GL_RGB;
                    type = GL_UNSIGNED_BYTE;
                    break;
                case GL_RGBA8:
                    format = GL_RGBA;
                    type = GL_UNSIGNED_BYTE;
                    break;
                case GL_DEPTH_COMPONENT16:
                    format = GL_DEPTH_COMPONENT;
                    type = GL_UNSIGNED_SHORT;
                    break;
                case GL_DEPTH24_STENCIL8:
                    format = GL_DEPTH_STENCIL;
                    type = GL_UNSIGNED_INT_24_8;
                default:
                    FAIL() << "Unrecognized GL format"; break;
            }
            glTexImage2D(mTexTarget, 0, desc.format, desc.width, desc.height, 0,
                         format, type, nullptr);
        }
    }
    ASSERT_EQ(GLenum{GL_NO_ERROR}, glGetError());
}

void AHardwareBufferGLTest::SetUpBufferObject(uint32_t size, GLenum target, GLbitfield flags) {
    glGenBuffers(1, &mBufferObjects[mWhich]);
    glBindBuffer(target, mBufferObjects[mWhich]);
    glBufferStorageExternalEXT(target, 0, size,
                               eglGetNativeClientBufferANDROID(mBuffer), flags);
    ASSERT_EQ(GLenum{GL_NO_ERROR}, glGetError());
}

void AHardwareBufferGLTest::SetUpFramebuffer(int width, int height,
                                             AttachmentType color,
                                             AttachmentType depth,
                                             AttachmentType stencil,
                                             AttachmentType depth_stencil) {
    AHardwareBuffer_Desc desc = GetParam();
    AttachmentType attachment_types[] = { color, depth, stencil, depth_stencil };
    GLenum attachment_points[] = {
        GL_COLOR_ATTACHMENT0, GL_DEPTH_ATTACHMENT, GL_STENCIL_ATTACHMENT,
        GL_DEPTH_STENCIL_ATTACHMENT
    };
    GLenum default_formats[] = {
      GL_RGBA8, GL_DEPTH_COMPONENT16, GL_STENCIL_INDEX8, GL_DEPTH24_STENCIL8
    };
    GLuint& fbo = mFramebuffers[mWhich];
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    for (int i = 0; i < 3; ++i) {
        switch (attachment_types[i]) {
            case kNone:
                break;
            case kBufferAsTexture:
                ASSERT_NE(0U, mTextures[mWhich]);
                if (mTexTarget == GL_TEXTURE_2D) {
                    glFramebufferTexture2D(GL_FRAMEBUFFER, attachment_points[i], mTexTarget,
                                           mTextures[mWhich], 0);
                } else {
                    // desc.layers is never modified in the test body.
                    glFramebufferTextureLayer(GL_FRAMEBUFFER, attachment_points[i],
                                              mTextures[mWhich], 0, desc.layers - 1);
                }
                break;
            case kBufferAsRenderbuffer: {
                GLuint renderbuffer = 0;
                glGenRenderbuffers(1, &renderbuffer);
                glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
                if (desc.stride == 0) {
                    glEGLImageTargetRenderbufferStorageOES(GL_RENDERBUFFER,
                                                           static_cast<GLeglImageOES>(mEGLImage));
                } else {
                    glRenderbufferStorage(GL_RENDERBUFFER, desc.format, width, height);
                }
                glFramebufferRenderbuffer(GL_FRAMEBUFFER, attachment_points[i],
                                          GL_RENDERBUFFER, renderbuffer);
                break;
            }
            case kRenderbuffer: {
                GLuint renderbuffer = 0;
                glGenRenderbuffers(1, &renderbuffer);
                glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
                glRenderbufferStorage(GL_RENDERBUFFER, default_formats[i], width, height);
                glFramebufferRenderbuffer(GL_FRAMEBUFFER, attachment_points[i],
                                          GL_RENDERBUFFER, renderbuffer);
                break;
            }
            default: FAIL() << "Unrecognized binding type";
        }
    }
    ASSERT_EQ(GLenum{GL_NO_ERROR}, glGetError());
    ASSERT_EQ(GLenum{GL_FRAMEBUFFER_COMPLETE},
              glCheckFramebufferStatus(GL_FRAMEBUFFER));
    glViewport(0, 0, width, height);
}

void AHardwareBufferGLTest::TearDown() {
    eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    for (int i = 0; i < 2; ++i) {
        // All GL objects will be deleted along with the context.
        eglDestroyContext(mDisplay, mContext[i]);
    }
    if (mBuffer != nullptr) {
        eglDestroyImageKHR(mDisplay, mEGLImage);
        AHardwareBuffer_release(mBuffer);
    }
    if (mSurface != EGL_NO_SURFACE) {
        eglDestroySurface(mDisplay, mSurface);
    }
    eglTerminate(mDisplay);
}


class AHardwareBufferBlobFormatTest : public AHardwareBufferGLTest {
public:
    bool SetUpBuffer(const AHardwareBuffer_Desc& desc) override {
        if (!HasGLExtension("GL_EXT_external_buffer")) return false;
        return AHardwareBufferGLTest::SetUpBuffer(desc);
    }
};

// Verifies that a blob buffer can be used to supply vertex attributes to a shader.
TEST_P(AHardwareBufferBlobFormatTest, GpuDataBufferVertexBuffer) {
    AHardwareBuffer_Desc desc = GetParam();
    desc.width = sizeof kQuadPositions;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_DATA_BUFFER;
    if (!SetUpBuffer(desc)) return;

    SetUpProgram(kVertexShader, kColorFragmentShader, kQuadPositions, 0.5f);

    for (int i = 0; i < mContextCount; ++i) {
        MakeCurrent(i);
        SetUpBufferObject(desc.width, GL_ARRAY_BUFFER,
                          GL_DYNAMIC_STORAGE_BIT_EXT | GL_MAP_WRITE_BIT);
    }
    float* data = static_cast<float*>(
        glMapBufferRange(GL_ARRAY_BUFFER, 0, desc.width,
                         GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT));
    memcpy(data, kQuadPositions, desc.width);
    glUnmapBuffer(GL_ARRAY_BUFFER);
    glFinish();

    MakeCurrent(0);
    SetUpFramebuffer(40, 40, kRenderbuffer);
    GLint a_position_location = glGetAttribLocation(mProgram, "aPosition");
    glVertexAttribPointer(a_position_location, 2, GL_FLOAT, GL_TRUE, 0, 0);
    glDrawArrays(GL_TRIANGLES, 0, kQuadVertexCount);
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());

    // Check the rendered pixels. There should be a red square in the middle.
    std::vector<GoldenPixel> goldens{
        {5, 35, kZero}, {15, 35, kZero}, {25, 35, kZero}, {35, 35, kZero},
        {5, 25, kZero}, {15, 25, kRed},  {25, 25, kRed},  {35, 25, kZero},
        {5, 15, kZero}, {15, 15, kRed},  {25, 15, kRed},  {35, 15, kZero},
        {5,  5, kZero}, {15,  5, kZero}, {25, 5,  kZero}, {35, 5,  kZero},
    };
    CheckGoldenPixels(goldens, /*float_format=*/false, /*alpha_format=*/true);
}

// Verifies that a blob buffer can be directly accessed from the CPU.
TEST_P(AHardwareBufferBlobFormatTest, GpuDataBufferCpuWrite) {
    AHardwareBuffer_Desc desc = GetParam();
    desc.width = sizeof kQuadPositions;
    desc.usage = AHARDWAREBUFFER_USAGE_CPU_WRITE_RARELY | AHARDWAREBUFFER_USAGE_GPU_DATA_BUFFER;
    if (!SetUpBuffer(desc)) return;

    SetUpProgram(kVertexShader, kColorFragmentShader, kQuadPositions, 0.5f);

    for (int i = 0; i < mContextCount; ++i) {
        MakeCurrent(i);
        SetUpBufferObject(desc.width, GL_ARRAY_BUFFER,
                          GL_DYNAMIC_STORAGE_BIT_EXT | GL_MAP_WRITE_BIT);
    }

    // Clear the buffer to zero
    std::vector<float> zero_data(desc.width / sizeof(float), 0.f);
    glBufferSubData(GL_ARRAY_BUFFER, 0, desc.width, zero_data.data());
    glFinish();

    // Upload actual data with CPU access
    float* data = nullptr;
    int result = AHardwareBuffer_lock(mBuffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_RARELY,
                                      -1, nullptr, reinterpret_cast<void**>(&data));
    ASSERT_EQ(NO_ERROR, result);
    memcpy(data, kQuadPositions, desc.width);
    AHardwareBuffer_unlock(mBuffer, nullptr);

    // Render the buffer in the other context
    MakeCurrent(0);
    SetUpFramebuffer(40, 40, kRenderbuffer);
    GLint a_position_location = glGetAttribLocation(mProgram, "aPosition");
    glVertexAttribPointer(a_position_location, 2, GL_FLOAT, GL_TRUE, 0, 0);
    glDrawArrays(GL_TRIANGLES, 0, kQuadVertexCount);
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());

    // Check the rendered pixels. There should be a red square in the middle.
    std::vector<GoldenPixel> goldens{
        {5, 35, kZero}, {15, 35, kZero}, {25, 35, kZero}, {35, 35, kZero},
        {5, 25, kZero}, {15, 25, kRed},  {25, 25, kRed},  {35, 25, kZero},
        {5, 15, kZero}, {15, 15, kRed},  {25, 15, kRed},  {35, 15, kZero},
        {5,  5, kZero}, {15,  5, kZero}, {25, 5,  kZero}, {35, 5,  kZero},
    };
    CheckGoldenPixels(goldens, /*float_format=*/false, /*alpha_format=*/true);
}

// Verifies that data written into a blob buffer from the GPU can be read on the CPU.
TEST_P(AHardwareBufferBlobFormatTest, GpuDataBufferCpuRead) {
    AHardwareBuffer_Desc desc = GetParam();
    desc.width = sizeof kQuadPositions;
    desc.usage = AHARDWAREBUFFER_USAGE_CPU_READ_RARELY | AHARDWAREBUFFER_USAGE_GPU_DATA_BUFFER;
    // Shader storage buffer objects are only supported in OpenGL ES 3.1+
    if (mGLVersion < 31) return;
    if (!SetUpBuffer(desc)) return;

    for (int i = 0; i < mContextCount; ++i) {
        MakeCurrent(i);
        SetUpBufferObject(desc.width, GL_SHADER_STORAGE_BUFFER,
                          GL_DYNAMIC_STORAGE_BIT_EXT | GL_MAP_READ_BIT);
    }

    // Clear the buffer to zero
    std::vector<float> zero_data(desc.width / sizeof(float), 0.f);
    glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, desc.width, zero_data.data());
    glFinish();

    // Write into the buffer with a shader
    SetUpFramebuffer(40, 40, kRenderbuffer);
    SetUpProgram(kSsboVertexShaderEs3, kColorFragmentShaderEs3, kQuadPositions, 0.5f);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, mBufferObjects[mWhich]);
    glDrawArrays(GL_TRIANGLES, 0, kQuadVertexCount);
    glFinish();
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());

    // Inspect the data written into the buffer using CPU access.
    MakeCurrent(0);
    float* data = nullptr;
    int result = AHardwareBuffer_lock(mBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_RARELY,
                                      -1, nullptr, reinterpret_cast<void**>(&data));
    ASSERT_EQ(NO_ERROR, result);
    std::ostringstream s;
    for (int i = 0; i < 12; ++i) {
        s << data[i] << ", ";
    }
    EXPECT_EQ(0, memcmp(kQuadPositions, data, desc.width)) << s.str();
    AHardwareBuffer_unlock(mBuffer, nullptr);
}

// The first case tests an ordinary GL buffer, while the second one tests an AHB-backed buffer.
INSTANTIATE_TEST_CASE_P(
    BlobBuffer,
    AHardwareBufferBlobFormatTest,
    ::testing::Values(
        AHardwareBuffer_Desc{1, 1, 1, AHARDWAREBUFFER_FORMAT_BLOB, 0, 0, 0, 0}));


class AHardwareBufferColorFormatTest : public AHardwareBufferGLTest {};

// Verify that when allocating an AHardwareBuffer succeeds with GPU_COLOR_OUTPUT,
// it can be bound as a framebuffer attachment, glClear'ed and then read from
// another context using glReadPixels.
TEST_P(AHardwareBufferColorFormatTest, GpuColorOutputIsRenderable) {
    AHardwareBuffer_Desc desc = GetParam();
    desc.width = 100;
    desc.height = 100;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT;
    // This test does not make sense for layered buffers - don't bother testing them.
    if (desc.layers > 1) return;
    if (!SetUpBuffer(desc)) return;

    for (int i = 0; i < mContextCount; ++i) {
        MakeCurrent(i);
        SetUpFramebuffer(desc.width, desc.height, kBufferAsRenderbuffer);
    }

    // Draw a simple checkerboard pattern in the second context, which will
    // be current after the loop above, then read it in the first.
    DrawCheckerboard(desc.width, desc.height);
    glFinish();

    MakeCurrent(0);
    std::vector<GoldenPixel> goldens{
        {10, 90, kRed},  {40, 90, kRed},  {60, 90, kBlue}, {90, 90, kBlue},
        {10, 60, kRed},  {40, 60, kRed},  {60, 60, kBlue}, {90, 60, kBlue},
        {10, 40, kZero}, {40, 40, kZero}, {60, 40, kRed},  {90, 40, kRed},
        {10, 10, kZero}, {40, 10, kZero}, {60, 10, kRed},  {90, 10, kRed},
    };
    CheckGoldenPixels(goldens, FormatIsFloat(desc.format), FormatHasAlpha(desc.format));
}

// Verifies that the content of GPU_COLOR_OUTPUT buffers can be read on the CPU.
TEST_P(AHardwareBufferColorFormatTest, GpuColorOutputCpuRead) {
    AHardwareBuffer_Desc desc = GetParam();
    desc.width = 10;
    desc.height = 10;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT | AHARDWAREBUFFER_USAGE_CPU_READ_RARELY;
    // This test does not make sense for GL formats. Layered buffers do not support CPU access.
    if (desc.stride != 0 || desc.layers > 1) return;
    if (!SetUpBuffer(desc)) return;

    MakeCurrent(1);
    SetUpFramebuffer(desc.width, desc.height, kBufferAsRenderbuffer);
    // Draw a simple checkerboard pattern in the second context, which will
    // be current after the loop above, then read it in the first.
    DrawCheckerboard(desc.width, desc.height);
    glFinish();

    MakeCurrent(0);
    // Retrieve the stride and lock the buffer for CPU access.
    AHardwareBuffer_describe(mBuffer, &desc);
    void* data = nullptr;
    int result = AHardwareBuffer_lock(mBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_RARELY,
                                      -1, nullptr, &data);
    ASSERT_EQ(NO_ERROR, result);

    std::vector<GoldenPixel> goldens{
        {0, 9, kRed},  {4, 9, kRed},  {5, 9, kBlue}, {9, 9, kBlue},
        {0, 5, kRed},  {4, 5, kRed},  {5, 5, kBlue}, {9, 5, kBlue},
        {0, 4, kZero}, {4, 4, kZero}, {5, 4, kRed},  {9, 4, kRed},
        {0, 0, kZero}, {4, 0, kZero}, {5, 0, kRed},  {9, 0, kRed},
    };
    for (const GoldenPixel& golden : goldens) {
        ptrdiff_t row_offset = golden.y * desc.stride;
        switch (desc.format) {
            case AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM:
            case AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM: {
                uint8_t* pixel = reinterpret_cast<uint8_t*>(data) + (row_offset + golden.x) * 4;
                uint8_t pixel_to_check[4];
                memcpy(pixel_to_check, pixel, 4);
                if (desc.format == AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM) {
                    pixel_to_check[3] = 255;
                }
                CheckGoldenPixel(golden, pixel_to_check, FormatHasAlpha(desc.format));
                break;
            }
            case AHARDWAREBUFFER_FORMAT_R8G8B8_UNORM: {
                uint8_t* pixel = reinterpret_cast<uint8_t*>(data) + (row_offset + golden.x) * 3;
                uint8_t pixel_to_check[4];
                memcpy(pixel_to_check, pixel, 3);
                pixel_to_check[3] = 255;
                CheckGoldenPixel(golden, pixel_to_check, /*alpha_format=*/false);
                break;
            }
            case AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM: {
                uint16_t* pixel = reinterpret_cast<uint16_t*>(
                    reinterpret_cast<uint8_t*>(data) + (row_offset + golden.x) * 2);
                uint8_t pixel_to_check[4] = {
                    static_cast<uint8_t>(((*pixel & 0xF800) >> 11) * (255.f/31.f)),
                    static_cast<uint8_t>(((*pixel & 0x07E0) >> 5) * (255.f/63.f)),
                    static_cast<uint8_t>((*pixel & 0x001F) * (255.f/31.f)),
                    255,
                };
                CheckGoldenPixel(golden, pixel_to_check, /*alpha_format=*/false);
                break;
            }
            case AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT: {
                uint16_t* pixel = reinterpret_cast<uint16_t*>(
                    reinterpret_cast<uint8_t*>(data) + (row_offset + golden.x) * 8);
                float pixel_to_check[4];
                for (int i = 0; i < 4; ++i) {
                    pixel_to_check[i] = FloatFromHalf(pixel[i]);
                }
                CheckGoldenPixel(golden, pixel_to_check, /*alpha_format=*/true);
                break;
            }
            case AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM: {
                uint32_t* pixel = reinterpret_cast<uint32_t*>(
                    reinterpret_cast<uint8_t*>(data) + (row_offset + golden.x) * 4);
                uint8_t pixel_to_check[4] = {
                    static_cast<uint8_t>((*pixel & 0x000003FF) * (255.f/1023.f)),
                    static_cast<uint8_t>(((*pixel & 0x000FFC00) >> 10) * (255.f/1023.f)),
                    static_cast<uint8_t>(((*pixel & 0x3FF00000) >> 20) * (255.f/1023.f)),
                    static_cast<uint8_t>(((*pixel & 0xC0000000) >> 30) * (255.f/3.f)),
                };
                CheckGoldenPixel(golden, pixel_to_check, /*alpha_format=*/true);
                break;
            }
            default: FAIL() << "Unrecognized AHardwareBuffer format"; break;
        }
    }
    AHardwareBuffer_unlock(mBuffer, nullptr);
}

// Verify that when allocating an AHardwareBuffer succeeds with GPU_SAMPLED_IMAGE,
// it can be bound as a texture, set to a color with glTexSubImage2D and sampled
// from in a fragment shader.
TEST_P(AHardwareBufferColorFormatTest, GpuSampledImageCanBeSampled) {
    AHardwareBuffer_Desc desc = GetParam();
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
    if (!SetUpBuffer(desc)) return;

    // Bind the EGLImage to textures in both contexts.
    const int kTextureUnit = 6;
    for (int i = 0; i < mContextCount; ++i) {
        MakeCurrent(i);
        SetUpTexture(desc, kTextureUnit);
    }
    // In the second context, upload opaque red to the texture.
    UploadRedPixels(desc);
    glFinish();

    // In the first context, draw a quad that samples from the texture.
    MakeCurrent(0);
    SetUpFramebuffer(40, 40, kRenderbuffer);
    glClearColor(0.f, 0.f, 0.f, 0.f);
    glClear(GL_COLOR_BUFFER_BIT);

    SetUpProgram(desc.layers > 1 ? kVertexShaderEs3 : kVertexShader,
                 desc.layers > 1 ? kArrayFragmentShaderEs3 : kTextureFragmentShader,
                 kQuadPositions, 0.5f, kTextureUnit);
    glDrawArrays(GL_TRIANGLES, 0, kQuadVertexCount);
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());

    // Check the rendered pixels. There should be a red square in the middle.
    std::vector<GoldenPixel> goldens{
        {5, 35, kZero}, {15, 35, kZero}, {25, 35, kZero}, {35, 35, kZero},
        {5, 25, kZero}, {15, 25, kRed},  {25, 25, kRed},  {35, 25, kZero},
        {5, 15, kZero}, {15, 15, kRed},  {25, 15, kRed},  {35, 15, kZero},
        {5,  5, kZero}, {15,  5, kZero}, {25, 5,  kZero}, {35, 5,  kZero},
    };
    CheckGoldenPixels(goldens, /*float_format=*/false, /*alpha_format=*/true);
}

// Verify that buffers which have both GPU_SAMPLED_IMAGE and GPU_COLOR_OUTPUT
// can be both rendered and sampled as a texture.
TEST_P(AHardwareBufferColorFormatTest, GpuColorOutputAndSampledImage) {
    AHardwareBuffer_Desc desc = GetParam();
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
    if (!SetUpBuffer(desc)) return;

    // Bind the EGLImage to textures in both contexts.
    const int kTextureUnit = 1;
    for (int i = 0; i < mContextCount; ++i) {
        MakeCurrent(i);
        SetUpTexture(desc, kTextureUnit);
    }

    // In the second context, draw a checkerboard pattern.
    SetUpFramebuffer(desc.width, desc.height, kBufferAsTexture);
    DrawCheckerboard(desc.width, desc.height);
    glFinish();

    // In the first context, draw a quad that samples from the texture.
    MakeCurrent(0);
    SetUpFramebuffer(40, 40, kRenderbuffer);
    glClearColor(0.f, 0.f, 0.f, 0.f);
    glClear(GL_COLOR_BUFFER_BIT);

    SetUpProgram(desc.layers > 1 ? kVertexShaderEs3 : kVertexShader,
                 desc.layers > 1 ? kArrayFragmentShaderEs3 : kTextureFragmentShader,
                 kQuadPositions, 0.5f, kTextureUnit);
    glDrawArrays(GL_TRIANGLES, 0, kQuadVertexCount);
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
}

INSTANTIATE_TEST_CASE_P(
    SingleLayer,
    AHardwareBufferColorFormatTest,
    ::testing::Values(
        AHardwareBuffer_Desc{75, 33, 1, GL_RGB8, 0, 1, 0, 0},
        AHardwareBuffer_Desc{64, 80, 1, GL_RGBA8, 0, 1, 0, 0},
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
        AHardwareBuffer_Desc{75, 33, 5, GL_RGB8, 0, 1, 0, 0},
        AHardwareBuffer_Desc{64, 80, 6, GL_RGBA8, 0, 1, 0, 0},
        AHardwareBuffer_Desc{25, 77, 7, AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM, 0, 0, 0, 0},
        //AHardwareBuffer_Desc{32, 32, 4, AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{30, 30, 3, AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{50, 50, 4, AHARDWAREBUFFER_FORMAT_R8G8B8_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{20, 10, 2, AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{20, 20, 4, AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT, 0, 0, 0, 0},
        AHardwareBuffer_Desc{30, 20, 16, AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM, 0, 0, 0, 0}));


class AHardwareBufferDepthFormatTest : public AHardwareBufferGLTest {};

// Verify that depth testing against a depth buffer rendered in another context
// works correctly.
TEST_P(AHardwareBufferDepthFormatTest, DepthAffectsDrawAcrossContexts) {
    AHardwareBuffer_Desc desc = GetParam();
    desc.width = 40;
    desc.height = 40;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT;
    // This test does not make sense for layered buffers - don't bother testing them.
    if (desc.layers > 1) return;
    if (!SetUpBuffer(desc)) return;

    // Bind the EGLImage to renderbuffers and framebuffers in both contexts.
    // The depth buffer is shared, but the color buffer is not.
    for (int i = 0; i < mContextCount; ++i) {
        MakeCurrent(i);
        SetUpFramebuffer(40, 40, kRenderbuffer, kBufferAsRenderbuffer);
    }

    // In the second context, clear the depth buffer to a checkerboard pattern.
    DrawCheckerboard(40, 40);
    glFinish();

    // In the first context, clear the color buffer only, then draw a red pyramid.
    MakeCurrent(0);
    SetUpProgram(kVertexShader, kColorFragmentShader, kPyramidPositions, 1.f);
    glClearColor(0.f, 0.f, 0.f, 0.f);
    glClear(GL_COLOR_BUFFER_BIT);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LESS);
    glDrawArrays(GL_TRIANGLES, 0, kPyramidVertexCount);
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());

    // Check golden pixels.
    std::vector<GoldenPixel> goldens{
        {5, 35, kRed}, {15, 35, kRed},  {25, 35, kZero}, {35, 35, kZero},
        {5, 25, kRed}, {15, 25, kZero}, {25, 25, kZero}, {35, 25, kZero},
        {5, 15, kRed}, {15, 15, kRed},  {25, 15, kZero}, {35, 15, kRed},
        {5, 5,  kRed}, {15, 5,  kRed},  {25, 5,  kRed},  {35, 5,  kRed},
    };
    CheckGoldenPixels(goldens, /*float_format=*/false, /*alpha_format=*/true);
}

// Verify that depth buffers with usage GPU_SAMPLED_IMAGE can be used as textures.
TEST_P(AHardwareBufferDepthFormatTest, DepthCanBeSampled) {
    AHardwareBuffer_Desc desc = GetParam();
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
    // ES 2.0 does not support depth textures. There is an extension OES_depth_texture, but it is
    // incompatible with ES 3.x depth texture support.
    if (mGLVersion < 30) return;
    if (!SetUpBuffer(desc)) return;

    // Bind the EGLImage to renderbuffers and framebuffers in both contexts.
    // The depth buffer is shared, but the color buffer is not.
    const int kTextureUnit = 3;
    for (int i = 0; i < 2; ++i) {
        MakeCurrent(i);
        SetUpTexture(desc, kTextureUnit);
        glTexParameteri(mTexTarget, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(mTexTarget, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    }

    // In the second context, attach the depth texture to the framebuffer and clear to 1.
    SetUpFramebuffer(40, 40, kNone, kBufferAsTexture);
    glClearDepthf(1.f);
    glClear(GL_DEPTH_BUFFER_BIT);
    glFinish();

    // In the first context, draw a quad using the depth texture.
    MakeCurrent(0);
    SetUpFramebuffer(40, 40, kRenderbuffer);
    glClearColor(0.f, 0.f, 0.f, 0.f);
    glClear(GL_COLOR_BUFFER_BIT);
    SetUpProgram(desc.layers > 1 ? kVertexShaderEs3 : kVertexShader,
                 desc.layers > 1 ? kArrayFragmentShaderEs3 : kTextureFragmentShader,
                 kQuadPositions, 0.5f, kTextureUnit);
    glDrawArrays(GL_TRIANGLES, 0, 12);
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());
    glFinish();

    // Check the rendered pixels. There should be a red square in the middle.
    std::vector<GoldenPixel> goldens{
        {5, 35, kZero}, {15, 35, kZero}, {25, 35, kZero}, {35, 35, kZero},
        {5, 25, kZero}, {15, 25, kRed},  {25, 25, kRed},  {35, 25, kZero},
        {5, 15, kZero}, {15, 15, kRed},  {25, 15, kRed},  {35, 15, kZero},
        {5,  5, kZero}, {15,  5, kZero}, {25, 5,  kZero}, {35, 5,  kZero},
    };
    CheckGoldenPixels(goldens, /*float_format=*/false, /*alpha_format=*/true);
}

// See comment in SetUpBuffer for explanation of nonzero stride and GL format.
INSTANTIATE_TEST_CASE_P(
    SingleLayer,
    AHardwareBufferDepthFormatTest,
    ::testing::Values(
        AHardwareBuffer_Desc{16, 24, 1, GL_DEPTH_COMPONENT16, 0, 1, 0, 0},
        AHardwareBuffer_Desc{16, 24, 1, AHARDWAREBUFFER_FORMAT_D16_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{44, 21, 1, AHARDWAREBUFFER_FORMAT_D24_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{57, 33, 1, AHARDWAREBUFFER_FORMAT_D24_UNORM_S8_UINT, 0, 0, 0, 0},
        AHardwareBuffer_Desc{20, 10, 1, AHARDWAREBUFFER_FORMAT_D32_FLOAT, 0, 0, 0, 0},
        AHardwareBuffer_Desc{57, 33, 1, AHARDWAREBUFFER_FORMAT_D32_FLOAT_S8_UINT, 0, 0, 0, 0}));


INSTANTIATE_TEST_CASE_P(
    MultipleLayers,
    AHardwareBufferDepthFormatTest,
    ::testing::Values(
        AHardwareBuffer_Desc{16, 24, 6, GL_DEPTH_COMPONENT16, 0, 1, 0, 0},
        AHardwareBuffer_Desc{16, 24, 6, AHARDWAREBUFFER_FORMAT_D16_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{44, 21, 4, AHARDWAREBUFFER_FORMAT_D24_UNORM, 0, 0, 0, 0},
        AHardwareBuffer_Desc{57, 33, 7, AHARDWAREBUFFER_FORMAT_D24_UNORM_S8_UINT, 0, 0, 0, 0},
        AHardwareBuffer_Desc{20, 10, 5, AHARDWAREBUFFER_FORMAT_D32_FLOAT, 0, 0, 0, 0},
        AHardwareBuffer_Desc{57, 33, 3, AHARDWAREBUFFER_FORMAT_D32_FLOAT_S8_UINT, 0, 0, 0, 0}));


class AHardwareBufferStencilFormatTest : public AHardwareBufferGLTest {};

// Verify that stencil testing against a stencil buffer rendered in another context
// works correctly.
TEST_P(AHardwareBufferStencilFormatTest, StencilAffectsDrawAcrossContexts) {
    AHardwareBuffer_Desc desc = GetParam();
    desc.width = 40;
    desc.height = 40;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT;
    // This test does not make sense for layered buffers - don't bother testing them.
    if (desc.layers > 1) return;
    if (!SetUpBuffer(desc)) return;

    // Bind the EGLImage to renderbuffers and framebuffers in both contexts.
    // The depth buffer is shared, but the color buffer is not.
    for (int i = 0; i < mContextCount; ++i) {
        MakeCurrent(i);
        SetUpFramebuffer(40, 40, kRenderbuffer, kNone, kBufferAsRenderbuffer);
    }

    // In the second context, clear the stencil buffer to a checkerboard pattern.
    DrawCheckerboard(40, 40);
    glFinish();

    // In the first context, clear the color buffer only, then draw a flat quad.
    MakeCurrent(0);
    SetUpProgram(kVertexShader, kColorFragmentShader, kQuadPositions, 1.f);
    glClearColor(0.f, 0.f, 0.f, 0.f);
    glClear(GL_COLOR_BUFFER_BIT);
    glEnable(GL_STENCIL_TEST);
    glStencilFunc(GL_ALWAYS, 0, 0xFF);
    glStencilOp(GL_KEEP, GL_INCR, GL_INCR);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glClear(GL_COLOR_BUFFER_BIT);
    glStencilFunc(GL_EQUAL, 2, 0xFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    EXPECT_EQ(GLenum{GL_NO_ERROR}, glGetError());

    // Check golden pixels.
    std::vector<GoldenPixel> goldens{
        {5, 35, kRed},  {15, 35, kRed},  {25, 35, kZero}, {35, 35, kZero},
        {5, 25, kRed},  {15, 25, kRed},  {25, 25, kZero}, {35, 25, kZero},
        {5, 15, kZero}, {15, 15, kZero}, {25, 15, kRed},  {35, 15, kRed},
        {5, 5,  kZero}, {15, 5,  kZero}, {25, 5,  kRed},  {35, 5,  kRed},
    };
    CheckGoldenPixels(goldens, /*float_format=*/false, /*alpha_format=*/true);
}

// See comment in SetUpBuffer for explanation of nonzero stride and GL format.
INSTANTIATE_TEST_CASE_P(
    SingleLayer,
    AHardwareBufferStencilFormatTest,
    ::testing::Values(
        AHardwareBuffer_Desc{49, 57, 1, GL_STENCIL_INDEX8, 0, 1, 0, 0},
        AHardwareBuffer_Desc{26, 26, 1, AHARDWAREBUFFER_FORMAT_S8_UINT, 0, 0, 0, 0},
        AHardwareBuffer_Desc{57, 33, 1, AHARDWAREBUFFER_FORMAT_D24_UNORM_S8_UINT, 0, 0, 0, 0},
        AHardwareBuffer_Desc{17, 23, 1, AHARDWAREBUFFER_FORMAT_D32_FLOAT_S8_UINT, 0, 0, 0, 0}));

}  // namespace android
