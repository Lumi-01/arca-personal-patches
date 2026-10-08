# Arca Live personal Morphe patches

This repository contains experimental [Morphe](https://github.com/MorpheApp/morphe-patcher) patches for **Arca Live Plus** (`live.arca.android`) and the Play Store app (`live.arca.android.playstore`). It contains patch source only, not the vendor APK, decompiled application, signing keys, or account data. It is not affiliated with Arca Live or Morphe.

## Patches

- **앱 광고 요청·영역 제거:** Prevents the app's `/api/v1/pagead` request and skips the 105 dp ad component that otherwise shows the `¡Hola!` default image or leaves a blank space. The separate user text-ad API, posts, comments and linked images remain available. User-created advertisements in posts are untouched.
- **분석 수집·백그라운드 작업 축소:** Sets Firebase Analytics, Crashlytics and Sessions collection flags to false, disables analytics-only measurement and session services/jobs, and removes advertising-ID and install-referrer permissions. Firebase Messaging and Remote Config remain for notifications and app settings. This avoids those background entry points but does not establish that every possible telemetry request is gone or quantify battery savings.
- **이미지 저장 스트리밍:** Marks the image download endpoint as streaming and copies its response to the Android media file using a 64 KiB buffer. The original Android 10+ path loaded the complete response as a byte array before writing it. The Plus build's legacy Android saver already streams. This reduces peak memory use and avoids waiting for Retrofit's full-body buffer before writing. The app's serial download queue is unchanged, so a batch still saves one file at a time; network and server limits can still dominate elapsed time.

Verified baselines are **Plus 0.9.32768** (`versionCode 122768`, original APK SHA-256 `0EBA2DCB598B9F3AB6720266DF1E4E822D3A9522AED43479F79AE3C8BA2E0AB4`) and **Play Store 0.9.35185** (`versionCode 125185`, original APK SHA-256 `2057733B08DD779A6CB0BC2BC7B069F021CBCEFDED37596CC91F21FF0D004066`). Both originals have publisher signing certificate SHA-1 `D7F68DAD9E0C02DCF5A4167BE13E86B5E68696B5`. Later versions are marked experimental and each required class/method anchor is checked at patch time. Review and test every app update before use.

## Build and install

The [Morphe Gradle setup](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_1_setup.md) requires JDK 21, Android SDK and GitHub Packages authentication with `read:packages`.

```sh
./gradlew :patches:buildAndroid generatePatchesList
```

The resulting bundle is `patches/build/libs/patches-0.2.0.mpp`. In Morphe Manager, add `https://github.com/Lumi-01/arca-personal-patches` as a [patch source](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md) and patch your own copy of the app. The [v0.2.0 release](https://github.com/Lumi-01/arca-personal-patches/releases/tag/v0.2.0) supplies the bundle directly. Morphe's signature differs from the publisher's, so installing over the stock app may require backing up local data and uninstalling it first. Keep the same Morphe signing key for future patched updates.

When publishing a new `patches-bundle.json`, keep `created_at` in Morphe's local date-time format (`YYYY-MM-DDTHH:mm:ss`, without `Z` or an offset). Morphe Manager 1.34.0 cannot parse a UTC suffix in this field and then reports that the bundle could not be downloaded.

## Verification and limits

Morphe Desktop 1.18.1 applied the three compatible patches to each baseline and rebuilt and signed them. On an Android 15 x86_64 emulator, Plus opened a channel and post, kept the text-ad row, removed the `¡Hola!` area without a blank gap, and saved a 123 KB image into `Pictures/ArcaLive`. The Play Store baseline was rebuilt after compatibility changes. No runtime crash appeared during the tested Plus flows. Login, posting, push delivery, other Android releases, batch download speed and real-device battery impact have not been verified. The source could still change between app releases, and this patch does not guarantee a speed increase or a measurable battery improvement.

The patches target app-owned requests. Embedded web content or user-provided links may still make their own network requests.
