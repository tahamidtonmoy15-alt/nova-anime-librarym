# NOVA — Native Android (v1.0.1)

NOVA-র ওয়েব UI + VLC-র মতো native ভিডিও ইঞ্জিন (LibVLC) + ব্যাকআপ হিসেবে Media3/ExoPlayer।

## ভিডিও ইঞ্জিন
- **LibVLC 3.6.5** (VLC-র নিজস্ব ইঞ্জিন): MKV, AVI, MP4, MOV, TS, FLV, WebM, HEVC, AV1 ইত্যাদি অধিকাংশ ফরম্যাট।
- **Media3/ExoPlayer**: LibVLC ব্যর্থ হলে নিজে থেকে চালু হয়।
- Player-এ আছে: Play/Pause, Seek bar, ±10 সেকেন্ড, গতি (0.5x–2x), Audio track, Subtitle track, Aspect (Fit/16:9/4:3/Fill)।
- ফোনের Gallery/File Manager থেকে ভিডিও-তে ট্যাপ করে "Open with → NOVA" দিলেও চলবে।

## অ্যাপের নিচের বাটন
- **▶ Play File** — কপি ছাড়াই সরাসরি ফোনের ভিডিও চালায়।
- **＋ Import** — ভিডিও NOVA-র private folder-এ কপি করে (ব্যাকগ্রাউন্ডে, অ্যাপ হ্যাং হবে না)।
- **My Videos** — Import করা ভিডিও Play / Delete।
- **Reels** — উপর-নিচ swipe করে Import করা ভিডিও দেখা।
- **Storage** — NOVA-র ব্যবহার করা জায়গা।

## APK বানানো (ফোন থেকেই সম্ভব)
1. GitHub-এ নতুন repository খোলো (Private রাখলে শুধু তুমি দেখবে)।
2. ZIP extract করে `NOVA_Android_Native_Project`-এর **ভেতরের সব ফাইল ও ফোল্ডার** repo-র root-এ upload করো (`.github` ফোল্ডারসহ)।
3. Repo → **Actions** → **NOVA Android APK** → **Run workflow**।
4. সবুজ ✓ হলে run খুলে নিচে **Artifacts → NOVA-debug-APK** ডাউনলোড করো।
5. ZIP খুলে `app-debug.apk` বের করো।

## ফোনে Install
1. `app-debug.apk` ট্যাপ করো।
2. "Install unknown apps" চাইলে Chrome/Files-কে অনুমতি দাও।
3. Install → Open।
(Play Protect সতর্ক করলে "Install anyway" — কারণ এটা তোমার নিজের debug build।)

## Build ব্যর্থ হলে
Actions-এ লাল ✗ run খুলে ব্যর্থ step-এর শেষ ~30 লাইন কপি করে আমাকে পাঠাও, আমি ঠিক করে দেব।
- `libvlc-all:3.6.5` না পেলে `app/build.gradle.kts`-এ ওটা `3.6.2` করে দেখো।

## সীমাবদ্ধতা (সততার সাথে)
- এই কোড এখানে compile/test করা যায়নি; প্রথম Actions build-ই আসল পরীক্ষা।
- কোনো প্লেয়ারই DRM-সুরক্ষিত বা নষ্ট ফাইলে ১০০% গ্যারান্টি দিতে পারে না।
- Debug APK শুধু নিজের ব্যবহারের জন্য; Play Store-এ দিলে signed release ও LibVLC (LGPL) লাইসেন্স শর্ত দেখতে হবে।
- ওয়েব অংশের browser-local data আর Native Import আলাদা storage।
- Build: AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, compileSdk 35, minSdk 23, ABI: arm64-v8a + armeabi-v7a।
