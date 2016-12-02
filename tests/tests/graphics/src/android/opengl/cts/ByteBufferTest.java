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

package android.opengl.cts;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import static android.opengl.EGL14.*;
import static android.opengl.GLES30.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for functions that return a ByteBuffer.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ByteBufferTest {
    @Test
    public void testMapBufferRange() {
        EGLDisplay eglDisplay = Egl14Utils.createEglDisplay();
        // Requesting OpenGL ES 2.0 context will return an ES 3.0 context on capable devices
        EGLConfig eglConfig = Egl14Utils.getEglConfig(eglDisplay, 2);
        EGLContext eglContext = Egl14Utils.createEglContext(eglDisplay, eglConfig, 2);
        EGLSurface eglSurface = eglCreatePbufferSurface(eglDisplay, eglConfig, new int[] {
                EGL_WIDTH, 1,
                EGL_HEIGHT, 1,
                EGL_NONE
        }, 0);
        eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

        android.util.Log.d("View", "" + Egl14Utils.getMajorVersion());
        // Always pass on ES 2.0
        if (Egl14Utils.getMajorVersion() >= 3) {
            int[] buffer = new int[1];
            glGenBuffers(1, buffer, 0);
            glBindBuffer(GL_UNIFORM_BUFFER, buffer[0]);
            glBufferData(GL_UNIFORM_BUFFER, 1024, null, GL_DYNAMIC_READ);

            Buffer mappedBuffer = glMapBufferRange(GL_UNIFORM_BUFFER, 0, 1024, GL_MAP_READ_BIT);

            assertNotNull(mappedBuffer);
            assertTrue(mappedBuffer instanceof ByteBuffer);

            Buffer pointerBuffer = glGetBufferPointerv(GL_UNIFORM_BUFFER, GL_BUFFER_MAP_POINTER);
            assertNotNull(pointerBuffer);
            assertTrue(pointerBuffer instanceof ByteBuffer);

            glUnmapBuffer(GL_UNIFORM_BUFFER);

            glBindBuffer(GL_UNIFORM_BUFFER, 0);
            glDeleteBuffers(1, buffer, 0);
        }

        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(eglDisplay, eglSurface);
        Egl14Utils.destroyEglContext(eglDisplay, eglContext);
        Egl14Utils.releaseAndTerminate(eglDisplay);
    }
}
