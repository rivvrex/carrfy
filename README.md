# Carrfy

A landscape-first Android music player designed for in-car head units and tablets. Carrfy pairs a Compose UI built around a fixed left sidebar with an ExoPlayer audio engine, Firebase accounts, and music metadata pulled from public catalog APIs.

The app is locked to landscape orientation and sized for glanceable, low-distraction use while driving: large artwork, big touch targets, and a persistent now-playing view.

> **Heads up on scope:** Carrfy is *not* a Spotify Connect client. It uses Spotify's public catalog API (app-level Client Credentials) for metadata, search, and playlist import only. Actual audio comes from freely available preview/stream endpoints — see [Where the audio comes from](#where-the-audio-comes-from).

---

## Features

- **Landscape car UI** — persistent sidebar navigation across five destinations: Now Playing, Recents, Library, Radio, and Search.
- **Real audio playback** — Media3/ExoPlayer singleton so playback survives tab switches, with autoplay queue refilling, shuffle, and repeat (off / context / track).
- **Firebase accounts** — email + password sign-up and login, with per-user profiles, preferences, and listening history in Cloud Firestore.
- **Cloud state sync** — queue, recents, and player state persist to Firestore and rehydrate on launch; the app falls back to locally seeded data when Firebase is absent or empty.
- **Playlist import** — paste a playlist URL from Spotify, YouTube Music, Apple Music, or Amazon Music and import its track list.
- **Internet radio** — curated built-in stations (SomaFM, Radio Paradise) plus live station discovery via the Radio Browser directory.
- **Dynamic theming** — AndroidX Palette extracts a dominant colour from the current artwork and tints the background behind the player.

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 1.9.22 |
| UI | Jetpack Compose, Material 3, Compose compiler ext. 1.5.8 |
| Audio | AndroidX Media3 ExoPlayer 1.4.1 |
| Auth & data | Firebase Auth + Cloud Firestore (BOM 33.2.0) |
| Networking | `HttpURLConnection` + Gson (no Retrofit) |
| Images | Coil 2.4.0 |
| Colour extraction | AndroidX Palette |
| Build | Android Gradle Plugin 8.11.0 |

**SDK levels:** `minSdk 24` (Android 7.0) · `compileSdk` / `targetSdk 34` · `applicationId com.carrfy`

---

## Getting started

### Prerequisites

