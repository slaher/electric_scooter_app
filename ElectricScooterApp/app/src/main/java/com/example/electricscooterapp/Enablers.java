package com.example.electricscooterapp;

import android.bluetooth.BluetoothAdapter;

class Enablers {
    boolean isBluetoothEnabled(BluetoothAdapter bluetoothAdapter) {
        return bluetoothAdapter.isEnabled();
    }

}
