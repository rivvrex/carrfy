# Carrfy Firebase Setup

## 1. Create Firebase Project

1. Open [Firebase Console](https://console.firebase.google.com/)
2. Create a new project named "Carrfy" (or similar)
3. Add an Android app with package name `com.carrfy`
4. Download `google-services.json` and place it at:
   - `carrfy/app/google-services.json`

## 2. Enable Authentication

1. In Firebase Console → **Authentication** tab
2. Click **Get Started**
3. Enable **Email/Password** provider:
   - Click "Email/Password"
   - Toggle both "Enable" and "Enable email link (passwordless sign-in)" if desired
   - Click **Save**

## 3. Enable Firestore Database

1. In Firebase Console → **Firestore Database** tab
2. Click **Create database**
3. Start in **Test mode** (or use production with proper Security Rules)
4. Choose location (recommended: closest to your users)
5. Click **Create**

## 4. Set Firestore Security Rules

**Critical: Replace default rules with these:**

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can only read/write their own documents
    match /users/{userId} {
      allow read, write: if request.auth.uid == userId;
      
      // Users can read/write their own subcollections
      match /{document=**} {
        allow read, write: if request.auth.uid == userId;
      }
    }
    
    // Public collections (if you need them)
    match /artists/{document=**} {
      allow read: if true;
      allow write: if false;
    }
    
    match /tracks/{document=**} {
      allow read: if true;
      allow write: if false;
    }
  }
}
```

## 5. Create Firestore Collections for Music Data

Create these collections in Firestore (Native mode):

- `artists`
- `tracks`
- `playlists`
- `state`

### artists document fields

- `id` (string)
- `name` (string)
- `imageUrl` (string, optional)
- `genres` (array of strings, optional)

### tracks document fields

- `id` (string)
- `name` (string)
- `uri` (string, optional; fallback: `spotify:track:<id>`)
- `durationMs` (number, optional)
- `albumId` (string, optional)
- `albumName` (string, optional)
- `albumImageUrl` (string, optional)
- `artistIds` (array of strings, preferred)
- `artistNames` (array of strings, optional fallback)

### playlists document fields

- `id` (string)
- `name` (string)
- `description` (string, optional)
- `imageUrl` (string, optional)
- `ownerName` (string, optional)
- `trackIds` (array of track ids)

## 6. User Collection Structure

The app automatically creates user documents in the `users` collection when users sign up. Each user document has:

**users/{userId}**
- `id` (string): Firebase UID
- `email` (string): User's email
- `displayName` (string): User's display name
- `createdAt` (timestamp): Account creation time
- `preferences` (object):
  - `autoplay` (boolean)
  - `quality` (string)
  - `language` (string)
  - `theme` (string)
  - `notificationsEnabled` (boolean)
- `listeningHistory` (array): Track listen records
- `importedPlaylists` (array): Imported playlist metadata

**Subcollections:**
- `users/{userId}/listening_history/` - Individual listen records
- `users/{userId}/imported_playlists/` - Imported playlists

## Troubleshooting

### "Configuration not found" error

This typically means Firebase is not properly initialized. Ensure:

1. ✅ `google-services.json` is in `carrfy/app/` directory
2. ✅ Firebase plugin is applied in `build.gradle` (`id 'com.google.gms.google-services'`)
3. ✅ Firebase dependencies are added (check `build.gradle` dependencies)
4. ✅ Android Internet permission is declared in `AndroidManifest.xml`
5. ✅ Device has active internet connection
6. ✅ Firebase project is activated (check Firebase Console - project should be visible)

### "User data not found in database"

This means Firebase Auth worked but Firestore document wasn't created. Check:

1. Firestore Security Rules allow writes to `/users/{uid}` for authenticated users
2. Firestore database is active (not deleted)
3. Try signing up again - user document should auto-create on signup

### Authentication failures

Check Firebase Console Logs:
1. Go to **Functions** → **Logs** (or use **Cloud Logging**)
2. Filter for errors related to Authentication
3. Review error codes and messages

### state/player document fields

- `queueUris` (array of strings)
- `recentTrackIds` (array of track ids)
- `currentTrackUri` (string, optional)
- `isPlaying` (boolean)
- `shuffleEnabled` (boolean)
- `repeatMode` (string: `OFF`, `CONTEXT`, `TRACK`)
- `updatedAtMs` (number)

## 3. Firestore Security Rules (single personal user)

Use strict temporary rules while you are the only user:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

For local prototyping without auth, use test mode briefly and lock it down later.

## 4. Current Runtime Behavior

- If Firebase is configured and data exists, Carrfy reads from Firestore.
- If Firebase is missing or empty, Carrfy continues using local seeded data.
- UI remains unchanged.

## 5. Notes

- This phase wires metadata/state via Firestore.
- Playback engine is still local simulation in repository methods.
- Next phase should replace simulated playback with ExoPlayer-backed audio playback.
