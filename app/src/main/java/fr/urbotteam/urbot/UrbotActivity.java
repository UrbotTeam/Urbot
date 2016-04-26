package fr.urbotteam.urbot;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class UrbotActivity extends Activity {
    private static final String TAG = "CameraDebug";
    private CameraService mCameraService;
    private UrbotBluetoothService urbotBluetoothService;
    private boolean mBound;
    private final int mId = 42;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_camera);

        if(savedInstanceState == null) {
            Intent intent = new Intent(this, CameraService.class);
            bindService(intent, mCameraConnection, Context.BIND_AUTO_CREATE);

            intent = new Intent(this, UrbotBluetoothService.class);
            bindService(intent, mBluetoothConnection, Context.BIND_AUTO_CREATE);

            initNotification();
        } else {
            Log.d(TAG, "onCreate savedInstanceState non null");
        }
    }

    // Create Notification
    private void initNotification() {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle("Urbot")
                        .setContentText("Click to stop");

        mBuilder.setOngoing(true);
        mBuilder.setAutoCancel(true);

        //Register a receiver to stop Service
        registerReceiver(stopServiceReceiver, new IntentFilter("myFilter"));
        PendingIntent contentIntent = PendingIntent.getBroadcast(this, 0, new Intent("myFilter"), PendingIntent.FLAG_UPDATE_CURRENT);

        mBuilder.setContentIntent(contentIntent);

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(mId, mBuilder.build());
    }


    //We need to declare the receiver with onReceive function as below
    protected BroadcastReceiver stopServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
            Log.d(TAG, "onReceive finish");
        }
    };

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy application");

        unregisterReceiver(stopServiceReceiver);

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
            Log.v(TAG, "Bluetooth service connected");
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
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mCameraConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.v(TAG, "Camera service connected");

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
