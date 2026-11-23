# ADR-0006 — Lower the minimum password length to 10 code points to ease access for non-technical members

- **Status:** Accepted
- **Date:** 2026-11-23
- **Deciders:** Víctor Cañizares (maintainer)
- **Applies to:** `lucoenergia/conluz` (`PasswordPolicy` and the operations that set a password: `init`,
  `changePassword`, `resetPassword`, `acceptInvitation`)

## Context

Every password that is set goes through one policy, `PasswordPolicy` (`domain/admin/user/password`). It is applied
to the default admin created by `/init`, on a password change, on a password reset and on accepting an invitation,
which is how almost every member gets their first password. Until this decision it required between 15 and 64
Unicode code points and at most 72 UTF-8 bytes, with no composition rules. The 15 came from NIST SP 800-63B-4 for a
password used as the single authentication factor, adopted in #330, and the policy's Javadoc said it followed that
standard.

Many community members are non-technical users who are not used to long passwords. Having to choose and type a
15-character password when accepting an invitation or resetting a password is a barrier to adoption: it discourages
members from activating their account and makes access harder for exactly the people the platform has to reach.

The guard in question protects against guessing: online, against the login endpoint, and offline, against the
BCrypt hashes if the database leaks. Conluz has no second authentication factor and no check of new passwords
against lists of compromised passwords. Online guessing is limited only by the failed-login throttling: 5 failures
per account or 20 per client address within 15 minutes (`docs/security/authentication-throttling.md`).

## Decision

1. Require at least **10** Unicode code points for every password that is set, a deliberate departure from the NIST
   SP 800-63B-4 minimum for single-factor passwords.
2. Leave the rest of the policy unchanged: at most 64 code points, at most 72 UTF-8 bytes (BCrypt's input limit), no
   composition rules, the value never transformed, and the rules checked in the order too short, too long, too many
   bytes.
3. Stop claiming compliance with NIST SP 800-63B-4 for single-factor passwords in the code; the policy points to this
   ADR instead.

Existing passwords are untouched: all of them have at least 15 code points and stay valid.

## Alternatives considered

**Keep 15 and improve the passphrase guidance.** Explaining that a passphrase of a few ordinary words is long but
easy to remember would keep the standard's minimum. It was rejected because the barrier remains for the target users:
they still have to choose and type 15 characters, and guidance on a screen they already find hard does not remove
that.

**Lower it to 8 code points.** 8 is the NIST SP 800-63B-4 minimum when the password is one factor of a multi-factor
authentication. It was rejected because Conluz has no MFA: the password is the only factor, so the condition under
which the standard accepts 8 does not hold.

## Consequences

**Positive**

- Members can activate their account and recover access with a password they can remember and type, which removes a
  barrier to adoption.

**Negative**

- The accepted risk: online guessing is limited only by the throttling, and if the database leaks, the BCrypt hashes
  of short passwords resist offline cracking less than those of 15-character ones.
- The compensating controls are the existing failed-login throttling (`docs/security/authentication-throttling.md`)
  and the check of new passwords against a list of compromised passwords (#614). Until #614 ships, the throttling is
  the only one.
- Any further reduction of the minimum must come with evidence that length, and not another source of friction, is
  what keeps members from signing in.

**Operational**

- No data step: existing hashes are untouched. The backend must be deployed before the `conluz-web` release that
  lowers the client-side minimum, or the client would send passwords the backend still refuses.

## Revisit if

- MFA is introduced: the password then stops being the single factor, and the minimum should be reconsidered against
  the standard's multi-factor requirements.
- A credential-guessing incident is detected, such as a successful login after a burst of throttled attempts or a
  leaked hash cracked.
- A further reduction is proposed: it requires evidence that length, and not another source of friction, is what
  blocks members.

## References

- `src/main/java/org/lucoenergia/conluz/domain/admin/user/password/PasswordPolicy.java`
- `docs/specs/accounts.md`, ACC-021
- `docs/security/authentication-throttling.md`
- #330 (original policy), #613 (this decision), #614 (compromised-password check)
