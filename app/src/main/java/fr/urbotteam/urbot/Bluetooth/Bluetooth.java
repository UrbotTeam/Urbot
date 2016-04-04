package fr.urbotteam.urbot.Bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by paul on 4/4/16.
 */
public class Bluetooth {
    private BluetoothAdapter mBLuetoothAdapter;
    private BluetoothDevice mDevice;
    private BluetoothSocket mSocket;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    Activity mActivity;

    Bluetooth(Activity activity)
    {
        mActivity = activity;
        mBLuetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Inscrire le BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mActivity.registerReceiver(receiver, filter);

        if (!mBLuetoothAdapter.isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivity.startActivityForResult(turnOn, 0);
        }

        mBLuetoothAdapter.startDiscovery();
    }

    private void openBT() throws IOException
    {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        mSocket = mDevice.createRfcommSocketToServiceRecord(uuid);
        mSocket.connect();
        mOutputStream = mSocket.getOutputStream();
        mInputStream = mSocket.getInputStream();

        String msg = "perdu";
        msg += "\n";
        mOutputStream.write(msg.getBytes());
        //beginListenForData();
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

    // On crée un BroadcastReceiver pour ACTION_FOUND
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // Quand la recherche trouve un terminal
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // On récupère l'object BluetoothDevice depuis l'Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // On ajoute le nom et l'adresse du périphérique dans un ArrayAdapter (par exemple pour l'afficher dans une ListView)
                if (device.getName().equals("CVBT_B")) {
                    mDevice = device;
                    mBLuetoothAdapter.cancelDiscovery();
                    try
                    {
                        openBT();
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
