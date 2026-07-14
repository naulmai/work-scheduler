# Work Scheduler (Ringer Scheduler)

An Android application designed to automatically schedule and manage your device's ringer modes (Silent, Vibrate, Normal) based on user-defined work schedules.

## Features
- **Automatic Profile Switching:** Automatically switch your phone to Silent or Vibrate mode during work hours and revert to Normal mode afterwards.
- **Customizable Schedules:** Easily configure days and times for your ringer schedules.
- **Reliable Background Execution:** Uses Android's AlarmManager/WorkManager to ensure profile switches happen exactly on time, even if the app is closed.

## Getting Started

### Prerequisites
- Android Studio (latest version recommended)
- Android SDK

### Installation & Build
1. Clone this repository:
   ```bash
   git clone https://github.com/naulmai/work-scheduler.git
   ```
2. Open the project in **Android Studio**.
3. Let Gradle sync the project dependencies.
4. Click **Run** to build and install the app on your emulator or physical Android device.

## Technologies Used
- Kotlin
- Android SDK
- Gradle
