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
package android.media.cts;

import android.media.cts.R;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

@TargetApi(16)
public class DecodeAccuracyTestBase
    extends ActivityInstrumentationTestCase2<DecodeAccuracyTestActivity> {

    protected Context mContext;
    protected Resources mResources;
    protected DecodeAccuracyTestActivity mActivity;
    protected TestHelper testHelper;

    public DecodeAccuracyTestBase() {
        super(DecodeAccuracyTestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        getInstrumentation().waitForIdleSync();
        mContext = getInstrumentation().getTargetContext();
        mResources = mContext.getResources();
        testHelper = new TestHelper(mContext, mActivity);
    }

    @Override
    protected void tearDown() throws Exception {
        mActivity = null;
        super.tearDown();
    }

    protected TestHelper getHelper() {
        return testHelper;
    }

    public static <T> T checkNotNull(T reference) {
        assertNotNull(reference);
        return reference;
    }

    public static class SimplePlayer {

        public static final long DECODE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(1) / 2;

        private static final int NO_TRACK_INDEX = -3;
        private static final long DEQUEUE_TIMEOUT_US = 20;
        private static final String TAG = SimplePlayer.class.getSimpleName();

        private final Context context;
        private final MediaExtractor extractor;
        private MediaCodec decoder;

        public SimplePlayer(Context context) {
            this(context, new MediaExtractor());
        }

        public SimplePlayer(Context context, MediaExtractor extractor) {
            this.context = checkNotNull(context);
            this.extractor = checkNotNull(extractor);
        }

        /*
         * The function play the corresponding file for certain number of frames,
         *
         * @param surface is the surface view of decoder output.
         * @param videoFormat is the format of the video to extract and decode.
         * @param numOfTotalFrame is the number of Frame wish to play.
         * @return a PlayerResult object indicating success or failure.
         */
        public PlayerResult decodeVideoFrames(
                Surface surface, VideoFormat videoFormat, int numOfTotalFrames) {
            PlayerResult playerResult;
            if (prepare(surface, videoFormat)) {
                if (startDecoder()) {
                    playerResult = decodeFramesAndDisplay(
                            surface, numOfTotalFrames, numOfTotalFrames * DECODE_TIMEOUT_MS);
                } else {
                    playerResult = PlayerResult.failToStart();
                }
            } else {
                playerResult = new PlayerResult();
            }
            release();
            return new PlayerResult(playerResult);
        }

        public PlayerResult decodeVideoFrames(VideoFormat videoFormat, int numOfTotalFrames) {
            return decodeVideoFrames(null, videoFormat, numOfTotalFrames);
        }

        /*
         * The function set up the extractor and decoder with proper format.
         * This must be called before decodeFramesAndDisplay.
         */
        private boolean prepare(Surface surface, VideoFormat videoFormat) {
            if (!setExtractorDataSource(videoFormat)) {
                return false;
            }
            int trackNum = getFirstVideoTrackIndex(extractor);
            if (trackNum == NO_TRACK_INDEX) {
                return false;
            }
            extractor.selectTrack(trackNum);
            MediaFormat mediaFormat = extractor.getTrackFormat(trackNum);
            configureFormat(mediaFormat, videoFormat);
            return configureDecoder(surface, mediaFormat);
        }

        /* The function decode video frames and display in a surface. */
        private PlayerResult decodeFramesAndDisplay(
                Surface surface, int numOfTotalFrames, long timeOutMs) {
            checkNotNull(decoder);
            int numOfDecodedFrames = 0;
            long decodeStart = 0;
            boolean renderToSurface = surface != null ? true : false;
            BufferInfo info = new BufferInfo();
            ByteBuffer inputBuffer;
            ByteBuffer[] inputBufferArray = decoder.getInputBuffers();
            long loopStart = SystemClock.elapsedRealtime();

            while (numOfDecodedFrames < numOfTotalFrames
                    && (SystemClock.elapsedRealtime() - loopStart < timeOutMs)) {
                try {
                    int inputBufferIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
                    if (inputBufferIndex >= 0) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                            inputBuffer = inputBufferArray[inputBufferIndex];
                        } else {
                            inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                        }
                        if (decodeStart == 0) {
                            decodeStart = SystemClock.elapsedRealtime();
                        }
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize > 0) {
                            decoder.queueInputBuffer(
                                    inputBufferIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                    int decoderStatus = decoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                    if (decoderStatus >= 0 && info.size > 0) {
                        decoder.releaseOutputBuffer(decoderStatus, renderToSurface);
                        numOfDecodedFrames++;
                    }
                } catch (IllegalStateException exception) {
                    Log.e(TAG, "IllegalStateException in decodeFramesAndDisplay " + exception);
                    break;
                }
            }
            long totalTime = SystemClock.elapsedRealtime() - decodeStart;
            return new PlayerResult(true, true, numOfTotalFrames == numOfDecodedFrames, totalTime);
        }

        private void release() {
            decoderRelease();
            extractorRelease();
        }

        private boolean setExtractorDataSource(VideoFormat videoFormat) {
            try {
                extractor.setDataSource(context, videoFormat.loadUri(context), null);
            } catch (IOException exception) {
                Log.e(TAG, "IOException in setDataSource", exception);
                return false;
            }
            return true;
        }

        private boolean configureDecoder(Surface surface, MediaFormat mediaFormat) {
            try {
                decoder = MediaCodec.createDecoderByType(
                        mediaFormat.getString(MediaFormat.KEY_MIME));
                decoder.configure(mediaFormat, surface, null, 0);
            } catch (Exception exception) {
                if (exception instanceof IOException) {
                    Log.e(TAG, "IOException in createDecoderByType", exception);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        && exception instanceof CodecException) {
                    Log.e(TAG, "CodecException in createDecoderByType", exception);
                    decoder.reset();
                } else {
                    Log.e(TAG, "Unknown exception in createDecoderByType", exception);
                }
                decoderRelease();
                return false;
            }
            return true;
        }

        private boolean startDecoder() {
            try {
                decoder.start();
            } catch (Exception exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        && exception instanceof CodecException) {
                    Log.e(TAG, "CodecException in startDecoder", exception);
                    decoder.reset();
                } else if (exception instanceof IllegalStateException) {
                    Log.e(TAG, "IllegalStateException in startDecoder", exception);
                } else {
                    Log.e(TAG, "Unknown exception in startDecoder", exception);
                }
                decoderRelease();
                return false;
            }
            return true;
        }

        private void decoderRelease() {
            if (decoder == null) {
                return;
            }
            try {
                decoder.stop();
            } catch (IllegalStateException exception) {
                // IllegalStateException happens when decoder fail to start.
                Log.e(TAG, "IllegalStateException in decoder stop" + exception);
            } finally {
                try {
                    decoder.release();
                } catch (IllegalStateException exception) {
                    Log.e(TAG, "IllegalStateException in decoder release" + exception);
                }
            }
            decoder = null;
        }

        private void extractorRelease() {
            if (extractor == null) {
                return;
            }
            try {
                extractor.release();
            } catch (IllegalStateException exception) {
                Log.e(TAG, "IllegalStateException in extractor release" + exception);
            }
        }

        private static void configureFormat(MediaFormat mediaFormat, VideoFormat videoFormat) {
            checkNotNull(mediaFormat);
            checkNotNull(videoFormat);
            videoFormat.setMimeType(mediaFormat.getString(MediaFormat.KEY_MIME));
            videoFormat.setWidth(mediaFormat.getInteger(MediaFormat.KEY_WIDTH));
            videoFormat.setHeight(mediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
            mediaFormat.setInteger(MediaFormat.KEY_WIDTH, videoFormat.getWidth());
            mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, videoFormat.getHeight());

            if (videoFormat.getMaxWidth() != VideoFormat.UNSET
                    && videoFormat.getMaxHeight() != VideoFormat.UNSET) {
                mediaFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, videoFormat.getMaxWidth());
                mediaFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, videoFormat.getMaxHeight());
            }
        }

        /*
         * The function returns the first video track found.
         *
         * @param extractor is the media extractor instantiated with a video uri.
         * @return the index of the first video track if found, NO_TRACK_INDEX otherwise.
         */
        private static int getFirstVideoTrackIndex(MediaExtractor extractor) {
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat trackMediaFormat = extractor.getTrackFormat(i);
                if (trackMediaFormat.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                    return i;
                }
            }
            Log.e(TAG, "couldn't get a video track");
            return NO_TRACK_INDEX;
        }

        /* Stores the result from SimplePlayer. */
        public static final class PlayerResult {

            public static final int UNSET = -1;
            private final boolean configureSuccess;
            private final boolean startSuccess;
            private final boolean decodeSuccess;
            private final long totalTime;

            public PlayerResult(
                    boolean configureSuccess, boolean startSuccess,
                    boolean decodeSuccess, long totalTime) {
                this.configureSuccess = configureSuccess;
                this.startSuccess = startSuccess;
                this.decodeSuccess = decodeSuccess;
                this.totalTime = totalTime;
            }

            public PlayerResult(PlayerResult playerResult) {
                this(playerResult.configureSuccess, playerResult.startSuccess,
                        playerResult.decodeSuccess, playerResult.totalTime);
            }

            public PlayerResult() {
                // Dummy PlayerResult.
                this(false, false, false, UNSET);
            }

            public static PlayerResult failToStart() {
                return new PlayerResult(true, false, false, UNSET);
            }

            public boolean isConfigureSuccess() {
                return configureSuccess;
            }

            public boolean isStartSuccess() {
                return startSuccess;
            }

            public boolean isDecodeSuccess() {
                return decodeSuccess;
            }

            public boolean isSuccess() {
                return isConfigureSuccess() && isStartSuccess()
                        && isDecodeSuccess() && getTotalTime() != UNSET;
            }

            public long getTotalTime() {
                return totalTime;
            }

            public boolean isFailureForAll() {
                return (!isConfigureSuccess() && !isStartSuccess()
                        && !isDecodeSuccess() && getTotalTime() == UNSET);
            }
        }

    }

    /* Utility class for collecting common test case functionality. */
    class TestHelper {

        private final Context context;
        private final Handler handler;
        private final Activity activity;

        public TestHelper(Context context, Activity activity) {
            this.context = checkNotNull(context);
            this.handler = new Handler(Looper.getMainLooper());
            this.activity = activity;
        }

        public Bitmap generateBitmapFromImageResourceId(int resourceId) {
            return BitmapFactory.decodeStream(context.getResources().openRawResource(resourceId));
        }

        public Context getContext() {
            return context;
        }

        public void rotateOrientation() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    final int orientation = context.getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    } else {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    }
                }
            });
        }

        public void unsetOrientation() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            });
        }

        public void generateView(View view) {
            RelativeLayout relativeLayout =
                    (RelativeLayout) activity.findViewById(R.id.attach_view);
            ViewGenerator viewGenerator = new ViewGenerator(relativeLayout, view);
            handler.post(viewGenerator);
        }

        public void cleanUpView(View view) {
            ViewCleaner viewCleaner = new ViewCleaner(view);
            handler.post(viewCleaner);
        }

        public synchronized Bitmap generateBitmapFromVideoViewSnapshot(VideoViewSnapshot snapshot) {
            handler.post(snapshot);
            try {
                while (!snapshot.isBitmapReady()) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return snapshot.getBitmap();
        }

        private class ViewGenerator implements Runnable {

            private final View view;
            private final RelativeLayout relativeLayout;

            public ViewGenerator(RelativeLayout relativeLayout, View view) {
                this.view = checkNotNull(view);
                this.relativeLayout = checkNotNull(relativeLayout);
            }

            @Override
            public void run() {
                if (view.getParent() != null) {
                    ((ViewGroup) view.getParent()).removeView(view);
                }
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        VideoViewFactory.VIEW_WIDTH, VideoViewFactory.VIEW_HEIGHT);
                view.setLayoutParams(params);
                relativeLayout.addView(view);
            }

        }

        private class ViewCleaner implements Runnable {

            private final View view;

            public ViewCleaner(View view) {
                this.view = checkNotNull(view);
            }

            @Override
            public void run() {
                if (view.getParent() != null) {
                    ((ViewGroup) view.getParent()).removeView(view);
                }
            }

        }

    }

}

