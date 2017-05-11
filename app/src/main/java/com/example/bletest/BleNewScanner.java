package com.example.bletest;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by youchuangwen on 11/05/2017.
 */

public class BleNewScanner extends BleScanner {
    private BluetoothLeScanner mLEScanner;
    private BluetoothAdapter mBluetoothAdapter;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothLE bluetoothLE;


    @SuppressLint("NewApi")
    public BleNewScanner(BluetoothLE bluetoothLE, BluetoothAdapter mBluetoothAdapter){
        this.bluetoothLE = bluetoothLE;
        this.mBluetoothAdapter = mBluetoothAdapter;
        this.mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        this.settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        this.filters = new ArrayList<ScanFilter>();
    }

    @SuppressLint("NewApi")
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            final int rssi = result.getRssi();
            Log.i("rssi", "name=" + result.getDevice().getName());
            Log.i("rssi", "add=" + result.getDevice().getAddress());
            Log.i("rssi", "rssi=" + rssi);
            if (bluetoothLE.getDeviceScanned())
                return;

            BluetoothDevice btDevice = result.getDevice();
            if (bluetoothLE.getDeviceName().equals(btDevice.getName())) {
                Log.i("rssi", "inin");

                bluetoothLE.setDeviceAddress(btDevice.getAddress());
                bluetoothLE.setDeviceScanned(true);

                //Intent gattServiceIntent = new Intent(activity, BluetoothLeService.class);

                //activity.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
                //activity.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
                bluetoothLE.scanFound();
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    @SuppressLint("NewApi")
    public void startScan(){
        mLEScanner.startScan(filters, settings, mScanCallback);
    }

    @SuppressLint("NewApi")
    public void stopScan(){
        mLEScanner.stopScan(mScanCallback);
    }
}
