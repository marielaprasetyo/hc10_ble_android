/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.bletest;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
@SuppressLint("NewApi")
public class BluetoothLeService extends Service {
    private final static String TAG = "BluetoothLeService";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_DATA_WRITE_SUCCESS =
            "com.example.bluetooth.le.ACTION_DATA_WRITE_SUCCESS";
    public final static String ACTION_DATA_WRITE_FAIL =
            "com.example.bluetooth.le.ACTION_DATA_WRITE_FAIL";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    public final static String ACTION_DATA_NOTIFY =
            "com.example.bluetooth.le.ACTION_DATA_NOTIFY";

    public final static String EXTRA_UUID = "com.example.bluetooth.le.EXTRA_UUID";
    public final static String EXTRA_STATUS = "com.example.bluetooth.le.EXTRA_STATUS";
    public final static String EXTRA_ADDRESS = "com.example.bluetooth.le.EXTRA_ADDRESS";


    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";

    public final static UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString(HEART_RATE_MEASUREMENT);

    public static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_MOV_DATA = UUID.fromString("f000aa81-0451-4000-b000-000000000000");
    public static final UUID UUID_ACC_DATA = UUID.fromString("f000aa11-0451-4000-b000-000000000000");

    private static final Queue<Object> sWriteQueue = new ConcurrentLinkedQueue<Object>();
    private static boolean sIsWriting = false;

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                Log.i(TAG, "GATT services discovered.");
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                Log.i(TAG, "Characteristic Read Callback");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_NOTIFY, characteristic);
            Log.i(TAG, "Characteristic Changed Callback");
        }

        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_WRITE_SUCCESS, characteristic);
                Log.i(TAG, "Characteristic Write Callback");
                sIsWriting = false;
                nextWrite();
            }
            else {
                broadcastUpdate(ACTION_DATA_WRITE_FAIL, characteristic);
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt,
                                     BluetoothGattDescriptor descriptor, int status) {
            Log.i(TAG, "Descriptor Read Callback");
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            Log.i(TAG, "Descriptor Write Callback");
            sIsWriting = false;
            nextWrite();
        }
    };

    private void broadcastUpdate(final String action) {
    	
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (UUID_MOV_DATA.equals(characteristic.getUuid()))
        {
            byte[] raw_value = characteristic.getValue();
            Point3D v;

            //ACC
            float SCALE = (float) 4096.0;
            int x = (raw_value[7]<<8) + raw_value[6];
            int y = (raw_value[9]<<8) + raw_value[8];
            int z = (raw_value[11]<<8) + raw_value[10];
            v = new Point3D(((x / SCALE) * -1), y / SCALE, ((z / SCALE)*-1));
            Log.i( TAG, String.format("X:%.2fG, Y:%.2fG, Z:%.2fG", v.x, v.y, v.z));

            //GYRO
            SCALE = (float) 128.0;
            x = (raw_value[1]<<8) + raw_value[0];
            y = (raw_value[3]<<8) + raw_value[2];
            z = (raw_value[5]<<8) + raw_value[4];
            v = new Point3D(x / SCALE, y / SCALE, z / SCALE);
            Log.i( TAG, String.format("X:%.2f°/s, Y:%.2f°/s, Z:%.2f°/s", v.x, v.y, v.z));

            //MAG
            SCALE = (float) (32768 / 4912);
            if (raw_value.length >= 18) {
                x = (raw_value[13]<<8) + raw_value[12];
                y = (raw_value[15]<<8) + raw_value[14];
                z = (raw_value[17]<<8) + raw_value[16];
                v = new Point3D(x / SCALE, y / SCALE, z / SCALE);
            }
            else
                v =  new Point3D(0,0,0);
            Log.i( TAG, String.format("X:%.2fuT, Y:%.2fuT, Z:%.2fuT", v.x, v.y, v.z));
        }

        if (UUID_ACC_DATA.equals(characteristic.getUuid()))
        {
            byte[] raw_value = characteristic.getValue();
            Log.i( TAG, "size: " + raw_value.length);
            //Log.i( TAG, "raw_value[0]: " + raw_value[0] + ",raw_value[1] "+raw_value[1] + ",raw_value[2] " +raw_value[2] );
            //Log.i( TAG, "raw_value[3]: " + raw_value[3] + ",raw_value[4] "+raw_value[4] + ",raw_value[5] " +raw_value[5] );

            float pressure_mbar = -1;
            if(raw_value[2] == -1 && raw_value[4] == -1 && raw_value[5] ==-1)
            {

            }
            else {
                //Integer x = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                //Integer y = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 1);
                //Integer z = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 2) * -1;
                int pressure = 0;
                int convert_H = ((int) raw_value[5]) << 16;
                int convert_L = ((int) raw_value[4]) << 8;
                int convert_XL = ((int) raw_value[2]);

                pressure = (convert_H & 0x00FFFFFF) | (convert_L & 0x0000FFFF) | (convert_XL & 0x000000FF);
                //Log.i(TAG, "5: " + raw_value[5] + ", convert: " + convert5);
                //Log.i(TAG, "4: " + raw_value[4] + ", convert: " + convert4);
                //Log.i(TAG, "2: " + raw_value[2] + ", convert: " + convert2);
                Log.i(TAG, "pressure: " + pressure);


                //convert the 2's complement 24 bit to 2's complement 32 bit
                if ((pressure & (int) 0x00800000) != 0) {
                    pressure |= 0xFF000000;
                }

                //Calculate Pressure in mbar
                pressure_mbar = (float) pressure / 4096.0f;
            }
            Log.i( TAG, "pressure_mbar: " + pressure_mbar);

            //double scaledX = x / 64.0;
            //double scaledY = y / 64.0;
            //double scaledZ = z / 64.0;

            //Log.i( TAG, String.format("11 X:%.2fuT, Y:%.2fuT, Z:%.2fuT", scaledX, scaledY, scaledZ));
        }

        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.

        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        boolean success = mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        Log.i("testesttest", "Trying to create a new connection: " + success);
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    public void writeCharacteristic( BluetoothGattCharacteristic characteristic, byte[] b)
    {
        characteristic.setValue(b);
        //mBluetoothGatt.writeCharacteristic(characteristic);
        write(characteristic);
    }

    public void writeDescriptor( BluetoothGattCharacteristic characteristic, boolean enabled)
    {
        BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
        if (clientConfig != null) {
            if(enabled) {
                Log.i(TAG, "Enable notification: " + characteristic.getUuid().toString());
                clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
            else
            {
                Log.i(TAG, "disable notification: " + characteristic.getUuid().toString());
                clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            }
            //mBluetoothGatt.writeDescriptor(clientConfig);
            write(clientConfig);
        }
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, true);
        Log.i(TAG, "set Characteristic Notification");
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }


    private synchronized void write(Object o)
    {
        if(sWriteQueue.isEmpty() && !sIsWriting)
        {
            doWrite(o);
        }
        else
        {
            sWriteQueue.add(o);
        }
    }

    private synchronized void nextWrite()
    {
        if(!sWriteQueue.isEmpty() && !sIsWriting)
        {
            doWrite(sWriteQueue.poll());
        }
    }

    private synchronized void doWrite(Object o)
    {
        if(o instanceof BluetoothGattCharacteristic)
        {
            sIsWriting = true;
            mBluetoothGatt.writeCharacteristic(
                    (BluetoothGattCharacteristic)o);
            Log.i(TAG, "writeCharacteristic");
        }
        else if(o instanceof BluetoothGattDescriptor)
        {
            sIsWriting = true;
            mBluetoothGatt.writeDescriptor((BluetoothGattDescriptor) o);
            Log.i(TAG, "writeDescriptor");
        }
        else
        {
            nextWrite();
        }
    }
}


