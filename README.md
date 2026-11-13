# Joyous Remap

Remap the **Essential key** of a Nothing / CMF Phone without ADB and without root.

Each press type (single, double, long, triple) can run its own action: open an app, camera (open, then press = photo),
flashlight with a side brightness panel, QR scanner, Google Wallet, voice recorder, sound mode, music controls,
auto-rotate, Do Not Disturb, screenshot, Wi-Fi/Bluetooth, Wi-Fi settings and assistant.
The on-screen notice, the glowing screen edge and the flashlight panel are customizable. English and Spanish.

The app has **no Internet permission**. It detects the key with an accessibility service.

## Install
Download `Joyous_remap.apk` from the latest release, open it and allow the install.
If Google Play Protect warns about it, it is because the app is not distributed through the Play Store and uses
the accessibility service. On Android 13+ you may also need *App info → ⋮ → Allow restricted settings* before
turning the service on.

## Use
1. Open the app and turn on the accessibility service.
2. Choose an action for each press type.
3. (Optional) Disable Essential Space without a PC using Shizuku, from the *Essential Space* section.

## Build
Open the folder in Android Studio and run, or:

    gradle assembleDebug

The GitHub Actions workflow builds the APK on every push to `main`. To sign it with your own key add these
repository secrets: `JOYOUS_KEYSTORE_B64` (the keystore in base64), `JOYOUS_KEYSTORE_PASSWORD`,
`JOYOUS_KEY_ALIAS` and `JOYOUS_KEY_PASSWORD`. Without them it uses the Android debug key.

---

# Joyous Remap (español)

Remapea la **tecla Essential** de un Nothing / CMF Phone sin ADB y sin root. Cada tipo de pulsación (simple, doble,
larga, triple) puede ejecutar su propia acción. Sin permiso de Internet. Descarga `Joyous_remap.apk` de la última
release y sigue los pasos de arriba. Disponible en español e inglés.
