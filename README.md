# ChatApp – Android Real-Time Chat Application

A WhatsApp-inspired Android messaging application built with **Java** and **Android Studio**. ChatApp lets users create an account, discover other registered users, and communicate through a clean, familiar chat interface. It uses Firebase services for authentication and real-time data, with Cloudinary for image uploads.

> **Note:** This is an independent learning/project application and is not affiliated with, endorsed by, or connected to WhatsApp or Meta.

## Features

- **User authentication** – Register with a name, email address, and password, then log in securely using Firebase Authentication.
- **User discovery and one-to-one chats** – Browse registered users and start or reopen individual conversations.
- **Real-time messaging** – Send and receive text messages with updates delivered through Firestore listeners.
- **Image sharing** – Select and share images in a conversation; images are uploaded to Cloudinary.
- **Message status** – Track sent, delivered, and read states.
- **Reply to messages** – Reply to a particular message with a quoted preview.
- **Forward messages** – Forward a message to another conversation.
- **Starred messages** – Save important messages and view them in a dedicated screen.
- **Message deletion** – Delete a message for yourself or, where permitted by the app's time limit, for everyone.
- **Online presence and typing indicators** – View availability and typing activity using Firebase Realtime Database.
- **Chat list management** – Pin conversations and view recent conversations, with pinned chats prioritised.
- **Notifications** – Receive message notifications through Firebase Cloud Messaging and the app's notification handling.
- **Profile and account controls** – View/edit available profile information and log out.

## Tech Stack

| Area | Technology |
|---|---|
| Language | Java |
| IDE | Android Studio |
| UI | XML layouts, Material Components, Android View Binding |
| Backend / Authentication | Firebase Authentication |
| Chat and user data | Cloud Firestore |
| Presence and typing state | Firebase Realtime Database |
| Push notifications | Firebase Cloud Messaging (FCM) |
| Image hosting | Cloudinary Android SDK |
| Image loading | Glide |
| Build system | Gradle (Kotlin DSL) |

## Application Screens and Flow

1. **Login / Sign Up:** Users sign in or create an account.
2. **Chats:** The main screen displays conversations and provides access to new chats and starred messages.
3. **New Chat:** Users select another registered user to open a one-to-one conversation.
4. **Conversation:** Users exchange text and image messages and use message actions such as reply, forward, star, copy, and delete.
5. **Profile:** Users can access their profile details and account controls.

## Project Structure

```text
app/
└── src/main/
    ├── java/com/example/chatapp/
    │   ├── adapter/       # RecyclerView adapters for chats, messages and users
    │   ├── data/          # Firebase data repositories
    │   ├── model/         # User, Chat and Message models
    │   ├── service/       # Firebase messaging service
    │   ├── ui/
    │   │   ├── auth/      # Login and registration screens
    │   │   └── chat/      # Chat, user list, profile, forwarding and starred screens
    │   ├── util/          # Presence, notifications and image upload/compression helpers
    │   └── MainActivity.java
    └── res/
        ├── drawable/      # Icons and UI backgrounds
        ├── layout/        # Activity and RecyclerView item layouts
        ├── menu/          # App menus
        └── values/        # Colors, strings and themes
```

## Requirements

- Android Studio with Android SDK support for **compileSdk 37**.
- JDK 17 or the JDK recommended by the installed Android Gradle Plugin.
- An Android device or emulator running **Android 7.0 (API 24) or later**.
- A Firebase project and a Cloudinary account for the backend and image-upload features.
- Internet access for Gradle dependency downloads and app services.

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/YOUR-USERNAME/YOUR-REPOSITORY.git
cd YOUR-REPOSITORY
```

Replace the URL with your GitHub repository URL.

### 2. Open the project

1. Open Android Studio.
2. Select **Open** and choose the cloned project folder (the folder containing `settings.gradle.kts`).
3. Allow Gradle to sync and install any required Android SDK components.

### 3. Configure Firebase

1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Register an Android app using this application ID:

   ```text
   com.example.chatapp
   ```

3. Download your own `google-services.json` and place it in the `app/` directory.
4. Enable **Email/Password** sign-in in Firebase Authentication.
5. Create/enable Cloud Firestore and Firebase Realtime Database.
6. Configure Firebase security rules so authenticated users can access only the data they are authorised to read or modify.
7. Configure Firebase Cloud Messaging if you want push notifications.

Do not commit your personal Firebase configuration or credentials to a public repository. Keep `google-services.json` out of Git (for example, add `/app/google-services.json` to `.gitignore`) and provide setup instructions instead. If a configuration file or credential has already been pushed publicly, review and rotate/restrict the affected credentials and review your Firebase security rules.

### 4. Configure Cloudinary image uploads

The project uses Cloudinary for chat image uploads. Create a Cloudinary account and configure an **unsigned upload preset** for the intended upload folder. Update the Cloudinary cloud name and upload-preset configuration used by the app to your own values. Do not place an API secret in Android client code. Restrict the unsigned preset (allowed formats, size, and folder) to reduce abuse.

### 5. Build and run

1. Connect an Android device with USB debugging enabled or start an emulator.
2. In Android Studio, select the `app` run configuration.
3. Click **Run** to build, install, and launch ChatApp.

You can also build a debug APK from the project root using the Gradle wrapper:

```bash
# macOS / Linux
./gradlew assembleDebug

# Windows
 gradlew.bat assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

## Backend Overview

The application separates Firebase operations into repository/helper classes:

- `UserRepository` retrieves user records from Cloud Firestore.
- `ChatRepository` manages conversations, messages, message status, starred messages, forwarding, and deletion operations.
- `PresenceManager` manages online/offline presence and typing indicators through Firebase Realtime Database.
- `NotificationHelper` handles notification records and displays notifications.
- `CloudinaryUploader` and `ImageCompressor` support chat image uploads.

Your Firebase database rules and Cloudinary upload-preset restrictions are essential for a safe deployment. Do not use permissive test rules in production.

## Current Scope

This project focuses on one-to-one text and image messaging. Features such as end-to-end encryption, voice/video calling, and WhatsApp account integration are **not** provided by this project.

## Contributing

Contributions and suggestions are welcome:

1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/your-feature`).
3. Commit your changes (`git commit -m "Add your feature"`).
4. Push the branch and open a Pull Request.

## License

No license has been specified yet. Unless a license is added to this repository, all rights are reserved by the copyright holder; check before reusing or distributing the code.

---

**Built with Java, Android Studio, Firebase, and Cloudinary.**
