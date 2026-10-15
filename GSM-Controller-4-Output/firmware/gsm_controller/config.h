#pragma once
// Set these locally before flashing. Defaults deliberately disable control.
constexpr char AUTHORIZED_PHONE[] = ""; // sender phone +countrycode, NOT controller SIM
constexpr char SMS_PIN[] = ""; // 6–12 digits, match Android app device PIN
constexpr char AP_PASSWORD[] = ""; // 12–63 characters
constexpr char API_TOKEN[] = ""; // random secret, at least 24 characters
constexpr char AP_NAME[] = "GSM-Switch";
// Active-HIGH MOSFET drivers with external gate pulldowns and coil flyback diodes.
constexpr uint8_t RELAY1_PIN = 5;  // D1, timed
constexpr uint8_t RELAY2_PIN = 4;  // D2, manual
constexpr uint8_t RELAY3_PIN = 16; // D0, manual
constexpr uint8_t RELAY4_PIN = 13; // D7, manual
constexpr uint8_t GSM_RX_PIN = 14; // D5, SIM800L TX through existing level buffer
constexpr uint8_t GSM_TX_PIN = 12; // D6, to SIM800L RX through existing level shifter
