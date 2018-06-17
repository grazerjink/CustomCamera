package com.example.winsonmac.democameraapi;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.*;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private final static String TAG = "main_activity";
    private Camera camera;
    private CameraPreview cameraPreview;
    private FrameLayout previewLayout;
    private Button btnCapture;
    private ImageView rotateImage;

    private SensorManager sensorManager;
    private ExifInterface exif;
    private int orientation;
    private int degrees = -1;

    // To avoid click many time in a moment
    private boolean didClickOnce = false;

    private PictureCallback mPicterCallback = new PictureCallback() {

        public void onPictureTaken(byte[] data, Camera camera) {
            new SaveImageTask().execute(data);
            Log.d(TAG, "onPictureTaken - jpeg");

            if (camera != null) {
                camera.startPreview();
            } else {
                setUpCameraPreivew(CameraInfo.CAMERA_FACING_BACK);
            }
            didClickOnce = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        // Getting the sensor service.
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        previewLayout = findViewById(R.id.preview);
        rotateImage = findViewById(R.id.rotateImage);
        btnCapture = findViewById(R.id.capture);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!didClickOnce) {
                    didClickOnce = true;
                    camera.takePicture(null, null, mPicterCallback);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpCameraPreivew(CameraInfo.CAMERA_FACING_BACK);
        // Register this class as a listener for the accelerometer sensor
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // release the camera immediately on pause event
        releaseCamera();
        // removing the inserted view - so when we come back to the app we
        // won't have the views on top of each other.
        previewLayout.removeViewAt(0);
    }

    private void setUpCameraPreivew(int cameraType) {
        camera = getCurrentCameraWithType(cameraType);
        cameraPreview = new CameraPreview(this, camera, cameraType);
        previewLayout.addView(cameraPreview, 0);
    }

    public Camera getCurrentCameraWithType(int cameraType) {
        Camera c = null;
        try {
            // attempt to get a Camera instance
            c = Camera.open(cameraType);
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }

        // returns null if camera is unavailable
        return c;
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.release(); // release the camera for other applications
            camera = null;
        }
    }

    private void refreshGallery(File file) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
        sendBroadcast(mediaScanIntent);
    }

    private class SaveImageTask extends AsyncTask<byte[], Void, String> {

        @Override
        protected String doInBackground(byte[]... data) {
            FileOutputStream outStream = null;

            // Write to SD Card
            try {
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File(sdCard.getAbsolutePath() + "/demo");
                dir.mkdirs();

                String fileName = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()).toString() + ".jpg";
                File outFile = new File(dir, fileName);

                outStream = new FileOutputStream(outFile);
                Bitmap rotatedBmp = adjustImageForRightOrientation(data[0], orientation);
                rotatedBmp.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
                outStream.flush();
                outStream.close();

                Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length + " to " + outFile.getAbsolutePath());

                // Adding Exif data for the orientation. For some strange reason the
                // ExifInterface class takes a string instead of a file.
                try {
                    exif = new ExifInterface("/sdcard/" + dir.getAbsolutePath() + fileName);
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION, "" + orientation);
                    exif.saveAttributes();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                refreshGallery(outFile);
                return outFile.getAbsolutePath();

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String message) {
            if (message != null) {
                Toast.makeText(MainActivity.this, "Saved file into " + message, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Saved file failed, please try again!", Toast.LENGTH_SHORT).show();
            }
        }

        private Bitmap adjustImageForRightOrientation(byte[] data, int orientation) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, options);

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(-270);
                    break;
                default:
                    matrix.postRotate(0);
                    break;
            }
            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        }
    }

    /**
     * Check if this device has a camera
     */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    /**
     * Check if this device has mounted a sd card
     */
    private boolean checkSDCard() {
        boolean state = false;

        String sd = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(sd)) {
            state = true;
        }

        return state;
    }

    /**
     * Putting in place a listener so we can get the sensor data only when
     * something changes.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        synchronized (this) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                RotateAnimation animation = null;
                if (event.values[0] < 4 && event.values[0] > -4) {
                    if (event.values[1] > 0 && orientation != ExifInterface.ORIENTATION_ROTATE_90) {
                        // UP
                        orientation = ExifInterface.ORIENTATION_ROTATE_90;
                        animation = getRotateAnimation(0);
                        degrees = 0;
                    } else if (event.values[1] < 0 && orientation != ExifInterface.ORIENTATION_ROTATE_270) {
                        // UP SIDE DOWN
                        orientation = ExifInterface.ORIENTATION_ROTATE_270;
                        animation = getRotateAnimation(180);
                        degrees = 180;
                    }
                } else if (event.values[1] < 4 && event.values[1] > -4) {
                    if (event.values[0] > 0 && orientation != ExifInterface.ORIENTATION_NORMAL) {
                        // LEFT
                        orientation = ExifInterface.ORIENTATION_NORMAL;
                        animation = getRotateAnimation(90);
                        degrees = 90;
                    } else if (event.values[0] < 0 && orientation != ExifInterface.ORIENTATION_ROTATE_180) {
                        // RIGHT
                        orientation = ExifInterface.ORIENTATION_ROTATE_180;
                        animation = getRotateAnimation(270);
                        degrees = 270;
                    }
                }
                if (animation != null) {
                    rotateImage.startAnimation(animation);
                }
            }

        }
    }

    /**
     * STUFF THAT WE DON'T NEED BUT MUST BE HEAR FOR THE COMPILER TO BE HAPPY.
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * Calculating the degrees needed to rotate the image imposed on the button
     * so it is always facing the user in the right direction
     *
     * @param toDegrees
     * @return
     */
    private RotateAnimation getRotateAnimation(float toDegrees) {
        float compensation = 0;

        if (Math.abs(degrees - toDegrees) > 180) {
            compensation = 360;
        }

        // When the device is being held on the left side (default position for
        // a camera) we need to add, not subtract from the toDegrees.
        if (toDegrees == 0) {
            compensation = -compensation;
        }

        // Creating the animation and the RELATIVE_TO_SELF means that he image
        // will rotate on it center instead of a corner.
        RotateAnimation animation = new RotateAnimation(degrees, toDegrees - compensation, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

        // Adding the time needed to rotate the image
        animation.setDuration(250);

        // Set the animation to stop after reaching the desired position. With
        // out this it would return to the original state.
        animation.setFillAfter(true);

        return animation;
    }

}
