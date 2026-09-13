# AGENTS.md

## Overview

`notification-reminders` is a Kotlin Android application that monitors incoming device notifications and intelligently matches them against user-defined reminders. When a notification contains words matching an active reminder, the application creates a new notification to surface that reminder.

---

## Tech Stack & Architecture

- **Language**: Kotlin (`jvmTarget = 11`)
- **Platform**: Android (Target SDK 34, Min SDK 24)
- **Key Frameworks / Libraries**:
  - `NotificationListenerService` for background notification monitoring
  - AndroidX Core KTX, AppCompat, Material Components
  - Room Database (for persistent reminder storage)
  - View Binding

---

## Repository Structure

```
.
├── .github/
│   └── workflows/
│       └── ci.yml                # CI/CD GitHub Actions workflow
├── app/
│   ├── build.gradle.kts          # App module dependencies & configuration
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/bas080/notificationreminders/
│       │   │   ├── MainActivity.kt
│       │   │   └── utils/
│       │   │       └── ReminderMatcher.kt
│       │   └── res/               # Layouts, values, and themes
│       └── test/
│           └── kotlin/com/bas080/notificationreminders/
│               └── utils/
│                   └── ReminderMatcherTest.kt
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradlew                       # Gradle wrapper script
├── build.gradle.kts              # Root build script
├── gradle.properties             # Gradle build properties
├── settings.gradle.kts           # Included modules setup
└── README.md
```

---

## Environment & Build Instructions

### Gradle Configuration Notes
- AndroidX properties must be enabled in `gradle.properties`:
  ```properties
  android.useAndroidX=true
  android.enableJetifier=true
  android.suppressUnsupportedCompileSdk=34
  ```

### Build & Test Commands
- **Run all unit tests**:
  ```bash
  ./gradlew test
  ```
- **Compile Kotlin source files**:
  ```bash
  ./gradlew compileDebugUnitTestKotlin
  ```
- **Clean build directory**:
  ```bash
  ./gradlew clean
  ```

---

## CI/CD Pipeline

The GitHub Actions workflow (`.github/workflows/ci.yml`) runs test, build, and release steps using the Gradle wrapper (`./gradlew`) on:
1. **Pull Request Pushes**: Names release/build using PR title and PR number (`<PR Title> (#<PR Number>)`).
2. **Push to `master`**: Names release/build using commit message title and short hash (`<Commit Title> (<Short Hash>)`).
3. **Version Tag Push (`v*`)**: Names release/build using the tag name (e.g. `v1.0.0`).

---

## Key Components & Code Logic

### Matching Logic (`ReminderMatcher.kt`)
- Performs case-insensitive word matching between reminder text and incoming notification content.
- Filters out common stop words (e.g., "the", "a", "is", "in", "to", etc.).
- Matches based on word presence regardless of word order, supporting partial substring matches.
- Tested by `ReminderMatcherTest.kt`.

---

## Code Style & Conventions

- Follow standard Kotlin coding conventions.
- Package naming: `com.bas080.notificationreminders`.
- Use ViewBinding for layout interactions.
- Avoid committing generated build outputs (`build/`, `.gradle/`).
