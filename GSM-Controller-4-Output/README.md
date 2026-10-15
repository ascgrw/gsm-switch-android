# Four-output NodeMCU firmware

For NodeMCU 1.0 (ESP-12E), ESP8266 Arduino core **3.1.2**. This firmware extends the original two-output protocol. Configure the sender phone, PIN, AP password and API token in `gsm_controller/config.h` before flashing. Empty defaults keep all outputs OFF. Keep secrets out of shared repositories.

| SMS output | NodeMCU | GPIO | Behavior |
|---|---|---|---|
| R1 | D1 | 5 | Auto-off, 1–900 seconds |
| R2 | D2 | 4 | Manual |
| R3 | D0 | 16 | Manual |
| R4 | D7 | 13 | Manual |

Use active-HIGH MOSFET/transistor drivers with gate pulldowns and flyback diodes for all four coils, following the original driver circuit. Never connect relay coils directly to GPIOs. Supply the GSM module and relays from appropriate regulated supplies with a common ground. Test without a mains load first.

D5/GPIO14 and D6/GPIO12 remain reserved for the GSM UART. GPIO6–11 connect to flash; GPIO0/2/15 select boot mode; GPIO1/3 remain the debug UART. These are deliberately not extra relay outputs. See the [official GPIO reference](https://arduino-esp8266.readthedocs.io/en/latest/reference.html), [NodeMCU pin mapping](https://github.com/esp8266/Arduino/blob/3.1.2/variants/nodemcu/pins_arduino.h), and [boot pin requirements](https://docs.espressif.com/projects/esptool/en/latest/esp8266/advanced-topics/boot-mode-selection.html).

Install the ESP8266 3.1.2 core in Arduino IDE, select NodeMCU 1.0 (ESP-12E Module), open `gsm_controller/gsm_controller.ino`, configure and upload. Alternatively: `arduino-cli compile --fqbn esp8266:esp8266:nodemcuv2 firmware/gsm_controller`.

In GSM Switch Navy v1.7 choose **Updated · 4 outputs** for that controller only after flashing. Original controllers continue using **Original · 2 outputs**. Empty app tiles add the remaining outputs. Every output on one phone number must use the same firmware profile and PIN.

Commands are `PIN 8_HEX_ID COMMAND`: R1 ON requires a timer, R2/R3/R4 ON has no timer, OFF has no timer, ALL ON requires a timer, ALL OFF and STATUS have no timer. Only the configured sending phone and matching PIN are authorized. Duplicate IDs never repeat an action; conflicting IDs are rejected. All four outputs reset OFF. Only R1 expires; the loop services it even while the modem is busy.

Replies contain R1–R4, uptime and R1 time left. The app does not read incoming SMS: check replies in Messages. Its glow means the last outgoing command was sent, not confirmed hardware state. The local Wi-Fi UI at `http://192.168.4.1` uses the API token and polls controller GPIO command state; it still has no relay-contact feedback.
