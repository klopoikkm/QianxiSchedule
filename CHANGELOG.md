# Changelog

## 1.7.0

- Removed fixed school URLs so any academic-system address can be entered or saved.
- Removed hostname restrictions from specialized Northeast University adapters.
- Detected compatible EAMS and JWAPP systems from URL paths on custom domains.
- Changed all adapters to scan only the timetable page opened by the user.
- Added WebView connection, TLS and render-process diagnostics with generic-page fallback.

## 1.6.0

- Fixed legacy JavaScript menus, popup windows and target-window form navigation.
- Unified Northeast University adapters into a single visible choice.
- Applied system-bar insets to every screen.

## 1.5.0

- Added a dedicated Northeastern University at Qinhuangdao EAMS adapter.
- Discovered the internal student id and current semester from the authenticated course-table page.
- Requested and rendered the EAMS personal timetable from the home page login session.
- Warned before saving a manually edited course that overlaps an existing active week.
- Reported existing-schedule conflicts in the import preview.
- Distinguished odd/even weeks, irregular week masks and adjacent time ranges.

## 1.4.0

- Rebuilt generic table scanning as a logical grid with rowspan and colspan support.
- Detected weekday columns from table headers before using positional fallback.
- Added adapter, page, table, iframe and candidate diagnostics to empty import errors.

## 1.3.0

- Kept weekday and date headers fixed while the timetable body scrolls vertically.
- Preserved seven-day phone layouts, today highlighting and body hit testing.

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
