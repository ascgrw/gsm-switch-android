# GSM Switch Navy — sender app 1.7

An Android app with simple ON/OFF controls and automatic outgoing SMS for 2-output and 4-output GSM controllers.

## Using the app

1. Install GSM-Switch-Navy-v1.7.apk. In Settings, enable SMS and choose a sending SIM.
2. Tap Add switch. Enter its name, controller number with country code, output,
   auto-off time for output 1, and the controller's 6–12 digit PIN.
3. Choose Original for existing 2-output controllers or Updated for 4-output controllers.
4. Save. Tap ON or OFF to send directly. Read replies in the phone's Messages app.
5. Tap the edit icon to change or remove a switch. Add more with Add switch.

Up to 30 controller numbers and 120 switches are supported.
The same number/output pair cannot be added twice.
The encrypted controller PIN is shared by its outputs. All controls operate
only outputs added to the app; a controller with partial configured switches gets R1–R4 commands rather than ALL commands.

The app displays command sending results, not confirmed physical relay state.
It has no automatic retries. Output 1's ON command includes a controller-enforced
auto-off timer of 1–900 seconds. Outputs 2–4 stay on until switched off.
Activity shows brief sending results; Settings contains SIM setup, backup/restore, custom labels, app name customization, and collapsed help/wiring. Existing controller backups remain supported.

## Permissions and privacy

SEND_SMS is required. READ_PHONE_STATE is optional for SIM selection.
The app requests no RECEIVE_SMS, READ_SMS, Internet, notification-listener or
accessibility permission. Automatic reception and receiver simulation are not
available.
PINs use Android Keystore AES-GCM and are omitted from backups. SMS itself
carries the PIN in plain text, and the phone's messaging app may retain it.
Only bundled assets can use the native bridge. Native permissions are requested
by user interaction; the app never sends on startup or permission approval.

## Build and verification

Package: com.shahid.gsmcontrol.navy, versionCode 8, versionName 1.7.
Use JDK 17, Gradle 8.11.1, AGP 8.9.2 and Android SDK 35.
Run `gradle --no-daemon assembleDebug lintDebug`.
Run `node tests/SenderFlowTest.js`.
Compile Protocol.java and tests/ProtocolTest.java with javac, then run
com.shahid.gsmcontrol.ProtocolTest.
APK output: app/build/outputs/apk/debug/app-debug.apk.
