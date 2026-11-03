# PDF Max – Google Play launch checklist

This checklist is separate from the GitHub Release. A successful GitHub build does not mean Google Play has approved the app.

## Package, size and signing
- [x] Target SDK 36 / Android 16.
- [x] Configure R8 code shrinking and resource shrinking. Google Play App Bundle delivery is used for device-specific APK splits.
- [ ] Review CI-reported APK/AAB sizes and compare with the previous universal APK before accepting the size optimization.
- [x] Configure GitHub Actions to build the universal Release APK and Android App Bundle (AAB).
- [x] Validate the private upload keystore before release builds.
- [ ] Confirm in Play Console that Play App Signing is enabled and upload the AAB from the stable release.
- [ ] Verify that the package name `com.nexauren.pdfmax` is the intended permanent application ID.
- [ ] Install the universal APK on a clean 64-bit ARM Android device; also test the AAB through Play internal testing.

## Prepared submission materials
- [x] Drafted a bilingual store listing with accurate feature limitations: [PLAY_STORE_LISTING.md](PLAY_STORE_LISTING.md).
- [x] Drafted the All files access use-case explanation and evidence checklist: [GOOGLE_PLAY_PERMISSION_DECLARATION.md](GOOGLE_PLAY_PERMISSION_DECLARATION.md).
- [ ] Capture actual screenshots and a permission-flow recording from the tested release build; do not submit concept-art mockups as app screenshots.

## Restricted file permission
- [ ] Complete the Google Play Permissions Declaration Form for `MANAGE_EXTERNAL_STORAGE`.
- [ ] Explain that PDF Max's core purpose includes locating and managing supported documents across shared storage; explain why user-selected SAF folders would materially impair the requested automatic library and indexing workflow.
- [ ] Provide an in-app explanation before sending users to Android's All files access settings.
- [ ] Submit the app for policy review and wait for approval. Eligibility as a document-management app does not guarantee approval.

Policy reference: https://support.google.com/googleplay/android-developer/answer/10467955

## Store listing and privacy
- [ ] Publish [PRIVACY_POLICY.md](PRIVACY_POLICY.md) at a stable public HTTPS URL and enter that URL in Play Console.
- [ ] Complete the Data safety form to match the final SDKs and actual app behaviour. ML Kit documents collection of device/app information, per-installation or device identifiers, performance metrics, API configuration, feature input/output sizes, feature version, event types and error codes for diagnostics and usage analytics; review the linked SDK disclosure before selecting Play Console data types: https://developers.google.com/ml-kit/android-data-disclosure.
- [ ] Include Google Play services/model-download behaviour and relevant third-party SDK disclosures; the fact that OCR/translation content is processed on-device does not mean ML Kit sends no telemetry.
- [ ] Complete content rating, target audience, ads declaration and app category.
- [ ] Provide the 512×512 store icon, feature graphic, phone screenshots and short/full descriptions in Portuguese and English.
- [ ] Test onboarding, all-files permission, scanning, PDF creation, import/open, sharing, printing, translation, favourites, recent files, reversible trash, bookmarks, text search, zoom, night mode, form flattening, metadata removal and PDF report on at least one Android 13 device and one Android 15/16 device.
- [ ] Test R8/minified release behaviour for OCR, PDFBox operations, translation model downloads, signing, form filling, form flattening, metadata removal and Office conversions; compilation alone does not prove these runtime paths.

## Release checks
- [ ] Install the signed APK on a clean device and over the previous version; confirm the app updates without losing the library cache unexpectedly.
- [ ] Create a long multi-page PDF with headings, paragraphs and a table; open it in another PDF reader and verify all pages.
- [ ] Verify file scanning after granting All files access, after revoking it, and after returning to the app.
- [ ] Confirm the upload key matches the key registered in Play Console before submitting.

The GitHub Release contains the signed APK and AAB. Google Play publication and policy approval must still be completed by the Play Console account owner.
