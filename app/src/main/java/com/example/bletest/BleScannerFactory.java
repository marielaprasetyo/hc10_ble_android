package com.example.bletest;

import android.bluetooth.BluetoothAdapter;

/**
 * Created by youchuangwen on 11/05/2017.
 */

public class BleScannerFactory{

    public static BleScanner getBleScanner(BluetoothLE bluetoothLE, BluetoothAdapter bluetoothAdapter) {

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            return new BleNewScanner(bluetoothLE, bluetoothAdapter);
        }else{
            return new BleOldScanner(bluetoothLE, bluetoothAdapter);
        }

    }
}