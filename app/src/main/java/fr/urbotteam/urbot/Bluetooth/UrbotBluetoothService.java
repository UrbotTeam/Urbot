package fr.urbotteam.urbot.Bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by paul on 4/18/16.
 */
public class UrbotBluetoothService extends Service {
    private static final String TAG = "CameraDebug";

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice;
    private BluetoothSocket mSocket;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private final IBinder mBinder = new LocalBinder();

    private boolean bluetoothConnected = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(TAG, "Found device : " + device.getName());

                if (device.getName() != null && device.getName().equals("CVBT_B")) {
                    Log.d(TAG, "Found arduino device");
                    mDevice = device;
                    mBluetoothAdapter.cancelDiscovery();

                    try {
                        openConnexion();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e(TAG, "Error while opening bluetooth connexion");
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeBluetooth();

        Log.d(TAG, "onDestroy UrbotBluetoothService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startBluetooth();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void startBluetooth() {
        if (!bluetoothConnected) {
            Log.d(TAG, "Starting bluetooth");

            // Inscrire le BroadcastReceiver
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            this.getApplication().registerReceiver(receiver, filter);

            if (!mBluetoothAdapter.isEnabled()) {
                mBluetoothAdapter.enable();
            }

            mBluetoothAdapter.startDiscovery();
        } else {
            Log.d(TAG, "Bluetooth is already connected");
        }
    }

    private void openConnexion() throws IOException {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        mSocket = mDevice.createRfcommSocketToServiceRecord(uuid);

        try {
            mSocket.connect();
            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream();

            bluetoothConnected = true;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Socket error");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Unknown error");
        }
    }

    public void closeBluetooth() {
        Log.d(TAG, "Closing bluetooth");

        try {
            bluetoothConnected = false;

            mBluetoothAdapter.cancelDiscovery();
            mBluetoothAdapter.disable();
            getApplication().unregisterReceiver(receiver);

            if (mOutputStream != null)
                mOutputStream.close();
            if (mInputStream != null)
                mInputStream.close();
            if (mSocket != null)
                mSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendData(String message) throws IOException {
        try {
            if (bluetoothConnected) {
                Log.d(TAG, "Sending data");
                message += "\n";
                mOutputStream.write(message.getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "sendData Error");
        }
    }

    public class LocalBinder extends Binder {
        public UrbotBluetoothService getService() {
            // Return this instance of LocalService so clients can call public methods
            return UrbotBluetoothService.this;
        }
    }
}
