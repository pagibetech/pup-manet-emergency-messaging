# MANET Adaptive Failover System - Android Starter Files

This starter is made for **Android Studio on Mac** and for a **first-time Android developer**.

## What this app does
- Shows the **currently connected GSM/cellular network**
- Shows the **signal strength in dBm and bars**
- Shows a text status: **Weak / Fair / Strong**
- Works with **single SIM or dual SIM** phones
- Shows a second list of **detected cell/network information** that Android exposes on the phone
- Displays the **Adamson University logo** and the banner **MANET Adaptive Failover System**

## Important limitation
A normal Play-style Android app can reliably read the **current operator**, **signal strength**, and **cell info** exposed by Android.

A true full manual scan of **all available carriers** is more restricted on Android and may require privileged/carrier access depending on device and API path. So the **Detected Networks** section in this starter shows the cell/network information the phone makes available, not a guaranteed full engineering scan.

---

# Part 1 - Create the base project in Android Studio

1. Open **Android Studio**
2. Click **New Project**
3. Choose **Empty Views Activity**
   - Do **not** choose Compose for this first project
4. Click **Next**
5. Set these values:
   - **Name:** `MANET Adaptive Failover System`
   - **Package name:** `ph.edu.adamson.manetfailover`
   - **Save location:** anywhere you want
   - **Language:** `Kotlin`
   - **Minimum SDK:** `API 29` or higher
6. Click **Finish**
7. Wait for Gradle sync to finish completely

---

# Part 2 - Replace the generated files with the files in this starter

Copy these files from this starter into your Android Studio project:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/ph/edu/adamson/manetfailover/MainActivity.kt`
- `app/src/main/java/ph/edu/adamson/manetfailover/CellInfoAdapter.kt`
- `app/src/main/java/ph/edu/adamson/manetfailover/UiModels.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/item_cell_info.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/drawable/adamson_seal.png`

If Android Studio asks whether to overwrite, choose **Replace**.

---

# Part 3 - Update Gradle dependency

Open:

`app/build.gradle.kts`

Inside `dependencies { ... }`, add this line if it is not already there:

```kotlin
implementation("androidx.recyclerview:recyclerview:1.4.0")
```

Keep the Material dependency that Android Studio already generated.

Then click **Sync Now**.

---

# Part 4 - Prepare your Android phone

On your Redmi phone:

1. Open **Settings > About phone**
2. Tap **MIUI version** or **Build number** several times until Developer Options is enabled
3. Go to **Settings > Additional settings > Developer options**
4. Turn on:
   - **USB debugging**
5. Connect the phone to your Mac using USB
6. On the phone, allow the computer when the USB debugging prompt appears

---

# Part 5 - Run the app

1. In Android Studio, wait until your phone appears in the device list at the top
2. Click the **Run** button
3. Allow permissions on the phone when asked:
   - **Phone** permission
   - **Location** permission
4. Also make sure the phone's **Location** switch is ON, because some telephony cell-info APIs return limited or empty data when location is OFF

---

# What you should expect

## Current Network card
This shows:
- Active network name
- Signal in dBm
- Signal bars
- Status text: Weak / Fair / Strong
- Active SIM summary

## Detected Networks card
This shows rows with:
- Network/operator name if available
- Radio type like LTE / NR / GSM / WCDMA
- dBm
- bar level
- status text
- whether the cell is registered or neighbor cell

---

# Notes for Redmi / Xiaomi phones
Some Xiaomi/Redmi phones add their own permission prompts or battery restrictions.

If data looks stale:
- open the app settings
- allow phone and location permission
- disable aggressive battery optimization for the app during testing

---

# Easy improvements you can add later
- Auto-refresh timer every few seconds
- Separate tabs for **Current Network** and **Detected Networks**
- Color-coded status badges
- Logging/history screen
- Export signal readings to CSV
- App icon using the Adamson seal

