#!/usr/bin/env bash
# Run with your phone connected and USB debugging authorized. No state reset or content capture.
set -euo pipefail
profile_dir="${1:-build/device-profile/$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$profile_dir"
adb shell dumpsys meminfo in.scrollcount.probe > "$profile_dir/memory.txt"
adb shell dumpsys batterystats in.scrollcount.probe > "$profile_dir/battery.txt"
echo "Saved app memory and battery reports to $profile_dir"
echo 'Collect before and after normal Reels/Shorts use; battery impact still requires device measurement.'
