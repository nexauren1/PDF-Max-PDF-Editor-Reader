# PDF Max – PDF Editor & Reader

An Android-first PDF productivity app built with Kotlin and Jetpack Compose.

## Visual direction

The two supplied PDF Max concept boards are the product's primary visual reference:
- Deep navy surfaces with electric blue, violet and cyan accents.
- Rounded tool tiles with distinctive category colors.
- A dashboard with search, quick actions, popular tools and recent documents.
- A full side drawer for workspace, tools and preferences.
- Dedicated screens for tools, file management, settings and PDF reading.
- Dark and light appearance modes; Portuguese and English UI labels.

## Current foundation (0.1.0)

Implemented in the feature/pdfmax-compose-foundation branch:
- Native Android project structure.
- Compose dashboard, bottom navigation and side navigation drawer.
- Tools catalogue with category filters and individual tool configuration screens.
- File-management and settings layouts.
- Portuguese/English UI toggle and dark/light theme toggle.
- Android Storage Access Framework PDF picker.
- In-app PDF page rendering and previous/next page navigation using PdfRenderer.

## Processing roadmap

The tool screens are a visual and interaction foundation. Each processing engine must be connected and tested before its action can be advertised as complete.

1. PDF core: metadata, page thumbnails, zoom, search, bookmarks and page cache.
2. Page operations: merge, split, reorder, rotate, delete and extract.
3. Document output: compression, image/PDF conversion, export and sharing.
4. Editing: annotations, text/image overlays, stamps, forms and signatures.
5. Scan/OCR: camera capture, perspective correction, image cleanup and text recognition.
6. Security and quality: password protection, permission handling, error states, accessibility and performance.

## Build environment

- Android Gradle Plugin 9.3.1
- Gradle 9.5.0
- Kotlin 2.4.21 Compose compiler plugin
- Jetpack Compose BOM 2026.08.00
- JDK 17
- compileSdk 36, targetSdk 36, minSdk 26

Open the repository in Android Studio and allow Gradle sync to download the configured dependencies. The binary Gradle Wrapper JAR and launch scripts are not yet committed; use Android Studio's Gradle wrapper generation or install Gradle 9.6.0 locally until that is added.

## Package

com.nexauren.pdfmax
