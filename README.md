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
