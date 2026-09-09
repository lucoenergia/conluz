# ADR-NNNN — <Decision stated as an action, not a topic>

> Title rule: name what was decided, not the area it concerns. "Defer the coefficient overlap
> constraint and re-check it explicitly", not "Coefficient constraints". Someone scanning the
> directory should learn the decision from the filename alone.
>
> Filename: `NNNN-kebab-case-title.md`, number sequential across the whole series.
>
> Delete every quoted instruction block before committing.

- **Status:** Proposed | Accepted | Superseded by ADR-NNNN | Deprecated
- **Date:** YYYY-MM-DD
- **Deciders:** <who made the call>
- **Applies to:** <repository, and the specific component or subsystem>

## Context

> The situation that forced a decision. Written so a reader six months from now needs no other
> document to follow it.
>
> Include:
> - The constraint, requirement or failure that triggered this. If it was a bug, describe the
>   mechanism, not just the symptom — the mechanism is what makes the decision legible.
> - The relevant facts about how the system works today, stated concretely (table names, endpoints,
>   file paths), because the reader cannot be assumed to have them loaded.
> - Anything that made the problem harder to see than it should have been: a test that passed by
>   accident, a behaviour invisible in code review, a contract that said one thing and did another.
>   These are the details that stop the next person repeating the mistake.
>
> Do not state the decision here. Do not editorialise. If a fact is uncertain, say so.

## Decision

> What was decided, in the imperative. If the decision has parts that only work together, say so
> explicitly and explain why either part alone is worse than both — otherwise a future reader will
> apply half of it.
>
> Numbered subsections when there are several components. Each one states what changes and the
> reasoning specific to it, not the general rationale (that belongs above).

## <Domain clarification the decision creates or changes> *(optional)*

> Use this when the decision changes what some observable event *means* — a new error becoming
> reachable, a metric changing definition, a status now derivable in a new way.
>
> This is often the most valuable section long-term: it is what someone needs when they see the
> thing in production and have to judge whether it is a defect, a user mistake, or expected. Write
> it while the reasoning is fresh; it cannot be reconstructed from a log.
>
> Omit the section entirely if the decision changes no meanings.

## Alternatives considered

> One paragraph per alternative: what it was, and why it was rejected. Include the alternatives that
> were genuinely tempting — especially any that were smaller, cheaper or more obvious than the chosen
> option. An ADR that only lists straw men proves nothing and will be second-guessed.
>
> Reject on grounds that will still hold later: coupling, blast radius, testability, who bears the
> cost. "We didn't have time" is a real reason, but write it as a reason with an expiry, not as an
> engineering argument.

## Consequences

> What is now true that was not before. Be honest; an ADR that lists only benefits is a
> justification, not a record.

**Positive**

- <What this enables or fixes, including effects beyond the immediate problem.>

**Negative**

- <What is now harder, more constrained, or easier to get wrong.>
- <Especially: any rule future work must follow that is not obvious from reading the code. State it
  as an obligation, not an observation — this is the line that stops the decision being half-applied
  later.>

**Operational** *(optional)*

- <Deployment, migration, locking, downtime, cost, or anything an operator needs to know before
  applying this to a bigger environment than the one it was developed against.>

## Revisit if

> Concrete, checkable conditions that would make this decision worth reopening. An accepted trade-off
> without a written expiry condition becomes permanent by default.
>
> Good conditions are observable: a tool gains a capability, a volume threshold is crossed, a second
> case appears, a dependency changes. Avoid "if it becomes a problem".
>
> If the decision is genuinely permanent, write "This decision is not expected to be revisited" and
> say why — that is also information.

## References

> Paths to the code, migrations, configuration and issues this decision produced or governs, so the
> ADR and the implementation can be checked against each other. Prefer stable paths over line
> numbers.
