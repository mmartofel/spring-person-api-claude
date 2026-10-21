# Lessons

Patterns learned from user corrections. Review at session start; add a rule after every correction.

<!-- Format:
## <short title>
- **Mistake:** what went wrong
- **Rule:** what to do instead
-->

## Confirm update semantics for PUT
- **Mistake:** Made `PUT` a full replacement by default, so fields not sent were cleared (`birthDate` became `null`). The user wanted unsent fields kept.
- **Rule:** In this project `PUT` merges non-null fields into the stored record and validates the merged result. For new update endpoints, follow this pattern, or ask before choosing replace semantics.
