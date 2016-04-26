package fr.urbotteam.urbot;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
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

public class CameraFragment extends Fragment {
    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private volatile LinkedList<Face> mFaces = new LinkedList();
    private Point center = new Point();
    private Context mContext;
    private Activity mActivity;
    private UrbotBluetoothService urbotBluetoothService;
    private boolean mBound;
    private Timer scheduledTimer;
    private boolean keepBluetooth = false;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    private static final String TAG = "CameraDebug";

    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public static CameraFragment newInstance() {
        return new CameraFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int rc = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        }

        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        try {
            mPreview = (CameraSourcePreview) view.findViewById(R.id.preview);
            mGraphicOverlay = (GraphicOverlay) view.findViewById(R.id.faceOverlay);
        } catch (ClassCastException e) {
            Log.e(TAG, "onViewCreated Cast exception", e);
        } catch (Exception e) {
            Log.e(TAG, "onViewCreated Unknown exception", e);
        }
    }

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mActivity = getActivity();
        mContext = mActivity.getApplicationContext();

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this.getActivity(), Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }

        Intent intent = new Intent(this.getActivity(), UrbotBluetoothService.class);
        mActivity.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "onServiceConnected ");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            UrbotBluetoothService.LocalBinder binder = (UrbotBluetoothService.LocalBinder) service;
            urbotBluetoothService = binder.getService();
            urbotBluetoothService.turnOnBluetooth();
            urbotBluetoothService.startDiscovery();

            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this.getActivity(), Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this.getActivity(), permissions, RC_HANDLE_CAMERA_PERM);
        }
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {
        FaceDetector detector = new FaceDetector.Builder(mContext)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        detector.setProcessor(new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());

        if (!detector.isOperational()) {
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        Point displaySize = new Point();
        getActivity().getWindowManager().getDefaultDisplay().getSize(displaySize);

        int width = displaySize.x;
        int height = displaySize.y;

        if (isPortraitMode()) {
            height = displaySize.x;
            width = displaySize.y;
        }

        mCameraSource = new CameraSource.Builder(mContext, detector)
                .setRequestedPreviewSize(width, height)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    private boolean isPortraitMode() {
        try {
            int orientation = mContext.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                return false;
            }
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                return true;
            }
        } catch (NullPointerException e) {
            Log.d(TAG, "isPortraitMode ", e);
        }

        Log.d(TAG, "isPortraitMode returning false by default");
        return false;
    }

    /**
     * Restarts the camera.
     */
    @Override
    public void onResume() {
        super.onResume();
        Log.d("TRY", "onResume ");

        Intent intent = new Intent(this.getActivity(), UrbotBluetoothService.class);
        mActivity.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        startCameraSource();
        processCentre();
    }

    /**
     * Stops the camera.
     */
    @Override
    public void onPause() {
        super.onPause();
        mPreview.stop();
        Log.d("TRY", "onPause ");

        scheduledTimer.cancel();
        scheduledTimer.purge();

        try {
            if(((UrbotApplication)mActivity.getApplication()).isKeepBluetooth()) {
                if (urbotBluetoothService != null) {
                    urbotBluetoothService.closeBluetooth();

                    if (mBound) {
                        mActivity.unbindService(mConnection);
                        mBound = false;
                    }
                }
            }
        } catch (ClassCastException e) {
            Log.e(TAG, "Cast exception", e);
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d("TRY", "onDestroy ");
        scheduledTimer.cancel();
        scheduledTimer.purge();

        try {
            if(((UrbotApplication)mActivity.getApplication()).isKeepBluetooth()) {
                if (urbotBluetoothService != null) {
                    urbotBluetoothService.closeBluetooth();

                    if (mBound) {
                        mActivity.unbindService(mConnection);
                        mBound = false;
                    }
                }
            }
        } catch (ClassCastException e) {
            Log.e(TAG, "Cast exception", e);
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mContext);
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this.getActivity(), code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    private void processCentre() {
        // TODO remove graphic overlay
        scheduledTimer = new Timer();
        scheduledTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(urbotBluetoothService != null && mFaces.size() != 0) {
                    Iterator<Face> iterator = mFaces.iterator();
                    Face face;
                    float x = 0, y = 0;
                    int size = mFaces.size();

                    while (iterator.hasNext()) {
                        face = iterator.next();
                        PointF facePosition = face.getPosition();

                        x += mGraphicOverlay.getWidth() - mGraphicOverlay.getWidthScale() * (facePosition.x + face.getWidth() / 2); // Because camera is mirrored
                        y += mGraphicOverlay.getHeightScale() * (facePosition.y + face.getHeight() / 2);
                    }

                    x /= size;
                    y /= size;

                    center.set((int) x, (int) y);
                    Point movementNeeded = getMovementNeeded(center);

                    try {
                        if (movementNeeded.x > 0) {
                            urbotBluetoothService.sendData("g");
                            Log.d(TAG, "send g");
                        } else if(movementNeeded.x < 0){
                            urbotBluetoothService.sendData("d");
                            Log.d(TAG, "send d");
                        }

                        if (movementNeeded.y > 0) {
                            urbotBluetoothService.sendData("h");
                            Log.d(TAG, "send h");
                        } else if(movementNeeded.y < 0){
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

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;
        private Face face;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);

        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
            face = item;
            mFaces.add(face);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
            mFaces.set(mFaces.indexOf(this.face), face);

            mFaceGraphic.p = center;
            this.face = face;
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
            mFaces.remove(face);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
            mFaces.remove(face);
        }
    }
}


