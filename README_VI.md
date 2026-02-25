<div align="left">
  <a href="README.md"><b>EN</b></a> &nbsp;|&nbsp; <a href="README_VI.md">🇻🇳 Tiếng Việt</a>
</div>

# 🎵 MusicPlayer

Ứng dụng nghe nhạc trực tuyến trên Android được xây dựng bằng **Java**, sử dụng **Firebase** làm backend. Ứng dụng cho phép người dùng duyệt, tìm kiếm và phát nhạc mượt mà với nhiều tính năng phong phú và hệ thống tài khoản Premium.

---

## 📱 Giao diện

> _Đăng nhập · Trang chủ · Trình phát · Tìm kiếm_

---

## ✨ Tính năng

- 🔐 **Xác thực tài khoản** — Đăng nhập, đăng ký, quên mật khẩu & đổi mật khẩu qua Firebase Auth
- 🏠 **Trang chủ** — Duyệt nhạc theo danh mục và album
- 🔍 **Tìm kiếm** — Tìm bài hát nhanh chóng theo thời gian thực
- 🎧 **Trình phát nhạc** — Phát/dừng, bài trước/tiếp theo, lặp lại và phát ngẫu nhiên
- 📋 **Mini Player** — Thanh phát nhạc nhỏ hiển thị liên tục ở tất cả màn hình
- 🔔 **Foreground Service** — Tiếp tục phát nhạc trong nền kèm thông báo điều khiển
- 👤 **Hồ sơ** — Xem và quản lý thông tin tài khoản
- 💎 **Tài khoản Premium** — Nghe nhạc không quảng cáo với gói Premium
- 🖼️ **Ảnh bìa album** — Tải ảnh mượt mà qua Glide từ Firebase Storage

---

## 🛠️ Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |
| Build System | Gradle (Kotlin DSL) |
| Backend | Firebase (Auth, Firestore, Storage) |
| Phát nhạc | ExoPlayer / Media3 |
| Tải ảnh | Glide 4.16 |
| Giao diện | Material Design 3, ConstraintLayout |

---

## 📁 Cấu trúc dự án

```
app/src/main/java/vn/khengandkhoi/musicplayer/
├── activity/
│   ├── LoginActivity.java          # Màn hình đăng nhập (entry point)
│   ├── SignupActivity.java         # Đăng ký tài khoản mới
│   ├── ForgotPasswordActivity.java # Quên mật khẩu qua email
│   ├── ChangePasswordActivity.java # Đổi mật khẩu khi đã đăng nhập
│   ├── MainActivity.java           # Màn hình chính (Home/Search/Profile)
│   ├── PlayerActivity.java         # Trình phát nhạc toàn màn hình
│   ├── CategoryAlbumsActivity.java # Danh sách album theo danh mục
│   ├── SongListActivity.java       # Danh sách bài hát trong album
│   └── SubscriptionActivity.java   # Màn hình đăng ký Premium
├── fragment/
│   ├── HomeFragment.java           # Trang chủ — danh mục & gợi ý
│   ├── SearchFragment.java         # Tìm kiếm bài hát, album
│   └── ProfileFragment.java        # Hồ sơ người dùng
├── adapter/
│   ├── AlbumsAdapter.java          # Adapter RecyclerView cho album
│   ├── CategoriesAdapter.java      # Adapter RecyclerView cho danh mục
│   ├── SongsAdapter.java           # Adapter RecyclerView cho bài hát
│   └── RecentSongsAdapter.java     # Adapter cho bài hát nghe gần đây
├── object/
│   ├── SongRemote.java             # Model bài hát (từ Firestore)
│   ├── Album.java                  # Model album
│   └── Category.java               # Model danh mục
├── MusicService.java               # Foreground Service phát nhạc nền
└── GridSpacingItemDecoration.java  # Tuỳ chỉnh khoảng cách RecyclerView
```

---

## 🚀 Hướng dẫn cài đặt

### Yêu cầu

- Android Studio Hedgehog trở lên
- JDK 11+
- Dự án Firebase đã bật **Auth**, **Firestore** và **Storage**

### Các bước thực hiện

1. **Clone repository**

   ```bash
   git clone https://github.com/HiImSunny/MusicPlayer.git
   cd MusicPlayer
   ```

2. **Thêm cấu hình Firebase**

   Tải file `google-services.json` từ [Firebase Console](https://console.firebase.google.com/) và đặt vào:

   ```
   app/google-services.json
   ```

3. **Mở bằng Android Studio**

   Mở thư mục gốc trong Android Studio và đợi Gradle sync hoàn tất.

4. **Chạy ứng dụng**

   Chọn thiết bị hoặc máy ảo và nhấn **Run ▶**.

---

## 🔧 Cấu hình Firebase

Ứng dụng cần các dịch vụ Firebase sau:

| Dịch vụ | Mục đích sử dụng |
|---|---|
| **Firebase Auth** | Đăng nhập, đăng ký, đặt lại mật khẩu |
| **Cloud Firestore** | Lưu metadata bài hát, album, danh mục, thông tin tài khoản và Premium |
| **Firebase Storage** | Lưu file nhạc và ảnh bìa album |

---

## 📦 Các thư viện chính

```kotlin
// Firebase
implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
implementation("com.google.firebase:firebase-auth")
implementation("com.google.firebase:firebase-firestore")
implementation("com.google.firebase:firebase-storage")

// Media3 (ExoPlayer)
implementation("androidx.media3:media3-exoplayer:1.3.1")
implementation("androidx.media3:media3-session:1.3.1")

// Tải ảnh
implementation("com.github.bumptech.glide:glide:4.16.0")

// Giao diện
implementation("com.google.android.material:material:1.12.0")
```

---

## 📄 Giấy phép

Dự án được xây dựng phục vụ mục đích học tập. Bạn có thể tự do sử dụng và chỉnh sửa.

---

<div align="center">
  Made with ❤️ by <a href="https://github.com/HiImSunny">HiImSunny</a>
</div>
