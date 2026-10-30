# PDF Max – PDF Editor & Reader

An Android PDF productivity app built with Kotlin and Jetpack Compose.

## Visual direction
The project follows the supplied PDF Max reference: deep navy surfaces, electric-blue outlines, violet accents, colorful tool tiles, a dashboard with recent files, a five-action bottom navigation and a full navigation drawer.

## Open in Android Studio
1. Open this repository as a Gradle project.
2. Use JDK 17.
3. Allow Gradle to sync and install Android SDK platform 35 if prompted.
4. Run the app configuration on an Android 8.0+ device or emulator.

## Current foundation
- Compose application shell and theme.
- Dashboard, recent documents, tools grid, file manager and navigation drawer.
- PDF import using Android's document picker.
- First-page preview for imported PDFs using Android PdfRenderer.
- Reader, editor, converter, scanner and document-protection interface foundations.

## Implementation note
This commit establishes the visual system and navigation foundation. Advanced document operations (text-object editing, OCR, conversion, merge/split, compression, encryption and digital signing) require dedicated processing implementations and tests. Their tool screens must not be mistaken for completed PDF engines.
