package edu.csusm.weatherservice;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;

public class WeatherServiceActivity extends AppCompatActivity {

    private int BLUETOOTH_PERMISSION_CODE = 1;

    private static final String TAG = "WeatherServiceActivity";

    AlertDialog mSelectionDialog;
    DevicesAdapter mDevicesAdapter;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothLeScanner mBluetoothScanner;
    Handler mHandler;
    boolean mScanning;
    BluetoothGatt mGatt;
    private static final int SCAN_PERIOD = 100000;
    private Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<>();
    private Queue<BluetoothGattCharacteristic> characteristicReadQueue = new LinkedList<>();
    private static final int REQUEST_ENABLE_BT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather_service);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        mHandler = new Handler();
        mDevicesAdapter = new DevicesAdapter(getLayoutInflater(),this);
        if (checkPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION)) {
            handlePermissionRequest(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION});
        }
        System.out.println("On Create");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_device);

        builder.setAdapter(mDevicesAdapter, (dialogInterface, i) -> {
            finishScanning();
            BluetoothDevice device = (BluetoothDevice) mDevicesAdapter.getItem(i);
            if (device != null) {
                if (checkPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT)) {
                    Log.i(TAG, "failed permission check: Bluetooth_Connect");
                }
                Log.i(TAG, "Connecting to GATT server at: " + device.getAddress());
                Log.d("ConnectClick","Connecting to the gatt");
                mGatt = device.connectGatt(WeatherServiceActivity.this, false, mGattCallback);
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.setOnDismissListener(dialogInterface -> finishScanning());
        mSelectionDialog = builder.create();

        // 4.1 Write the code here to enable Bluetooth - Instantiate the class
        BluetoothManager bluetoothManager=
                (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothScanner = mBluetoothAdapter.getBluetoothLeScanner();
        Button connectBtn = WeatherServiceActivity.this.findViewById(R.id.connect_button);

        connectBtn.setOnClickListener(this::onConnectClick);
    }
    public void onConnectClick(View view) {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, BLUETOOTH_PERMISSION_CODE);

        String btnText = ((Button) view).getText().toString();
        if (btnText.equals(getString(R.string.connect))) {
            openSelectionDialog();
        } else if (btnText.equals(getString(R.string.disconnect))) {
            if (checkPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT)) {
                handlePermissionRequest(this, new String[]{Manifest.permission.BLUETOOTH_SCAN});
            }
            mGatt.disconnect();
            mGatt.close();
            updateConnectButton(BluetoothProfile.STATE_DISCONNECTED);
        }
        // 4.1 Write the code here to check if Bluetooth is disabled
        if(!BluetoothAdapter.getDefaultAdapter().isEnabled())
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (checkPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.d("Permission Check","I didn't pass the second permission check for BT_CONNECT in onConnectClick(), returning now.");
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else{
            btnText = ((Button) view).getText().toString();
            if (btnText.equals(getString(R.string.connect))) {
                openSelectionDialog();
            } else if (btnText.equals(getString(R.string.disconnect))) {
                mGatt.disconnect();
                mGatt.close();
                updateConnectButton(BluetoothProfile.STATE_DISCONNECTED);
            }
        }

    }

    public static boolean checkPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED;
    }

    public void handlePermissionRequest(Activity activity, String[] permission) {
        ActivityCompat.requestPermissions(activity, permission, 1);
    }

    void openSelectionDialog() {
        beginScanning();
        mSelectionDialog.show();

    }

    private void beginScanning() {

        if (!mScanning) {

            if (mBluetoothScanner == null)
                mBluetoothScanner = mBluetoothAdapter.getBluetoothLeScanner();
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(this::finishScanning, SCAN_PERIOD);
            mDevicesAdapter.clear();
            mDevicesAdapter.add(null);
            mDevicesAdapter.updateScanningState(mScanning = true);
            mDevicesAdapter.notifyDataSetChanged();

            if (checkPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN)) {
                handlePermissionRequest(this, new String[]{Manifest.permission.BLUETOOTH_SCAN});
            }
            mBluetoothScanner.startScan(leScanCallback);
        }
    }

    private void finishScanning() {
        if (mBluetoothScanner == null)
            mBluetoothScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mDevicesAdapter.updateScanningState(mScanning = false);
        if (mScanning) {
            if (mDevicesAdapter.getItem(0) == null) {
                mDevicesAdapter.notifyDataSetChanged();
            }
            if (checkPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN)) {
                handlePermissionRequest(this, new String[]{Manifest.permission.BLUETOOTH_SCAN});
            }
            mBluetoothScanner.stopScan(leScanCallback);
        }
    }

    private void disconnect() {
        if (mGatt != null) {

            if (checkPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT)) {
                handlePermissionRequest(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT});
            }
            mGatt.disconnect();
            mGatt.close();
            mGatt = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!mBluetoothAdapter.isEnabled()) finishScanning();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                openSelectionDialog();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "App cannot run with bluetooth off", Toast.LENGTH_LONG).show();
                finish();
            }
        }
        else
            super.onActivityResult(requestCode, resultCode, data);
    }

    private void updateConnectButton(int state) {
        Button connectBtn = WeatherServiceActivity.this.findViewById(R.id.connect_button);
        System.out.println("update connect button");
        switch (state) {
            case BluetoothProfile.STATE_DISCONNECTED:
                connectBtn.setText(getString(R.string.connect));
                Log.d("state","Disconnected");
                runOnUiThread(() -> {
                    TextView tempTxt = WeatherServiceActivity.this.findViewById(R.id.temperature_value);
                    tempTxt.setText(R.string.unknown);
                    TextView humidityTxt = WeatherServiceActivity.this.findViewById(R.id.humidity_value);
                    humidityTxt.setText(R.string.unknown);
                    TextView pressureTxt = WeatherServiceActivity.this.findViewById(R.id.pressure_value);
                    pressureTxt.setText(R.string.unknown);
                    TextView windDirText = WeatherServiceActivity.this.findViewById(R.id.wind_direction_value);
                    windDirText.setText(R.string.unknown);
                });
                break;
            case BluetoothProfile.STATE_CONNECTING:
                Log.d("state","Connecting");
                connectBtn.setText(getString(R.string.connecting));
                break;
            case BluetoothProfile.STATE_CONNECTED:
                Log.d("state","Connected");
                connectBtn.setText(getString(R.string.disconnect));
                break;
        }
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);
            runOnUiThread(() -> {
                if (mDevicesAdapter.getItem(0) == null) {
                    mDevicesAdapter.remove(0);
                }
                mDevicesAdapter.add(result.getDevice());
                mDevicesAdapter.notifyDataSetChanged();
            });
        }
    };

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, final int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (checkPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT)) {
                    Log.i(TAG, "failed permission check: Bluetooth_Connect");
                }
                Log.i(TAG, "Connected to GATT server.");
                if (mGatt.discoverServices()) {
                    Log.i(TAG, "Started service discovery.");
                } else {
                    Log.w(TAG, "Service discovery failed.");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
            }
            runOnUiThread(() -> updateConnectButton(newState));
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            BluetoothGattService wsService = gatt.getService(
                    AssignedNumber.getBleUuid("Environmental Services")
            );
            if (wsService != null) {
                BluetoothGattCharacteristic tempCharacteristic = wsService.getCharacteristic(
                        AssignedNumber.getBleUuid("Temperature")
                );
                if (tempCharacteristic != null) {
                    Log.i(TAG, "Subscribing to temperature characteristic.");
                    subscribeToNotifications(tempCharacteristic);
                } else {
                    Log.w(TAG, "Can't find temperature characteristic.");
                }
                BluetoothGattCharacteristic humidityCharacteristic = wsService.getCharacteristic(
                        AssignedNumber.getBleUuid("Humidity Measurement")
                );
                if (humidityCharacteristic != null) {
                    Log.i(TAG, "Subscribing to Humidity Measurement characteristic.");
                    subscribeToNotifications(humidityCharacteristic);
                } else {
                    Log.w(TAG, "Can't find Humidity Measurement characteristic.");
                }
                BluetoothGattCharacteristic pressureCharacteristic = wsService.getCharacteristic(
                        AssignedNumber.getBleUuid("Pressure Measurement")
                );
                if (pressureCharacteristic != null) {
                    Log.i(TAG, "Subscribing to Pressure Measurement characteristic.");
                    subscribeToNotifications(pressureCharacteristic);
                } else {
                    Log.w(TAG, "Can't find Pressure Measurement characteristic.");
                }
                BluetoothGattCharacteristic windDirectionCharacteristic = wsService.getCharacteristic(
                        AssignedNumber.getBleUuid("Wind Direction")
                );
                if (windDirectionCharacteristic != null) {
                    Log.i(TAG, "Subscribing to Wind Direction characteristic.");
                    subscribeToNotifications(windDirectionCharacteristic);
                } else {
                    Log.w(TAG, "Can't find Wind Direction characteristic.");
                }
            } else {
                Log.w(TAG, "Can't find weather service.");
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            if (descriptorWriteQueue.size() > 0){
                descriptorWriteQueue.remove();
                if (descriptorWriteQueue.size() > 0){
                    gatt.writeDescriptor(descriptorWriteQueue.element());
                }else{
                    if(characteristicReadQueue.size() > 0){
                        gatt.readCharacteristic(characteristicReadQueue.element());
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            WeatherStationMeasurement weatherStationMeasurement = new WeatherStationMeasurement(characteristic);
            int type = weatherStationMeasurement.getType();
            if(type == 1) {
                    float getTemperature = weatherStationMeasurement.getTemperature();
                    String tempText = getTemperature + getString(R.string.degrees);
                    runOnUiThread(() -> {
                        TextView tempTxt = WeatherServiceActivity.this.findViewById(R.id.temperature_value);
                        tempTxt.setText(tempText);
                    });
            }
            else if(type == 2)
            {
                float humidity = weatherStationMeasurement.getHumidity();
                String humidText = humidity + getString(R.string.percent);
                runOnUiThread(() -> {
                    TextView tempTxt = WeatherServiceActivity.this.findViewById(R.id.humidity_value);
                    tempTxt.setText(humidText);
                });
            }
            else if(type == 3)
            {
                float pressure = weatherStationMeasurement.getPressure();
                String pressureText = pressure+ " " +getString(R.string.pressureText);
                runOnUiThread(() -> {
                    TextView tempTxt = WeatherServiceActivity.this.findViewById(R.id.pressure_value);
                    tempTxt.setText(pressureText);
                });
            }
            else if(type == 4)
            {
                float windDirection = weatherStationMeasurement.getWindDirection();
                runOnUiThread(() -> {
                    TextView tempTxt = WeatherServiceActivity.this.findViewById(R.id.wind_direction_value);
                    tempTxt.setText(weatherStationMeasurement.returnDirection(windDirection));
                });
            }

        }

    };

    private void subscribeToNotifications(BluetoothGattCharacteristic wsCharacteristic) {
        if (checkPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.i(TAG, "Couldn't confirm BLUETOOTH_CONNECT permission.");
        }
        characteristicReadQueue.add(wsCharacteristic);
        // Enable notifications.
        mGatt.setCharacteristicNotification(wsCharacteristic, true);
        // Tell the WSS we want to receive notifications.
        Log.d("CharacteristicUID","Characteristic information service:"+wsCharacteristic.getUuid());
        UUID ccc = AssignedNumber.getBleUuid("Client Characteristic Configuration");
        BluetoothGattDescriptor descriptor = wsCharacteristic.getDescriptor(ccc);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        writeGattDescriptor(descriptor);
    }

    public void writeGattDescriptor(BluetoothGattDescriptor descriptor) {
        descriptorWriteQueue.add(descriptor);

        Log.d(TAG, "Subscribed to " + descriptorWriteQueue.size() + " notification/s");

        try {
            if (descriptorWriteQueue.size() == 1)
                if (checkPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT)) {
                    Log.i(TAG, "Couldn't confirm BLUETOOTH_CONNECT permission.");
                }
            mGatt.writeDescriptor(descriptor);
        } catch (Exception e) {
            e.getLocalizedMessage();
        }
    }

    class GattCharacteristicWrapper {
        protected BluetoothGattCharacteristic characteristic;

        public GattCharacteristicWrapper(BluetoothGattCharacteristic characteristic) {
            this.characteristic = characteristic;
        }
    }

    class WeatherStationMeasurement extends GattCharacteristicWrapper {
        private final String tempTag = Objects.requireNonNull(AssignedNumber.getBleUuid("Temperature")).toString();
        private final String humidTag = Objects.requireNonNull(AssignedNumber.getBleUuid("Humidity Measurement")).toString();
        private final String pressureTag = Objects.requireNonNull(AssignedNumber.getBleUuid("Pressure Measurement")).toString();
        private final String windDirTag = Objects.requireNonNull(AssignedNumber.getBleUuid("Wind Direction")).toString();
        public WeatherStationMeasurement(BluetoothGattCharacteristic characteristic) {
            super(characteristic);
        }

        public int getFlag() {
            return characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        public int getType() {
            int Type = 0;
            if(this.characteristic.getUuid().toString().equals(tempTag)){
                Type = 1;
            }
            else if(this.characteristic.getUuid().toString().equals(humidTag)) {
                Type = 2;
            }
            else if(this.characteristic.getUuid().toString().equals(pressureTag)) {
                Type = 3;
            }
            else if(this.characteristic.getUuid().toString().equals(windDirTag)) {
                Type = 4;
            }
            return Type;
        }

        public float getTemperature() {
            int format = BluetoothGattCharacteristic.FORMAT_SINT16;
            float temperature = characteristic.getIntValue(format, 0);
            return temperature / 100;
        }
        public float getHumidity() {
            int format = BluetoothGattCharacteristic.FORMAT_SINT16;
            float temperature = characteristic.getIntValue(format, 0);
            return temperature / 100;
        }
        public float getWindDirection() {
            int format = BluetoothGattCharacteristic.FORMAT_SINT16;
            float temperature = characteristic.getIntValue(format, 0);
            return temperature / 100;
        }

        public float getPressure() {
            int format = BluetoothGattCharacteristic.FORMAT_SINT16;
            float temperature = characteristic.getIntValue(format, 0);
            return temperature / 10;
        }

        public String returnDirection(float Value) {
            if (Value > 348.75 || Value < 11.25)
                return "North";
            else if(Value >= 11.25 && Value < 33.75)
                return "NNEast";
            else if(Value >= 33.75 && Value < 56.25)
                return "NEast";
            else if(Value >= 56.25 && Value < 78.75)
                return "ENEast";
            else if(Value >= 78.75 && Value < 101.25)
                return "East";
            else if(Value >= 101.25 && Value < 123.75)
                return "ESEast";
            else if(Value >= 123.75 && Value < 146.25)
                return "SEast";
            else if(Value >= 146.25 && Value < 168.75)
                return "SSEast";
            else if(Value >= 168.75 && Value < 191.25)
                return "South";
            else if(Value >= 191.25 && Value < 213.75)
                return "SSWest";
            else if(Value >= 213.75 && Value < 236.25)
                return "SWest";
            else if(Value >= 236.25 && Value < 258.75)
                return "WSWest";
            else if(Value >= 258.75 && Value < 281.25)
                return "West";
            else if(Value >= 281.25 && Value < 303.75)
                return "WNWest";
            else if(Value >= 303.75 && Value < 326.25)
                return "NWest";
            else
                return "NNWest";
        }

        public boolean isContactSupported() {
            return (getFlag() & 0x04) != 0;
        }

    }

}