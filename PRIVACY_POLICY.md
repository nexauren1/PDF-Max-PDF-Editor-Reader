# PDF Max – Privacy Policy

**Last updated: 31 October 2026**

PDF Max is an Android PDF reader and document-management/editor application. This policy explains how the app accesses and handles information on your device.

## Files and folders

PDF Max uses Android's **All files access** permission (`MANAGE_EXTERNAL_STORAGE`) to find supported documents in shared device storage, display its internal document library, and let you run document operations without selecting every source file in a separate file-picker app. The app reads and writes files only as needed for the document features you choose. Original documents are not automatically deleted.

Generated files are saved in **Documents/PDF Max**. The library index (file names, paths, sizes and modification times) is stored in the app's private preferences so the library can be restored between sessions. This index is not a cloud backup. You can clear the app's data or uninstall PDF Max to remove its private index and preferences; files saved in shared Documents storage remain until you delete them.

## Camera and OCR

The camera permission is used only when you choose the in-app document capture feature. Captured images and OCR are processed on the device using the Android camera and ML Kit components. Captured scans are saved locally in the PDF Max output folder.

## Translation

When you choose PDF translation, PDF Max uses Google ML Kit's on-device translation models. The first use of a language pair downloads the required language models through Google Play services; translated document text is processed on the device. Scanned PDFs need OCR before text can be translated.

## Sharing and printing

Sharing or printing starts only after you choose the corresponding action. Android's system share sheet or print service then handles the file and any recipient or printer you select. Those services are subject to their own privacy policies.

## Network, analytics and advertising

The Internet permission is used for required model downloads and related Google Play services communication. The current app build does not include a developer-operated account system, advertising SDK, analytics SDK or document-upload service. PDF Max does not send document contents to servers operated by the developer.

## Information collected by the developer

The current app build does not ask you to create an account and does not collect your name, email address, precise location or contacts. The developer does not receive the private file index stored on your device.

## Data security and control

Use your device's screen lock and keep Android up to date. You control which documents you open, edit, share, print or delete. Uninstalling the app removes its private app data; it does not remove exported documents from shared storage.

## Children's privacy

PDF Max is a general-purpose document utility and does not knowingly collect personal information from children.

## Changes and contact

This policy may be updated when app functionality or legal requirements change. The current version will be published with the app's source repository.

For privacy questions or requests, contact the developer through the [PDF Max GitHub support page](https://github.com/nexauren1/PDF-Max-PDF-Editor-Reader/issues). Please do not attach private documents or sensitive personal information to a public issue.

**Developer:** PDF Max / Nexauren
