#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ -f web/package.json ]]; then
  echo 'Angular application already exists in web/; refusing to overwrite it.'
  exit 0
fi
if find web -mindepth 1 -maxdepth 1 -type f ! -name README.md ! -name .gitkeep | grep -q .; then
  echo 'web/ has existing files. Move them safely or add your Angular starter manually.' >&2
  exit 1
fi
if find web -mindepth 1 -type d | grep -q .; then
  echo 'web/ has existing directories. Refusing to overwrite.' >&2
  exit 1
fi
command -v npx >/dev/null || { echo 'Node.js npm/npx is required.' >&2; exit 1; }
rm -f web/README.md web/.gitkeep
npx --yes @angular/cli@22 new vizhi-tivi-web --directory web --routing --style scss --skip-git --package-manager npm
printf 'Angular app created under web/.\n'
