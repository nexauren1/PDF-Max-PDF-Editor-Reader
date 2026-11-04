# PDF Max – Privacy Policy

**Version:** 1.2.6 (versionCode 11)  
**Last updated:** 4 November 2026  
**Public policy page:** https://nexauren1.github.io/PDF-Max-PDF-Editor-Reader/privacy-policy.html

The bilingual HTML page has been committed under `docs/privacy-policy.html`. The GitHub Pages address above is the intended public URL; it becomes live after GitHub Pages is enabled for this repository with **GitHub Actions** as the publishing source. Until then, the HTML file can be viewed in the repository at https://github.com/nexauren1/PDF-Max-PDF-Editor-Reader/blob/main/docs/privacy-policy.html.

## Summary

PDF Max is an Android document utility. Normal PDF operations, the document index, local preferences, camera capture and ML Kit OCR/translation processing are designed to run on the device. The developer does not operate an account system, cloud document storage, advertising SDK, developer analytics SDK or document-upload service.

## Permissions

- **All files access** (`MANAGE_EXTERNAL_STORAGE`): used to discover supported documents in shared storage and provide the app's internal library and document tools. This is broad access to shared storage, not access limited to one user-selected file.
- **Read external storage** (up to Android 12L): used on older Android versions for the document library.
- **Camera**: requested only when the user starts the in-app document capture feature.
- **Internet / network state**: used by Google Play services and ML Kit for language-model downloads and related service/diagnostic communication, and when opening external support or information links.

## Information stored locally

The app stores a local index (file names, paths, extensions, sizes and modification dates), favourites, recently opened paths, in-app Trash entries, reader bookmarks, language preference and temporary operation/share files in private app storage. These are not uploaded to a PDF Max account. The in-app Trash only hides a library item; it does not delete the original file.

Generated documents are saved in `Documents/PDF Max`; camera scans are saved in `Documents/PDF Max/Scans`. Temporary review copies are kept in the app cache and removed when possible after saving or discarding. Android may clear cache data. Clearing app data or uninstalling removes the private index, preferences and cache, but exported files in shared Documents storage remain until the user deletes them.

## Local processing and Google ML Kit

PDF reading and editing operations run locally. Camera images and OCR are processed on-device. PDF translation uses on-device ML Kit translation models; the current implementation requests language-model downloads over Wi-Fi through Google Play services. The document text used by OCR/translation is processed on the device and is not sent to servers operated by the developer.

Google's ML Kit Android SDKs collect technical diagnostics and usage metrics. Google's disclosure says these can include device manufacturer/model and Android version/build, app package/version, device or per-installation identifiers, performance/latency metrics, API configuration, input/output size, feature version, event types and error codes. The Translate feature may also collect configured source/target languages and use Firebase remote-configuration/installation diagnostics. Google states that the listed data is encrypted in transit using HTTPS and is not transferred by Google to third parties. See [Google ML Kit Android data disclosure](https://developers.google.com/ml-kit/android-data-disclosure), [ML Kit terms](https://developers.google.com/ml-kit/terms) and [Google Privacy Policy](https://policies.google.com/privacy).

## Sharing and printing

Sharing and printing start only after the user chooses the action. Android's share sheet or print service then handles the file and the selected recipient, app or printer under that service's own privacy policy.

## Developer collection and contact

The current app build does not include a developer-operated account system, ads SDK, developer analytics SDK or document-upload service. The developer does not receive documents, the file index, favourites, recents, in-app Trash entries, bookmarks or language preference through the app. The app does not request location, contacts, microphone or SMS permissions. These statements do not exclude the ML Kit technical diagnostics described above.

For privacy questions, use the [PDF Max GitHub support page](https://github.com/nexauren1/PDF-Max-PDF-Editor-Reader/issues). GitHub issues may be public; do not post private documents, passwords or other sensitive information in a public issue.

**Developer:** Nexauren · PDF Max

---

The HTML page contains the complete policy in Portuguese and English.
