# Firestore Rules Tests

Run work calendar rules tests using Firestore Emulator only:

```bash
npm --prefix functions run emulators:rules:work-calendars:java21
```

Run attendance_daily rules tests using Firestore Emulator only:

```bash
npm --prefix functions run emulators:rules:attendance:java21
```

Notes:
- This command starts/stops emulators automatically.
- No production Firebase credentials are required.
