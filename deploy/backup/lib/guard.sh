#!/usr/bin/env bash
#
# guard.sh
#
# Small, sourced safety helper shared by every backup/DR script. It has no side effects on
# source: it only defines functions. Source it, then call the guards you need.
#
#   source "<dir>/lib/guard.sh"
#   guard_mountpoint "$BACKUP_MOUNT"
#
# The functions call `exit` (not `return`) on violation: these are hard, fail-closed invariants,
# so a caller that forgets to check the return value still aborts. Source this into a script that
# runs under `set -euo pipefail`.

# guard_mountpoint <path>
#
# Cross-cutting safety invariant: refuse to proceed unless <path> is an actual mountpoint. This
# prevents ever writing backups into a plain directory on the root filesystem when the external
# backup drive is not mounted (which would silently fill the OS disk and produce no real backup).
#
# Exits non-zero if <path> is empty, does not exist, or is not a mountpoint.
guard_mountpoint() {
  local path="${1:-}"

  if [[ -z "$path" ]]; then
    echo "ERROR: guard_mountpoint requires a path argument." >&2
    exit 1
  fi

  if [[ ! -d "$path" ]]; then
    echo "ERROR: backup path '$path' does not exist." >&2
    exit 1
  fi

  # `mountpoint -q` is the authoritative check: it is true only when <path> is the root of a
  # mounted filesystem, not merely an existing directory.
  if ! mountpoint -q "$path"; then
    echo "ERROR: backup path '$path' is not a mountpoint. Refusing to write." >&2
    echo "       Mount the backup drive there and retry." >&2
    exit 1
  fi
}
