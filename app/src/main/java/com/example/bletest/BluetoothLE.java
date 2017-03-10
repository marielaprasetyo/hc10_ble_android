package com.example.bletest;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;


@SuppressLint("NewApi")
public class BluetoothLE {
	private static final String TAG = "BluetoothLE";

    // Write UUID
    public static final UUID UUID_MOV_SERV = UUID.fromString("f000aa80-0451-4000-b000-000000000000");
    public static final UUID UUID_MOV_DATA = UUID.fromString("f000aa81-0451-4000-b000-000000000000");
    public static final UUID UUID_MOV_CONF = UUID.fromString("f000aa82-0451-4000-b000-000000000000"); // 0: disable, bit 0: enable x, bit 1: enable y, bit 2: enable z
    public static final UUID UUID_MOV_PERI = UUID.fromString("f000aa83-0451-4000-b000-000000000000");

    public static final UUID UUID_ACC_SERV = UUID.fromString("f000aa10-0451-4000-b000-000000000000");
    public static final UUID UUID_ACC_DATA = UUID.fromString("f000aa11-0451-4000-b000-000000000000");
    public static final UUID UUID_ACC_CONF = UUID.fromString("f000aa12-0451-4000-b000-000000000000");
    public static final UUID UUID_ACC_PERI = UUID.fromString("f000aa13-0451-4000-b000-000000000000");
    
	// Intent request codes
    private static final int REQUEST_ENABLE_BT = 2;
	
	private Activity activity = null;
    private String mDeviceName = null;
    private String mDeviceAddress = null;

	private BluetoothAdapter mBluetoothAdapter;

    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private boolean deviceScanned = false;


    private BluetoothGattCharacteristic dataC;
    private BluetoothGattCharacteristic configC;
    private BluetoothGattCharacteristic periodC;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic mWriteStateCharacteristic;

    private Handler mHandler;
    private Runnable mRunnable;

    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 3000;

    private int testCount = 0;


    public BluetoothLE(Activity activity, String mDeviceName) {
        mHandler = new Handler();

        this.activity = activity;
        this.mDeviceName = mDeviceName;

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(activity, "BLE not supported!", Toast.LENGTH_SHORT).show();
            ((BluetoothListener) activity).bleNotSupported();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(activity, "Bluetooth is not supported!", Toast.LENGTH_SHORT).show();
            ((BluetoothListener) activity).bleNotSupported();
            return;
        }

        if (Build.VERSION.SDK_INT >= 21) {
            Log.i("rssi", "Build.VERSION.SDK_INT >= 21");
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            filters = new ArrayList<ScanFilter>();
        }
    }
     
	// Code to manage Service lifecycle.
	private final ServiceConnection mServiceConnection = new ServiceConnection() {
	    @Override
	    public void onServiceConnected(ComponentName componentName, IBinder service) {
	        mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
	        if (!mBluetoothLeService.initialize()) {
	            Log.e(TAG, "Unable to initialize Bluetooth");
//	            activity.finish();
	        }
	        // Automatically connects to the device upon successful start-up initialization.
	        mBluetoothLeService.connect(mDeviceAddress);
	    }
	
	    @Override
	    public void onServiceDisconnected(ComponentName componentName) {
	        mBluetoothLeService = null;
            unbindBleService();
	    }
	};


    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            List <BluetoothGattService> serviceList;

            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.i("rssi", "mConnected = true");
                mConnected = true;
                //Terminate the BLE connection timeout (10sec)
                mHandler.removeCallbacks(mRunnable);
                ((BluetoothListener) activity).bleConnected();

//                Toast.makeText(activity, "BLE connected", Toast.LENGTH_SHORT).show();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.i("rssi", "mConnected = false");
                mConnected = false;
                ((BluetoothListener) activity).bleDisconnected();
                unbindBleService();