/* Factory for manipulating a {@link View}. */
abstract class VideoViewFactory {

    public final long VIEW_AVAILABLE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(1);
    public static final int VIEW_WIDTH = 480;
    public static final int VIEW_HEIGHT = 360;

    public VideoViewFactory() {}

    public abstract void release();

    public abstract String getName();

    public abstract View createView(Context context);

    public abstract void waitForViewIsAvailable();

    public abstract Surface getSurface();

    public abstract VideoViewSnapshot getVideoViewSnapshot();

}

/* Factory for building a {@link TextureView}. */
@TargetApi(16)
class TextureViewFactory extends VideoViewFactory implements TextureView.SurfaceTextureListener {

    private final String TAG = TextureViewFactory.class.getSimpleName();
    private final Object syncToken = new Object();
    private TextureView textureView;

    public TextureViewFactory() {}

    @Override
    public TextureView createView(Context context) {
        textureView = DecodeAccuracyTestBase.checkNotNull(new TextureView(context));
        textureView.setSurfaceTextureListener(this);
        return textureView;
    }

    @Override
    public void release() {
        textureView = null;
    }

    @Override
    public String getName() {
        return "TextureView";
    }

    @Override
    public Surface getSurface() {
        return new Surface(textureView.getSurfaceTexture());
    }

