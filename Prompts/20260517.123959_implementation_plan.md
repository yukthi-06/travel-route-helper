# Implementation Plan: Update Storage Directory

**Date:** 2026-05-17
**Task:** Move the project settings and data folder base from `/sdcard/Downloads/Travel_Route_Helper` to `/sdcard/Vypeensoft/Travel_Route_Helper`.

## Files Affected
1. `app/src/main/java/com/vypeensoft/routehelper/utils/FileUtils.java`

## Changes Made
- Located the `getRoutesDirectory()` method in `FileUtils.java`.
- Identified that the original code was using `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)`.
- Replaced this implementation to use `Environment.getExternalStorageDirectory()` as the base (`/sdcard/`).
- Appended a new subdirectory `"Vypeensoft"` to the base path.
- Ensured the `Travel_Route_Helper` directory is created inside the `Vypeensoft` directory.
- The new data path logic is now fully updated and operational.
