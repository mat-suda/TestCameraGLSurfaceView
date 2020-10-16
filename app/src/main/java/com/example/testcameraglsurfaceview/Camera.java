package com.example.testcameraglsurfaceview;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Collections;

public class Camera {

    enum CameraRotation {ROTATION_0, ROTATION_90, ROTATION_180, ROTATION_270}

    private AppCompatActivity mActivity;
    private CameraDevice mCamera;
    private int mTextureID;
    private Size mImageSize;
    private Size mCameraSize;
    private CameraRotation mCameraRotation;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;
    private SurfaceTexture mSurfaceTexture;
    private boolean mIsPortraitDevice;
    private boolean mInitialized = false;

    public Camera(AppCompatActivity activity, int textureID) {
        mActivity = activity;
        mTextureID = textureID;

        boolean isPortraitApp =
                mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        int orientation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        if (isPortraitApp) {
            mIsPortraitDevice = (orientation == Surface.ROTATION_0 || orientation ==  Surface.ROTATION_180);
        } else {
            mIsPortraitDevice = (orientation == Surface.ROTATION_90 || orientation ==  Surface.ROTATION_270);
        }
    }

    private CameraDevice.StateCallback mCameraDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCamera = camera;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCamera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCamera = null;
        }
    };

    CameraCaptureSession.StateCallback mCameraCaptureSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mPreviewSession = session;
            updatePreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Toast.makeText(mActivity, "onConfigureFailed", Toast.LENGTH_LONG).show();
        }
    };

    public void open() {
        try {
            android.hardware.camera2.CameraManager manager =
                    (android.hardware.camera2.CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    mCameraSize = getClosestSize(map.getOutputSizes(SurfaceTexture.class), new Size(1600,1200));

                    HandlerThread thread = new HandlerThread("OpenCamera");

                    thread.start();
                    Handler backgroundHandler = new Handler(thread.getLooper());

                    manager.openCamera(cameraId, mCameraDeviceCallback, backgroundHandler);

                    return;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCaptureSession() {
        mSurfaceTexture = new SurfaceTexture(mTextureID);
        mSurfaceTexture.setDefaultBufferSize(mCameraSize.getWidth(), mCameraSize.getHeight());
        Surface surface = new Surface(mSurfaceTexture);

        try {
            mPreviewBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mPreviewBuilder.addTarget(surface);
        try {
            mCamera.createCaptureSession(Collections.singletonList(surface), mCameraCaptureSessionCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        setCameraRotation();

        mInitialized = true;
    }

    private void updatePreview() {
        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        HandlerThread thread = new HandlerThread("CameraPreview");
        thread.start();
        Handler backgroundHandler = new Handler(thread.getLooper());

        try {
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void updateTexture() {
        if (mInitialized) {
            mSurfaceTexture.updateTexImage();
        }
    }

    public void setCameraRotation() {
        if (!mInitialized) {
            return;
        }

        Point displaySize = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getSize(displaySize);

        if (displaySize.x > displaySize.y) {
            double scale = (double) displaySize.y / (double) mCameraSize.getHeight();
            mImageSize = new Size((int)(scale * mCameraSize.getWidth()), (int)(scale * mCameraSize.getHeight()));
        } else {
            double scale = (double) displaySize.x / (double) mCameraSize.getHeight();
            mImageSize = new Size((int)(scale * mCameraSize.getHeight()), (int)(scale * mCameraSize.getWidth()));
        }

        int orientation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        switch(orientation) {
            case Surface.ROTATION_0:
                mCameraRotation = mIsPortraitDevice ? CameraRotation.ROTATION_270 : CameraRotation.ROTATION_0;
                break;
            case Surface.ROTATION_90:
                mCameraRotation = mIsPortraitDevice ? CameraRotation.ROTATION_0 : CameraRotation.ROTATION_90;
                break;
            case Surface.ROTATION_180:
                mCameraRotation = mIsPortraitDevice ? CameraRotation.ROTATION_90 : CameraRotation.ROTATION_180;
                break;
            case Surface.ROTATION_270:
                mCameraRotation = mIsPortraitDevice ? CameraRotation.ROTATION_180 : CameraRotation.ROTATION_270;
                break;
        }
    }

    public CameraRotation getCameraRotation() {
        return mCameraRotation;
    }

    public Size getCameraSize() {
        return mImageSize;
    }

    public boolean getInitialized() {
        return mInitialized;
    }

    private Size getClosestSize(Size[] inputSizes, Size idealSize){

        Size ret = null;
        long idealArea = getArea(idealSize);
        long smallestDiff = 0;

        for(Size size : inputSizes){
            if(ret == null){
                ret = size;
                smallestDiff = Math.abs(idealArea - getArea(size));
            } else {
                long nowDiff = Math.abs(idealArea - getArea(size));
                if(nowDiff == 0){
                    return size;
                } else if(nowDiff < smallestDiff){
                    ret = size;
                    smallestDiff = nowDiff;
                }
            }
        }

        return ret;
    }

    private long getArea(Size size){
        return size.getWidth() * size.getHeight();
    }
}