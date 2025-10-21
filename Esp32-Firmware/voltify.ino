#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>

// Service UUID and Characteristic UUIDs (must match Android app)
#define SERVICE_UUID        "12345678-1234-5678-9abc-123456789abc"
#define TX_CHARACTERISTIC_UUID "12345678-1234-5678-9abc-123456789abd"  // ESP32 -> Android
#define RX_CHARACTERISTIC_UUID "12345678-1234-5678-9abc-123456789abe"  // Android -> ESP32

// BLE Objects
BLEServer *pServer = nullptr;
BLECharacteristic *pTxCharacteristic = nullptr;
BLECharacteristic *pRxCharacteristic = nullptr;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// Power supply variables (received from app)
float setVoltage = 12.0;
float setCurrent = 2.0;
float maxVoltage = 30.0;
float maxCurrent = 15.0;
bool outputEnabled = false;

// Simulated output variables
float outputVoltage = 0.0;
float outputCurrent = 0.0;
float outputEnergy = 0.0;
bool ccMode = false;

// Timing variables
unsigned long lastUpdateTime = 0;
unsigned long lastDataSendTime = 0;
const unsigned long UPDATE_INTERVAL = 100;
const unsigned long DATA_SEND_INTERVAL = 1000;

// Function declarations
void processReceivedData(String data);
void updatePowerSupplyValues();
void sendDataToApp();
void printDebugInfo();
void initBLE();

// BLE Server Callbacks
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("📱 Device connected");
    }

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("📱 Device disconnected");
    }
};

// BLE Characteristic Callbacks for receiving data
class MyCharacteristicCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      String rxValue = pCharacteristic->getValue();
      
      if (rxValue.length() == 12) {
        processReceivedData(rxValue);
      } else {
        Serial.print("❌ Invalid data length: ");
        Serial.println(rxValue.length());
      }
    }
};

void processReceivedData(String data) {
  const uint8_t* bytes = (uint8_t*)data.c_str();
  
  // Extract values (little endian uint16_t format)
  setVoltage = (bytes[1] << 8 | bytes[0]) / 1000.0;
  setCurrent = (bytes[3] << 8 | bytes[2]) / 1000.0;
  maxVoltage = (bytes[5] << 8 | bytes[4]) / 1000.0;
  maxCurrent = (bytes[7] << 8 | bytes[6]) / 1000.0;
  outputEnabled = (bytes[9] << 8 | bytes[8]) == 1;
  
  Serial.println("\n📥 Received Data from App:");
  Serial.println("==========================");
  Serial.printf("Set Voltage: %.2f V\n", setVoltage);
  Serial.printf("Set Current: %.2f A\n", setCurrent);
  Serial.printf("Max Voltage: %.2f V\n", maxVoltage);
  Serial.printf("Max Current: %.2f A\n", maxCurrent);
  Serial.printf("Output: %s\n", outputEnabled ? "ON" : "OFF");
  Serial.println("==========================");
}

void updatePowerSupplyValues() {
  if (!outputEnabled) {
    outputVoltage = 0.0;
    outputCurrent = 0.0;
    ccMode = false;
    return;
  }

  // Simulate power supply behavior with some noise
  outputVoltage = setVoltage + (random(-50, 50) / 1000.0);
  outputCurrent = setCurrent + (random(-30, 30) / 1000.0);
  
  // Ensure values are realistic and within limits
  outputVoltage = constrain(outputVoltage, 0.0, maxVoltage);
  outputCurrent = constrain(outputCurrent, 0.0, maxCurrent);
  
  // Simulate CC/CV mode - switch to CC if current reaches limit
  ccMode = (outputCurrent >= setCurrent * 0.95);
  
  // Calculate energy (simplified integration)
  static unsigned long lastEnergyUpdate = 0;
  unsigned long now = millis();
  if (lastEnergyUpdate > 0 && outputEnabled) {
    float timeHours = (now - lastEnergyUpdate) / 3600000.0;
    outputEnergy += (outputVoltage * outputCurrent) * timeHours;
  }
  lastEnergyUpdate = now;
}