    @Override
    public TextureViewSnapshot getVideoViewSnapshot() {
        return new TextureViewSnapshot(textureView);
    }

    @Override
    public void waitForViewIsAvailable() {
        while (!textureView.isAvailable()) {
            synchronized (syncToken) {
                try {
                    syncToken.wait(VIEW_AVAILABLE_TIMEOUT_MS);
                } catch (InterruptedException exception) {
                    Log.e(TAG, "InterruptedException in waitForViewIsAvailable", exception);
                }
            }
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        synchronized (syncToken) {
            syncToken.notify();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(
            SurfaceTexture surfaceTexture, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}

}

/**
 * Factory for building EGL and GLES that could render to GLSurfaceView.
 * {@link GLSurfaceView} {@link EGL10} {@link GLES20}.
 */
@TargetApi(16)
class GLSurfaceViewFactory extends VideoViewFactory {

    private final String TAG = GLSurfaceViewFactory.class.getSimpleName();
    private final Object surfaceSyncToken = new Object();
    private final Object snapshotSyncToken = new Object();

    private GLSurfaceViewThread glSurfaceViewThread;
    private boolean snapshotIsReady = false;

    public GLSurfaceViewFactory() {}

    @Override
    public void release() {
        glSurfaceViewThread.release();
        glSurfaceViewThread = null;
    }

    @Override
    public String getName() {
        return "GLSurfaceView";
    }

    @Override
    public View createView(Context context) {
        // Do all GL rendering in the GL thread.
        glSurfaceViewThread = new GLSurfaceViewThread();
        glSurfaceViewThread.start();
        // No necessary view to display, return null.
        return null;
    }

    @Override
    public void waitForViewIsAvailable() {
        while (glSurfaceViewThread.getSurface() == null) {
            synchronized (surfaceSyncToken) {
                try {
                    surfaceSyncToken.wait(VIEW_AVAILABLE_TIMEOUT_MS);
                } catch (InterruptedException exception) {
                    Log.e(TAG, "InterruptedException in waitForViewIsAvailable", exception);
                }
            }
        }
    }

    @Override
    public Surface getSurface() {
        return glSurfaceViewThread.getSurface();
    }

    @Override
    public VideoViewSnapshot getVideoViewSnapshot() {
        while (!snapshotIsReady) {
            synchronized (snapshotSyncToken) {
                try {
                    snapshotSyncToken.wait(VIEW_AVAILABLE_TIMEOUT_MS);
                } catch (InterruptedException exception) {
                    Log.e(TAG, "InterruptedException in getVideoViewSnapshot", exception);
                }
            }
        }
        return new GLSurfaceViewSnapshot(
                glSurfaceViewThread.getByteBuffer(), VIEW_WIDTH, VIEW_HEIGHT);
    }

    /* Does all GL operations. */
    private class GLSurfaceViewThread extends Thread
            implements SurfaceTexture.OnFrameAvailableListener {

        private static final int FLOAT_SIZE_BYTES = 4;
        private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
        private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
        private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
        private FloatBuffer triangleVertices;
        private float[] textureTransform = new float[16];

        private float[] triangleVerticesData = {
                // X, Y, Z, U, V
                -1f, -1f,  0f,  0f,  1f,
                 1f, -1f,  0f,  1f,  1f,
                -1f,  1f,  0f,  0f,  0f,
                 1f,  1f,  0f,  1f,  0f,
        };
        // Make the top-left corner corresponds to texture coordinate
        // (0, 0). This complies with the transformation matrix obtained from
        // SurfaceTexture.getTransformMatrix.

        private static final String VERTEX_SHADER =
                "attribute vec4 aPosition;\n"
                + "attribute vec4 aTextureCoord;\n"
                + "uniform mat4 uTextureTransform;\n"
                + "varying vec2 vTextureCoord;\n"
                + "void main() {\n"
                + "    gl_Position = aPosition;\n"
                + "    vTextureCoord = (uTextureTransform * aTextureCoord).xy;\n"
                + "}\n";

        private static final String FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n"
                + "precision mediump float;\n"      // highp here doesn't seem to matter
                + "varying vec2 vTextureCoord;\n"
                + "uniform samplerExternalOES sTexture;\n"
                + "void main() {\n"
                + "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
                + "}\n";

        private int glProgram;
        private int textureID = -1;
        private int aPositionHandle;
        private int aTextureHandle;
        private int uTextureTransformHandle;
        private EGLDisplay eglDisplay = null;
        private EGLContext eglContext = null;
        private EGLSurface eglSurface = null;
        private EGL10 egl10;
        private Surface surface = null;
        private SurfaceTexture surfaceTexture;
        private ByteBuffer byteBuffer;

        public GLSurfaceViewThread() {}

        @Override
        public void run() {
            Looper.prepare();
            triangleVertices = ByteBuffer
                    .allocateDirect(triangleVerticesData.length * FLOAT_SIZE_BYTES)
                            .order(ByteOrder.nativeOrder()).asFloatBuffer();
            triangleVertices.put(triangleVerticesData).position(0);

            eglSetup();
            makeCurrent();
            eglSurfaceCreated();

            surfaceTexture = new SurfaceTexture(getTextureId());
            surfaceTexture.setOnFrameAvailableListener(this);
            surface = new Surface(surfaceTexture);
            synchronized (surfaceSyncToken) {
                surfaceSyncToken.notify();
            }

            // Store pixels from surface
            byteBuffer = ByteBuffer.allocateDirect(VIEW_WIDTH * VIEW_HEIGHT * 4);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            Looper.loop();
        }

        @Override
        public void onFrameAvailable(SurfaceTexture st) {
            checkGlError("before updateTexImage");
            surfaceTexture.updateTexImage();
            st.getTransformMatrix(textureTransform);

            drawFrame();
            saveFrame();
            snapshotIsReady = true;
            synchronized(snapshotSyncToken) {
                snapshotSyncToken.notify();
            }
        }

        /* Prepares EGL to use GLES 2.0 context and a surface that supports pbuffer. */
        public void eglSetup() {
            egl10 = (EGL10) EGLContext.getEGL();
            eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                throw new RuntimeException("unable to get egl10 display");
            }
            int[] version = new int[2];
            if (!egl10.eglInitialize(eglDisplay, version)) {
                eglDisplay = null;
                throw new RuntimeException("unable to initialize egl10");
            }
            // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
            int[] configAttribs = {
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                    EGL10.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!egl10.eglChooseConfig(
                    eglDisplay, configAttribs, configs, configs.length, numConfigs)) {
                throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
            }
            // Configure EGL context for OpenGL ES 2.0.
            int[] contextAttribs = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL10.EGL_NONE
            };
            eglContext = egl10.eglCreateContext(
                    eglDisplay, configs[0], EGL10.EGL_NO_CONTEXT, contextAttribs);
            checkEglError("eglCreateContext");
            if (eglContext == null) {
                throw new RuntimeException("null context");
            }
            // Create a pbuffer surface.
            int[] surfaceAttribs = {
                    EGL10.EGL_WIDTH, VIEW_WIDTH,
                    EGL10.EGL_HEIGHT, VIEW_HEIGHT,
                    EGL10.EGL_NONE
            };
            eglSurface = egl10.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttribs);
            checkEglError("eglCreatePbufferSurface");
            if (eglSurface == null) {
                throw new RuntimeException("surface was null");
            }
        }

        public void release() {
            if (eglDisplay != EGL10.EGL_NO_DISPLAY) {
                egl10.eglMakeCurrent(eglDisplay,
                        EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                egl10.eglDestroySurface(eglDisplay, eglSurface);
                egl10.eglDestroyContext(eglDisplay, eglContext);
                egl10.eglTerminate(eglDisplay);
            }
            eglDisplay = EGL10.EGL_NO_DISPLAY;
            eglContext = EGL10.EGL_NO_CONTEXT;
            eglSurface = EGL10.EGL_NO_SURFACE;
            surface.release();
            surfaceTexture.release();
        }

        /* Makes our EGL context and surface current. */
        public void makeCurrent() {
            if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw new RuntimeException("eglMakeCurrent failed");
            }
            checkEglError("eglMakeCurrent");
        }

        /* Call this after the EGL Surface is created and made current. */
        public void eglSurfaceCreated() {
            glProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            if (glProgram == 0) {
                throw new RuntimeException("failed creating program");
            }
            aPositionHandle = GLES20.glGetAttribLocation(glProgram, "aPosition");
            checkLocation(aPositionHandle, "aPosition");
            aTextureHandle = GLES20.glGetAttribLocation(glProgram, "aTextureCoord");
            checkLocation(aTextureHandle, "aTextureCoord");
            uTextureTransformHandle = GLES20.glGetUniformLocation(glProgram, "uTextureTransform");
            checkLocation(uTextureTransformHandle, "uTextureTransform");

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            checkGlError("glGenTextures");
            textureID = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);
            checkGlError("glBindTexture");

            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            checkGlError("glTexParameter");
        }

