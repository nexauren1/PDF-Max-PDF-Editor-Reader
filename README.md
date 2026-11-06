# PDF Max – PDF Editor & Reader

PDF Max 2.0 is a fresh Android PDF workspace. The previous interface and its broad-storage scanning model have been removed. The Android application ID remains `com.nexauren.pdfmax`.

## Product experience

The interface follows the supplied ten-screen direction:
- Deep navy surfaces, one bright cyan/blue accent family, and a matching light theme.
- Home with three primary actions: Compress, Merge and Create PDF.
- A separate Tools catalogue with individual workspaces for each operation.
- My Files with search, page thumbnails, favourites, recently opened files and an in-app Trash.
- Reader with page-by-page rendering, sharing and quick access to editing tools.
- Settings for Portuguese/English and dark/light appearance.
- Animated screen transitions. Navigation and back navigation display a rotating gear transition; PDF operations show a separate processing screen while work runs.

## Current local PDF operations

- Create a multi-page PDF from selected photos or a camera capture.
- Merge selected PDFs in the order chosen by the user.
- Rewrite/re-save a PDF and report the actual before/after size. This is a best-effort optimization; the result can be larger than the source.
- Extract selected page ranges into a new PDF.
- Remove selected page ranges into a new PDF.
- Rotate every page by 90°, 180° or 270°.
- Add a text watermark or text overlay.
- Add page numbers.
- Protect a PDF with an opening password.
- Remove PDF protection when the current password is provided.
- Extract selectable text to UTF-8 TXT.
- Render pages to PNG images inside a ZIP file.
- Update PDF title and author metadata.

Each tool has a dedicated options screen. Operations run locally, create a separate output, and do not overwrite the source document. The processing indicator is indeterminate because PDFBox does not expose trustworthy page-by-page progress for every operation; the app does not fabricate a percentage.

## Files and privacy model

PDF Max no longer requests `MANAGE_EXTERNAL_STORAGE`, broad shared-storage access, or network access. Users explicitly choose PDFs through Android's document picker and choose photos through the system photo picker. The app copies imported PDFs into its private document workspace.

The in-app library currently indexes PDFs imported or created by PDF Max; it does not crawl the whole device. Favourites, recent paths and Trash state are stored locally. Trash moves an app-owned file to a private Trash folder; permanent deletion requires a separate user action.

The app does not include a developer-operated account, cloud sync, advertising SDK, analytics SDK or document-upload service. Sharing is started only by the user through Android's sharing mechanism.

## Current limitations

- Camera capture uses Android's system camera capture flow. Automatic edge detection, perspective correction, manual crop handles and scanner filters are not part of this build yet.
- Compression is a best-effort PDF rewrite, not a guaranteed image recompression. The result screen reports the actual size change, including when the output is larger.
- Text and watermark tools add overlays; they do not edit the original text objects already inside a PDF.
- Text extraction exports selectable text. Scanned image-only pages require OCR, which is not included in this clean 2.0 foundation.
- The reader renders pages and provides share/favourite/tool navigation. Highlight, freehand drawing and certificate-backed digital signatures are not yet implemented as editing actions.

## Build

- Kotlin, Jetpack Compose and Material 3.
- Android Gradle Plugin 8.10.1, Kotlin 2.0.21 and JDK 17.
- Compile/target SDK 36, minimum Android 8.0 (API 26).
- PDFBox-Android 2.0.27.0.
- Application ID: `com.nexauren.pdfmax`.
- Version: `2.0.0` (`versionCode 12`).

Release builds require the private upload keystore environment variables configured in GitHub Actions. A successful debug build is not the same as a signed release or a device-tested APK.

## Privacy policy

- [Privacy policy (HTML)](docs/privacy-policy.html)
- [Privacy policy (Markdown)](PRIVACY_POLICY.md)
- Public URL after GitHub Pages is enabled: https://nexauren1.github.io/PDF-Max-PDF-Editor-Reader/privacy-policy.html
