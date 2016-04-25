package fr.urbotteam.urbot;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import fr.urbotteam.urbot.Bluetooth.UrbotBluetoothService;

public class UrbotActivity extends Activity {
    // TODO : - Notification bar

    private static final String TAG = "CameraDebug";
    private CameraService mCameraService;
    private UrbotBluetoothService urbotBluetoothService;
    private boolean mBound;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_camera);

        Intent intent = new Intent(this, UrbotBluetoothService.class);
        bindService(intent, mBluetoothConnection, Context.BIND_AUTO_CREATE);

        intent = new Intent(this, CameraService.class);
        bindService(intent, mCameraConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            if (mBound) {
                unbindService(mBluetoothConnection);
                unbindService(mCameraConnection);
                mBound = false;
            }

            if (((UrbotApplication) getApplication()).isKeepBluetooth()) {
                if (urbotBluetoothService != null) {
                    urbotBluetoothService.closeBluetooth();
                }
            }
        } catch (ClassCastException e) {
            Log.e(TAG, "Cast exception", e);
        }
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mBluetoothConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Bluetooth service connected");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            UrbotBluetoothService.LocalBinder binder = (UrbotBluetoothService.LocalBinder) service;
            urbotBluetoothService = binder.getService();
            urbotBluetoothService.startBluetooth();

            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mCameraConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Camera service connected");

            // We've bound to LocalService, cast the IBinder and get LocalService instance
            CameraService.LocalBinder binder = (CameraService.LocalBinder) service;
            mCameraService = binder.getService();
            mCameraService.init(urbotBluetoothService);

            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
}
