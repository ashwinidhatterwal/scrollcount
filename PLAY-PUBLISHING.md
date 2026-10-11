# Publishing preparation

Source version `1.2.4-rc1`, versionCode `22`, target API `36`. This is preparation, not a published or approved app.

1. Follow `GITHUB-TERMUX-SETUP.md`, then upload the signed `app-release.aab` to a Play internal testing track. Enable Play App Signing. Keep the existing application ID; increment versionCode for later uploads.
2. The public contact is configured as ashwinidhatterwal@gmail.com. Deploy backend v4. It serves the policy at `https://scrollcount-friends.actionbridge.workers.dev/privacy` and deletion at `https://scrollcount-friends.actionbridge.workers.dev/delete`. Use these URLs only after deployment and successful public checks. Operator/effective date/retention are included; confirm deployed observability is disabled and D1 recovery settings match the policy. See PLAY-REVIEW-PACK.md.
3. Complete Data safety accurately for optional sharing as well as offline counting. Names, identifiers, friendship/invitation metadata, public keys and encrypted daily snapshots reach the backend. Encryption alone does not exempt collection. Describe actual purposes, optionality, deletion and service providers.
4. Complete the Accessibility API declaration and demonstration video: show the separate prominent disclosure, explicit agreement, Android settings enablement and counting. This counter is not primarily a disability support tool; retain `isAccessibilityTool=false`. Play review determines eligibility.
5. Use `branding/ScrollCount-Play-icon-512.png`. Capture real phone screenshots and prepare a 1024×500 feature graphic. Include useful Today/Insights/setup/Friends views.
6. Add the Play app-signing certificate to backend App Links and redeploy. Test invitation confirmation, registration, sharing pause/delete and offline recovery on two phones.
7. Test Android 16, supported older versions, large text, screen-off/reboot, fast scrolling and battery impact. Signing/distribution does not guarantee Android/OEM Accessibility access or Play approval.

Full Gradle/lint/APK/AAB verification remains pending the first successful GitHub run. The ZIP contains no private key or completed operator details. Review `TEST-GUIDE.md` for device checks.

Official references: [target API](https://support.google.com/googleplay/android-developer/answer/11926878), [Accessibility API](https://support.google.com/googleplay/android-developer/answer/10964491), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [account deletion](https://support.google.com/googleplay/android-developer/answer/13327111), [store assets](https://support.google.com/googleplay/android-developer/answer/9866151).
