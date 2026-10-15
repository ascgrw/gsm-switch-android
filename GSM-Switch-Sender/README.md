# GSM Switch — sender app 1.3

An Android app with simple ON/OFF controls and automatic outgoing SMS.

## Using the app

1. Install GSM-Switch-Sender.apk. In Settings, enable SMS and choose a sending SIM.
2. Tap Add switch. Enter its name, controller number with country code, output,
   auto-off time for output 1, and the controller's 6–12 digit PIN.
3. Save. Tap ON or OFF to send directly. Read replies in the phone's Messages app.
4. Tap the edit icon to change or remove a switch. Add more with Add switch.

Up to 30 controller numbers and 60 switches are supported. Each current
NodeMCU/SIM800L controller supports only two real relay outputs. Adding outputs
3 or higher would require different firmware and hardware; the app does not
pretend they exist. The same number/output pair cannot be added twice.
The encrypted controller PIN is shared by its two outputs. All controls operate
only outputs added to the app; a controller with one configured switch gets R1
or R2 commands rather than ALL commands.

The app displays command sending results, not confirmed physical relay state.
It has no automatic retries. Output 1's ON command includes a controller-enforced
auto-off timer of 1–900 seconds. Output 2 stays on until switched off.
Activity shows brief sending results; Settings contains SIM setup, backup/restore,
and collapsed help/wiring. Existing controller backups remain supported.

## Permissions and privacy

SEND_SMS is required. READ_PHONE_STATE is optional for SIM selection.
The app requests no RECEIVE_SMS, READ_SMS, Internet, notification-listener or
accessibility permission. Automatic reception and receiver simulation are not
available. Google Play Protect clearance is not claimed; phone installation
and actual SMS behavior still need testing.
PINs use Android Keystore AES-GCM and are omitted from backups. SMS itself
carries the PIN in plain text, and the phone's messaging app may retain it.
Only bundled assets can use the native bridge. Native permissions are requested
by user interaction; the app never sends on startup or permission approval.

## Build and verification

Package: com.shahid.gsmcontrol.sender, versionCode 4, versionName 1.3.
Use JDK 17, Gradle 8.11.1, AGP 8.9.2 and Android SDK 35.
Run `gradle --no-daemon assembleDebug lintDebug`.
Run `node tests/SenderFlowTest.js`.
Compile Protocol.java and tests/ProtocolTest.java with javac, then run
com.shahid.gsmcontrol.ProtocolTest.
APK output: app/build/outputs/apk/debug/app-debug.apk.

Validation: command protocol and add/edit/remove flow checks; shared controller
and PIN behavior; duplicate prevention; correct R1/R2/ALL targets; permission
denial; no false hardware state; backup compatibility and PIN exclusion.
Mobile layout checks cover 320, 360, 412 and 768 pixel widths, with live form
interaction and screenshots. Android compilation/lint, signature and alignment
checks are also run. Device installation and real SMS remain unverified.

This is a personal debug build. Updates use the existing debug signing key in
this workspace. A clean CI build generates a different key unless configured
with a stable private signing key; it may require uninstalling an older build.
