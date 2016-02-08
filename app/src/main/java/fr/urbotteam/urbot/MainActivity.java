package fr.urbotteam.urbot;

import android.app.Activity;
import android.os.Bundle;

import fr.urbotteam.urbot.Bluetooth.BluetoothFragment;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (null == savedInstanceState) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, BluetoothFragment.newInstance())
                    .commit();
        }
    }
}
