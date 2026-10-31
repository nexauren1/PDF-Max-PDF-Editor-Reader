# PDF Max – PDF Editor & Reader

PDF Max is an Android document workspace built with Kotlin, Jetpack Compose, Android PdfRenderer, PDFBox-Android, CameraX and ML Kit.

## Product direction

The UI follows the supplied PDF Max reference:
- Deep navy interface, electric-blue borders and violet accents.
- Color-coded tool cards with category filters and a separate tool workspace with matching accent color.\n- Every operation follows three screens: file selection and options, visible processing with elapsed time, then result review and explicit save.
- Dashboard with recent documents, quick actions and an internal document library.
- Reader with page navigation and page previews.
- In-app bottom navigation and a full side drawer.

## Internal document library

PDF Max scans supported files on the device and displays them inside its own library. Users search and select documents in PDF Max; normal tool flows do not open a third-party file picker.

Indexed extensions include:
- PDF.
- Word: DOC, DOCX, ODT and RTF.
- Spreadsheets: XLS, XLSX, CSV and ODS.
- Presentations: PPT, PPTX and ODP.
- Images: JPG, JPEG, PNG, WEBP, GIF, BMP, TIF, TIFF and HEIC.
- Text and archives: TXT, MD, LOG, ZIP, RAR, 7Z, TAR and GZ.

On Android 11 and later, full device scanning requires the Android “All files access” special permission. PDF Max explains this in the app and opens the relevant system permission screen. Google Play distribution may require an All files access declaration and policy review. The app does not use this permission to upload documents.

## Implemented local operations

- Import and preview PDF pages with Android PdfRenderer.
- Search/index supported files in the internal library.
- Merge and split PDFs.
- Reorder, extract, remove selected pages and insert blank pages.
- Rotate pages, add text overlays, watermarks and page numbering.
- Set PDF open passwords.
- Fill existing AcroForm text fields by field name.
- Add a handwritten visual signature stamp to a PDF.
- Extract text to UTF-8 TXT and images; convert PDF pages to images.
- Batch-create a multi-page PDF from selected images and run batch OCR on several scans into one searchable PDF.
- Create ZIP archives from selected documents and detect exact duplicate files with SHA-256 (no automatic deletion).
- Edit PDF title, author, subject and keywords.
- Create structured PDFs in the Writing Lab: title, subtitle, paragraphs, Sans/Serif/Mono fonts, Normal/Bold/Italic styles, adjustable body size, live preview and editable three-column tables.
- OCR from an image using on-device ML Kit.
- Capture a document photo inside the app using CameraX, then run OCR.
- Convert modern DOCX/XLSX/PPTX and text documents to text-based PDF.
- Export selectable PDF text to DOCX, XLSX and PPTX.
- Compare extracted text and inspect PDF metadata.
- Share PDFs through Android's secure FileProvider and print through Android PrintManager.

Outputs are saved under Documents/PDF Max (scans under Documents/PDF Max/Scans) and then appear in the internal library.

## Known limitations

- Legacy binary DOC/XLS/PPT files can be indexed, but local preview/conversion is not implemented. Save them as DOCX/XLSX/PPTX first.
- PDF-to-Office exports preserve selectable text, not the original page layout, typography, complex tables or embedded images.
- Adding text is an overlay; it does not rewrite existing PDF text objects.
- The handwritten signature is a visible handwritten stamp, not a certificate-backed cryptographic digital signature.
- Filling forms supports existing AcroForm text fields, not every XFA form.
- PDF re-save, repair and compression depend on the source; a saved file is not guaranteed to be smaller. The compression result screen compares the original and output sizes and reports the actual change. A severely corrupted file may not be recoverable.
- PDF translation uses on-device ML Kit language models. The first use downloads the selected source/target models over Wi‑Fi (model downloads can be roughly 30 MB each); scanned PDFs need OCR before translation.

## Build and release

- Android Gradle Plugin 8.10.1, Gradle 8.11.1 and JDK 17.
- Compile SDK 36 and target SDK 36 (Android 16); minimum Android 8.0 (API 26).
- GitHub Actions builds a Debug APK, a privately signed Release APK and a signed Android App Bundle (AAB).
- Release jobs validate the private upload keystore, password and alias before building. They fail instead of publishing a debug-signed production package.
- The stable GitHub release is tagged `v1.2.3`; upload the AAB to Google Play, not the debug APK.

## Google Play policy note

PDF Max requests Android All files access because its core document library discovers and manages compatible documents across shared device storage. Google Play treats `MANAGE_EXTERNAL_STORAGE` as a restricted, high-risk permission: submit the Permissions Declaration Form, explain why a document library's full-device indexing cannot be replaced by a user-selected Storage Access Framework flow, and wait for approval. Approval is not guaranteed. See [Google Play's All files access policy](https://support.google.com/googleplay/android-developer/answer/10467955).

Before Play submission, complete the [Google Play launch checklist](GOOGLE_PLAY_LAUNCH_CHECKLIST.md) and publish the [privacy policy](PRIVACY_POLICY.md) at a stable public URL.
