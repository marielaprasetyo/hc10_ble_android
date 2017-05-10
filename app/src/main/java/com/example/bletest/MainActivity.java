package com.example.bletest;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;


public class MainActivity extends Activity implements BluetoothListener {

    private static final String TAG = "BluetoothLE";

	private BluetoothLE ble = null;
    MainActivity mainActivity = this;
    private EditText et_device;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        
        Button buttonStart = (Button)findViewById(R.id.buttonStart);
        
        et_device = (EditText)findViewById(R.id.device_name);
        
        buttonStart.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if(ble != null) {
                    return;
                }
                ble = new BluetoothLE(mainActivity, et_device.getText().toString());
                ble.bleConnect();
            }

        });
        
        
        Log.i(TAG, "On create"); 
        
        Button buttonClose = (Button)findViewById(R.id.buttonClose);

        buttonClose.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if(ble != null) {
                    ble.bleDisconnect();
                    ble = null;
                }
            }

        });

        //setBluetooth(false);

        //setBluetooth(true);

	}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(ble != null) {
            ble.bleDisconnect();
        }
    }



    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        ble.onBleActivityResult(requestCode, resultCode, data);
    }

    public static boolean setBluetooth(boolean enable) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean isEnabled = bluetoothAdapter.isEnabled();
        if (enable && !isEnabled) {
            return bluetoothAdapter.enable();
        }
        else if(!enable && isEnabled) {
            return bluetoothAdapter.disable();
        }
        // No need to change bluetooth state
        return true;
    }

    public void enableBT(View view){
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()){
            Intent intentBtEnabled = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            // The REQUEST_ENABLE_BT constant passed to startActivityForResult() is a locally defined integer (which must be greater than 0), that the system passes back to you in your onActivityResult()
            // implementation as the requestCode parameter.
            int REQUEST_ENABLE_BT = 1;
            startActivityForResult(intentBtEnabled, REQUEST_ENABLE_BT);
        }
    }

    public void disableBT(View view){
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter.isEnabled()){
            mBluetoothAdapter.disable();
        }
    }

    @Override
    public void bleNotSupported() {

//        this.finish();
    }

    @Override
    public void bleConnectionTimeout() {
        Toast.makeText(this, "BLE connection timeout", Toast.LENGTH_SHORT).show();
        if(ble != null) {
            ble = null;
        }
    }

    @Override
    public void bleConnected() {
    	Toast.makeText(this, "BLE connected", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "BLE connected");
    }

    @Override
    public void bleDisconnected() {
    	Toast.makeText(this, "BLE disconnected", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "BLE disconnected");
        if(ble != null) {
            ble = null;
        }
    }

    @Override
    public void bleWriteStateSuccess() {
    	Toast.makeText(this, "BLE ACTION_DATA_WRITE_SUCCESS", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "BLE ACTION_DATA_WRITE_SUCCESS");
    }

    @Override
    public void bleWriteStateFail() {
    	Toast.makeText(this, "BLE ACTION_DATA_WRITE_FAIL", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "BLE ACTION_DATA_WRITE_FAIL");
    }

    @Override
    public void bleNoPlug() {
    	Toast.makeText(this, "No test plug", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "No test plug");
    }

    @Override
    public void blePlugInserted(byte[] plugId) {
    	//Toast.makeText(this, "Test plug is inserted", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Test plug is inserted");
    }

    @Override
    public void bleColorReadings(byte[] colorReadings) {
    	Toast.makeText(this, "Color sensor readings", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Color sensor readings");
    }

	@Override
	public void bleElectrodeAdcReading(byte state, byte[] adcReading) {
		// TODO Auto-generated method stub
		
	}
}
