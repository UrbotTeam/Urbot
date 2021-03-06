package fr.urbotteam.urbot;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.widget.Toast;

/**
 * Created by paul on 4/20/16.
 * Main application
 * Starts if bluetooth is supported
 */
public class UrbotApplication extends Application {
    /**
     * Save if the bluetooth was activated before the app launched
     */
    private boolean keepBluetooth = false;

    @Override
    public void onCreate() {
        super.onCreate();

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(this.getApplicationContext(),
                    "Device does not support bluetooth", Toast.LENGTH_SHORT)
                    .show();
        } else {
            if (mBluetoothAdapter.isEnabled()) {
                keepBluetooth = true;
            }

            new UrbotActivity();
        }
    }

    /**
     *
     * @return true if bluetooth was on before the app, false otherwise
     */
    public boolean isKeepBluetooth() {
        return this.keepBluetooth;
    }
}