        public void drawFrame() {
            GLES20.glUseProgram(glProgram);
            checkGlError("glUseProgram");
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            checkGlError("glActiveTexture");
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);
            checkGlError("glBindTexture");

            triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices);
            checkGlError("glVertexAttribPointer aPositionHandle");
            GLES20.glEnableVertexAttribArray(aPositionHandle);
            checkGlError("glEnableVertexAttribArray aPositionHandle");

            triangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
            GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices);
            checkGlError("glVertexAttribPointer aTextureHandle");
            GLES20.glEnableVertexAttribArray(aTextureHandle);
            checkGlError("glEnableVertexAttribArray aTextureHandle");

            GLES20.glUniformMatrix4fv(uTextureTransformHandle, 1, false,
                    textureTransform, 0);
            checkGlError("glUniformMatrix uTextureTransformHandle");

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            checkGlError("glDrawArrays");
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        }

        /* Reads the pixels to a ByteBuffer. */
        public void saveFrame() {
            byteBuffer.clear();
            GLES20.glReadPixels(0, 0, VIEW_WIDTH, VIEW_HEIGHT, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, byteBuffer);
        }

        public int getTextureId() {
            return textureID;
        }

        public Surface getSurface() {
            return surface;
        }

        public ByteBuffer getByteBuffer() {
            return byteBuffer;
        }

        private int loadShader(int shaderType, String source) {
            int shader = GLES20.glCreateShader(shaderType);
            checkGlError("glCreateShader type=" + shaderType);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);

            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
            return shader;
        }

        private int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }
            int program = GLES20.glCreateProgram();
            if (program == 0) {
                Log.e(TAG, "Could not create program");
            }
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);

            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            return program;
        }

        private void checkEglError(String msg) {
            int error;
            if ((error = egl10.eglGetError()) != EGL10.EGL_SUCCESS) {
                throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
            }
        }

        public void checkGlError(String op) {
            int error;
            if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
                Log.e(TAG, op + ": glError " + error);
                throw new RuntimeException(op + ": glError " + error);
            }
        }

        public void checkLocation(int location, String label) {
            if (location < 0) {
                throw new RuntimeException("Unable to locate '" + label + "' in program");
            }
        }
    }

}

