#!/usr/bin/env bash
# Checks that a pull request description declares its upgrade actions (#503).
#
# The description is read from the PR_BODY environment variable, never from arguments, and is only
# ever compared as text: it is written by whoever opens the pull request.
#
# The section starts at a "## Upgrade actions" line (any case, surrounding whitespace allowed) and
# ends at the next level-1 or level-2 heading, or at the end of the description; deeper headings
# belong to it. HTML comments are removed from the whole description first, so neither the
# template's instructions nor a heading inside a comment count. The check passes when anything
# other than whitespace is left in the section ("None" being the common case), and fails otherwise.

set -euo pipefail

readonly README_URL='https://github.com/lucoenergia/conluz-infra/blob/main/docs/deployment/upgrade-actions/README.md'
readonly SECTION_START='^[[:space:]]*##[[:space:]]+upgrade actions[[:space:]]*$'
readonly SECTION_END='^[[:space:]]*#{1,2}([[:space:]]|$)'

fail() {
  echo "::error title=Upgrade actions::$1"
  echo "$1" >&2
  echo "Under a '## Upgrade actions' heading, write 'None', or describe the manual step an operator" >&2
  echo "must take to upgrade (what, before or after the new version starts, whether it is required," >&2
  echo "how to check it) and register it in lucoenergia/conluz-infra, following ${README_URL}" >&2
  echo "Editing the description re-runs this check; no new commit is needed." >&2
  exit 1
}

# Descriptions written on github.com end their lines with CRLF. Every pattern below treats the
# carriage return as [[:space:]], so it needs no separate handling.
body="${PR_BODY:-}"

while [[ $body == *'<!--'* ]]; do
  before="${body%%'<!--'*}"
  after="${body#*'<!--'}"
  if [[ $after == *'-->'* ]]; then
    body="${before}${after#*'-->'}"
  else
    # GitHub hides everything after an unclosed comment.
    body="$before"
  fi
done

shopt -s nocasematch
found=false
section=''
while IFS= read -r line || [[ -n $line ]]; do
  if [[ $found == false ]]; then
    if [[ $line =~ $SECTION_START ]]; then
      found=true
    fi
  elif [[ $line =~ $SECTION_END ]]; then
    break
  else
    section+="$line"
  fi
done <<< "$body"

if [[ $found == false ]]; then
  fail "The pull request description has no '## Upgrade actions' section."
fi
if [[ -z ${section//[[:space:]]/} ]]; then
  fail "The '## Upgrade actions' section of the pull request description is empty."
fi

echo "The pull request description declares its upgrade actions."
