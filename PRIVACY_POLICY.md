# PDF Max – Privacy Policy

**Last updated: 3 November 2026**

PDF Max is an Android PDF reader and document-management/editor application. This policy explains how the app accesses and handles information on your device.

## Files and folders

PDF Max uses Android's **All files access** permission (`MANAGE_EXTERNAL_STORAGE`) to find supported documents in shared device storage, display its internal document library, and let you run document operations without selecting every source file in a separate file-picker app. The app reads and writes files only as needed for the document features you choose. Original documents are not automatically deleted.

Generated files are saved in **Documents/PDF Max**. During an operation, PDF Max may create temporary working copies in its private app cache so you can review a result before saving it. Shared copies for Android's share sheet are also prepared in the private cache. These temporary files stay on the device; the app removes review copies after a successful save or when you discard the pending operation, and Android may clear cache data at any time.

The library index (file names, paths, sizes and modification times), favourites, recently opened paths, reversible in-app trash entries and reader bookmarks are stored in the app's private preferences so the library can be restored between sessions. The in-app trash only hides an entry inside PDF Max; it does not delete or move the source document. This information is not a cloud backup. You can clear the app's data or uninstall PDF Max to remove its private index, preferences and cache; files saved in shared Documents storage remain until you delete them.

## Camera and OCR

The camera permission is used only when you choose the in-app document capture feature. Captured images and OCR are processed on the device using the Android camera and ML Kit components. Captured scans are saved locally in the PDF Max output folder.

## Translation

When you choose PDF translation, PDF Max uses Google Translate through Google ML Kit's on-device translation models. The first use of a language pair downloads the required language models through Google Play services; translated document text is processed on the device. Scanned PDFs need OCR before text can be translated. Translations are automatic and can be inaccurate; review important content. Learn more at https://translate.google.com.

## Sharing and printing

Sharing or printing starts only after you choose the corresponding action. Android's system share sheet or print service then handles the file and any recipient or printer you select. Those services are subject to their own privacy policies.

## Network, analytics and advertising

The Internet permission is used for required model downloads and related Google Play services communication. The current app build does not include a developer-operated account system, advertising SDK, analytics SDK or document-upload service. PDF Max does not send document contents to servers operated by the developer.

## ML Kit diagnostics and usage metrics

PDF Max uses Google ML Kit for on-device text recognition and translation. The document text, scan images and translated output are processed on the device and are not sent to Google servers as part of the ML Kit processing itself. However, Google states that its ML Kit Android SDKs collect technical information for diagnostics and usage analytics. This can include device manufacturer/model and Android version, app package/version, per-installation or device identifiers, performance and latency metrics, API configuration, feature input/output size, feature version, event types and error codes. Google says these metrics are encrypted in transit and are not shared with third parties. See Google's [ML Kit Android data disclosure](https://developers.google.com/ml-kit/android-data-disclosure) and [ML Kit terms and privacy](https://developers.google.com/ml-kit/terms).

The first use of on-device translation may also download language models through Google Play services. Google Play services and ML Kit may process technical download and diagnostic information under Google's terms and privacy policy.

## Information collected by the developer

The current app build does not ask you to create an account and the developer does not receive your name, email address, precise location, contacts, private file index, favourites, recent-file list, in-app trash list or reader bookmarks. This statement does not exclude the third-party ML Kit diagnostics described above; account for those SDK transmissions in the Google Play Data safety form.

## Data security and control

Use your device's screen lock and keep Android up to date. You control which documents you open, edit, share, print or delete. Uninstalling the app removes its private app data; it does not remove exported documents from shared storage.

## Children's privacy

PDF Max is a general-purpose document utility and does not knowingly collect personal information from children.

## Changes and contact

This policy may be updated when app functionality or legal requirements change. The current version will be published with the app's source repository.

For privacy questions or requests, contact the developer through the [PDF Max GitHub support page](https://github.com/nexauren1/PDF-Max-PDF-Editor-Reader/issues). Please do not attach private documents or sensitive personal information to a public issue.

**Developer:** PDF Max / Nexauren
