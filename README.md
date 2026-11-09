# Pedometer

A simple Android step-counter app written in Kotlin with Jetpack Compose.

## Features

- Counts steps using the phone's built-in hardware **step counter sensor** (low power, keeps counting in hardware even when the app isn't on screen)
- Foreground service with an ongoing notification showing today's steps
- Progress ring toward a configurable daily goal (default 10,000)
- Distance (from configurable stride length) and estimated calories
- Bar chart of the last 7 days
- Keeps 90 days of history; survives reboots and app updates
- Material 3 with dynamic colour on Android 12+

## Requirements

- Android 8.0 (API 26) or newer
- A device with a step counter sensor (almost every modern phone; most emulators don't have one)

## Build & run

Open the project in Android Studio and press **Run**, or from the command line:

```sh
./gradlew test assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Every push also builds a debug APK on GitHub Actions — download it from the
**pedometer-debug-apk** artifact of the latest *Android build* workflow run.

On first launch, grant the **Physical activity** permission (and notifications on Android 13+).

## How it works

`TYPE_STEP_COUNTER` reports the total number of steps since the phone last booted.
`StepAccumulator` stores the previous reading and adds the difference to today's total.
After a reboot (detected from the boot count and uptime) the full reading counts as new steps.
Readings that jump by more steps than anyone could walk in the elapsed time (some phones
report 0 before their real total) are not counted; they only reset the baseline.
Steps taken while the service wasn't running are credited to the day the next reading arrives.

| File | Purpose |
| --- | --- |
| `data/StepAccumulator.kt` | Pure step-counting logic (unit tested) |
| `data/StepRepository.kt` | Persists history and settings, exposes a `StateFlow` |
| `service/StepCounterService.kt` | Foreground service listening to the sensor |
| `service/BootReceiver.kt` | Restarts tracking after reboot/update |
| `ui/PedometerScreen.kt` | Compose UI |