/* Definition of a VideoViewSnapshot and a runnable to get a bitmap from a view. */
abstract class VideoViewSnapshot implements Runnable {

    public abstract Bitmap getBitmap();

    public abstract boolean isBitmapReady();

}

/* Runnable to get a bitmap from a texture view on the UI thread via a handler. */
class TextureViewSnapshot extends VideoViewSnapshot {

    private final TextureView tv;
    private Bitmap bitmap = null;

    public TextureViewSnapshot(TextureView tv) {
        this.tv = DecodeAccuracyTestBase.checkNotNull(tv);
    }

    @Override
    public synchronized void run() {
        bitmap = tv.getBitmap();
    }

    @Override
    public Bitmap getBitmap() {
        return bitmap;
    }

    @Override
    public boolean isBitmapReady() {
        return bitmap != null;
    }

}

/**
 * Runnable to get a bitmap from a GLSurfaceView on the UI thread via a handler.
 * Note, because of how the bitmap is captured in GLSurfaceView,
 * this method does not have to be a runnable.
 */
class GLSurfaceViewSnapshot extends VideoViewSnapshot {

    private Bitmap bitmap = null;
    private ByteBuffer byteBuffer;
    private final int width;
    private final int height;
    private boolean bitmapIsReady = false;

    public GLSurfaceViewSnapshot(ByteBuffer byteBuffer, int width, int height) {
        this.byteBuffer = DecodeAccuracyTestBase.checkNotNull(byteBuffer);
        this.width = width;
        this.height = height;
    }

