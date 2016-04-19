package fr.urbotteam.urbot.Bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by paul on 4/4/16.
 */
public class Bluetooth {
    private static final String TAG = "CameraDebug";
    private static Bluetooth instance;

    private BluetoothAdapter mBLuetoothAdapter;
    private BluetoothDevice mDevice;
    private BluetoothSocket mSocket;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    Activity mActivity;

    private Bluetooth(Activity activity)
    {
        mActivity = activity;
        mBLuetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public static Bluetooth newInstance(Activity activity)
    {
        if(instance == null)
        {
            instance = new Bluetooth(activity);
        }

        return instance;
    }

    public void startBluetooth()
    {
        Log.d(TAG, "startBluetooth ");
        // Inscrire le BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mActivity.registerReceiver(receiver, filter);

        if (!mBLuetoothAdapter.isEnabled()) {
            Intent btIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            btIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mActivity.getApplicationContext().startActivity(btIntent);
            //Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //mActivity.startActivityForResult(turnOn, 0);
        }

        mBLuetoothAdapter.startDiscovery();
    }

    private void openConnexion() throws IOException
    {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        mSocket = mDevice.createRfcommSocketToServiceRecord(uuid);
        mSocket.connect();
        mOutputStream = mSocket.getOutputStream();
        mInputStream = mSocket.getInputStream();

        sendData("h");
        //beginListenForData();
    }

    public void sendData(String message) throws IOException
    {
        Log.d(TAG, "sendData ");
        message += "\n";
        mOutputStream.write(message.getBytes());
    }

    public void closeBluetooth()
    {
        try {
            mBLuetoothAdapter.cancelDiscovery();
            mBLuetoothAdapter.disable();
            mActivity.unregisterReceiver(receiver);

            if(mOutputStream != null)
                mOutputStream.close();
            if(mInputStream != null)
                mInputStream.close();
            if(mSocket != null)
                mSocket.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(TAG, "onReceive device : "+device.getName());

                if (device.getName().equals("CVBT_B")) {
                    mDevice = device;
                    mBLuetoothAdapter.cancelDiscovery();
                    Log.d(TAG, "onReceive Found the device");
                    try
                    {
                        openConnexion();
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    };
}
