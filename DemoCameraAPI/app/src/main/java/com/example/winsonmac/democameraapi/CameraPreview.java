package com.example.winsonmac.democameraapi;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.*;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private final String TAG = "Preview";
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private Context mContext;
    private int mCameraType;

    private float maximumZoomLevel;
    private float zoomLevel;
    private float fingerSpacing = 0;

    public CameraPreview(Context context, Camera camera, int cameraType) {
        super(context);
        this.mContext = context;
        this.mCameraType = cameraType;
        setUpPreview(camera);
    }

    private void setUpPreview(Camera camera) {
        mCamera = camera;

        // Setup holder for handling callback event
        mHolder = getHolder();
        mHolder.addCallback(this);

        // Will be required on Android version prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    private void setUpCameraFeatures() {
        // Get the camera device supported preview size
        List<Size> supportSizeList = mCamera.getParameters().getSupportedPreviewSizes();
        if (supportSizeList != null) {
            Size supportedPreviewSize = getOptimalPreviewSize(mCamera.getParameters().getSupportedPreviewSizes(), getWidth(), getHeight());
            mHolder.setFixedSize(supportedPreviewSize.width, supportedPreviewSize.height);
        }

        // Set auto focus
        Parameters params = mCamera.getParameters();

        // Check either device support or not
        if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        // Set focus area when touching on the preview
        if (params.getMaxNumMeteringAreas() > 0) { // check that metering areas are supported
            List<Camera.Area> meteringAreas = new ArrayList<Area>();

            Rect areaRect1 = new Rect(-100, -100, 100, 100);    // specify an area in center of image
            meteringAreas.add(new Area(areaRect1, 600)); // set weight to 60%
            Rect areaRect2 = new Rect(800, -1000, 1000, -800);  // specify an area in upper right of image
            meteringAreas.add(new Camera.Area(areaRect2, 400)); // set weight to 40%
            params.setMeteringAreas(meteringAreas);
        }

        // Set captured image format
        params.setPictureSize(1920, 1080);
        params.setPictureFormat(PixelFormat.JPEG);
        params.setJpegQuality(100);

        maximumZoomLevel = params.getMaxZoom();

        // Setup camera features
        mCamera.setParameters(params);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mCamera.setPreviewDisplay(holder);
            updateCameraOrientation();
            mCamera.startPreview();
            setUpCameraFeatures();
        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        if (mHolder.getSurface() == null) {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }

        try {
            // make any resize, rotate or reformatting changes here
            Camera.Parameters parameters = mCamera.getParameters();
            mCamera.setParameters(parameters);
            updateCameraOrientation();

            // start preview with new settings
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
        } catch (Exception e) {
            Log.d("DG_DEBUG", "Error starting camera preview: " + e.getMessage());
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            Parameters params = mCamera.getParameters();
            float currentFingerSpacing;

            if (event.getPointerCount() == 2) { //Multi touch.
                currentFingerSpacing = getFingerSpacing(event);
                float delta = 1.6f; //Control this value to control the zooming sensibility
                if (fingerSpacing != 0) {
                    if (currentFingerSpacing > fingerSpacing) { //Don't over zoom-in
                        if ((maximumZoomLevel - zoomLevel) <= delta) {
                            delta = maximumZoomLevel - zoomLevel;
                        }
                        zoomLevel = zoomLevel + delta;
                    } else if (currentFingerSpacing < fingerSpacing) { //Don't over zoom-out
                        if ((zoomLevel - delta) < 1f) {
                            delta = zoomLevel - 1f;
                        }
                        zoomLevel = zoomLevel - delta;
                    }
                    params.setZoom((int) zoomLevel);
                    mCamera.setParameters(params);
                }
                fingerSpacing = currentFingerSpacing;
            } else { //Single touch point, needs to return true in order to detect one more touch point
                return true;
            }
            return true;
        } catch (final Exception e) {
            //Error handling up to you
            return true;
        }
    }

    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    public void updateCameraOrientation() {
        int rotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getOrientation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        CameraInfo info = new CameraInfo();
        Camera.getCameraInfo(mCameraType, info);
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        mCamera.setDisplayOrientation(result);
    }

    private float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }
}
