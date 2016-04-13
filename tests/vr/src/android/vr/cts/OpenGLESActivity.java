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
package android.vr.cts;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import java.lang.InterruptedException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class OpenGLESActivity extends Activity {
    private static final String TAG = "OpenGLESActivity";

    public static final String EXTRA_VIEW_INDEX = "viewIndex";
    public static final String EXTRA_PROTECTED = "protected";
    public static final String EXTRA_PRIORITY = "priority";
    public static final String EXTRA_LATCH_COUNT = "latchCount";


    public static final int EGL_PROTECTED_CONTENT_EXT = 0x32C0;

    // Context priority enums are not exposed in Java.
    public static final int EGL_CONTEXT_PRIORITY_LEVEL_IMG = 0x3100;

    OpenGLES20View mView;
    Renderer mRenderer;
    int mRendererType;
    private CountDownLatch mLatch;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        int viewIndex = getIntent().getIntExtra(EXTRA_VIEW_INDEX, -1);
        int protectedAttribute = getIntent().getIntExtra(EXTRA_PROTECTED, -1);
        int priorityAttribute = getIntent().getIntExtra(EXTRA_PRIORITY, -1);
        int latchCount = getIntent().getIntExtra(EXTRA_LATCH_COUNT, 1);
        mLatch = new CountDownLatch(latchCount);
        mView = new OpenGLES20View(this, viewIndex, protectedAttribute, priorityAttribute,
            mLatch);

        setContentView(mView);
    }

    public int glGetError() {
        return ((RendererBasicTest)mRenderer).mError;
    }

    public static void checkEglError(String msg) {
        boolean failed = false;
        int error;
        while ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            Log.e(TAG, msg + ": EGL error: 0x" + Integer.toHexString(error));
            failed = true;
        }
        if (failed) {
            throw new RuntimeException("EGL error encountered (EGL error: 0x" +
                Integer.toHexString(error) + ")");
        }
    }

    public void runOnGlThread(Runnable r) throws Throwable {
        CountDownLatch fence = new CountDownLatch(1);
        RunSignalAndCatch wrapper = new RunSignalAndCatch(r, fence);

        mView.queueEvent(wrapper);
        fence.await(5000, TimeUnit.MILLISECONDS);
        if (wrapper.error != null) {
            throw wrapper.error;
        }
    }

    public static boolean contextHasAttributeWithValue(int attribute, int value) {
        int[] values = new int[1];
        EGL14.eglQueryContext(EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY),
            EGL14.eglGetCurrentContext(), attribute, values, 0);
        checkEglError("eglQueryContext");
        return values[0] == value;
    }

    public static boolean surfaceHasAttributeWithValue(int attribute, int value) {
        int[] values = new int[1];
        EGL14.eglQuerySurface(EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY),
            EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), attribute, values, 0);
        checkEglError("eglQueryContext");
        return values[0] == value;
    }

    public static void setSurfaceAttribute(int attribute, int value) {
        int[] values = new int[1];
        EGL14.eglSurfaceAttrib(EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY),
            EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), attribute, value);
        checkEglError("eglSurfaceAttrib");
    }

    public boolean waitForFrameDrawn() {
        boolean result = false;
        try {
            result = mLatch.await(1L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // just return false
        }
        return result;
    }

    public boolean supportsVrHighPerformance() {
        PackageManager pm = getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mView != null) {
            mView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mView != null) {
            mView.onResume();
        }
    }

    private class RunSignalAndCatch implements Runnable {
        public Throwable error;
        private Runnable mRunnable;
        private CountDownLatch mFence;

        RunSignalAndCatch(Runnable run, CountDownLatch fence) {
            mRunnable = run;
            mFence = fence;
        }

        @Override
        public void run() {
            try {
                mRunnable.run();
            } catch (Throwable t) {
                error = t;
            } finally {
                mFence.countDown();
            }
        }
    }

    class OpenGLES20View extends GLSurfaceView {

        public OpenGLES20View(Context context, int index, int protectedAttribute,
            int priorityAttribute, CountDownLatch latch) {
            super(context);
            setEGLContextClientVersion(2);

            if (protectedAttribute == 1) {
                setEGLContextFactory(new ProtectedContextFactory());
                setEGLWindowSurfaceFactory(new ProtectedWindowSurfaceFactory());
            } else if (priorityAttribute != 0) {
                setEGLContextFactory(new PriorityContextFactory(priorityAttribute));
            }

            if (index == 1) {
                mRenderer = new RendererBasicTest(latch);
            } else  if (index == 2) {
                mRenderer = new RendererProtectedTexturesTest(latch);
            } else  if (index == 3) {
                mRenderer = new RendererRefreshRateTest(latch);
            } else {
                throw new RuntimeException();
            }
            setRenderer(mRenderer);
        }

        @Override
        public void setEGLContextClientVersion(int version) {
            super.setEGLContextClientVersion(version);
        }
    }

    private class PriorityContextFactory implements GLSurfaceView.EGLContextFactory {
        private int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private int mEGLContextClientVersion = 2;

        private int mPriority;

        PriorityContextFactory(int priorityAttribute) {
            super();
            mPriority = priorityAttribute;
        }

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, mEGLContextClientVersion,
                EGL_CONTEXT_PRIORITY_LEVEL_IMG,  mPriority, EGL10.EGL_NONE };

            EGLContext context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT,
                attrib_list);
            if (context == EGL10.EGL_NO_CONTEXT) {
              Log.e(TAG, "Error creating EGL context.");
            }
            checkEglError("eglCreateContext");
            return context;
        }

        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            if (!egl.eglDestroyContext(display, context)) {
              Log.e("DefaultContextFactory", "display:" + display + " context: " + context);
            }
          }
    }

    private class ProtectedContextFactory implements GLSurfaceView.EGLContextFactory {
        private int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private int mEGLContextClientVersion = 2;

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, mEGLContextClientVersion,
                EGL_PROTECTED_CONTENT_EXT,  EGL14.EGL_TRUE,
                EGL10.EGL_NONE };

            EGLContext context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT,
                attrib_list);
            if (context == EGL10.EGL_NO_CONTEXT) {
              Log.e(TAG, "Error creating EGL context.");
            }
            checkEglError("eglCreateContext");
            return context;
        }

        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            if (!egl.eglDestroyContext(display, context)) {
              Log.e("DefaultContextFactory", "display:" + display + " context: " + context);
            }
          }
    }

    private static class ProtectedWindowSurfaceFactory implements GLSurfaceView.EGLWindowSurfaceFactory {

      public EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display,
                                            EGLConfig config, Object nativeWindow) {
        EGLSurface result = null;
        try {
          int[] attrib_list = { EGL_PROTECTED_CONTENT_EXT,  EGL14.EGL_TRUE, EGL10.EGL_NONE };
          result = egl.eglCreateWindowSurface(display, config, nativeWindow, attrib_list);
          checkEglError("eglCreateWindowSurface");
        } catch (IllegalArgumentException e) {
          Log.e(TAG, "eglCreateWindowSurface", e);
        }
        return result;
      }

      public void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface) {
        egl.eglDestroySurface(display, surface);
      }
    }
}
