package com.example.bletest;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

/**
 * Created by youchuangwen on 11/05/2017.
 */

public class BleOldScanner extends BleScanner {
    private static final String TAG = "BleOldScanner";
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLE bluetoothLE;

    public BleOldScanner(BluetoothLE bluetoothLE, BluetoothAdapter mBluetoothAdapter){
        this.bluetoothLE = bluetoothLE;
        this.mBluetoothAdapter = mBluetoothAdapter;
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    // Do nothing if target device is scanned
                    if(bluetoothLE.getDeviceScanned())
                        return;

                    Log.d(TAG, "device="+device.getName()+" add="+device.getAddress());
                    String tmpName = device.getName();
                    if(bluetoothLE.getDeviceName().equals(device.getName())){
                        bluetoothLE.setDeviceAddress(device.getAddress());
                        bluetoothLE.setDeviceScanned(true);

                        bluetoothLE.scanFound();
                        /*Intent gattServiceIntent = new Intent(activity, BluetoothLeService.class);

                        activity.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
                        activity.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
                        if (Build.VERSION.SDK_INT < 21) {
                            mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        } else {
                            mLEScanner.stopScan(mScanCallback);
                        }*/
                    }
                }
            };

    @SuppressLint("NewApi")
    public void startScan(){
        mBluetoothAdapter.startLeScan(mLeScanCallback);
    }

    @SuppressLint("NewApi")
    public void stopScan(){
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
    }
}
