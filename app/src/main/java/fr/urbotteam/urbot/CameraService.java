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
     * Checks for permissions and initiates the creation of the camera.
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

    /**
     *
     * @param intent
     * @return mBinder
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Stops the scheduled timer and releases the camera
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        scheduledTimer.cancel();
        scheduledTimer.purge();

        mCameraSource.stop();
        mCameraSource.release();

        Log.i(TAG, "Stopping camera");
    }

    /**
     *
     * @param intent
     * @param flags
     * @param startId
     * @return The state of the service
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    /**
     * Starts the camera and the position computing
     * @param urbotBluetoothService The bluetooth service needed to send data
     */
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

    /**
     * Local binder to give access to public method to another activity
     */
    public class LocalBinder extends Binder {
        public CameraService getService() {
            // Return this instance of LocalService so clients can call public methods
            return CameraService.this;
        }
    }

    /**
     * Creates the camera with the size of the screen with 30fps.
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
    /**
     * Starts a scheduled timer of 0.5s who computes the center of gravity of people on the screen
     * and send the movement needed to center it on screen via the bluetooth service
     */
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

                    Display display =
                            ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

                    // Because camera is mirrored
                    x += display.getWidth() - (facePosition.x + face.getWidth() / 2);
                    y += facePosition.y + face.getHeight() / 2;
                }

                x /= size;
                y /= size;
                center.set((int) x, (int) y);   // Center of gravity

                Point movementNeeded = getMovementNeeded(center);

                try {
                    if (movementNeeded.x > 0) {
                        urbotBluetoothService.sendData("d");
                    } else if (movementNeeded.x < 0) {
                        urbotBluetoothService.sendData("g");
                    } else if (movementNeeded.y > 0) {
                        urbotBluetoothService.sendData("b");
                    } else if (movementNeeded.y < 0) {
                        urbotBluetoothService.sendData("h");
                    }
                } catch (IOException e) {
                    Log.d(TAG, "IOException sending data", e);
                }
            }
            }
        }, 0, 500);
    }

    /**
     * Computes the movement needed to center the people on screen.
     * There is a margin of 150 pixels around the center.
     * @param center The center of gravity
     * @return The movement needed in pixels, (0,0) if nothing is needed
     */
    public Point getMovementNeeded(Point center) {
        Size size = mCameraSource.getPreviewSize();

        if (size != null && center.x != 0 && center.y != 0) {
            int margin = 150;
            float w = size.getWidth() / 2;
            float h = size.getHeight() / 2;
            float movementLeft, movementTop;

            if (isPortraitMode()) {
                h = size.getWidth() / 2;
                w = size.getHeight() / 2;
            }

            movementLeft = center.x - w;
            movementTop = center.y - h;

            if (movementLeft > -margin && movementLeft < margin) {
                movementLeft = 0;
            }
            if (movementTop > -margin && movementTop < margin) {
                movementTop = 0;
            }

            return new Point((int) movementLeft, (int) movementTop);
        }

        return new Point();
    }

    /**
     *
     * @return true if portrait, false if landscape
     */
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
     * Face tracker for each detected individual.
     */
    private class FaceTracker extends Tracker<Face> {
        private Face face;

        /**
         * Start tracking the detected face instance.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            face = item;
            mFaces.add(face);
        }

        /**
         * Update the position/characteristics of the face within the list.
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
         * Called when the face is assumed to be gone for good. Remove the face from the list.
         */
        @Override
        public void onDone() {
            mFaces.remove(face);
        }
    }
}


