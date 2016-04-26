package fr.urbotteam.urbot;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

public class CameraService extends Service {
    private volatile LinkedList<Face> mFaces = new LinkedList<>();
    private Point center = new Point();
    private Timer scheduledTimer;
    private UrbotBluetoothService urbotBluetoothService;
    private CameraSource mCameraSource;
    private final IBinder mBinder = new LocalBinder();

    private static final String TAG = "CameraDebug";

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate() {
        super.onCreate();

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            Log.e(TAG, "Camera service permission denied");
            //requestCameraPermission();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        scheduledTimer.cancel();
        scheduledTimer.purge();

        mCameraSource.stop();
        mCameraSource.release();

        Log.i(TAG, "Stopping camera");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    public void init(UrbotBluetoothService urbotBluetoothService)
    {
        Log.i(TAG, "Starting camera");
        this.urbotBluetoothService = urbotBluetoothService;

        try {
            mCameraSource.start();
            processCentre();
        } catch (SecurityException e) {
            Log.e(TAG, "createCameraSource ", e);
        } catch (IOException e) {
            Log.d(TAG, "createCameraSource ", e);
        }
    }

    public class LocalBinder extends Binder {
        public CameraService getService() {
            // Return this instance of LocalService so clients can call public methods
            return CameraService.this;
        }
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {
        FaceDetector detector = new FaceDetector.Builder(this)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        detector.setProcessor(new MultiProcessor.Builder<>(new FaceTrackerFactory()).build());

        if (!detector.isOperational()) {
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        WindowManager window = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display displaySize = window.getDefaultDisplay();

        int width = displaySize.getWidth();
        int height = displaySize.getHeight();

        if (isPortraitMode()) {
            height = displaySize.getWidth();
            width = displaySize.getHeight();
        }

        mCameraSource = new CameraSource.Builder(this, detector)
                .setRequestedPreviewSize(width, height)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    //==============================================================================================
    // Computing movement needed to center the faces
    //==============================================================================================

    private void processCentre() {
        scheduledTimer = new Timer();

        scheduledTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (urbotBluetoothService != null && mFaces.size() != 0) {
                    Iterator<Face> iterator = mFaces.iterator();
                    Face face;
                    float x = 0, y = 0;
                    int size = mFaces.size();

                    while (iterator.hasNext()) {
                        face = iterator.next();
                        PointF facePosition = face.getPosition();

                        Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
                        x += display.getWidth() - (facePosition.x + face.getWidth() / 2); // Because camera is mirrored
                        y += facePosition.y + face.getHeight() / 2;
                    }

                    x /= size;
                    y /= size;

                    center.set((int) x, (int) y);
                    Point movementNeeded = getMovementNeeded(center);

                    try {
                        if (movementNeeded.x > 0) {
                            urbotBluetoothService.sendData("g");
                            Log.d(TAG, "send g");
                        } else if (movementNeeded.x < 0) {
                            urbotBluetoothService.sendData("d");
                            Log.d(TAG, "send d");
                        }

                        if (movementNeeded.y > 0) {
                            urbotBluetoothService.sendData("h");
                            Log.d(TAG, "send h");
                        } else if (movementNeeded.y < 0) {
                            urbotBluetoothService.sendData("b");
                            Log.d(TAG, "send b");
                        }
                    } catch (IOException e) {
                        Log.d(TAG, "IOException sending data", e);
                    }
                }
            }
        }, 0, 1000);
    }

    public Point getMovementNeeded(Point center) {
        Size size = mCameraSource.getPreviewSize();

        if (size != null && center.x != 0 && center.y != 0) {
            int margin = 50;
            float w = size.getWidth() / 2;
            float h = size.getHeight() / 2;
            float movementLeft, movementTop;

            if (isPortraitMode()) {
                h = size.getWidth() / 2;
                w = size.getHeight() / 2;
            }

            movementLeft = center.x - w;
            movementTop = center.y - h;

            if (movementLeft > -margin && movementLeft < margin)
                movementLeft = 0;
            if (movementTop > -margin && movementTop < margin)
                movementTop = 0;

            return new Point((int) movementLeft, (int) movementTop);
        }

        return new Point();
    }

    private boolean isPortraitMode() {
        try {
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                return false;
            }
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                return true;
            }
        } catch (NullPointerException e) {
            Log.d(TAG, "isPortraitMode ", e);
        }

        return false;
    }

    //==============================================================================================
    // Face Tracker
    //==============================================================================================
    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class FaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new FaceTracker();
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class FaceTracker extends Tracker<Face> {
        private Face face;

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            face = item;
            mFaces.add(face);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mFaces.set(mFaces.indexOf(this.face), face);
            this.face = face;
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mFaces.remove(face);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mFaces.remove(face);
        }
    }
}


