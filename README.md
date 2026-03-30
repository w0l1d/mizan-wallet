# Mizan Wallet — Money Manager

**Mizan Wallet** is a free and open source money management Android app. It is a fork of [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet) by Ivy Apps, continued independently under the same [GPL-3.0 License](LICENSE).

> **Mizan** (ميزان) means "balance" or "scale" in Arabic — fitting for a personal finance app.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Telegram](https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/w0l1d_dev)

---

## About

Mizan Wallet helps you track your personal finances with ease. Think of it as a manual expense tracker that replaces the spreadsheet for managing your money.

**Answer questions like:**

1. How much money do I have in total?
2. How much did I spend this month and on what?
3. How much can I spend and still meet my financial goals?

|                                                                                                            |                                                                                                            |                                                                                                            |                                                                                                            |
|:----------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------:|
| ![1](https://user-images.githubusercontent.com/5564499/189540998-4d6cdcd3-ab4d-40f7-85d4-c82fe8a017d1.png) | ![2](https://user-images.githubusercontent.com/5564499/189541011-1ebbd8b6-50fe-432a-91e2-59206efe99ce.png) | ![3](https://user-images.githubusercontent.com/5564499/189541023-35e7f163-d639-4466-9a91-c56890d5a28e.png) | ![4](https://user-images.githubusercontent.com/5564499/189541027-d352314c-fd5c-43eb-82ad-4aba14c7b0fa.png) |

---

## What's New in Mizan Wallet

Additions on top of the original Ivy Wallet codebase:

- **Zakat Tracking** — Built-in Zakat calculation and tracking for Muslim users (Nisab threshold, Hawl year, asset categorization)
- **Automated CI/CD** — GitHub Actions pipeline with Telegram notifications for every build and release
- **Mizan branding** — Rebranded UI across all 18+ supported languages

---

## Original Project

Mizan Wallet is a fork of **[Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet)** by [Ivy Apps](https://github.com/Ivy-Apps).

> As of November 5th, 2024, the original Ivy Wallet is no longer maintained by its original developers. This fork continues development independently.

All credit for the original architecture, UI, and features goes to the Ivy Apps team and the Ivy Wallet [contributors](https://github.com/Ivy-Apps/ivy-wallet/graphs/contributors).

---

## Tech Stack

### Core

- 100% [Kotlin](https://kotlinlang.org/)
- 100% [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material3 design](https://m3.material.io/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) + [Flow](https://kotlinlang.org/docs/flow.html)
- [Hilt](https://dagger.dev/hilt/) (DI)
- [ArrowKt](https://arrow-kt.io/) (functional programming)

### Persistence & Networking

- [Room DB](https://developer.android.com/training/data-storage/room) (SQLite ORM)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) (key-value storage)
- [Ktor client](https://ktor.io/docs/getting-started-ktor-client.html) (HTTP)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) (JSON)

### Build & CI

- [Gradle KTS](https://docs.gradle.org/current/userguide/kotlin_dsl.html)
- [GitHub Actions](https://github.com/features/actions) (CI/CD)
- [Fastlane](https://fastlane.tools/) (Google Play deployment)
- [Firebase Crashlytics](https://firebase.google.com/products/crashlytics)

### Testing

- [JUnit4](https://github.com/junit-team/junit4)
- [Kotest](https://kotest.io/)
- [Paparazzi](https://github.com/cashapp/paparazzi) (screenshot tests)
- [MockK](https://mockk.io/)

---

## Project Setup

**Requirements:** Java 17+, latest stable Android Studio

**1. Clone the repo**

```bash
git clone https://github.com/w0l1d/mizan-wallet.git
cd mizan-wallet
```

**2. Build**

```bash
./gradlew assembleDebug        # Debug APK
./gradlew assembleDemo         # Demo APK (release-signed with debug key)
./gradlew build                # Build + all tests
./gradlew detekt               # Lint
```

---

## Architecture

Three-layer clean architecture:

```
UI Layer (Composables, ViewModels)
    ↓
Domain Layer (UseCases, business logic)
    ↓
Data Layer (Repositories, Room DB, Ktor, DataStore)
```

See [docs/guidelines/Architecture.md](docs/guidelines/Architecture.md) for full details.

---

## License

This project is licensed under [GPL-3.0](LICENSE), the same license as the original Ivy Wallet.

---

## Acknowledgements

- **[Ivy Apps](https://github.com/Ivy-Apps)** — original creators of Ivy Wallet
- **[Ivy Wallet contributors](https://github.com/Ivy-Apps/ivy-wallet/graphs/contributors)** — all contributors to the upstream project