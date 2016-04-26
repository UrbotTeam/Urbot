package fr.urbotteam.urbot;

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
 * Bluetooth service
 * Connects to the arduino and handle messaging
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

    /**
     * Handle the devices discovered by the bluetooth broadcast.
     * If the device is the arduino, open the connexion.
     */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.v(TAG, "Found device : " + device.getName());

                if (device.getName() != null && device.getName().equals("CVBT_B")) {
                    Log.i(TAG, "Found arduino device");
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

    /**
     * When the service is created.
     * Gets the default bluetooth adapter.
     */
    @Override
    public void onCreate() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * When the service is destroyed.
     * Removes the receiver and closes the bluetooth connexion.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        getApplication().unregisterReceiver(receiver);
        closeBluetooth();
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
        turnOnBluetooth();
        startDiscovery();

        return START_STICKY;
    }

    /**
     *
     * @param intent
     * @return
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Activates the bluetooth and registers the receiver
     */
    public void turnOnBluetooth()
    {
        Log.i(TAG, "Starting bluetooth");

        // Inscrire le BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.getApplication().registerReceiver(receiver, filter);

        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
        }

        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
    }

    /**
     * Starts the bluetooth discovery if not connected yet.
     */
    public void startDiscovery() {
        if (!bluetoothConnected) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Log.d(TAG, "Starting discovery : " + mBluetoothAdapter.startDiscovery());
        } else {
            Log.v(TAG, "Bluetooth is already connected");
        }
    }

    /**
     * Connects to the arduino device
     * @throws IOException
     */
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

    /**
     * Closes the bluetooth connexion
     */
    public void closeBluetooth() {
        Log.i(TAG, "Closing bluetooth");

        try {
            bluetoothConnected = false;

            mBluetoothAdapter.cancelDiscovery();

            if(!((UrbotApplication)getApplication()).isKeepBluetooth()) {
                mBluetoothAdapter.disable();
            }

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

    /**
     * Sends data to the arduino device
     * @param message Data to send
     * @throws IOException
     */
    public void sendData(String message) throws IOException {
        try {
            if (bluetoothConnected) {
                Log.i(TAG, "Sending data : " + message);
                message += "\n";
                mOutputStream.write(message.getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "sendData Error");
        }
    }

    /**
     * ocal binder to give access to public method to another activity
     */
    public class LocalBinder extends Binder {
        public UrbotBluetoothService getService() {
            // Return this instance of LocalService so clients can call public methods
            return UrbotBluetoothService.this;
        }
    }
}
