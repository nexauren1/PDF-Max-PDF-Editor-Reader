# Google Play declaration — All files access

Use this as the draft explanation in the Play Console Permissions Declaration Form for `MANAGE_EXTERNAL_STORAGE`. Review the wording against the exact app build and the current Play Console questions before submitting. Approval is decided by Google Play and is not guaranteed.

## Core purpose

PDF Max is an on-device document management and PDF productivity app. Its central feature is an internal document library that locates supported documents in shared device storage, indexes them, and lets the user open, organize, convert, edit, protect, share and print those documents from inside PDF Max.

## Why All files access is required

PDF Max is designed to discover compatible files across the device's shared storage, including documents in multiple folders and mounted shared-storage volumes. The internal library refreshes its index and presents searchable results without requiring the user to select a folder every time a document operation is started.

A user-selected Storage Access Framework folder or individual-file picker would grant access only to selected locations or files. That would prevent the app's advertised whole-library discovery and ongoing index refresh from working as designed, and would require users to locate and re-authorize different folders/files repeatedly. This permission is therefore tied to the core document-library experience rather than advertising, analytics or unrelated features.

## User-facing features that use the permission

- Discover and index supported PDF, Word, spreadsheet, presentation, image, text and archive files in shared storage.
- Search the internal library and select source files for user-requested operations.
- Open and preview supported documents, and save generated results to `Documents/PDF Max`.
- Maintain local favourites, recently opened entries and a reversible in-app trash list. The trash list only hides entries in PDF Max; it does not delete source documents.

## Data handling

- File processing is performed on the device for the selected operation.
- PDF Max does not upload document contents to servers operated by the developer and has no cloud document sync in this release.
- The local document index and library preferences are stored in app-private preferences.
- Original source files are not automatically deleted by scanning, indexing or ordinary tool processing. Results are saved separately.
- Sharing and printing start only when the user explicitly chooses those actions and then use Android's system services.

## Permission flow

Before opening Android's All files access settings, PDF Max explains that this special permission is needed to locate compatible documents across shared storage. The user can decline and continue without the automatic whole-device library. The app provides an in-app route to review the permission in Android settings.

## Evidence to prepare for review

Capture screenshots or a short screen recording from the exact submitted build showing:
1. The in-app explanation before the permission request.
2. The Android All files access setting for PDF Max.
3. The internal document library displaying multiple supported file types and folders after access is granted.
4. A user selecting a document from the library and completing a document operation.
5. The output saved under `Documents/PDF Max`.

Only submit this declaration if the production app's primary purpose and the screenshots demonstrably match this explanation. See Google's current [All files access policy](https://support.google.com/googleplay/android-developer/answer/10467955).
