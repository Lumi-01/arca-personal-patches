# Arca Live personal Morphe patches

This repository contains an experimental [Morphe](https://github.com/MorpheApp/morphe-patcher) patch bundle for the official Android package `live.arca.android.playstore`. It contains patch source only, not the vendor APK, decompiled application, signing keys, or account data. It is not affiliated with Arca Live or Morphe.

## Patches

- **앱 광고 요청 제거:** Prevents the app's `/api/v1/pagead` request at its repository call site. The separate user text-ad API, posts, comments and linked images remain available. User-created advertisements in posts are untouched.
- **분석 수집 축소:** Sets Firebase Analytics, Crashlytics and Sessions collection flags to false and removes advertising-ID permissions. Firebase Messaging and Remote Config stay installed for app features. This does not establish that every possible telemetry request is gone.
- **이미지 저장 스트리밍:** Marks the image download endpoint as streaming and copies its response to the Android media file using a 64 KiB buffer. The original Android 10+ path loaded the complete response as a byte array before writing it. This reduces peak memory use and avoids waiting for Retrofit's full-body buffer before writing. The app's serial download queue is unchanged, so a batch still saves one file at a time; network and server limits can still dominate elapsed time.

The verified baseline is app **0.9.35185** (`versionCode 125185`). Later versions are marked experimental and each required class/method anchor is checked at patch time. Review and test every app update before use. The original APK examined had SHA-256 `2057733B08DD779A6CB0BC2BC7B069F021CBCEFDED37596CC91F21FF0D004066` and publisher signing certificate SHA-1 `D7F68DAD9E0C02DCF5A4167BE13E86B5E68696B5`.

## Build and install

The [Morphe Gradle setup](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_1_setup.md) requires JDK 21, Android SDK and GitHub Packages authentication with `read:packages`.

```sh
./gradlew :patches:buildAndroid generatePatchesList
```

The resulting bundle is `patches/build/libs/patches-0.1.0.mpp`. In Morphe Manager, add `https://github.com/Lumi-01/arca-personal-patches` as a [patch source](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md), enable pre-release patches and patch your own copy of the app. The [v0.1.0 release](https://github.com/Lumi-01/arca-personal-patches/releases/tag/v0.1.0) supplies the bundle directly. Morphe's signature differs from the publisher's, so installing over the stock app may require backing up local data and uninstalling it first. Keep the same Morphe signing key for future patched updates.

## Verification and limits

Morphe Desktop 1.18.1 applied all three patches, rebuilt and signed the verified baseline. An Android 15 x86_64 emulator opened the patched app, loaded a channel list and a post with image and video, kept the text-ad row, and saved a 1.3 MB image into `Pictures/ArcaLive`. No runtime crash appeared during these flows. Login, posting, push delivery, other Android releases, batch download speed and real-device battery impact have not been verified. The source could still change between app releases, and this patch does not guarantee a speed increase.

The patches target app-owned requests. Embedded web content or user-provided links may still make their own network requests.
