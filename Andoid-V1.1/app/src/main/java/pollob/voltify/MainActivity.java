package pollob.voltify;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;

public class MainActivity extends AppCompatActivity implements BLEService.BLEListener, UIUpdate.UIUpdateListener {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int ENABLE_BLUETOOTH_REQUEST_CODE = 1002;
    private static final String PREFS_NAME = "VoltifyPrefs";
    private static final String MAC_ADDRESS_KEY = "mac_address";
    private static final String DEFAULT_MAC = "00:11:22:33:44:55";

    // UI Elements
    private TextView statusText, outputVoltText, outputAmpText, setVoltText, setAmpText;
    private TextView outputPowerText, outputEnergyText, ccCvStatusText, outputStatusText;
    private Button connectButton, flipButton, outputToggleButton, settingsButton, sendButton;
    private View frontCard, backCard;
    private LineChart voltChart, ampChart;

    // Slider Elements
    private SeekBar setVoltSlider, setAmpSlider, maxVoltSlider, maxAmpSlider;
    private TextView setVoltValue, setAmpValue, maxVoltValue, maxAmpValue;

    // Services
    private BLEService bleService;
    private UIUpdate uiUpdate;
    private Handler mainHandler;

    // State variables
    private boolean isFrontVisible = true;
    private boolean isOutputOn = false;
    private String deviceMacAddress;

    // Slider values
    private double currentSetVolt = 7.0;
    private double currentSetAmp = 2.5;
    private double currentMaxVolt = 15.0;
    private double currentMaxAmp = 5.0;

