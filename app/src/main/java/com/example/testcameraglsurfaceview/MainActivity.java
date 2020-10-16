package com.example.testcameraglsurfaceview;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.pm.PackageManager;
import android.graphics.Point;
import android.opengl.EGLConfig;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity {

    private final boolean IS_DIRTY_MODE = true;

    private Renderer mRenderer;
    private GLSurfaceView mView;
    private int REQUEST_CODE_FOR_PERMISSIONS = 1234;;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkPermissions()) {
            init();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_FOR_PERMISSIONS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mView.onResume();
    }

    private void init(){
        setContentView(R.layout.activity_main);
        mView = (GLSurfaceView)findViewById(R.id.gl_surface_view);

        // Kotetsuではここが3。違いは？
        mView.setEGLContextClientVersion(2);
        mRenderer = new Renderer(this);
        mView.setRenderer(mRenderer);
        if(IS_DIRTY_MODE){
            mView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }

        ScreenUtil.hideSystemUI(this);
        ScreenUtil.setScreenBrightness(this, 1.0f);
    }


    private boolean checkPermissions(){
        for(String permission : REQUIRED_PERMISSIONS){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }

    public class Renderer implements GLSurfaceView.Renderer {
        private static final int FLOAT_SIZE_BYTES = 4;
        private static final String VERTEX_SHADER =
                "attribute vec4 position;\n" +
                        "attribute vec2 texcoord;\n" +
                        "varying vec2 texcoordVarying;\n" +
                        "void main() {\n" +
                        "    gl_Position = position;\n" +
                        "    texcoordVarying = texcoord;\n" +
                        "}\n";
        private static final String FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n" +
                        "precision mediump float;\n" +
                        "varying vec2 texcoordVarying;\n" +
                        "uniform samplerExternalOES texture;\n" +
                        "void main() {\n" +
                        "  gl_FragColor = texture2D(texture, texcoordVarying);\n" +
                        "}\n";

        private final float TEX_COORDS_ROTATION_0[] = {
                0.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 0.0f,
                1.0f, 1.0f
        };
        private final float TEX_COORDS_ROTATION_90[] = {
                1.0f, 0.0f,
                0.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f
        };
        private final float TEX_COORDS_ROTATION_180[] = {
                1.0f, 1.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                0.0f, 0.0f
        };
        private final float TEX_COORDS_ROTATION_270[] = {
                0.0f, 1.0f,
                1.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f
        };
        private final float VERTECES[] = {
                -1.0f, 1.0f, 0.0f,
                -1.0f, -1.0f, 0.0f,
                1.0f, 1.0f, 0.0f,
                1.0f, -1.0f, 0.0f
        };

        private AppCompatActivity mActivity;
        private int mProgram;
        private int mPositionHandle;
        private int mTexCoordHandle;
        private int mTextureHandle;
        private int mTextureID;
        private FloatBuffer mTexCoordBuffer;
        private FloatBuffer mVertexBuffer;

        private Camera mCamera;
        private boolean mConfigured = false;

        public Renderer(AppCompatActivity activity) {
            mActivity = activity;
        }

        @Override
        public void onSurfaceCreated(GL10 unused, javax.microedition.khronos.egl.EGLConfig config) {
            onSurfaceCreated();
        }

        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            onSurfaceCreated();
        }

        private void onSurfaceCreated(){

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            mTextureID = textures[0];

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

            mCamera = new Camera(mActivity, mTextureID);
            mCamera.open();

            mTexCoordBuffer =
                    ByteBuffer.allocateDirect(TEX_COORDS_ROTATION_0.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
            mVertexBuffer =
                    ByteBuffer.allocateDirect(VERTECES.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
            mVertexBuffer.put(VERTECES).position(0);

            mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);

            mPositionHandle = GLES20.glGetAttribLocation(mProgram, "position");
            GLES20.glEnableVertexAttribArray(mPositionHandle);
            mTexCoordHandle = GLES20.glGetAttribLocation(mProgram, "texcoord");
            GLES20.glEnableVertexAttribArray(mTexCoordHandle);
            checkGlError("glGetAttribLocation");

            mTextureHandle = GLES20.glGetUniformLocation(mProgram, "texture");
            checkGlError("glGetUniformLocation");

            mView.requestRender();
        }

        private final boolean DEBUG_IMPORTANT = false;
        private final boolean DEBUG_DETAIL = false;
        private final String TAG = "DEBUG";
        private final long BASE_NANO_SEC_30FPS = 33333334;
        private final long BASE_NANO_SEC_15FPS = BASE_NANO_SEC_30FPS * 2;
        private final long INTERVAL_NANO_SEC = BASE_NANO_SEC_30FPS;
        private int frameCount = 0;
        private long lastUpdateTime = 0;
        private long lastEndTime = 0;

        public void onDrawFrame(GL10 unused ) {

            long startTime = System.nanoTime();
            long delayTime = startTime - lastEndTime;

            GLES20.glClearColor(0.5f, 0.5f, 1.0f, 1.0f);
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

            if(mCamera == null){
                mView.requestRender();
                return;
            }

            if (!mConfigured) {
                if (mConfigured = mCamera.getInitialized()) {
                    mCamera.setCameraRotation();
                    setConfig();
                } else {
                    mView.requestRender();
                    return;
                }
            }

            mCamera.updateTexture();

            GLES20.glUseProgram(mProgram);

            GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mTexCoordBuffer);
            GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false, 0, mVertexBuffer);
            checkGlError("glVertexAttribPointer");

            GLES20.glUniform1i(mTextureHandle, 0);
            checkGlError("glUniform1i");

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
            checkGlError("glBindTexture");

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glUseProgram(0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

            mView.requestRender();

            /* 平均50fps以上出ているので、30fpsになるように調整 */
            long endTime = System.nanoTime();
            long processingTime = endTime - startTime;
            if(DEBUG_IMPORTANT && DEBUG_DETAIL) Log.d(TAG, String.format("process:%.2f,  delay:%.2f", processingTime/1000000.0, delayTime/1000000.0));
            processingTime += delayTime;
            if(IS_DIRTY_MODE && INTERVAL_NANO_SEC > processingTime){
                try{
                    long sleepTimeMills = (INTERVAL_NANO_SEC - processingTime) / 1000000;
                    if(DEBUG_IMPORTANT && DEBUG_DETAIL) Log.d(TAG, String.format("Sleep for %d[ms] to adjust fps.", sleepTimeMills));
                    Thread.sleep(sleepTimeMills);
                } catch (InterruptedException e){
                    Log.e(TAG, "onDrawFrame()", e);
                }
            }

            if(DEBUG_IMPORTANT){
                frameCount++;
                long timeDiff = endTime - lastUpdateTime;
                if(timeDiff > 1000000000){
                    long timeDiffMills = timeDiff / 1000000;
                    double fps = (double)(frameCount * 1000) / (double)timeDiffMills;
                    Log.d(TAG, String.format("fps:%.2f,  frame:%d,  ms:%d", fps, frameCount, timeDiffMills));
                    frameCount = 0;
                    lastUpdateTime = endTime;
                }
            }

            lastEndTime = System.nanoTime();
        }

        public void onSurfaceChanged (GL10 unused, int width, int height) {
            mConfigured = false;
            mView.requestRender();
        }

        private int loadShader(int shaderType, String source) {
            int shader = GLES20.glCreateShader(shaderType);
            if (shader != 0) {
                GLES20.glShaderSource(shader, source);
                GLES20.glCompileShader(shader);
                int[] compiled = new int[1];
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
                if (compiled[0] == 0) {
                    GLES20.glDeleteShader(shader);
                    shader = 0;
                }
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
                return 0;
            }

            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, pixelShader);
            GLES20.glLinkProgram(program);

            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            return program;
        }

        private void checkGlError(String op) {
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                throw new RuntimeException(op + ": glError " + error);
            }
        }

        private void setConfig() {
            switch(mCamera.getCameraRotation()) {
                case ROTATION_0:
                    mTexCoordBuffer.put(TEX_COORDS_ROTATION_0);
                    break;
                case ROTATION_90:
                    mTexCoordBuffer.put(TEX_COORDS_ROTATION_90);
                    break;
                case ROTATION_180:
                    mTexCoordBuffer.put(TEX_COORDS_ROTATION_180);
                    break;
                case ROTATION_270:
                    mTexCoordBuffer.put(TEX_COORDS_ROTATION_270);
                    break;
            }
            mTexCoordBuffer.position(0);

            Point displaySize = new Point();
            mActivity.getWindowManager().getDefaultDisplay().getSize(displaySize);
            Size textureSize = mCamera.getCameraSize();
            Point textureOrigin = new Point(
                    (displaySize.x - textureSize.getWidth()) / 2,
                    (displaySize.y - textureSize.getHeight()) / 2);

            GLES20.glViewport(textureOrigin.x, textureOrigin.y, textureSize.getWidth(), textureSize.getHeight());
        }
    }
}
