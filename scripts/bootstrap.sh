#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
command -v python3 >/dev/null || { echo 'Python 3 is required' >&2; exit 1; }
python3 scripts/validate-config.py
python3 -m unittest discover -s scripts/tests -v
if [[ -x android/gradlew ]]; then
  echo 'Existing Gradle wrapper available.'
elif command -v gradle >/dev/null; then
  echo 'Generating Gradle wrapper using the installed Gradle tool...'
  gradle -p android wrapper --gradle-version 9.5.0 --distribution-type bin
else
  echo 'Gradle not installed. Install Gradle 9.5.0 or open android/ in Android Studio to create a wrapper.'
  echo 'GitHub CI uses setup-gradle and works without a checked-in wrapper.'
fi
echo 'Starter setup checks passed. No Android app was built by this script.'
