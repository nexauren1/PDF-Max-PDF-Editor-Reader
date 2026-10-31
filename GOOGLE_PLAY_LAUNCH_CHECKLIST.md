# PDF Max – Google Play launch checklist

This checklist is separate from the GitHub Release. A successful GitHub build does not mean Google Play has approved the app.

## Package and signing
- [x] Target SDK 36 / Android 16.
- [x] Build Release APK and Android App Bundle (AAB).
- [x] Validate the private upload keystore before release builds.
- [ ] Confirm in Play Console that Play App Signing is enabled and upload the AAB from the stable release.
- [ ] Verify that the package name `com.nexauren.pdfmax` is the intended permanent application ID.

## Restricted file permission
- [ ] Complete the Google Play Permissions Declaration Form for `MANAGE_EXTERNAL_STORAGE`.
- [ ] Explain that PDF Max's core purpose includes locating and managing supported documents across shared storage; explain why user-selected SAF folders would materially impair the requested automatic library and indexing workflow.
- [ ] Provide an in-app explanation before sending users to Android's All files access settings.
- [ ] Submit the app for policy review and wait for approval. Eligibility as a document-management app does not guarantee approval.

Policy reference: https://support.google.com/googleplay/android-developer/answer/10467955

## Store listing and privacy
- [ ] Publish [PRIVACY_POLICY.md](PRIVACY_POLICY.md) at a stable public HTTPS URL and enter that URL in Play Console.
- [ ] Complete the Data safety form to match the final SDKs and actual app behaviour.
- [ ] Complete content rating, target audience, ads declaration and app category.
- [ ] Provide the 512×512 store icon, feature graphic, phone screenshots and short/full descriptions in Portuguese and English.
- [ ] Test onboarding, all-files permission, scanning, PDF creation, import/open, sharing, printing and translation on at least one Android 13 device and one Android 15/16 device.

## Release checks
- [ ] Install the signed APK on a clean device and over the previous version; confirm the app updates without losing the library cache unexpectedly.
- [ ] Create a long multi-page PDF with headings, paragraphs and a table; open it in another PDF reader and verify all pages.
- [ ] Verify file scanning after granting All files access, after revoking it, and after returning to the app.
- [ ] Confirm the upload key matches the key registered in Play Console before submitting.

The GitHub Release contains the signed APK and AAB. Google Play publication and policy approval must still be completed by the Play Console account owner.
