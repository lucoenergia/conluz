# Compromised-password list

A password that is set is refused when it appears, compared case-insensitively, in either file of this directory
(#614). Both files are loaded once at startup, and the application does not start if either is missing or empty.

Both files have the same format: UTF-8, one entry per line, each line ending in a line feed, lowercase, no blank
lines and no comments. The application lowercases a candidate password with `Locale.ROOT` before looking it up, so an
entry that is not lowercase would never match.

## `seclists-top-1m-min10.txt`

| | |
| --- | --- |
| Source | [SecLists](https://github.com/danielmiessler/SecLists), `Passwords/Common-Credentials/10-million-password-list-top-1000000.txt` |
| Upstream commit | `2d5dc7504a40962c53f932a6d9d5ece4b213dfc6` |
| Raw URL | https://raw.githubusercontent.com/danielmiessler/SecLists/2d5dc7504a40962c53f932a6d9d5ece4b213dfc6/Passwords/Common-Credentials/10-million-password-list-top-1000000.txt |
| Upstream file | 999,998 lines, 8,529,108 bytes, SHA-256 `1843265e3860a97f417b2236dfa332a0d93b38efef0fee1a0a291fdba5458478` |
| Filter | entries of at least 10 Unicode code points, the policy's minimum: a shorter one is refused as `TOO_SHORT` before the list is consulted |
| Entries | 112,130 |
| Size | 1,412,813 bytes |
| SHA-256 | `b36b0598c67b66edcf3c49b997904b3da0d32674053e0a8545b5a546709bd995` |

Upstream removed this file from its default branch in commit `4ff3ff8ef6b1c429db800e4a9b9deeac7425c5b6`, as a
duplicate of `Passwords/Common-Credentials/xato-net-10-million-passwords-1000000.txt`. That is why the commit pinned
here is the parent of the removal. The two upstream files are not byte-identical, so switching to the `xato-net` file
is a change of source: evaluate it on the next refresh of this list.

To regenerate the file byte for byte (Python 3, run from this directory):

```bash
curl -sfLo upstream.txt https://raw.githubusercontent.com/danielmiessler/SecLists/2d5dc7504a40962c53f932a6d9d5ece4b213dfc6/Passwords/Common-Credentials/10-million-password-list-top-1000000.txt
python3 -c 'import sys; e = {l.lower() for l in open(sys.argv[1], encoding="utf-8").read().split("\n") if len(l) >= 10}; open(sys.argv[2], "w", encoding="utf-8", newline="\n").write("".join(x + "\n" for x in sorted(e)))' upstream.txt seclists-top-1m-min10.txt
sha256sum seclists-top-1m-min10.txt
rm upstream.txt
```

That is: split on line feeds; keep the lines of at least 10 code points (`len` of a Python string counts code
points); lowercase them; remove duplicates; sort by code point, which for this all-ASCII file is plain byte order
(`LC_ALL=C sort`); write as UTF-8 with a line feed after every entry.

### Licence

SecLists is distributed under the MIT License:

```
MIT License

Copyright (c) 2018 Daniel Miessler

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## `curated-es.txt`

| | |
| --- | --- |
| Source | written for this project (#614): Spanish words, keyboard sequences and names related to the project that a member is likely to choose |
| Filter | none; every entry has at least 10 code points |
| Entries | 30 |
| Size | 385 bytes |

The maintainer extends this list by hand. Keep the format above: lowercase, one entry per line, at least 10 code
points (a shorter entry can never match).
