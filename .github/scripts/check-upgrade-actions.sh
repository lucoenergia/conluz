#!/usr/bin/env bash
# Checks that a pull request description declares its upgrade actions (#503).
#
# The description is read from the PR_BODY environment variable, never from arguments, and is only
# ever compared as text: it is written by whoever opens the pull request.
#
# The section starts at a "## Upgrade actions" line (any case, surrounding whitespace allowed) and
# ends at the next level-1 or level-2 heading, or at the end of the description; deeper headings
# belong to it. The check passes when anything other than whitespace is left in the section
# ("None" being the common case), and fails otherwise.
#
# What GitHub does not render as markup is not read as markup, so the result matches what a reader
# sees:
# - An HTML comment is removed when its "<!--" starts a line (after optional whitespace), as GitHub
#   hides those, up to the first "-->" or, when unclosed, to the end of the description. So neither
#   the template's instructions nor a heading inside a comment count. A "<!--" in the middle of a
#   line, such as one quoted in inline code, is text (#511 was failed by one).
# - Inside a fenced code block (``` or ~~~), lines are text: they are neither headings nor comments.

set -euo pipefail

readonly README_URL='https://github.com/lucoenergia/conluz-infra/blob/main/docs/deployment/upgrade-actions/README.md'
readonly SECTION_START='^[[:space:]]*##[[:space:]]+upgrade actions[[:space:]]*$'
readonly SECTION_END='^[[:space:]]*#{1,2}([[:space:]]|$)'
readonly COMMENT_START='^[[:space:]]*<!--'
readonly BACKTICK_FENCE_START='^[[:space:]]*```'
readonly BACKTICK_FENCE_END='^[[:space:]]*```+[[:space:]]*$'
readonly TILDE_FENCE_START='^[[:space:]]*~~~'
readonly TILDE_FENCE_END='^[[:space:]]*~~~+[[:space:]]*$'

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

shopt -s nocasematch
in_comment=false
fence_end=''
found=false
section=''
while IFS= read -r line || [[ -n $line ]]; do
  if [[ -n $fence_end ]]; then
    if [[ $line =~ $fence_end ]]; then
      fence_end=''
    fi
  else
    while true; do
      if [[ $in_comment == true ]]; then
        if [[ $line == *'-->'* ]]; then
          line="${line#*'-->'}"
          in_comment=false
        else
          line=''
          break
        fi
      elif [[ $line =~ $COMMENT_START ]]; then
        line="${line#*'<!--'}"
        in_comment=true
      else
        break
      fi
    done

    if [[ $line =~ $BACKTICK_FENCE_START ]]; then
      fence_end="$BACKTICK_FENCE_END"
    elif [[ $line =~ $TILDE_FENCE_START ]]; then
      fence_end="$TILDE_FENCE_END"
    elif [[ $found == false ]]; then
      if [[ $line =~ $SECTION_START ]]; then
        found=true
      fi
      continue
    elif [[ $line =~ $SECTION_END ]]; then
      break
    fi
  fi

  if [[ $found == true ]]; then
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
