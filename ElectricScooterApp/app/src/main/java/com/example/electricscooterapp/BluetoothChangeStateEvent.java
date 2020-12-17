package com.example.electricscooterapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;

class BluetoothChangeStateEvent {
    void ReceiveBluetoothChangeStateEvent(Intent intent)
    {
        final String action = intent.getAction();
        assert action != null;
        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);
            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                case BluetoothAdapter.STATE_TURNING_OFF:
                case BluetoothAdapter.STATE_TURNING_ON:
                    intent.putExtra("is_bluetooth_enabled", false);
                    break;
                case BluetoothAdapter.STATE_ON:
                    intent.putExtra("is_bluetooth_enabled", true);
                    break;
            }
        }
    }

    void ReceiveBluetoothDeviceEvent(Intent intent)
    {
        final String action = intent.getAction();
        assert action != null;
        if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
            intent.putExtra("is_bluetooth_device_connected", false);
        }
        else{
            intent.putExtra("is_bluetooth_device_connected", true);
        }
    }
}
