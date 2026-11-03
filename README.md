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
