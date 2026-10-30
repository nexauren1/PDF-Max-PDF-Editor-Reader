# PDF Max – PDF Editor & Reader

PDF Max is an Android document workspace built with Kotlin, Jetpack Compose, Android PdfRenderer, PDFBox-Android, CameraX and ML Kit.

## Product direction

The UI follows the supplied PDF Max reference:
- Deep navy interface, electric-blue borders and violet accents.
- Color-coded tool cards with a separate tool workspace and matching accent color.
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
- Reorder pages and remove selected pages.
- Rotate pages, add text overlays, watermarks and page numbering.
- Set PDF open passwords.
- Fill existing AcroForm text fields by field name.
- Add a handwritten visual signature stamp to a PDF.
- Extract text and images; convert PDF pages to images.
- Create PDFs from text and images.
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
- The handwritten signature is a visible image stamp, not a certificate-backed cryptographic digital signature.
- Filling forms supports existing AcroForm text fields, not every XFA form.
- PDF resave/repair/compression depends on the source PDF; a resaved file is not guaranteed to be smaller or recoverable if severely corrupted.
- PDF translation uses on-device ML Kit language models. The first use downloads the selected source/target models over Wi‑Fi (model downloads can be roughly 30 MB each); scanned PDFs need OCR before translation.

## Build in Android Studio

1. Open the repository as a Gradle project.
2. Use JDK 17.
3. Install Android SDK platform 35 and build tools 35.0.0.
4. Run the app configuration on Android 8.0+.

GitHub Actions builds Debug and Release APKs on pushes to main and publishes a preview release when the build succeeds.

Preview releases are signed with the Android debug key for device testing only. Configure a private upload keystore before publishing a production APK/AAB to Google Play.
