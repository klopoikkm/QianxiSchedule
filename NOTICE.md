# Acknowledgements

The Northeastern University adapter was validated against publicly documented
2026 timetable fields, endpoints and period times from these MIT-licensed
projects:

- [PopulusYang/NeuTimetable](https://github.com/PopulusYang/NeuTimetable)
- [PeterPtroc/neu-jwxt-to-wakeup](https://github.com/PeterPtroc/neu-jwxt-to-wakeup)

Qianxi Schedule contains an independently implemented Android/WebView adapter;
no account credentials are collected or sent to a Qianxi Schedule server.

The Northeast University at Qinhuangdao EAMS endpoint and request parameter
structure was cross-checked against the public
[TsiaohanWang/neuq-jwxt-rs](https://github.com/TsiaohanWang/neuq-jwxt-rs)
implementation. The Android/WebView adapter is independently implemented and
does not collect or transmit the user's EAMS password.

The app bundles jQuery 1.7.2 (MIT License) as a compatibility fallback for
legacy EAMS pages whose remote jQuery resource cannot be loaded by Android
WebView. Copyright 2012 jQuery Foundation and other contributors.

The background editor uses AndroidX ExifInterface 1.4.2 to read photo
orientation on Android 8. Copyright The Android Open Source Project.
It is distributed under the Apache License, Version 2.0; the license and
attribution are included in the APK under `assets/licenses/`.
