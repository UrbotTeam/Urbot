package fr.urbotteam.urbot.Bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

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

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (device.getName().equals("CVBT_B")) {
                mDevice = device;
                mBluetoothAdapter.cancelDiscovery();
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

    @Override
    public void onCreate()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public void onDestroy()
    {
        closeBluetooth();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        startBluetooth();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    public void startBluetooth()
    {
        // Inscrire le BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.getApplication().registerReceiver(receiver, filter);

        if (!mBluetoothAdapter.isEnabled()) {
            Intent btIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            btIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getApplicationContext().startActivity(btIntent);
        }

        mBluetoothAdapter.startDiscovery();
    }

    private void openConnexion() throws IOException
    {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        mSocket = mDevice.createRfcommSocketToServiceRecord(uuid);
        mSocket.connect();
        mOutputStream = mSocket.getOutputStream();
        mInputStream = mSocket.getInputStream();

        //beginListenForData();
    }

    public void closeBluetooth()
    {
        try {
            mBluetoothAdapter.cancelDiscovery();
            mBluetoothAdapter.disable();
            getApplication().unregisterReceiver(receiver);

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
}
