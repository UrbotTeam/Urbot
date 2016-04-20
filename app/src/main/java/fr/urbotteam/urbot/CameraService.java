package fr.urbotteam.urbot;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.face.Face;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

public class CameraService extends Service {
    // TODO Make a service out of the fragment
    private volatile LinkedList<Face> mFaces = new LinkedList();
    private Point center = new Point();
    private Timer scheduledTimer;

    private static final String TAG = "CameraDebug";

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    private void processCentre() {
        scheduledTimer = new Timer();
        //TODO changer service bluetooth to IntentService
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

        Log.d(TAG, "isPortraitMode returning false by default");
        return false;
    }
}