    @Override
    public synchronized void run() {
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        byteBuffer.rewind();
        bitmap.copyPixelsFromBuffer(byteBuffer);
        bitmapIsReady = true;
    }

    @Override
    public Bitmap getBitmap() {
        return bitmap;
    }

    @Override
    public boolean isBitmapReady() {
        return bitmapIsReady;
    }

}

/* Stores information of a video. */
class VideoFormat {

    public static final int UNSET = -1;
    public static final String MIMETYPE_UNSET = "UNSET";
    public static final String MIMETYPE_KEY = "mimeType";
    public static final String WIDTH_KEY = "width";
    public static final String HEIGHT_KEY = "height";
    public static final String FRAMERATE_KEY = "frameRate";

    private final String filename;
    private Uri uri;
    private String mimeType = MIMETYPE_UNSET;
    private int width = UNSET;
    private int height = UNSET;
    private int maxWidth = UNSET;
    private int maxHeight = UNSET;

    public VideoFormat(String filename, Uri uri) {
        this.filename = filename;
        this.uri = uri;
    }

    public VideoFormat(String filename) {
        this(filename, null);
    }

    public VideoFormat(VideoFormat videoFormat) {
        this(videoFormat.filename, videoFormat.uri);
    }

    public Uri loadUri(Context context) {
        uri = createCacheFile(context);
        return uri;
    }

    public Uri getUri() {
        return uri;
    }

    public String getFilename() {
        return filename;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }

