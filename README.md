# phone-guardian

Release APKs are built and signed through GitHub Actions using repository signing secrets. The private signing key is not stored in this repository.


Release signing verification run.


## v1.11
- Improved parsing of internationalized (Unicode) domain names in the local link checker.

## v1.12
- Added an Arabic button to copy the complete local link scan report to the clipboard.


## v1.13
- Added a local history of the latest 20 link scan reports, stored on the device, with an option to clear the history.


## v1.14
- History entries are now compact and tappable; tapping one opens the full saved scan report in a details dialog.


## v1.15
- Added local file/image preliminary inspection using Android's document picker.
- Checks file signatures against extensions, flags selected risky extensions and double extensions, and reports SHA-256.
- Does not execute or upload files and is not a full antivirus scanner.


## v1.16
- Added non-executing, limited text-pattern inspection for Python files (dynamic execution, shell/process calls, deletion, unsafe deserialization, and network-plus-execution indicators).
- Python checks are heuristic warnings only; they can miss threats and produce false positives. No file is executed or uploaded.


## v1.17
- Corrected file-scan advice so Python content indicators are reflected in the final recommendation.
- Added independent warnings for Python network access patterns, alongside process execution and unsafe deserialization indicators.
- Findings remain heuristic only; they are not proof of malware. Files are not executed or uploaded.


Build validation for v1.17.

## v1.27 (development)
- Expanded offline URL analysis for browser-interpretation tricks, encoded host authorities, bidirectional text controls, DNS hostname validity, nested redirect destinations, double encoding, and misleading fragments.
- URL reports explicitly state that no site is opened, redirects are not followed, and no live reputation database is queried.
- Heuristic scores remain advisory and are not a safety guarantee.


## v1.28 (development)
- Detects invisible Unicode formatting controls in the URL authority, including percent-encoded forms, before URI parsing.
- Inspects redirect parameter values through up to three percent-decoding layers without opening or contacting destinations.
- Preserves literal plus signs while inspecting query values and reports nested encoding as an advisory indicator.
- URL analysis remains local and heuristic; it does not establish site reputation or guarantee detection.
- Manual validation matrix: normal HTTPS URL; mixed-script and confusable domains; invisible Unicode in host; single- and multiply-encoded external redirect targets; user-info (@); backslash authority tricks; malformed URL input.
