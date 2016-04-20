package fr.urbotteam.urbot;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;

/**
 * Created by paul on 4/20/16.
 */
public class UrbotApplication extends Application {
    private boolean keepBluetooth = false;

    @Override
    public void onCreate() {
        super.onCreate();

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
        } else {
            if (mBluetoothAdapter.isEnabled()) {
                keepBluetooth = true;
            }
        }

        MainActivity urbotActivity = new MainActivity();
    }

    public boolean isKeepBluetooth() {
        return this.keepBluetooth;
    }
}
