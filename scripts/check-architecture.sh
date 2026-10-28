#!/usr/bin/env bash
# Executable architecture guard: the dependency rule is checked, not just documented.
# 依存関係ルールの自動チェック（ドキュメントだけでなく実行で担保）。
set -uo pipefail
cd "$(dirname "$0")/.."
status=0

expect_zero() { # description, count
  if [ "$2" -eq 0 ]; then printf '  ok    %s\n' "$1"; else printf '  FAIL  %s (%s hits)\n' "$1" "$2"; status=1; fi
}
count() { grep -rEn "$1" "${@:2}" 2>/dev/null | wc -l; }

echo "Architecture rules / アーキテクチャ規則"
expect_zero "shared/commonMain has no android/androidx/platform/cinterop/firebase imports" \
  "$(count '^import (android|androidx|platform|kotlinx\.cinterop|com\.google\.firebase)\.' shared/src/commonMain)"
expect_zero "ViewModels never import the Firebase SDK" \
  "$(grep -rl 'import com.google.firebase' composeApp/src/androidMain/kotlin --include='*ViewModel.kt' | wc -l)"
expect_zero "UI/DI code never imports Firestore or Auth directly" \
  "$(count 'import com\.google\.firebase\.(firestore|auth)' composeApp/src/androidMain/kotlin)"
expect_zero "no GlobalScope / runBlocking in production code" \
  "$(count 'GlobalScope|runBlocking' shared/src/commonMain shared/src/androidMain composeApp/src/androidMain)"
expect_zero "commonMain does not hardcode Dispatchers" \
  "$(count 'Dispatchers\.' shared/src/commonMain)"

exit $status
