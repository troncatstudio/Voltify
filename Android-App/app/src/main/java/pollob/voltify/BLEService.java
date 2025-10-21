package pollob.voltify;

import android.annotation.SuppressLint;
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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public class BLEService {
    private static final String TAG = "BLEService";

    // UUIDs for BLE service and characteristics
    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-5678-9abc-123456789abc");
    private static final UUID TX_CHARACTERISTIC_UUID = UUID.fromString("12345678-1234-5678-9abc-123456789abd"); // ESP32 -> Android (NOTIFY)
    private static final UUID RX_CHARACTERISTIC_UUID = UUID.fromString("12345678-1234-5678-9abc-123456789abe"); // Android -> ESP32 (WRITE)

    // UUID for the client characteristic configuration descriptor
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Context context;
    private BLEListener listener;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic rxCharacteristic; // For writing to ESP32
    private BluetoothGattCharacteristic txCharacteristic; // For receiving from ESP32

    private Handler mainHandler;
    private boolean isConnected = false;
    private boolean servicesDiscovered = false;

    public interface BLEListener {
        void onDeviceConnected();
        void onDeviceDisconnected();
        void onDataReceived(byte[] data);
        void onError(String error);
    }

    public BLEService(Context context, BLEListener listener) {
        this.context = context;
        this.listener = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());
        initializeBluetooth();
    }

    private void initializeBluetooth() {
        try {
            BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager != null) {
                bluetoothAdapter = bluetoothManager.getAdapter();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Bluetooth", e);
        }
    }

    @SuppressLint("MissingPermission")
    public void connect(String macAddress) {
        Log.d(TAG, "Connecting to: " + macAddress);

        if (bluetoothAdapter == null) {
            sendError("Bluetooth not available");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            sendError("Bluetooth is disabled");
            return;
        }

        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
            if (device == null) {
                sendError("Device not found: " + macAddress);
                return;
            }

            Log.d(TAG, "Found device: " + device.getName() + " - " + device.getAddress());

            // Disconnect first if already connected
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
            }

            bluetoothGatt = device.connectGatt(context, false, gattCallback);
            Log.d(TAG, "Connection initiated");

        } catch (Exception e) {
            Log.e(TAG, "Connection failed", e);
            sendError("Connection failed: " + e.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        Log.d(TAG, "Disconnecting...");
        servicesDiscovered = false;
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        isConnected = false;
        rxCharacteristic = null;
        txCharacteristic = null;
    }

    public boolean isConnected() {
        return isConnected && servicesDiscovered;
    }

    @SuppressLint("MissingPermission")
    public void sendData(double setVolt, double setAmp, double maxVolt, double maxAmp, boolean outputOn) {
        if (bluetoothGatt == null || rxCharacteristic == null) {
            sendError("Not connected to device or characteristic not ready");
            Log.e(TAG, "Cannot send data - bluetoothGatt: " + bluetoothGatt + ", rxCharacteristic: " + rxCharacteristic);
            return;
        }

        try {
            byte[] data = createDataPacket(setVolt, setAmp, maxVolt, maxAmp, outputOn);

            Log.d(TAG, "Sending data to ESP32: " + data.length + " bytes");

            // Set the value and write to the characteristic
            rxCharacteristic.setValue(data);
            boolean success = bluetoothGatt.writeCharacteristic(rxCharacteristic);

            if (success) {
                Log.d(TAG, "Data sent successfully to ESP32");
                Log.d(TAG, String.format("Sent: SetV=%.2fV, SetA=%.2fA, MaxV=%.2fV, MaxA=%.2fA, Output=%s",
                        setVolt, setAmp, maxVolt, maxAmp, outputOn ? "ON" : "OFF"));
            } else {
                Log.e(TAG, "Failed to write to RX characteristic");
                sendError("Failed to send data - write operation failed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending data", e);
            sendError("Error sending data: " + e.getMessage());
        }
    }

    private byte[] createDataPacket(double setVolt, double setAmp, double maxVolt, double maxAmp, boolean outputOn) {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Convert values to uint16_t (multiply by 1000 to preserve decimals)
        buffer.putShort((short) (setVolt * 1000));   // Set Voltage
        buffer.putShort((short) (setAmp * 1000));    // Set Current
        buffer.putShort((short) (maxVolt * 1000));   // Max Voltage
        buffer.putShort((short) (maxAmp * 1000));    // Max Current
        buffer.putShort((short) (outputOn ? 1 : 0)); // Output State
        buffer.putShort((short) 0); // Reserved

        return buffer.array();
    }

    @SuppressLint("MissingPermission")
    private void enableTXNotifications() {
        if (bluetoothGatt == null || txCharacteristic == null) {
            Log.e(TAG, "Cannot enable notifications - GATT or TX characteristic is null");
            return;
        }

        // Enable local notifications
        boolean notificationSet = bluetoothGatt.setCharacteristicNotification(txCharacteristic, true);
        Log.d(TAG, "Notification set: " + notificationSet);

        // Enable remote notifications via client characteristic configuration descriptor
        BluetoothGattDescriptor descriptor = txCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            boolean descriptorWrite = bluetoothGatt.writeDescriptor(descriptor);
            Log.d(TAG, "Descriptor write: " + descriptorWrite);
        } else {
            Log.e(TAG, "Client characteristic configuration descriptor not found!");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "Connection state changed: " + newState + ", status: " + status);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                Log.d(TAG, "Connected to device, discovering services...");
                gatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                servicesDiscovered = false;
                rxCharacteristic = null;
                txCharacteristic = null;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onDeviceDisconnected();
                    }
                });
                Log.d(TAG, "Disconnected from device");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "Services discovered: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    Log.d(TAG, "Service found, setting up characteristics...");

                    // Get RX Characteristic (for writing TO ESP32)
                    rxCharacteristic = service.getCharacteristic(RX_CHARACTERISTIC_UUID);

                    // Get TX Characteristic (for receiving FROM ESP32)
                    txCharacteristic = service.getCharacteristic(TX_CHARACTERISTIC_UUID);

                    if (rxCharacteristic != null && txCharacteristic != null) {
                        Log.d(TAG, "Both characteristics found successfully");
                        Log.d(TAG, "RX Char UUID: " + rxCharacteristic.getUuid());
                        Log.d(TAG, "TX Char UUID: " + txCharacteristic.getUuid());

                        // Enable notifications for TX characteristic
                        enableTXNotifications();

                        servicesDiscovered = true;

                        mainHandler.post(() -> {
                            if (listener != null) {
                                listener.onDeviceConnected();
                            }
                        });

                        Log.d(TAG, "BLE setup complete - ready for communication");

                    } else {
                        Log.e(TAG, "Characteristics not found - RX: " + rxCharacteristic + ", TX: " + txCharacteristic);
                        sendError("Required characteristics not found on device");
                    }
                } else {
                    Log.e(TAG, "Service not found: " + SERVICE_UUID);

                    // List all available services for debugging
                    Log.d(TAG, "Available services:");
                    for (BluetoothGattService s : gatt.getServices()) {
                        Log.d(TAG, "Service: " + s.getUuid());
                    }

                    sendError("Required service not found on device");
                }
            } else {
                Log.e(TAG, "Service discovery failed: " + status);
                sendError("Service discovery failed");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            Log.d(TAG, "Characteristic changed: " + characteristic.getUuid());

            if (characteristic.getUuid().equals(TX_CHARACTERISTIC_UUID)) {
                byte[] data = characteristic.getValue();
                Log.d(TAG, "Received data from ESP32: " + data.length + " bytes");

                // Print raw bytes for debugging
                StringBuilder hexData = new StringBuilder();
                for (byte b : data) {
                    hexData.append(String.format("%02X ", b));
                }
                Log.d(TAG, "Raw data: " + hexData.toString());

                if (listener != null) {
                    listener.onDataReceived(data);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (characteristic.getUuid().equals(RX_CHARACTERISTIC_UUID)) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Data successfully written to ESP32");
                } else {
                    Log.e(TAG, "Failed to write data to ESP32, status: " + status);
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.d(TAG, "Descriptor write completed: " + descriptor.getUuid() + ", status: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.getUuid().equals(CLIENT_CHARACTERISTIC_CONFIG)) {
                    Log.d(TAG, "Notifications enabled successfully!");
                }
            } else {
                Log.e(TAG, "Failed to write descriptor: " + status);
            }
        }
    };

    private void sendError(String error) {
        Log.e(TAG, error);
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onError(error);
            }
        });
    }
}