- Android Studio (Ladybug or newer recommended)
- JDK 17
- An Android device or emulator on API 24+, ideally in landscape
- A [Spotify Developer](https://developer.spotify.com/dashboard) app (for the Client ID / Secret)
- A [Firebase](https://console.firebase.google.com/) project with Auth and Firestore enabled

### 1. Clone

```bash
git clone https://github.com/rivvrex/carrfy.git
```

### 2. Configure `local.properties`

This file is **git-ignored** and must be created locally. Copy the template and fill in your own values:

```bash
cp local.properties.example local.properties
```

Then edit it:

```properties
sdk.dir=/path/to/your/Android/Sdk
spotify.client_id=your_spotify_client_id
spotify.client_secret=your_spotify_client_secret
```

The Gradle build reads these and exposes them as `BuildConfig.SPOTIFY_CLIENT_ID` and `BuildConfig.SPOTIFY_CLIENT_SECRET`. If they are missing the build still succeeds and the fields fall back to `"NOT_SET"` — catalog features will fail at runtime rather than at compile time.

Carrfy uses the **Client Credentials** flow, so no redirect URI or user-facing Spotify login is required.

### 3. Add Firebase config

Create an Android app in your Firebase project with package name `com.carrfy`, download `google-services.json`, and place it at:

```
app/google-services.json
```

This file is also git-ignored. Full walkthrough — including Firestore collection shapes and security rules — is in [FIREBASE_SETUP.md](FIREBASE_SETUP.md).

### 4. Build and run

```bash
./gradlew assembleDebug
```

Or press **Run** in Android Studio.

> **Note:** the Gradle wrapper is currently pinned to `9.0-milestone-1`, a pre-release build. If you hit plugin incompatibilities, changing `distributionUrl` in `gradle/wrapper/gradle-wrapper.properties` to a stable release such as `gradle-8.13-bin.zip` is a safe first move.

---

## Project structure

```
carrfy/
├── app/
│   ├── build.gradle                 # Module config, deps, BuildConfig secret injection
│   ├── proguard-rules.pro           # R8/ProGuard rules (referenced by build.gradle)
│   ├── google-services.json         # Firebase config — NOT committed, add your own
│   └── src/main/
│       ├── AndroidManifest.xml      # Landscape lock, INTERNET permission, app entry
│       ├── res/values/              # colors.xml, themes.xml
│       └── java/com/carrfy/
│           ├── CarrfyApplication.kt         # Application class, Firebase init
│           ├── MainActivity.kt              # Single activity, hosts MainScreen
│           ├── auth/
│           │   ├── SpotifyAuthManager.kt    # Spotify Client Credentials token + caching
│           │   ├── SpotifyOAuthManager.kt   # CarrfyAuthManager: Firebase Auth + user docs
│           │   ├── SpotifyAuthModels.kt     # CarrfyUser, preferences, auth models
│           │   └── PlaylistImporter.kt      # Playlist URL parsing + import per service
│           ├── playback/
│           │   ├── PlayerManager.kt         # Global ExoPlayer singleton
│           │   └── PlaybackMonitor.kt       # Progress polling, track-completion hooks
│           ├── spotify/
│           │   ├── SpotifyRepository.kt     # Core state hub (see below)
│           │   └── models/                  # Track, Artist, Album, API response types
│           └── ui/
│               ├── MainScreen.kt            # Root scaffold, sidebar + content host
│               ├── Sidebar.kt               # SidebarDestination enum + nav rail
│               ├── NavigationState.kt       # NavigationManager, Screen sealed class
│               ├── AuthScreens.kt           # Login / signup
│               ├── NowPlayingScreen.kt      # Player, artwork, transport controls
│               ├── RecentsScreen.kt         # Listening history
│               ├── LibraryScreen.kt         # Playlists + import entry point
│               ├── RadioScreen.kt           # Station list and tuning
│               ├── SearchScreen.kt          # Catalog search
│               ├── ArtistsScreen.kt         # Artist grid
│               ├── ArtistDetailScreen.kt    # Artist page + top tracks
│               ├── PlaylistTracksScreen.kt  # Playlist track list
│               ├── PlaylistCard.kt          # Playlist card composable
│               ├── PlaylistCardData.kt      # Card view model
│               ├── ProfileModal.kt          # Account sheet, preferences, logout
│               ├── PlaybackUtils.kt         # Duration/format helpers
│               └── DynamicBackgroundUtil.kt # Palette-based background tinting
├── build.gradle                     # Root build, plugin versions
├── settings.gradle                  # Module includes, plugin repositories
├── gradle.properties                # AndroidX flag, JVM heap
├── gradle/wrapper/                  # Wrapper jar + properties — must be committed
├── gradlew / gradlew.bat            # Wrapper scripts — must be committed
├── .gitattributes                   # Forces LF on gradlew so it runs on Linux/macOS
├── local.properties.example         # Template for your local secrets
├── FIREBASE_SETUP.md                # Firebase + Firestore setup guide
└── README.md
```

### Files worth reading first

If you're new to the codebase, these four carry most of the weight:

1. **[`SpotifyRepository.kt`](app/src/main/java/com/carrfy/spotify/SpotifyRepository.kt)** — at ~1,800 lines this is the heart of the app. It holds the in-memory catalog, queue, recents, and player state in a `companion object`, mediates every external API call, drives autoplay, and syncs to Firestore. Start here.
2. **[`PlayerManager.kt`](app/src/main/java/com/carrfy/playback/PlayerManager.kt)** — the ExoPlayer singleton. Explains why playback keeps running across navigation.
3. **[`MainScreen.kt`](app/src/main/java/com/carrfy/ui/MainScreen.kt)** + **[`NavigationState.kt`](app/src/main/java/com/carrfy/ui/NavigationState.kt)** — navigation is hand-rolled with a `NavigationManager` and a `Screen` sealed class rather than Navigation-Compose, so routes won't be where you expect.
4. **[`app/build.gradle`](app/build.gradle)** — shows how `local.properties` secrets become `BuildConfig` fields.

---

## Architecture notes

**State lives in a singleton.** `SpotifyRepository` keeps catalog, queue, recents, shuffle, and repeat state in a `companion object`, so all screens observe one shared source. There is no ViewModel layer and no dependency injection framework — screens construct the repository directly and read from it.

**Firestore is a cache, not a requirement.** The repository initialises Firebase inside `runCatching`, so a missing or misconfigured `google-services.json` degrades gracefully to locally seeded data instead of crashing. Player state is mirrored to `state/player`; catalog data is read from the `tracks`, `artists`, and `playlists` collections.

**Two independent auth systems.** `CarrfyAuthManager` (in `SpotifyOAuthManager.kt`) handles the *user's* Carrfy account via Firebase. `SpotifyAuthManager` handles the *app's* Spotify catalog token via Client Credentials. They are unrelated — signing into Carrfy does not sign you into Spotify.

### Where the audio comes from

`SpotifyRepository` resolves playable URLs from several public endpoints, in preference order:

| Source | Used for |
|---|---|
| `saavnapi-two.vercel.app` | Full-length track streams |
| `itunes.apple.com/search` | 30-second preview fallback |
| Spotify `preview_url` | Preview fallback when present in metadata |
| SomaFM / Radio Paradise | Built-in radio stations |
| `de1.api.radio-browser.info` | Live radio station discovery |

These are third-party services outside the project's control; availability and coverage vary, and a track may fall back to a short preview or fail to resolve. Any production use should replace this layer with a licensed catalog.

---

## Repository hygiene: what to commit

The list below is the intended contract for this repo.

### Commit these

- `app/src/**` — all Kotlin sources, resources, and `AndroidManifest.xml`
- `build.gradle`, `app/build.gradle`, `settings.gradle`, `gradle.properties`
- `gradlew`, `gradlew.bat`, and `gradle/wrapper/` — **including `gradle-wrapper.jar`**, so clones can build without a local Gradle install
- `app/proguard-rules.pro`
- `README.md`, `FIREBASE_SETUP.md`, `local.properties.example`, `.gitignore`, `.gitattributes`

### Never commit these

| Path | Why |
|---|---|
| `local.properties` | Holds your Spotify **client secret** and machine-specific SDK path |
| `app/google-services.json` | Firebase project keys, tied to your project |
| `build/`, `app/build/` | Regenerated on every build |
| `.gradle/` | Local Gradle caches and lock files |
| `*.jks`, `*.keystore`, `keystore.properties` | Signing keys |
| `.idea/` (most of it), `*.iml` | Editor-local state |
| `local.properties`, `captures/`, `.externalNativeBuild/` | Local tooling output |

The included [`.gitignore`](.gitignore) enforces all of the above.

### If your clone already tracks build output

Adding a `.gitignore` does **not** untrack files git already knows about. To clear previously committed build artifacts and secrets while keeping them on disk:

```bash
git rm -r --cached build app/build .gradle .idea
```

```bash
git rm --cached local.properties app/google-services.json
```

```bash
git commit -m "Stop tracking build output, editor state, and local secrets"
```

**If a secret was ever pushed, untracking is not enough** — it stays in the commit history and must be treated as compromised. Rotate the Spotify client secret in the developer dashboard and regenerate any exposed Firebase keys, then purge history with `git filter-repo` or the BFG Repo-Cleaner if the repo is public.

---

## Known limitations

- Playback depends on third-party public APIs; some tracks resolve only to 30-second previews, and some may not resolve at all.
- Auth tokens for the Carrfy account are kept in plain `SharedPreferences` (`carrfy_auth`), not `EncryptedSharedPreferences`.
- The Spotify client secret is embedded in the APK via `BuildConfig`, which is inherently extractable — acceptable for a personal build, not for distribution.
- No automated tests, and no release signing configuration.
- Navigation is hand-rolled, so there is no deep-link or back-stack support beyond `goBack()`.
- Portrait orientation is unsupported by design.

---

## Contributing

Issues and pull requests are welcome. Please don't include `local.properties` or `google-services.json` in a PR.

## License

No license has been chosen yet. Without one, default copyright applies and others have no rights to reuse the code — adding an [MIT or Apache 2.0 license](https://choosealicense.com/) is recommended if you intend this to be usable by others.
