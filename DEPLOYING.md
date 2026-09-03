# Deploying Stonks to your phone

This gets the app onto your phone as a **real installed app** — one that survives Android
Studio being closed, the cable being unplugged, and the project being rebuilt.

You are not publishing to the Play Store, so there is no review, no developer account, and
no fee. You are building a signed APK and installing it yourself.

---

## Why a debug install is not enough

The build Android Studio installs when you hit Run is signed with a throwaway debug key
that is regenerated per machine and expires. It works, but it is tied to the development
setup rather than being the app.

A release build is signed with **your** key. That key is what lets a later build install
*over* the earlier one and keep its data. Losing the key means every future update has to
uninstall first, taking the database with it.

---

## Before you start: take a backup

Installing a release build over a debug build **requires uninstalling first**, because
Android refuses to replace an app with one signed by a different key. Uninstalling deletes
the database.

So: open the app, go to **Settings → Export a backup**, and send the file somewhere off the
phone. You will restore it at the end.

This is a one-time cost. Once you are on your own key, updates install over the top and
keep everything.

---

## 1. Create a signing key

Once, ever. Keep the file safe — a copy in a password manager or cloud drive is sensible.

From the repository root:

```bash
keytool -genkeypair -v -keystore stonks-release.jks -alias stonks -keyalg RSA -keysize 2048 -validity 10000
```

`keytool` ships with the JDK. If it is not on your PATH, use the one inside Android Studio:
`"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"`.

It asks for a password and some identifying details. The details are cosmetic for a
personal app — your name is fine. **Remember the password.**

`validity 10000` is about 27 years. Shorter is a trap: an expired key cannot sign updates.

> Android Studio can do this through **Build → Generate Signed App Bundle / APK → APK →
> Create new…** if you prefer a dialog. Same result.

## 2. Tell Gradle about the key

Create `app/keystore.properties`:

```
storeFile=stonks-release.jks
storePassword=your-password
keyAlias=stonks
keyPassword=your-password
```

`storeFile` is resolved from the repository root, which is where step 1 put the file.
Both passwords are the same unless you deliberately set a separate key password.

This file and `*.jks` are already in `.gitignore` — **they must never be committed.**
Without this file the release build still assembles, it just comes out unsigned, so
nothing breaks for anyone cloning the repo.

## 3. Build the release APK

```bash
.\gradlew.bat assembleRelease
```

The APK lands at `app/build/outputs/apk/release/app-release.apk`.

If Gradle complains it cannot find a JDK, point it at Android Studio's bundled one for that
shell:

```bash
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

(Your system `java` is Java 8; Gradle wants 25.)

## 4. Install it

Uninstall the debug build first — this is the step that needs the backup you took:

```bash
adb uninstall dev.wizishan.stonks
```

```bash
adb install app\build\outputs\apk\release\app-release.apk
```

`adb` lives at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.

No cable? Copy the APK to the phone however you like — Drive, email, USB — and tap it.
Android will ask for permission to install from that app the first time.

## 5. Restore your data

Open Stonks → **Settings → Restore from a backup** → pick the file you exported.

---

## Updating later

Once you are on your own key, an update is three steps and keeps all data:

1. Bump `versionCode` (and usually `versionName`) in `app/build.gradle.kts`. Android
   refuses to install an APK whose `versionCode` is not higher than the installed one.
2. `.\gradlew.bat assembleRelease`
3. `adb install -r app\build\outputs\apk\release\app-release.apk`

No uninstall. The database stays where it is.

---

## If you ever want it on the Play Store

You would need a developer account (one-off fee), a privacy policy, and an **App Bundle**
rather than an APK — `.\gradlew.bat bundleRelease`, output at
`app/build/outputs/bundle/release/app-release.aab`. Nothing about the app needs to change.
Google would also take over signing, which removes the lose-your-key risk.

Not necessary for an app you are the only user of.
