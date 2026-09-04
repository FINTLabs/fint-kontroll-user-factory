# FINT Kontroll User Factory

## User Status Contract

The factory publishes source facts to the `user` topic. It does not decide the
final catalog status.

`fintStatus` describes whether the FINT source data has the relationships needed
to build a usable user:

- `VALID`: the source record has the required FINT links and resolved data.
- `INVALID`: the source record is missing a required relationship, for example
  an elev without elevforhold, an elevforhold without skole, or a skole without
  organisasjon.

`entraStatus` describes the Entra account state on regular user payloads:
`ACTIVE` or `DISABLED`. Entra deletions are represented as tombstones on the
`user` topic instead of regular payloads with `entraStatus=DELETED`. The user
catalog combines `fintStatus`, `entraStatus`, `validFrom`, and `validTo` on
regular payloads, and handles tombstones as deleted users. After Entra marks a
user as deleted, the factory skips later regular and invalid payloads for that
user.

The factory still uses its `days-before-start` settings when selecting the
current/main FINT relationship that supplies organisation data. Those settings do
not decide the final published user status in this service.

## Missing Links Versus Cache Misses

Student publishing distinguishes missing source links from cache timing issues.

- Missing source link: publish a minimal user with `fintStatus=INVALID`,
  including `identityProviderUserObjectId` when it is available from Entra.
- Linked resource missing from cache: log and skip publishing for this run.
- Complete source data: publish the user with `fintStatus=VALID`.

When several elevforhold exist, the factory chooses the main elevforhold as
before: the first active relationship within the student days-before-start
window, or the first resolved elevforhold if none are active. Skole and
organisasjon are resolved from that selected elevforhold. The catalog still
decides whether the user is active or disabled based on the combined validity
window.

Employee publishing follows the same ownership split. The factory iterates all
personalressurs records and resolves linked arbeidsforhold. A personalressurs
without an arbeidsforhold link is published as `fintStatus=INVALID`; a linked
arbeidsforhold missing from cache is skipped for the current run. The main
arbeidsforhold is chosen as before: hovedstilling among relationships valid
within the employee days-before-start window, or the valid non-main relationship
with the highest percentage. If no arbeidsforhold is valid within that selection
window, or the selected arbeidssted is outside that window, the employee is
skipped for the current run.
