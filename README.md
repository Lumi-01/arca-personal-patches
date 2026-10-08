# Arca Live personal Morphe patches

This repository contains experimental [Morphe](https://github.com/MorpheApp/morphe-patcher) patches for **Arca Live Plus** (`live.arca.android`) and the Play Store app (`live.arca.android.playstore`). It contains patch source only, not the vendor APK, decompiled application, signing keys, or account data. It is not affiliated with Arca Live or Morphe.

## Patches

- **Morphe 설정·광고·화면 전환:** Adds a separate **설정 → Morphe 설정** row. Its runtime switches control the app-owned `/api/v1/pagead` request and its `¡Hola!` slot, shared Compose navigation, and (if installed) the optional Activity animation patch below. Compose navigation uses a 280 ms balanced slide. Turning the motion switch off restores the bundled 250 ms effects. The switch reads an in-memory value rather than disk on each animation frame. The separate user text-ad API, posts, comments and linked images remain available. User-created advertisements in posts are untouched. When the ad switch is off, the original ad slot returns.
- **화면 전환 애니메이션 조정 (experimental, off by default):** Replaces the four stock Activity slide resources with a 300 ms fast-out-slow-in slide and packages copies of the stock resources for the in-app off switch. It adds no repeating animation or background task. Normal channel-to-post navigation occurs inside MainActivity and does not use these resources; separate Activity entry points can use them. This patch does not change network loading.
- **게시글 화면 경량화 (experimental, off by default):** Changes the article Activity's translucent manifest theme to the app's non-translucent theme. The article code also sets a transparent decor background. Emulator comparisons did not establish a rendering or jank benefit, so the theme change is opt-in at patch time and cannot be changed by the in-app switch or by restarting the app.
- **분석 수집·백그라운드 작업 축소:** Sets Firebase Analytics, Crashlytics and Sessions collection flags to false, disables analytics-only measurement and session services/jobs, and removes advertising-ID and install-referrer permissions. Firebase Messaging and Remote Config remain for notifications and app settings. This avoids those background entry points but does not establish that every possible telemetry request is gone or quantify battery savings.
- **미디어 저장 스트리밍:** Marks the shared media download endpoint as streaming and copies its response to the Android media file using a 64 KiB buffer. The Android 10+ save path is shared by image, GIF and video download items; the original path loaded the complete response as a byte array before writing it. The Plus build's legacy Android saver already streams. The copy now closes both streams even if writing fails. This reduces peak memory use and avoids waiting for Retrofit's full-body buffer before writing. It does not alter playback, previews, or the serial download queue; network and server limits can still dominate elapsed time.

Verified baselines are **Plus 0.9.32768** (`versionCode 122768`, original APK SHA-256 `0EBA2DCB598B9F3AB6720266DF1E4E822D3A9522AED43479F79AE3C8BA2E0AB4`) and **Play Store 0.9.35185** (`versionCode 125185`, original APK SHA-256 `2057733B08DD779A6CB0BC2BC7B069F021CBCEFDED37596CC91F21FF0D004066`). Both originals have publisher signing certificate SHA-1 `D7F68DAD9E0C02DCF5A4167BE13E86B5E68696B5`. Later versions are marked experimental and each required class/method anchor is checked at patch time. Review and test every app update before use.

## Build and install

The [Morphe Gradle setup](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_1_setup.md) requires JDK 21, Android SDK and GitHub Packages authentication with `read:packages`.

```sh
./gradlew :patches:buildAndroid generatePatchesList
```

The resulting bundle is `patches/build/libs/patches-0.3.2.mpp`. In Morphe Manager, add `https://github.com/Lumi-01/arca-personal-patches` as a [patch source](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md) and patch your own copy of the app. The [v0.3.2 release](https://github.com/Lumi-01/arca-personal-patches/releases/tag/v0.3.2) supplies the bundle directly. Morphe's signature differs from the publisher's, so installing over the stock app may require backing up local data and uninstalling it first. Keep the same Morphe signing key for future patched updates.

When publishing a new `patches-bundle.json`, keep `created_at` in Morphe's local date-time format (`YYYY-MM-DDTHH:mm:ss`, without `Z` or an offset). Morphe Manager 1.34.0 cannot parse a UTC suffix in this field and then reports that the bundle could not be downloaded.

## Verification and limits

Morphe Desktop 1.18.1 applied the three default patches and, separately, the optional Activity animation patch to each baseline. On an Android 15 x86_64 emulator, both apps opened a channel and post; Plus kept the text-ad row, removed the `¡Hola!` area without a blank gap, and saved a 123 KB image into `Pictures/ArcaLive`. The Morphe settings page opened without clipping and persisted the motion switch across an app restart. The optional native animation resource patch rebuilt successfully for both baselines. Emulator frame measurements for the optional opaque article theme, native animation changes and alternative fades showed no reliable improvement or were worse than baseline, so the optional patches are off by default. The user's reported stutter occurs on a real phone and cannot be considered fixed from emulator results alone. Article content can still have a short blank/loading period before network data arrives. Login, posting, push delivery, other Android releases, batch download speed and real-device battery impact have not been verified. The source could still change between app releases, and this patch does not guarantee a speed increase or a measurable battery improvement.

The patches target app-owned requests. Embedded web content or user-provided links may still make their own network requests.
