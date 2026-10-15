# BetterYou for Android

An Android app that keeps you on track with your habits, helps you control bad habits, and helps you grow your hobbies through regular check-ins. It's the Android version of the [BetterYou iOS app](https://github.com/Terence-wilson/Betteryou), with the same features and rules.

Built with Kotlin, Jetpack Compose (Material 3), Room and AlarmManager notifications. Requires **Android 8.0 (API 26)+**; builds with **JDK 17** and **Android Studio Ladybug or newer**.

## Getting started

1. Open the project folder in Android Studio and let Gradle sync.
2. Run the **app** configuration on an emulator or device.
3. Run the unit tests with `./gradlew testDebugUnitTest`.

Or from the command line: `./gradlew assembleDebug` builds `app/build/outputs/apk/debug/app-debug.apk`.

Every push runs the unit tests and builds a debug APK on GitHub Actions; the APK is attached to the run as the `betteryou-debug-apk` artifact.

## How it works

### Cycles, resets and check-ins

- **Check-in frequency** — any number of days, weeks or months. Anything longer than a month shows a warning that check-ins that far apart aren't very effective.
- **Reset period** — the start of every cycle. By default it is **1/4 of the cycle**, unless the cycle is **shorter than 7 days**, in which case the reset lasts the **whole cycle**. You can set a custom length in Settings.
- **During the reset** the goal is to stop bad habits and break activities entirely, and do good habits every day.
- **At the check-in** you:
  1. Rate each focused hobby from 1–5 on **consistency**, **enjoyment** and **improvement**.
  2. Rate how well you followed each focused good habit, bad habit and break **after the reset period and before the check-in** (or during the cycle, if the whole cycle was a reset).
  3. Keep your current focus or pick new ones for the next cycle, then the next cycle starts.

### Lists

- Hobbies & interests, good habits, bad habits, and "break" activities (e.g. a break from social media).
- Focus on up to **3** hobbies, **3** good habits and **3** bad habits per cycle, plus any number of breaks. Focus can be changed at each check-in, or edited mid-cycle from the Today tab.

### Daily check-in (optional)

A quick daily mood log (1–5 with an optional note), with an optional daily notification at a time you choose.

### Notifications

- Check-in due reminder.
- "Reset complete" reminder when the reset ends.
- Optional daily mood reminder.

Tapping a notification opens the check-in or daily mood screen. Reminders are restored after a reboot. On Android 13+ the app asks for notification permission when you turn reminders on.

### Progress

The **Progress** tab shows:

- Overview stats: check-ins completed, average habit adherence, hobby enjoyment, 7-day mood, mood streak.
- Check-in score trends over time.
- Per-hobby charts of consistency, enjoyment and improvement.
- Per-habit adherence charts.
- Mood over the last 30 days, mood distribution and recent notes.

## Project layout

```
app/src/main/java/com/betteryou/app/
  BetterYouApplication.kt     App entry point; owns the repository
  MainActivity.kt             Hosts the Compose UI and routes notification taps
  domain/                     Plain Kotlin models and rules (no Android dependencies)
    Schedule.kt               Frequency, reset-length and cycle-phase rules
    Models.kt                 Settings, items, cycles, check-ins, ratings, mood
    ItemKind.kt               The four list kinds and their copy
    Metrics.kt                Calculations behind the Progress tab
  data/                       Room database, entities and the repository
  notifications/              AlarmManager scheduling, reminder and reboot receivers
  ui/
    BetterYouApp.kt           Root: onboarding vs. tabs, overlays, notification routing
    AppViewModel.kt           UI state and actions
    onboarding/               First-run setup
    dashboard/                Today tab: cycle status, focus, daily mood
    checkin/                  The check-in flow
    lists/                    Manage hobbies, habits and breaks
    metrics/                  Progress charts
    settings/                 Schedule and notification settings
    components/               Shared cards, rating/mood controls, item pickers, charts
app/src/test/                 Unit tests for the scheduling rules and metrics
```
