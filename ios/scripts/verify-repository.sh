#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."

readonly in_progress_count="$(awk -F'|' '$2 ~ /S0[0-8]/ && $0 ~ /`in_progress`/ { count++ } END { print count + 0 }' ../docs/ios/roadmap.md)"
test "${in_progress_count}" -eq 1
readonly active_branch="$(awk -F'|' '$2 ~ /S0[0-8]/ && $4 ~ /`in_progress`/ { value = $5; gsub(/[`[:space:]]/, "", value); print value }' ../docs/ios/roadmap.md)"
test -n "${active_branch}"
grep -Fq -- "- Active branch: \`${active_branch}\`" ../docs/ios/memory.md

for slice in $(seq -f '%02g' 0 8); do
  test -f "../docs/ios/slices/S${slice}.md"
done
test -f Playboard.xcodeproj/xcshareddata/xcschemes/Playboard.xcscheme
test -f Playboard.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved
python3 scripts/verify_documentation.py

if find . -type f \( -name 'GoogleService-Info.plist' -o -name '*.mobileprovision' -o -name '*.p12' -o -name '*.p8' -o -name '*.cer' -o -name '*.key' \) -print -quit | grep -q .; then
  echo "Forbidden provider or signing file found." >&2
  exit 1
fi

python3 scripts/verify_no_secrets.py . ../docs/ios ../codemagic.yaml

echo "Repository controls verified; inspect 'git status --short' before staging paths explicitly."
