# 🌊 Navula

### Your Entertainment. Your Space. Your Experience.

**Navula** is a modern Android streaming application designed to bring online entertainment into one clean, intuitive, and seamless experience.

Built with Kotlin and Jetpack Compose, Navula combines content discovery, streaming provider integration, personalized browsing, and an offline-capable media player in a modern Android application.

<p align="center">
  <strong>Discover · Stream · Save · Enjoy</strong>
</p>

---

## ✨ Features

### 🎬 Streaming & Content Discovery
- Spacedom.live as the primary streaming provider.
- Extensible architecture for additional cloud streaming providers.
- Content discovery and browsing.
- Integrated streaming experience.

### 🔍 Search & Personalization
- Content search functionality.
- Search history support.
- Bookmarks for saving favorite content.
- Convenient access to previously discovered content.

### ▶️ Media Player
- Integrated AndroidX Media3 / ExoPlayer.
- Offline media playback support.
- HLS streaming support.
- MPEG-DASH streaming support.
- Native Android media playback integration.

### ☁️ Provider Architecture
- Primary Spacedom provider.
- Architecture designed to accommodate additional providers.
- Extensible provider integration approach.

### 🎨 Modern Android Experience
- Built with Jetpack Compose.
- Material 3 UI components.
- Modern Android application architecture.
- Designed for an intuitive browsing and viewing experience.

---

## 🛠️ Technology Stack

| Technology | Purpose |
|---|---|
| Kotlin | Primary programming language |
| Jetpack Compose | Modern UI development |
| Material 3 | UI components |
| AndroidX Media3 | Video playback |
| ExoPlayer | Media engine |
| Room | Local database |
| DataStore | Preference storage |
| WorkManager | Background tasks |
| Coil | Image loading |
| OkHttp | Network communication |
| Kotlin Coroutines | Asynchronous operations |
| Kotlinx Serialization | Data serialization |
| Gemini API | AI integration support |

---

## 📱 Platform Requirements

| Requirement | Specification |
|---|---|
| Platform | Android |
| Minimum Android | Android 7.0 (API 24) |
| Target SDK | Android 16 (API 36) |
| Compile SDK | API 36 |
| Language | Kotlin |
| Build system | Gradle |
| UI Framework | Jetpack Compose |

---

## 🚀 Getting Started

### Prerequisites

Before building Navula, make sure you have:

- Android Studio
- JDK compatible with your Android Gradle Plugin
- Android SDK 36
- Git
- Internet connection

### Clone the Repository

```bash
git clone https://github.com/nsakib1290/Navula.git
```

Navigate to the project directory:

```bash
cd Navula
```

### Build the Application

On Windows:

```bash
gradlew.bat assembleDebug
```

On macOS / Linux:

```bash
./gradlew assembleDebug
```

The generated APK can be found in:

```text
app/build/outputs/apk/debug/
```

---

## 🔐 Configuration

Navula includes support for Gemini API configuration through the Gradle Secrets Plugin.

1. Create a `.env` file in the project root.
2. Configure your API key if Gemini API functionality is required.

Example:

```env
GEMINI_API_KEY=YOUR_API_KEY
```

**Important:**
- Never commit your actual API keys.
- Keep `.env` private.
- Use `.env.example` as a configuration reference.
- Do not expose sensitive credentials in public builds.

---

## 📂 Project Structure

```text
Navula/
│
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           ├── res/
│           └── AndroidManifest.xml
│
├── gradle/
│   └── libs.versions.toml
│
├── .env.example
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── README.md
```

*The structure above represents the main project layout; additional source packages and files may exist.*

---

## 🔒 Privacy & Security

Navula is designed with Android platform security in mind.

- HTTPS-only cleartext traffic policy.
- API credentials should be kept outside source control.
- Network access is handled through Android networking components.
- No unnecessary runtime permissions are declared in the current manifest.

Always review provider integrations and their privacy policies before use.

---

## 🗺️ Roadmap

Planned areas for continued development:

- [ ] Additional streaming provider integrations.
- [ ] Improved provider management.
- [ ] Enhanced content discovery.
- [ ] Better search and filtering.
- [ ] Improved playback performance.
- [ ] More efficient download management.
- [ ] Enhanced offline experience.
- [ ] Improved caching and network handling.
- [ ] UI and animation refinements.
- [ ] Performance and memory optimizations.
- [ ] More customization options.

*Roadmap items are development goals, not necessarily currently available features.*

---

## 🤝 Contributing

Contributions, suggestions, and bug reports are welcome.

1. Fork the repository.
2. Create a feature branch.
3. Make your changes.
4. Test your implementation.
5. Submit a Pull Request.

For major changes, open an issue first to discuss your proposal.

---

## ⚠️ Disclaimer

Navula is an independent software project.

- Navula does not claim ownership of third-party content.
- Streaming availability depends on the configured providers.
- Users are responsible for ensuring that their use of external content complies with applicable laws and provider terms.
- Third-party services, logos, and trademarks belong to their respective owners.

---

## 👨‍💻 Developer

**Muhammad Najmus Sakib**

Project: **Navula**

GitHub: [@nsakib1290](https://github.com/nsakib1290)

Repository: [github.com/nsakib1290/Navula](https://github.com/nsakib1290/Navula)

---

<p align="center">
  <strong>A Project By SAKIB</strong>
  <br/>
  Built with passion for a better streaming experience.
</p>

---

<p align="center">
  © 2026 Navula. All rights reserved unless otherwise specified by a project license.
</p>
