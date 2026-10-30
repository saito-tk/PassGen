# EFF Long Wordlist

- Source: https://www.eff.org/files/2016/07/18/eff_large_wordlist.txt
- Retrieved: 2026-10-29
- SHA-256: `addd35536511597a02fa0a9ff1e5284677b8883b83e986e43f15a3db996b903e`
- 7,776 unique words. Four words contain hyphens. A test verifies that hyphen-token sequences form a prefix-free code (their first fragments are not standalone words), so space, hyphen, underscore and dot separators all preserve unique decoding.
- Copyright Electronic Frontier Foundation; CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).
- Attribution: https://www.eff.org/dice and https://www.eff.org/copyright
- Unchanged source included; Kotlin representation contains every source word.

## ZXing core (Android)

- Version: 3.5.4, official stable bug-fix release. Only the encoding/decoding core is included; no scanner app or camera integration.
- Source: https://github.com/zxing/zxing/tree/zxing-3.5.4/core
- License: Apache License 2.0. The unchanged [license text](../androidApp/src/main/assets/licenses/zxing-LICENSE.txt) is bundled in the APK and accessible in settings.
- Maven Central artifacts and their published SHA-1 values were matched before pinning SHA-256 in Gradle's strict dependency verification metadata.
