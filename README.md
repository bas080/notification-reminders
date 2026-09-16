# notification-reminders

[![Stand with Palestine](https://img.shields.io/badge/🇵🇸%20%20Stand%20With%20Palestine-007A3D?style=flat-square&color=brightgreen)](https://www.islamic-relief.org.uk/giving/appeals/palestine/)
[![Donate via Liberapay](https://img.shields.io/badge/Donate-Liberapay-F6C915?style=flat-square&logo=liberapay&logoColor=black)](https://liberapay.com/bas080)

A Kotlin Android app that monitors your notifications and intelligently reminds you about related to-do items. When you receive a notification containing words from your reminders, the app creates a new notification surfacing the matching reminder so you can take action.

## How It Works

1. **Create Reminders**: Click the action on an Android notification to open a text input dialog and add a new reminder
2. **Smart Matching**: The app continuously monitors all incoming notifications (excluding its own) for words matching your reminders
3. **Intelligent Matching**:
   - Case-insensitive word matching
   - Filters out common words (the, a, is, etc.)
   - Matches on word presence (not order)
   - Supports partial word matches
4. **Notification Pop-up**: When a match is found, a new notification is created to surface the reminder
5. **Manage Reminders**: 
   - Click the notification to open a dialog showing all your reminders
   - Click any reminder to see its pop-up notification
   - From the pop-up, you can:
     - **Swipe** to dismiss/ignore the reminder
     - **Mark Done** to complete it (stored with timestamp)
     - **Edit** to update the reminder text
     - **Delete** to remove it

## Features

- **Minimal Text Interface**: Clean, typography-driven UI using system colors for high contrast, distinct text weights, and elegant text navigation
- **Single Top Input**: A single, clean input field at the top of the main UI for adding new reminders
- **Persistent Reminders**: Reminders are stored and continuously monitored across all notifications
- **Smart Word Matching**: Intelligently matches reminder text against notification content while avoiding false positives
- **Quick Actions**: Swipe, mark done, edit, or delete reminders directly from notifications
- **History Tracking**: Completed reminders are stored with timestamps (viewing history coming in v2)

## Installation & Google Play Protect

Because the APK releases downloaded directly from GitHub are self-signed (sideloaded), Android's **Google Play Protect** may show a warning dialog when installing (e.g. *"Unrecognized app"* or *"Blocked by Play Protect"*).

### How to Install:
1. Open the downloaded `.apk` file on your Android device.
2. When the Google Play Protect prompt appears, tap **"More details"** (or **"Advanced"**).
3. Tap **"Install anyway"** to complete installation.

## Tech Stack

- **Language**: Kotlin
- **Platform**: Android
- **Key API**: Android NotificationListenerService

## License

MIT License
