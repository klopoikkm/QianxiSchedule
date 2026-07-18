# Changelog

## 1.2.1

- Made the settings screen vertically scrollable while keeping its toolbar fixed.
- Kept permissions, backup controls and app information reachable on compact phones.

## 1.2.0

- Added a next-class summary to the timetable home screen.
- Added local JSON backup export/import with validation and merge/replace restore.
- Extended generic import scanning for SPA JSON state, data attributes and common grid IDs.
- Added backup parser regression tests.

## 1.1.0

- Redesigned the timetable for seven-day, no-horizontal-scroll phone layouts.
- Added a dedicated Northeastern University 2026 academic-system adapter.
- Added NEU term and campus discovery through the current WebView login session.
- Added the verified NEU 12-period timetable from 08:30 through 22:10.
- Added named custom academic-system URLs with adapter binding and local reuse.
- Added automatic adapter detection for `jwxt.neu.edu.cn`.
- Added asynchronous import progress, timeout handling and actionable errors.
- Added structured fields, table `rowspan` support and equivalent-session merging.

## 1.0.0

- Initial weekly timetable, course editing and local SQLite storage.
- Generic educational-administration page import.
- Overlap-safe automatic silent mode with original ringer restoration.
