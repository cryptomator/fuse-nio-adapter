[![Build Status](https://github.com/cryptomator/fuse-nio-adapter/workflows/Build/badge.svg)](https://github.com/cryptomator/fuse-nio-adapter/actions?query=workflow%3ABuild)
[![Codacy Code Quality](https://app.codacy.com/project/badge/Grade/47914e82b4c54f39b6150c24b83d7d09)](https://www.codacy.com/gh/cryptomator/fuse-nio-adapter/dashboard)
[![Codacy Coverage](https://app.codacy.com/project/badge/Coverage/47914e82b4c54f39b6150c24b83d7d09)](https://www.codacy.com/gh/cryptomator/fuse-nio-adapter/dashboard)
[![Known Vulnerabilities](https://snyk.io/test/github/cryptomator/fuse-nio-adapter/badge.svg)](https://snyk.io/test/github/cryptomator/fuse-nio-adapter)

# fuse-nio-adapter
Provides directory contents specified by a `java.nio.file.Path` via a FUSE filesystem.

Uses [jfuse](https://github.com/cryptomator/jfuse), i.e. you need to install the specified fuse drivers for your OS.

## Usage

This library calls native code and therefore requires native access to be enabled for its module, in addition to the one of jfuse:

```
java --enable-native-access=org.cryptomator.jfuse,org.cryptomator.frontend.fuse ...
```

Native access is only used on Windows to locate the WinFsp installation directory by reading the registry via `Advapi32.dll`.

## License

This project is dual-licensed under the AGPLv3 for FOSS projects as well as a commercial license for independent software vendors and resellers. If you want to use this library in applications, that are *not* licensed under the AGPL, feel free to contact our [support team](https://cryptomator.org/help/).
