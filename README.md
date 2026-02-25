# 🎵 MusicPlayer

An Android music streaming application built with **Java** and powered by **Firebase** backend services. The app allows users to browse, search, and stream music online with a smooth playback experience and premium subscription support.

---

## 📱 Screenshots

> _Login · Home · Player · Search_

---

## ✨ Features

- 🔐 **Authentication** — Login, Sign up, Forgot password & Change password via Firebase Auth
- 🏠 **Home Screen** — Browse music by categories and albums
- 🔍 **Search** — Find songs quickly with real-time search
- 🎧 **Music Player** — Full-featured player with play/pause, skip, repeat, and shuffle controls
- 📋 **Mini Player** — Persistent mini playback bar accessible from all screens
- 🔔 **Foreground Service** — Keeps music playing in the background with notification controls
- 👤 **Profile** — View and manage your account settings
- 💎 **Premium Subscription** — Ad-free listening with premium account support
- 🖼️ **Album Art** — Smooth image loading via Glide with Firebase Storage

---

## 🛠️ Tech Stack

| Category | Technology |
|---|---|
| Language | Java |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |
| Build System | Gradle (Kotlin DSL) |
| Backend | Firebase (Auth, Firestore, Storage) |
| Media Playback | ExoPlayer / Media3 |
| Image Loading | Glide 4.16 |
| UI | Material Design 3, ConstraintLayout |

---

## 📁 Project Structure

```
app/src/main/java/vn/khengandkhoi/musicplayer/
├── activity/
│   ├── LoginActivity.java          # App entry point — authentication
│   ├── SignupActivity.java         # New account registration
│   ├── ForgotPasswordActivity.java # Password reset via email
│   ├── ChangePasswordActivity.java # Change password for logged-in user
│   ├── MainActivity.java           # Main shell (Home/Search/Profile tabs)
│   ├── PlayerActivity.java         # Full-screen music player
│   ├── CategoryAlbumsActivity.java # Albums list by category
│   ├── SongListActivity.java       # Songs list for an album
│   └── SubscriptionActivity.java   # Premium subscription screen
├── fragment/
│   ├── HomeFragment.java           # Categories & recommendations
│   ├── SearchFragment.java         # Search songs & albums
│   └── ProfileFragment.java        # User profile & settings
├── adapter/
│   ├── AlbumsAdapter.java          # RecyclerView adapter for albums
│   ├── CategoriesAdapter.java      # RecyclerView adapter for categories
│   ├── SongsAdapter.java           # RecyclerView adapter for songs
│   └── RecentSongsAdapter.java     # Recently played songs adapter
├── object/
│   ├── SongRemote.java             # Song data model (Firestore)
│   ├── Album.java                  # Album data model
│   └── Category.java               # Category data model
├── MusicService.java               # Foreground service for audio playback
└── GridSpacingItemDecoration.java  # Custom RecyclerView item spacing
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 11+
- A Firebase project with **Auth**, **Firestore**, and **Storage** enabled

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/HiImSunny/MusicPlayer.git
   cd MusicPlayer
   ```

2. **Add Firebase config**

   Download your `google-services.json` from the [Firebase Console](https://console.firebase.google.com/) and place it in:
   ```
   app/google-services.json
   ```

3. **Open in Android Studio**

   Open the project root folder in Android Studio and let Gradle sync.

4. **Run the app**

   Select a device or emulator and press **Run ▶**.

---

## 🔧 Firebase Configuration

The app requires the following Firebase services:

| Service | Usage |
|---|---|
| **Firebase Auth** | User login, signup, password reset |
| **Cloud Firestore** | Song metadata, albums, categories, user data, premium status |
| **Firebase Storage** | Audio files and album cover images |

---

## 📦 Key Dependencies

```kotlin
// Firebase
implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
implementation("com.google.firebase:firebase-auth")
implementation("com.google.firebase:firebase-firestore")
implementation("com.google.firebase:firebase-storage")

// Media3 (ExoPlayer)
implementation("androidx.media3:media3-exoplayer:1.3.1")
implementation("androidx.media3:media3-session:1.3.1")

// Image Loading
implementation("com.github.bumptech.glide:glide:4.16.0")

// UI
implementation("com.google.android.material:material:1.12.0")
```

---

## 📄 License

This project is for educational purposes. Feel free to use and modify it.

---

<div align="center">
  Made with ❤️ by <a href="https://github.com/HiImSunny">HiImSunny</a>
</div>
