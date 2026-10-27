#!/usr/bin/env bash
# Tests for check-upgrade-actions.sh (#503). Run with: bash .github/scripts/test-check-upgrade-actions.sh

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly CHECK="$script_dir/check-upgrade-actions.sh"
readonly TEMPLATE="$script_dir/../pull_request_template.md"
readonly NO_HEADING="has no '## Upgrade actions' section"
readonly EMPTY="section of the pull request description is empty"

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

failures=0
total=0

# expect <pass|fail> <name> <body> [fragment the output must contain]
expect() {
  local expected="$1" name="$2" body="$3" fragment="${4:-}"
  local output status=0
  output="$(PR_BODY="$body" bash "$CHECK" 2>&1)" || status=$?
  check_result "$expected" "$name" "$status" "$output" "$fragment"
}

indent() {
  local line
  while IFS= read -r line; do
    echo "    $line"
  done <<< "$1"
}

check_result() {
  local expected="$1" name="$2" status="$3" output="$4" fragment="$5"
  local actual=pass
  total=$((total + 1))
  [[ $status -eq 0 ]] || actual=fail
  if [[ $actual != "$expected" ]]; then
    echo "FAIL: $name (expected $expected, got $actual, exit $status)"
    indent "$output"
    failures=$((failures + 1))
  elif [[ -n $fragment && $output != *"$fragment"* ]]; then
    echo "FAIL: $name (output does not contain: $fragment)"
    indent "$output"
    failures=$((failures + 1))
  else
    echo "ok:   $name"
  fi
}

template_comment='<!-- Upgrade actions: mandatory, checked by CI. Write "None", or describe each manual step. -->'
template_multiline_comment='<!-- Upgrade actions: mandatory, checked by CI. Write "None", or describe
     each manual step an operator must take when upgrading.
     ## Not a heading: this line is inside the comment. -->'

expect fail "no heading" $'## Summary\n\nSomething changed.' "$NO_HEADING"
expect fail "heading with nothing under it" '## Upgrade actions' "$EMPTY"
expect fail "heading with only whitespace" $'## Upgrade actions\n\n   \n\t\n' "$EMPTY"
expect fail "heading with only the single-line template comment" \
  $'## Upgrade actions\n\n'"$template_comment"$'\n' "$EMPTY"
expect fail "heading with only the multi-line template comment" \
  $'## Upgrade actions\n\n'"$template_multiline_comment"$'\n\n## Frontend impact\n\nNone' "$EMPTY"
expect fail "heading only inside an HTML comment" \
  $'## Summary\n\n<!--\n## Upgrade actions\nNone\n-->\n' "$NO_HEADING"
expect fail "unclosed comment hides the rest of the section" \
  $'## Upgrade actions\n\n<!-- unclosed\nNone' "$EMPTY"
expect pass "None" $'## Upgrade actions\n\nNone'
expect pass "none" $'## Upgrade actions\n\nnone'
expect pass "None after the template comment" \
  $'## Upgrade actions\n\n'"$template_multiline_comment"$'\n\nNone\n\n## Frontend impact\n'
expect pass "a real action description" \
  $'## Upgrade actions\n\nBefore starting the new version, set the new mandatory variable CONLUZ_EXAMPLE. Required. Check that the backend starts.'
expect pass "a ### subheading inside the section" $'## Upgrade actions\n\n### Before the upgrade\nSet CONLUZ_EXAMPLE.'
expect pass "a ### subheading alone inside the section" $'## Upgrade actions\n### Before the upgrade\n'
expect fail "content under the next ## heading does not count" \
  $'## Upgrade actions\n\n## Frontend impact\n\nRegenerate the client.' "$EMPTY"
expect fail "content under the next # heading does not count" \
  $'## Upgrade actions\n\n# Appendix\n\nNotes.' "$EMPTY"
expect pass "heading in upper case" $'## UPGRADE ACTIONS\n\nNone'
expect pass "heading in mixed case with surrounding whitespace" $'  ## Upgrade Actions  \n\nNone'
expect fail "a different heading that starts the same" $'## Upgrade actions taken\n\nNone' "$NO_HEADING"
expect pass "CRLF line endings" $'## Summary\r\n\r\nText.\r\n\r\n## Upgrade actions\r\n\r\nNone\r\n\r\n## Frontend impact\r\n'
expect fail "CRLF line endings with an empty section" $'## Upgrade actions\r\n\r\n## Frontend impact\r\nNone\r\n' "$EMPTY"
expect fail "empty PR_BODY" '' "$NO_HEADING"

status=0
output="$(env -u PR_BODY bash "$CHECK" 2>&1)" || status=$?
check_result fail "unset PR_BODY" "$status" "$output" "$NO_HEADING"

marker="$work_dir/executed"
# shellcheck disable=SC2016 # the expansions must reach the check unexpanded
metacharacters='$(touch '"$marker"') `touch '"$marker"'` ; touch '"$marker"' && touch '"$marker"' | touch '"$marker"
expect pass "shell metacharacters are text" $'## Upgrade actions\n\n'"$metacharacters"
expect fail "shell metacharacters outside the section are text" $'## Summary\n\n'"$metacharacters" "$NO_HEADING"
total=$((total + 1))
if [[ -e $marker ]]; then
  echo "FAIL: shell metacharacters were executed"
  failures=$((failures + 1))
else
  echo "ok:   shell metacharacters were not executed"
fi

template="$(cat "$TEMPLATE")"
expect fail "the untouched pull request template" "$template" "$EMPTY"
filled="$(sed 's/^## Upgrade actions$/## Upgrade actions\n\nNone/' "$TEMPLATE")"
expect pass "the pull request template with None filled in" "$filled"

echo
echo "$((total - failures))/$total passed"
[[ $failures -eq 0 ]]
