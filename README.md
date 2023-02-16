[![Build Status](https://github.com/cryptomator/fuse-nio-adapter/workflows/Build/badge.svg)](https://github.com/cryptomator/fuse-nio-adapter/actions?query=workflow%3ABuild)
[![Codacy Code Quality](https://app.codacy.com/project/badge/Grade/47914e82b4c54f39b6150c24b83d7d09)](https://www.codacy.com/gh/cryptomator/fuse-nio-adapter/dashboard)
[![Codacy Coverage](https://app.codacy.com/project/badge/Coverage/47914e82b4c54f39b6150c24b83d7d09)](https://www.codacy.com/gh/cryptomator/fuse-nio-adapter/dashboard)
[![Known Vulnerabilities](https://snyk.io/test/github/cryptomator/fuse-nio-adapter/badge.svg)](https://snyk.io/test/github/cryptomator/fuse-nio-adapter)

# fuse-nio-adapter
Provides directory contents specified by a `java.nio.file.Path` via a FUSE filesystem.

Uses [jfuse](https://github.com/cryptomator/jfuse), i.e. you need to install the specified fuse drivers for your OS.

This project uses [JDK 19 preview features](https://docs.oracle.com/en/java/javase/19/language/preview-language-and-vm-features.html).
If you are using it in your project , during runtime, you will need to add `--enable-preview` and allow native access via `--enable-native-access=...` option.

## Configuration Parameters
The following system properties are used:
* `org.cryptomator.frontend.fuse.mountTimeOut` - The mount timeout threshold in milliseonds. If the mounting operation exceeds it, the mounting is aborted.

## License

This project is dual-licensed under the AGPLv3 for FOSS projects as well as a commercial license for independent software vendors and resellers. If you want to use this library in applications, that are *not* licensed under the AGPL, feel free to contact our [support team](https://cryptomator.org/help/).
