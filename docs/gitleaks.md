# Secret scanning with gitleaks

This repository is **public** and world-readable, so any secret that is ever committed must be
treated as compromised and rotated immediately. To stop a real secret from entering git in the
first place, a [gitleaks](https://github.com/gitleaks/gitleaks) scan runs as a `pre-commit`
hook. Real secret values live only in gitignored `.env` files on the host; committed files use
`${VAR}` interpolation and `*.env.example` templates. The hook is the backstop if one is ever
accidentally staged.

## Install & enable (one time, per clone)

On Ubuntu/Debian, install both tools from the distro (avoids the PEP 668
`externally-managed-environment` error from `pip install`, and needs no Go toolchain):

```bash
sudo apt install pre-commit gitleaks
pre-commit install            # writes .git/hooks/pre-commit
```

Other platforms: `brew install pre-commit gitleaks`, or `pipx install pre-commit` +
download the gitleaks binary from its releases page.

`.pre-commit-config.yaml` invokes the **system** `gitleaks` binary (`language: system`), so
every `git commit` scans the staged changes and **blocks the commit** if a secret is detected.

## Run it manually

```bash
pre-commit run gitleaks --all-files    # via the framework
gitleaks protect --staged --redact     # scan staged changes directly (gitleaks 8.16–8.18)
```

> Version note: `gitleaks protect --staged` is the pre-commit subcommand in gitleaks
> **8.16–8.18** (the current apt version). In **8.19+** it becomes `gitleaks git --staged`;
> update the `entry:` in `.pre-commit-config.yaml` if you upgrade.

## Full-history audit

`pre-commit` scans commits going forward. To audit the **entire existing history**:

```bash
# with the installed binary (gitleaks 8.16–8.18):
gitleaks detect --source . --log-opts="--all" --redact --verbose

# or without installing anything, via Docker:
docker run --rm -v "$PWD:/repo" ghcr.io/gitleaks/gitleaks:v8.18.4 \
  detect --source /repo --log-opts="--all" --redact --verbose
```

A clean run exits 0 with "no leaks found". If it reports a real finding on a **public** repo,
**stop and rotate the credential immediately** — do not rewrite history unilaterally;
coordinate with the repo owner (history rewriting is a separate, human decision that does not
by itself undo the exposure).

## The public/private boundary

Production topology and operational tooling — real hostnames, filesystem paths, community
names, CUPS codes, backup schedules, credentials — live in the **private `conluz-infra`
repository**, never here. `deploy/` in this repo is a **sanitized reference example** only.
See the "Deployment & infrastructure boundary" section in `CLAUDE.md`.
