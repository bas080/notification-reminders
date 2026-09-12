# notification-reminders

A Kotlin Android app that monitors your notifications and intelligently reminds you about related to-do items. When you receive a notification containing keywords from your reminders, the app surfaces the matching reminder so you can take action right away.

## Features

- **Create Reminders**: Set up reminders using a simple notification-style input interface
- **Smart Matching**: The app scans incoming notifications (excluding its own) for keywords that match your reminders
- **Contextual Pop-ups**: When a match is found, a reminder pop-up appears so you can act on it immediately
- **Quick Actions**: 
  - **Swipe** to dismiss a reminder
  - **Mark Done** to complete a reminder
  - **Edit** to update reminder details
  - **Delete** to remove a reminder

## How It Works

1. Create a reminder with specific keywords
2. The app continuously monitors all incoming notifications
3. When a notification contains words matching your reminders, a pop-up appears
4. Interact with the reminder using swipe, mark done, edit, or delete actions

## Tech Stack

- **Language**: Kotlin
- **Platform**: Android

## Getting Started

[Add installation and setup instructions here]

## License

[Add your license here]