//                Toast.makeText(activity, "BLE disconnected!", Toast.LENGTH_SHORT).show();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {

                // Show all the supported services and characteristics on the user interface.
                serviceList = mBluetoothLeService.getSupportedGattServices();
                Log.i(TAG, " FIND SERVICE : " +  serviceList.size());
                if (serviceList.size() > 0) {
                    for (int ii = 0; ii < serviceList.size(); ii++) {
                        BluetoothGattService s = serviceList.get(ii);
                        Log.i(TAG, " service with uuid : " + s.getUuid().toString());

                        /*if(s.getUuid().toString().equals( UUID_MOV_SERV.toString()))
                        {
                            Log.i(TAG,"Motion1 !");

                            List<BluetoothGattCharacteristic> characteristics = s.getCharacteristics();

                            for (BluetoothGattCharacteristic c : characteristics) {
                                if (c.getUuid().toString().equals(UUID_MOV_DATA.toString())) {
                                    dataC = c;
                                }
                                if (c.getUuid().toString().equals(UUID_MOV_CONF.toString())) {
                                    configC = c;
                                }
                                if (c.getUuid().toString().equals(UUID_MOV_PERI.toString())) {
                                    periodC = c;
                                }
                            }
                        }*/
                        if(s.getUuid().toString().equals( UUID_ACC_SERV.toString()))
                        {
                            Log.i(TAG,"ACCELEROMETER !");

                            List<BluetoothGattCharacteristic> characteristics = s.getCharacteristics();

                            for (BluetoothGattCharacteristic c : characteristics) {
                                if (c.getUuid().toString().equals(UUID_ACC_DATA.toString())) {
                                    dataC = c;
                                }
                                if (c.getUuid().toString().equals(UUID_ACC_CONF.toString())) {
                                    configC = c;
                                }
                                if (c.getUuid().toString().equals(UUID_ACC_PERI.toString())) {
                                    periodC = c;
                                }
                            }
                        }
                    }
                }

                /*byte b[] = new byte[] {0x7F,0x00};
                b[0] = (byte)0xFF;
                mBluetoothLeService.setCharacteristicNotification(dataC, true);
                mBluetoothLeService.writeCharacteristic(configC, b);
                mBluetoothLeService.writeDescriptor(dataC, true);
                Log.i(TAG,"Found Motion !");

                int period = 1000;
                byte[] p = new byte[1];
                p[0] = (byte)((period / 10) + 10);
                mBluetoothLeService.writeCharacteristic(periodC, p);*/

                mBluetoothLeService.setCharacteristicNotification(dataC, true);
                mBluetoothLeService.writeCharacteristic(configC, new byte[]{1});
                mBluetoothLeService.writeDescriptor(dataC, true);
                Log.i(TAG,"Found Accelerometer !");

                int period = 1000;
                byte[] p = new byte[1];
                p[0] = (byte)((period / 10) + 10);
                mBluetoothLeService.writeCharacteristic(periodC, p);

            } else if(BluetoothLeService.ACTION_DATA_NOTIFY.equals(action)) {
                Log.i(TAG,"NOTIFY !");
            }

            else if (BluetoothLeService.ACTION_DATA_WRITE_SUCCESS.equals(action)) {
               // ((BluetoothListener) activity).bleWriteStateSuccess();
                Log.i(TAG,"WRITE_SUCCESS !");

            } else if (BluetoothLeService.ACTION_DATA_WRITE_FAIL.equals(action)) {
               //  ((BluetoothListener) activity).bleWriteStateFail();
                Log.i(TAG,"WRITE_FAIL !");

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
            	// byte[] data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);

//                String dataString = "";
//                for(int i=0; i<data.length; i++) {
//                    dataString += data[i] + " ";
//                }
//                Log.i(TAG, dataString);
//
//                if(testCount == 1) {
//                    Log.i(TAG, "WRITE 0x03");
//                    bleWriteState((byte)0x03);
//                }
//                testCount++;

/*                switch(data[0]) { // Handling notification depending on types
                    case (byte)0xFA:
                        Log.i(TAG, "----0xFA----");
                        ((BluetoothListener) activity).bleNoPlug();
                        break;
                    case (byte)0xFB:
                        Log.i(TAG, "----0xFB----");
                        byte[] plugId = new byte[data.length-1];
                        System.arraycopy(data, 1, plugId, 0, data.length - 1);
                        ((BluetoothListener) activity).blePlugInserted(plugId);
                        break;
                    case (byte)0xFC:
                    case (byte)0xFD:
                    case (byte)0xFE:
                        byte[] adcReading = new byte[data.length-1];
                        System.arraycopy(data, 1, adcReading, 0, data.length - 1);
                        ((BluetoothListener) activity).bleElectrodeAdcReading(data[0], adcReading);
                        break;

                    case (byte)0xFF:
                        Log.i(TAG, "----0xFF----");
                        byte[] colorReadings = new byte[data.length-1];
                        System.arraycopy(data, 1, colorReadings, 0, data.length-1);
                        ((BluetoothListener) activity).bleColorReadings(colorReadings);
                        break;
                }
*/
//                int color_sensor0[] = new int[4];
//                int color_sensor1[] = new int[4];
//                for(int i=0; i<4; i++) {
//                    color_sensor0[i] = data[(i*2)+1]<<8 + data[i*2];
//                    color_sensor1[i] = data[(i*2)+9]<<8 + data[i*2+8];

                Log.i(TAG,"DATA_AVAILABLE !");

            }
            else{
                	Log.i(TAG, "----BLE Can't handle data----");
                }
            
            
        }
    };

    private void unbindBleService() {
        activity.unbindService(mServiceConnection);
        activity.unregisterReceiver(mGattUpdateReceiver);
        deviceScanned = false;
    }

	
	public void bleConnect() {
		
		// Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        else {
            bleScan();
        }
		
		return;
	}

    public void bleDisconnect() {
        if(mBluetoothLeService != null) {
            mBluetoothLeService.disconnect();
        }

        if(!mConnected) {
            //Terminate the BLE connection timeout (10sec)
            mHandler.removeCallbacks(mRunnable);
        }

        if (Build.VERSION.SDK_INT < 21) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        } else {
            mLEScanner.stopScan(mScanCallback);
        }


    }

    public void bleWriteState(byte state) {

        if((mBluetoothLeService != null) && (mWriteStateCharacteristic != null)) {
            mWriteStateCharacteristic.setValue(new byte[] { state });
            mBluetoothLeService.writeCharacteristic(mWriteStateCharacteristic);
        }
        return;
    }

    private void bleScan() {
        mHandler.postDelayed(mRunnable = new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT < 21) {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                } else {
                    mLEScanner.stopScan(mScanCallback);

                }
                ((BluetoothListener) activity).bleConnectionTimeout();
//                    Log.i("BLE", "thread run");
            }
        }, SCAN_PERIOD);
        if (Build.VERSION.SDK_INT < 21) {
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mLEScanner.startScan(filters, settings, mScanCallback);
        }
    }
	
	private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_WRITE_SUCCESS);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_WRITE_FAIL);
        return intentFilter;
    }

	
	public void onBleActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
        case REQUEST_ENABLE_BT:
        	// When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, enable BLE scan
                bleScan();
            } else{
                // User did not enable Bluetooth or an error occured
                Toast.makeText(activity, "Bluetooth did not enable!", Toast.LENGTH_SHORT).show();
//                activity.finish();
            }
        	break;
		}
		
	}

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            final int rssi = result.getRssi();
            Log.i("rssi", "name="+result.getDevice().getName());
            Log.i("rssi", "add="+result.getDevice().getAddress());
            Log.i("rssi", "rssi="+rssi);
            if(deviceScanned)
                return;

            BluetoothDevice btDevice = result.getDevice();
            if(mDeviceName.equals(btDevice.getName())){
                Log.i("rssi", "inin");

                mDeviceAddress = btDevice.getAddress();
                deviceScanned = true;

                Intent gattServiceIntent = new Intent(activity, BluetoothLeService.class);

                activity.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
                activity.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());


                //mHandler.removeCallbacks(mRunnable);
                /*if (Build.VERSION.SDK_INT < 21) {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                } else {
                    mLEScanner.stopScan(mScanCallback);
                }*/
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

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    // Do nothing if target device is scanned
                    if(deviceScanned)
                        return;
                    
                    Log.d(TAG, "device="+device.getName()+" add="+device.getAddress());
                    String tmpName = device.getName();
                    if(mDeviceName.equals(device.getName())){
                        mDeviceAddress = device.getAddress();
                        deviceScanned = true;

                        Intent gattServiceIntent = new Intent(activity, BluetoothLeService.class);

                        activity.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
                        activity.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
                        if (Build.VERSION.SDK_INT < 21) {
                            mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        } else {
                            mLEScanner.stopScan(mScanCallback);
                        }
                    }
                }
            };
}