    private BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupClickListeners();
        setupSliderListeners();
        initializeServices();
        loadMacAddress();
        initializeBluetooth();
        setDefaultSliderValues();
    }

    private void initializeViews() {
        // Status and main UI
        statusText = findViewById(R.id.statusText);
        outputVoltText = findViewById(R.id.outputVoltText);
        outputAmpText = findViewById(R.id.outputAmpText);
        setVoltText = findViewById(R.id.setVoltText);
        setAmpText = findViewById(R.id.setAmpText);
        outputPowerText = findViewById(R.id.outputPowerText);
        outputEnergyText = findViewById(R.id.outputEnergyText);
        ccCvStatusText = findViewById(R.id.ccCvStatusText);
        outputStatusText = findViewById(R.id.outputStatusText);

        // Buttons
        connectButton = findViewById(R.id.connectButton);
        flipButton = findViewById(R.id.flipButton);
        outputToggleButton = findViewById(R.id.outputToggleButton);
        settingsButton = findViewById(R.id.settingsButton);
        sendButton = findViewById(R.id.sendButton);

        // Cards
        frontCard = findViewById(R.id.frontCard);
        backCard = findViewById(R.id.backCard);

        // Charts
        voltChart = findViewById(R.id.voltChart);
        ampChart = findViewById(R.id.ampChart);

        // Sliders
        setVoltSlider = findViewById(R.id.setVoltSlider);
        setAmpSlider = findViewById(R.id.setAmpSlider);
        maxVoltSlider = findViewById(R.id.maxVoltSlider);
        maxAmpSlider = findViewById(R.id.maxAmpSlider);

        // Slider value displays
        setVoltValue = findViewById(R.id.setVoltValue);
        setAmpValue = findViewById(R.id.setAmpValue);
        maxVoltValue = findViewById(R.id.maxVoltValue);
        maxAmpValue = findViewById(R.id.maxAmpValue);
    }

    private void setupClickListeners() {
        connectButton.setOnClickListener(v -> {
            Log.d("BLE", "Connect button clicked");
            checkPermissionsAndConnect();
        });

        flipButton.setOnClickListener(v -> flipCard());

        outputToggleButton.setOnClickListener(v -> toggleOutput());

        settingsButton.setOnClickListener(v -> showSettingsDialog());

        sendButton.setOnClickListener(v -> sendSliderDataToESP32());

        // Memory buttons
        setupMemoryButtons();
    }

    private void setupMemoryButtons() {
        int[] memoryButtonIds = {R.id.memory1, R.id.memory2, R.id.memory3, R.id.memory4, R.id.memory5};

        for (int i = 0; i < memoryButtonIds.length; i++) {
            Button memoryButton = findViewById(memoryButtonIds[i]);
            final int memoryIndex = i + 1;

            memoryButton.setOnClickListener(v -> recallMemory(memoryIndex));

            memoryButton.setOnLongClickListener(v -> {
                storeMemory(memoryIndex);
                return true;
            });
        }
    }

    private void setupSliderListeners() {
        // Set Voltage Slider (2.0V - 30.0V)
        setVoltSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSetVolt = 2.0 + (progress / 100.0);
                setVoltValue.setText(String.format("%.2f V", currentSetVolt));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Set Current Slider (0.5A - 15.0A)
        setAmpSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSetAmp = 0.5 + (progress / 100.0);
                setAmpValue.setText(String.format("%.2f A", currentSetAmp));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Max Voltage Slider (3.0V - 30.0V)
        maxVoltSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentMaxVolt = 3.0 + (progress / 100.0);
                maxVoltValue.setText(String.format("%.2f V", currentMaxVolt));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Max Current Slider (1.0A - 15.0A)
        maxAmpSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentMaxAmp = 1.0 + (progress / 100.0);
                maxAmpValue.setText(String.format("%.2f A", currentMaxAmp));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void initializeServices() {
        mainHandler = new Handler(Looper.getMainLooper());
        bleService = new BLEService(this, this);
        uiUpdate = new UIUpdate(this, this);
    }

    private void loadMacAddress() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        deviceMacAddress = prefs.getString(MAC_ADDRESS_KEY, DEFAULT_MAC);
        Log.d("BLE", "Loaded MAC: " + deviceMacAddress);
    }

    private void setDefaultSliderValues() {
        // Set default slider positions
        setVoltSlider.setProgress(500);  // 7.0V (500 * 0.01 = 5.0 + 2.0 = 7.0V)
        setAmpSlider.setProgress(200);   // 2.5A (200 * 0.01 = 2.0 + 0.5 = 2.5A)
        maxVoltSlider.setProgress(1200); // 15.0V (1200 * 0.01 = 12.0 + 3.0 = 15.0V)
        maxAmpSlider.setProgress(400);   // 5.0A (400 * 0.01 = 4.0 + 1.0 = 5.0A)

        // Update display values
        setVoltValue.setText("7.00 V");
        setAmpValue.setText("2.50 A");
        maxVoltValue.setText("15.00 V");
        maxAmpValue.setText("5.00 A");
    }

    private void initializeBluetooth() {
        try {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager != null) {
                bluetoothAdapter = bluetoothManager.getAdapter();
                if (bluetoothAdapter == null) {
                    Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
                    connectButton.setEnabled(false);
                }
            }
        } catch (Exception e) {
            Log.e("BLE", "Error initializing Bluetooth", e);
            Toast.makeText(this, "Error initializing Bluetooth: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void checkPermissionsAndConnect() {
        Log.d("BLE", "Checking permissions...");

        String[] requiredPermissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_SCAN and BLUETOOTH_CONNECT
            requiredPermissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            // Older versions
            requiredPermissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
            };
        }

        boolean allPermissionsGranted = true;
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (allPermissionsGranted) {
            Log.d("BLE", "All permissions granted, checking Bluetooth...");
            checkBluetoothAndConnect();
        } else {
            Log.d("BLE", "Requesting permissions...");
            ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE);
        }
    }

    private void checkBluetoothAndConnect() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.d("BLE", "Bluetooth not enabled, requesting enable...");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth connect permission required", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE);
        } else {
            Log.d("BLE", "Bluetooth enabled, connecting to device...");
            connectToDevice();
        }
    }

    private void connectToDevice() {
        Log.d("BLE", "Attempting to connect to: " + deviceMacAddress);

        if (bleService != null) {
            bleService.connect(deviceMacAddress);
        } else {
            Log.e("BLE", "BLE Service is null");
            Toast.makeText(this, "BLE Service not initialized", Toast.LENGTH_SHORT).show();
        }
    }

    private void flipCard() {
        Animation flipOut = AnimationUtils.loadAnimation(this, R.anim.flip_out);
        Animation flipIn = AnimationUtils.loadAnimation(this, R.anim.flip_in);

        if (isFrontVisible) {
            frontCard.startAnimation(flipOut);
            frontCard.setVisibility(View.GONE);
            backCard.setVisibility(View.VISIBLE);
            backCard.startAnimation(flipIn);
            flipButton.setText("Show Settings");
        } else {
            backCard.startAnimation(flipOut);
            backCard.setVisibility(View.GONE);
            frontCard.setVisibility(View.VISIBLE);
            frontCard.startAnimation(flipIn);
            flipButton.setText("Show Graphs");
        }
        isFrontVisible = !isFrontVisible;
    }

    private void toggleOutput() {
        isOutputOn = !isOutputOn;
        if (bleService != null && bleService.isConnected()) {
            outputToggleButton.setText(isOutputOn ? "OUT OFF" : "OUT ON");
            outputToggleButton.setBackgroundColor(Color.parseColor(isOutputOn ? "#009673" : "#E91E63"));
            sendSliderDataToESP32(); // Send all data including output state
        } else {
            Toast.makeText(this, "Not connected to device", Toast.LENGTH_SHORT).show();
            isOutputOn = !isOutputOn; // Revert if not connected
        }
    }

    private void sendSliderDataToESP32() {
        if (bleService != null && bleService.isConnected()) {
            bleService.sendData(currentSetVolt, currentSetAmp, currentMaxVolt, currentMaxAmp, isOutputOn);
            Toast.makeText(this, "Settings send", Toast.LENGTH_SHORT).show();

            // Log sent data
            Log.d("SLIDER_DATA", String.format(
                    "Sent: SetV=%.2fV, SetA=%.2fA, MaxV=%.2fV, MaxA=%.2fA, Output=%s",
                    currentSetVolt, currentSetAmp, currentMaxVolt, currentMaxAmp,
                    isOutputOn ? "ON" : "OFF"
            ));
        } else {
            Toast.makeText(this, "Not connected to Device", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Device Address");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mac_address, null);
        TextView macInput = dialogView.findViewById(R.id.macInput);
        macInput.setText(deviceMacAddress);

        builder.setView(dialogView);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newMac = macInput.getText().toString();
            if (isValidMacAddress(newMac)) {
                deviceMacAddress = newMac;
                saveMacAddress(newMac);
                Toast.makeText(this, "MAC Address updated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Invalid MAC Address", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private boolean isValidMacAddress(String mac) {
        return mac.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    }

    private void saveMacAddress(String mac) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(MAC_ADDRESS_KEY, mac);
        editor.apply();
    }

    private void recallMemory(int memoryIndex) {
        uiUpdate.recallMemoryForSliders(memoryIndex);
        Toast.makeText(this, "Recalled M" + memoryIndex, Toast.LENGTH_SHORT).show();
    }

    private void storeMemory(int memoryIndex) {
        // Store current slider values to memory
        uiUpdate.storeMemory(memoryIndex, currentSetVolt, currentSetAmp);
        Toast.makeText(this, "Stored to M" + memoryIndex, Toast.LENGTH_SHORT).show();
    }

    // BLEListener implementations
    @Override
    public void onDeviceConnected() {
        mainHandler.post(() -> {
            statusText.setText("Online");
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            connectButton.setVisibility(View.GONE);
            outputToggleButton.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Connected to Device", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDeviceDisconnected() {
        mainHandler.post(() -> {
            statusText.setText("Offline");
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            connectButton.setVisibility(View.VISIBLE);
            outputToggleButton.setVisibility(View.GONE);
            Toast.makeText(this, "Disconnected from ESP32", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDataReceived(byte[] data) {
        uiUpdate.processReceivedData(data);
    }

    @Override
    public void onError(String error) {
        mainHandler.post(() -> {
            Toast.makeText(this, "BLE Error: " + error, Toast.LENGTH_SHORT).show();
            Log.e("BLE", "Error: " + error);
        });
    }

    // UIUpdateListener implementations
    @Override
    public void updateOutputValues(double outputVolt, double outputAmp, double outputEnergy, String ccCvStatus) {
        mainHandler.post(() -> {
            outputVoltText.setText(String.format("%.3f V", outputVolt));
            outputAmpText.setText(String.format("%.3f A", outputAmp));
            outputEnergyText.setText(String.format("%.2f Wh", outputEnergy));
            ccCvStatusText.setText(ccCvStatus);

            // Calculate and display power
            double power = outputVolt * outputAmp;
            outputPowerText.setText(String.format("%.3f W", power));
        });
    }

    @Override
    public void updateSetValues(double setVolt, double setAmp) {
        mainHandler.post(() -> {
            setVoltText.setText(String.format("%.2f V", setVolt));
            setAmpText.setText(String.format("%.2f A", setAmp));

            // Update sliders with received set values
            //updateSlidersFromReceivedData(setVolt, setAmp);
        });
    }

    @Override
    public void updateOutputStatus(boolean isOutputOn) {
        mainHandler.post(() -> {
            this.isOutputOn = isOutputOn;
            outputStatusText.setText(isOutputOn ? "ON" : "OFF");
            outputToggleButton.setText(isOutputOn ? "TURN OFF" : "TURN ON");
            outputToggleButton.setBackgroundTintList(
                    ContextCompat.getColorStateList(this,
                            isOutputOn ? android.R.color.holo_red_dark : android.R.color.holo_green_dark)
            );
        });
    }

    @Override
    public void updateGraphs(double volt, double amp) {
        // Update charts with new data
        uiUpdate.updateCharts(voltChart, ampChart, volt, amp);
    }

    public void updateSlidersFromReceivedData(double recalledSetVolt, double recalledSetAmp) {
        // Update slider positions based on received data
        mainHandler.post(() -> {
            if (recalledSetVolt >= 2.0 && recalledSetVolt <= 30.0) {
                int voltProgress = (int) ((recalledSetVolt - 2.0) * 100);
                setVoltSlider.setProgress(voltProgress);
                setVoltValue.setText(String.format("%.2f V", recalledSetVolt));
                //currentSetVolt = setVolt;
            }

            if (recalledSetAmp >= 0.5 && recalledSetAmp <= 15.0) {
                int ampProgress = (int) ((recalledSetAmp - 0.5) * 100);
                setAmpSlider.setProgress(ampProgress);
                setAmpValue.setText(String.format("%.2f A", recalledSetAmp));
                //currentSetAmp = setAmp;
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Log.d("BLE", "Permissions granted, proceeding with connection");
                checkBluetoothAndConnect();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required to connect", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ENABLE_BLUETOOTH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Log.d("BLE", "Bluetooth enabled, connecting...");
                connectToDevice();
            } else {
                Toast.makeText(this, "Bluetooth must be enabled to connect", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleService != null) {
            bleService.disconnect();
        }
    }
}