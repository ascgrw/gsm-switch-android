# GSM Switch Controller & Android Sender App

An end-to-end open-source solution for remote GSM relay control via SMS and local Web UI. The workspace contains an Android sender application and ESP8266 NodeMCU 4-output controller firmware.

---

## Workspace Structure

- `GSM-Switch-Sender/`: Android app (`com.shahid.gsmcontrol.sender`, v1.3) with web view interface and Android native bridge for sending SMS commands.
- `GSM-Controller-4-Output/`: ESP8266 NodeMCU (ESP-12E) firmware controlling 4 relay outputs with SIM800L module, non-blocking AT state machine, and local Web UI.
- `GSM-Switch-Sender-Source.zip`: Source code archive for `GSM-Switch-Sender`.
- `GSM-Controller-4-Output-Firmware.zip`: Source code archive for `GSM-Controller-4-Output`.
- `GSM-Switch-Navy-v1.7.apk`: Pre-built Android application binary.
- `GSM-Switch-AutoSMS.apk`: Companion Android application binary.

---

## Architecture & Features

### GSM Switch Sender App (`GSM-Switch-Sender`)
- **Sender Controls:** Direct tap ON/OFF, ALL ON, ALL OFF, and STATUS commands.
- **Auto-off Timer:** Output 1 enforces a controller-enforced auto-off timer of 1–900 seconds. Outputs 2–4 remain ON until switched off manually.
- **PIN Security:** Controller PINs (6–12 digits) stored on device using Android KeyStore AES-GCM encryption and excluded from backups.
- **Dual SIM Support:** Select active SMS sending SIM on multi-SIM phones.
- **Permissions:** Requests `SEND_SMS` (required) and `READ_PHONE_STATE` (optional for SIM selection). No `RECEIVE_SMS` or `INTERNET` permissions required.

### Four-Output Controller Firmware (`GSM-Controller-4-Output`)
- **Outputs & Hardware Mapping:**
  - Output 1 (`R1`): GPIO 5 (D1) – Timed auto-off (1–900s).
  - Output 2 (`R2`): GPIO 4 (D2) – Manual.
  - Output 3 (`R3`): GPIO 16 (D0) – Manual.
  - Output 4 (`R4`): GPIO 13 (D7) – Manual.
- **Non-blocking Execution:** Timers service continuously even during modem operations. All outputs default to OFF on power reset.
- **Security & Authorization:** Authorized sender phone validation and 6–12 digit PIN matching. Duplicate 8-hex ID rejection prevents repeated command execution.
- **Local Web UI:** SoftAP interface at `http://192.168.4.1` with API token authentication for local status monitoring and command control.

---

## Command Protocol

SMS commands follow the structure:
```
<PIN> <8-HEX-ID> <COMMAND>
```

Examples:
- `123456 A1B2C3D4 R1 ON 300` (Turn Relay 1 ON for 300 seconds)
- `123456 A1B2C3D4 R1 OFF` (Turn Relay 1 OFF)
- `123456 A1B2C3D4 R2 ON` (Turn Relay 2 ON)
- `123456 A1B2C3D4 ALL ON 300` (Turn All Relays ON; R1 timed for 300s, R2–R4 manual ON)
- `123456 A1B2C3D4 ALL OFF` (Turn All Relays OFF)
- `123456 A1B2C3D4 STATUS` (Request status report)

Replies return current states, uptime, and remaining R1 timer seconds:
```
GSM1 ID=A1B2C3D4 R1=ON R2=OFF R3=OFF R4=OFF UP=120 LEFT=280
```

---

## Build & Testing

### 1. Android Sender App (`GSM-Switch-Sender`)
Prerequisites: JDK 17, Node.js, Gradle 8.11.1, Android SDK 35.

- **Run JavaScript UI & Flow Tests:**
  ```bash
  cd GSM-Switch-Sender
  node tests/SenderFlowTest.js
  ```
- **Run Java Protocol Unit Tests:**
  ```bash
  cd GSM-Switch-Sender
  javac -d . app/src/main/java/com/shahid/gsmcontrol/Protocol.java tests/ProtocolTest.java
  java com.shahid.gsmcontrol.ProtocolTest
  ```
- **Run Java Syntax Checks:**
  ```bash
  cd GSM-Switch-Sender
  javac tests/JavaSyntaxCheck.java
  java -cp tests JavaSyntaxCheck app/src/main/java/com/shahid/gsmcontrol/*.java
  ```
- **Build APK:**
  ```bash
  cd GSM-Switch-Sender
  gradle --no-daemon assembleDebug lintDebug
  ```

### 2. NodeMCU Firmware (`GSM-Controller-4-Output`)
Prerequisites: G++ (for host tests), Arduino IDE or `arduino-cli` with ESP8266 Arduino Core 3.1.2.

- **Run C++ Control Core Tests:**
  ```bash
  g++ -std=c++11 -I GSM-Controller-4-Output/firmware/gsm_controller GSM-Controller-4-Output/tests/control_core_test.cpp -o control_core_test
  ./control_core_test
  ```
- **Compile Firmware (`arduino-cli`):**
  ```bash
  arduino-cli compile --fqbn esp8266:esp8266:nodemcuv2 GSM-Controller-4-Output/firmware/gsm_controller
  ```

---

## Security & Usage Notes

1. Configure `AUTHORIZED_PHONE`, `SMS_PIN`, `AP_PASSWORD`, and `API_TOKEN` in `GSM-Controller-4-Output/firmware/gsm_controller/config.h` before flashing firmware.
2. Relays require active-HIGH MOSFET/transistor driver circuits with flyback diodes and gate pulldowns. Never connect relay coils directly to ESP8266 GPIO pins.
3. Keep saved PIN secrets local; PINs use Android KeyStore AES-GCM and are excluded from backups.