    public int getWidth() {
        return width;
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    public int getHeight() {
        return height;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    private Uri createCacheFile(Context context) {
        try {
            File cacheFile = new File(context.getCacheDir(), filename);
            if (cacheFile.createNewFile() == false) {
                cacheFile.delete();
                cacheFile.createNewFile();
            }
            InputStream inputStream = context.getAssets().open(filename);
            FileOutputStream fileOutputStream = new FileOutputStream(cacheFile);
            final int bufferSize = 1024 * 512;
            byte[] buffer = new byte[bufferSize];

            while (inputStream.read(buffer) != -1) {
                fileOutputStream.write(buffer, 0, bufferSize);
            }
            fileOutputStream.close();
            inputStream.close();
            return Uri.fromFile(cacheFile);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}

/**
 * Compares bitmaps to determine if they are similar.
 *
 * <p>To determine greatest pixel difference we transform each pixel into the
 * CIE L*a*b* color space. The euclidean distance formula is used to determine pixel differences.
 */
class BitmapCompare {

    private static final int RED = 0;
    private static final int GREEN = 1;
    private static final int BLUE = 2;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    private static SparseArray<double[]> pixelTransformCache = new SparseArray<>();

    private BitmapCompare() {}

    /**
     * Produces greatest pixel between two bitmaps. Used to determine bitmap similarity.
     *
     * @param bitmap1 A bitmap to compare to bitmap2.
     * @param bitmap2 A bitmap to compare to bitmap1.
     * @return A {@link Difference} with an integer describing the greatest pixel difference,
     *     using {@link Integer#MAX_VALUE} for completely different bitmaps, and an optional
     *     {@link Pair<Integer, Integer>} of the (col, row) pixel coordinate
     *     where it was first found.
     */
    @TargetApi(12)
    public static Difference computeDifference(Bitmap bitmap1, Bitmap bitmap2) {
        if ((bitmap1 == null || bitmap2 == null) && bitmap1 != bitmap2) {
            return new Difference(Integer.MAX_VALUE);
        }
        if (bitmap1 == bitmap2 || bitmap1.sameAs(bitmap2)) {
            return new Difference(0);
        }
        if (bitmap1.getHeight() != bitmap2.getHeight() || bitmap1.getWidth() != bitmap2.getWidth()) {
            return new Difference(Integer.MAX_VALUE);
        }
        // Convert all pixels to CIE L*a*b* color space so we can do a direct color comparison using
        // euclidean distance formula.
        final double[][] pixels1 = convertRgbToCieLab(bitmap1);
        final double[][] pixels2 = convertRgbToCieLab(bitmap2);
        int greatestDifference = 0;
        int greatestDifferenceIndex = -1;
        for (int i = 0; i < pixels1.length; i++) {
            final int difference = euclideanDistance(pixels1[i], pixels2[i]);
            if (difference > greatestDifference) {
                greatestDifference = difference;
                greatestDifferenceIndex = i;
            }
        }
        return new Difference(greatestDifference, Pair.create(
            greatestDifferenceIndex % bitmap1.getWidth(),
            greatestDifferenceIndex / bitmap1.getHeight()));
    }

    private static double[][] convertRgbToCieLab(Bitmap bitmap) {
        final double[][] result = new double[bitmap.getHeight() * bitmap.getWidth()][3];
        final int pixels[] = new int[bitmap.getHeight() * bitmap.getWidth()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int i = 0; i < pixels.length; i++) {
            final double[] transformedColor = pixelTransformCache.get(pixels[i]);
            if (transformedColor != null) {
                result[i] = transformedColor;
            } else {
                result[i] = convertXyzToCieLab(convertRgbToXyz(pixels[i]));
                pixelTransformCache.put(pixels[i], result[i]);
            }
        }
        return result;
    }

    /**
     * Conversion from RGB to XYZ based algorithm as defined by:
     * http://www.easyrgb.com/index.php?X=MATH&H=02#text2
     *
     * <p><pre>{@code
     *   var_R = ( R / 255 )        //R from 0 to 255
     *   var_G = ( G / 255 )        //G from 0 to 255
     *   var_B = ( B / 255 )        //B from 0 to 255
     *
     *   if ( var_R > 0.04045 ) var_R = ( ( var_R + 0.055 ) / 1.055 ) ^ 2.4
     *   else                   var_R = var_R / 12.92
     *   if ( var_G > 0.04045 ) var_G = ( ( var_G + 0.055 ) / 1.055 ) ^ 2.4
     *   else                   var_G = var_G / 12.92
     *   if ( var_B > 0.04045 ) var_B = ( ( var_B + 0.055 ) / 1.055 ) ^ 2.4
     *   else                   var_B = var_B / 12.92
     *
     *   var_R = var_R * 100
     *   var_G = var_G * 100
     *   var_B = var_B * 100
     *
     *   // Observer. = 2°, Illuminant = D65
     *   X = var_R * 0.4124 + var_G * 0.3576 + var_B * 0.1805
     *   Y = var_R * 0.2126 + var_G * 0.7152 + var_B * 0.0722
     *   Z = var_R * 0.0193 + var_G * 0.1192 + var_B * 0.9505
     * }</pre>
     *
     * @param rgbColor A packed int made up of 4 bytes: alpha, red, green, blue.
     * @return An array of doubles where each value is a component of the XYZ color space.
     */
    private static double[] convertRgbToXyz(int rgbColor) {
        final double[] comp = {Color.red(rgbColor), Color.green(rgbColor), Color.blue(rgbColor)};

        for (int i = 0; i < comp.length; i++) {
            comp[i] /= 255.0;
            if (comp[i] > 0.04045) {
                comp[i] = Math.pow((comp[i] + 0.055) / 1.055, 2.4);
            } else {
                comp[i] /= 12.92;
            }
            comp[i] *= 100;
        }
        final double x = (comp[RED] * 0.4124) + (comp[GREEN] * 0.3576) + (comp[BLUE] * 0.1805);
        final double y = (comp[RED] * 0.2126) + (comp[GREEN] * 0.7152) + (comp[BLUE] * 0.0722);
        final double z = (comp[RED] * 0.0193) + (comp[GREEN] * 0.1192) + (comp[BLUE] * 0.9505);
        return new double[] {x, y, z};
    }

    /**
     * Conversion from XYZ to CIE-L*a*b* based algorithm as defined by:
     * http://www.easyrgb.com/index.php?X=MATH&H=07#text7
     *
     * <p><pre>
     * {@code
     *   var_X = X / ref_X          //ref_X =  95.047   Observer= 2°, Illuminant= D65
     *   var_Y = Y / ref_Y          //ref_Y = 100.000
     *   var_Z = Z / ref_Z          //ref_Z = 108.883
     *
     *   if ( var_X > 0.008856 ) var_X = var_X ^ ( 1/3 )
     *   else                    var_X = ( 7.787 * var_X ) + ( 16 / 116 )
     *   if ( var_Y > 0.008856 ) var_Y = var_Y ^ ( 1/3 )
     *   else                    var_Y = ( 7.787 * var_Y ) + ( 16 / 116 )
     *   if ( var_Z > 0.008856 ) var_Z = var_Z ^ ( 1/3 )
     *   else                    var_Z = ( 7.787 * var_Z ) + ( 16 / 116 )
     *
     *   CIE-L* = ( 116 * var_Y ) - 16
     *   CIE-a* = 500 * ( var_X - var_Y )
     *   CIE-b* = 200 * ( var_Y - var_Z )
     * }
     * </pre>
     *
     * @param comp An array of doubles where each value is a component of the XYZ color space.
     * @return An array of doubles where each value is a component of the CIE-L*a*b* color space.
     */
    private static double[] convertXyzToCieLab(double[] comp) {
        comp[X] /= 95.047;
        comp[Y] /= 100.0;
        comp[Z] /= 108.883;

        for (int i = 0; i < comp.length; i++) {
            if (comp[i] > 0.008856) {
                comp[i] = Math.pow(comp[i], (1.0 / 3.0));
            } else {
                comp[i] = (7.787 * comp[i]) + (16.0 / 116.0);
            }
        }
        final double l = (116 * comp[Y]) - 16;
        final double a = 500 * (comp[X] - comp[Y]);
        final double b = 200 * (comp[Y] - comp[Z]);
        return new double[] {l, a, b};
    }

    private static int euclideanDistance(double[] p1, double[] p2) {
        if (p1.length != p2.length) {
            return Integer.MAX_VALUE;
        }
        double result = 0;
        for (int i = 0; i < p1.length; i++) {
            result += Math.pow(p1[i] - p2[i], 2);
        }
        return (int) Math.round(Math.sqrt(result));
    }

    /* Describes the difference between two {@link Bitmap} instances. */
    public static final class Difference {

        public final int greatestPixelDifference;
        public final Pair<Integer, Integer> greatestPixelDifferenceCoordinates;

        private Difference(int greatestPixelDifference) {
            this(greatestPixelDifference, null);
        }

        private Difference(
                int greatestPixelDifference,
                Pair<Integer, Integer> greatestPixelDifferenceCoordinates) {
            this.greatestPixelDifference = greatestPixelDifference;
            this.greatestPixelDifferenceCoordinates = greatestPixelDifferenceCoordinates;
        }
    }

}

/* Wrapper for MIME types. */
final class MimeTypes {

    private MimeTypes() {}

    public static final String VIDEO_VP9 = "video/x-vnd.on2.vp9";
    public static final String VIDEO_H264 = "video/avc";

    public static boolean isVideo(String mimeType) {
        return mimeType.startsWith("video");
    }

}