void sendDataToApp() {
  if (!deviceConnected || pTxCharacteristic == nullptr) {
    return;
  }

  // Prepare data packet (12 bytes total)
  uint8_t data[12];
  
  // Convert values to uint16_t (multiply by 1000 for 3 decimal precision)
  uint16_t outVolt = (uint16_t)(outputVoltage * 1000);
  uint16_t outAmp = (uint16_t)(outputCurrent * 1000);
  uint16_t outEnergy = (uint16_t)(outputEnergy * 100); // 2 decimal precision for energy
  uint16_t ccCv = ccMode ? 1 : 0;
  uint16_t appSetVolt = (uint16_t)(setVoltage * 1000);
  uint16_t appSetAmp = (uint16_t)(setCurrent * 1000);
  
  // Pack data in little endian format
  data[0] = outVolt & 0xFF;
  data[1] = (outVolt >> 8) & 0xFF;
  data[2] = outAmp & 0xFF;
  data[3] = (outAmp >> 8) & 0xFF;
  data[4] = outEnergy & 0xFF;
  data[5] = (outEnergy >> 8) & 0xFF;
  data[6] = ccCv & 0xFF;
  data[7] = (ccCv >> 8) & 0xFF;
  data[8] = appSetVolt & 0xFF;
  data[9] = (appSetVolt >> 8) & 0xFF;
  data[10] = appSetAmp & 0xFF;
  data[11] = (appSetAmp >> 8) & 0xFF;
  
  // Send data via BLE
  pTxCharacteristic->setValue(data, 12);
  pTxCharacteristic->notify();
  
  Serial.print("📤 Sent: V=");
  Serial.print(outputVoltage, 3);
  Serial.print("V, A=");
  Serial.print(outputCurrent, 3);
  Serial.print("A, Mode=");
  Serial.print(ccMode ? "CC" : "CV");
  Serial.println();
}

void printDebugInfo() {
  static unsigned long lastPrint = 0;
  unsigned long now = millis();
  
  if (now - lastPrint >= 3000) {
    Serial.println("\n📊 ESP32 Status:");
    Serial.println("================");
    Serial.printf("Output: %s | Mode: %s\n", outputEnabled ? "ON" : "OFF", ccMode ? "CC" : "CV");
    Serial.printf("Voltage: %.3f V | Current: %.3f A\n", outputVoltage, outputCurrent);
    Serial.printf("Power: %.3f W | Energy: %.2f Wh\n", outputVoltage * outputCurrent, outputEnergy);
    Serial.printf("Set V: %.2f V | Set A: %.2f A\n", setVoltage, setCurrent);
    Serial.printf("Max V: %.2f V | Max A: %.2f A\n", maxVoltage, maxCurrent);
    Serial.printf("BLE Connected: %s\n", deviceConnected ? "Yes" : "No");
    Serial.println("================");
    
    lastPrint = now;
  }
}

void initBLE() {
  // Create BLE Device
  BLEDevice::init("ESP32_Power_Supply");

  // Create BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Create TX Characteristic (ESP32 -> Android) - NOTIFY only
  pTxCharacteristic = pService->createCharacteristic(
                      TX_CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_NOTIFY
                    );
  pTxCharacteristic->addDescriptor(new BLE2902());

  // Create RX Characteristic (Android -> ESP32) - WRITE only  
  pRxCharacteristic = pService->createCharacteristic(
                      RX_CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_WRITE
                    );
  pRxCharacteristic->setCallbacks(new MyCharacteristicCallbacks());

  // Start the service
  pService->start();

  // Start advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);  // Helps with iPhone connections
  pAdvertising->setMinPreferred(0x12);
  
  BLEDevice::startAdvertising();
  
  Serial.println("✅ BLE initialized and advertising as 'ESP32_Power_Supply'");
  Serial.print("📱 Device MAC: ");
  Serial.println(BLEDevice::getAddress().toString().c_str());
}

void setup() {
  Serial.begin(115200);
  Serial.println("\n🔌 ESP32 Power Supply BLE Controller");
  Serial.println("=====================================");

  // Initialize BLE
  initBLE();
  
  Serial.println("✅ ESP32 Ready - Waiting for connections...");
  Serial.println("   Make sure to use the correct MAC address in the Android app");
}

void loop() {
  // Handle BLE connection status
  if (!deviceConnected && oldDeviceConnected) {
    delay(500); // Give the Bluetooth stack time
    pServer->startAdvertising();
    Serial.println("🔄 Advertising restarted");
    oldDeviceConnected = deviceConnected;
  }
  
  if (deviceConnected && !oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
  }

  // Update simulated power supply values
  if (millis() - lastUpdateTime >= UPDATE_INTERVAL) {
    updatePowerSupplyValues();
    lastUpdateTime = millis();
  }

  // Send data to connected Android device
  if (deviceConnected && (millis() - lastDataSendTime >= DATA_SEND_INTERVAL)) {
    sendDataToApp();
    lastDataSendTime = millis();
    
    // Print debug info periodically
    printDebugInfo();
  }

  delay(10);
}