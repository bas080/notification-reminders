# notification-reminders

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

- **Persistent Reminders**: Reminders are stored and continuously monitored across all notifications
- **Smart Word Matching**: Intelligently matches reminder text against notification content while avoiding false positives
- **Quick Actions**: Swipe, mark done, edit, or delete reminders directly from notifications
- **History Tracking**: Completed reminders are stored with timestamps (viewing history coming in v2)

## Tech Stack

- **Language**: Kotlin
- **Platform**: Android
- **Key API**: Android NotificationListenerService

## License

MIT License
