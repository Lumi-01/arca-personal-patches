# Media saving, 0.3.13

The Plus Android 10+ MediaStore saver uses StreamCopy for image, GIF and video download items. The original Retrofit endpoint is marked streaming; no whole response byte array is retained. The Android <10 saver is unchanged.

The response InputStream can return one Okio segment (8 KiB) despite the caller's 64 KiB read array. Previously every such read immediately called FileOutputStream.write. A bounded BufferedOutputStream now coalesces those reads into 64 KiB output writes. Copy buffers total 128 KiB per active save, independent of file size; the existing queue remains serial. Close flushes the trailing bytes and closes the underlying stream, including failure paths. File type, progress callbacks, DNS, authentication, timeouts and network client construction are unchanged.

Run `python scripts/check-stream-copy.py`. In a 4 MiB + 17 byte fixture returning at most 8 KiB per read, 513 writes become 65, with exact byte equality. Boundary sizes, EOF flush, read/write failures and both stream closures are checked. This is not a network throughput or battery benchmark.

The 0.3.13 bundle builds and all three patches apply to Plus 0.9.32768. On the Android 15 emulator, opening an article image and choosing “이 이미지 다운로드” produces a completed 87,276-byte WebP in Pictures/ArcaLive. Its SHA-256 is identical to the same image saved by 0.3.12: `1477255bc4d98a3de1d237dd6a2ca4b8f00c423830dc07d2e471c3525cca3000`. No crash was recorded. GIF/video share the copy path but were not separately downloaded in this smoke check.

Further investigation found that the original download task creates a new Retrofit/OkHttp client with a per-item progress interceptor and a fresh custom DNS instance for every item. Sharing a pool alone would not ensure HTTPS reuse because the TLS and DNS identities also participate in connection compatibility. This release does not change those objects or claim that all download latency is fixed. Any future reuse change must preserve per-item progress, original TLS verification, headers, DNS fallback and cancellation.
