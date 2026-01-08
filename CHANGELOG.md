# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The changelog starts with version 5.0.4.
Changes to prior versions can be found on the [GitHub release page](https://github.com/cryptomator/fuse-nio-adapter/releases).

## [6.0.0] - tbd
### Added
* Software attestation in tagged builds ([#132](https://github.com/cryptomator/integrations-linux/pull/132))

### Changed
* *[Breaking Change]* Update project to JDK 25 ([#204](https://github.com/cryptomator/fuse-nio-adapter/pull/204))
* Refactored logging to be more consistent ([#206](https://github.com/cryptomator/fuse-nio-adapter/issues/206))
* Update dependency `com.github.ben-manes.caffeine:caffeine` from 3.2.2 to 3.2.3
* Pin GitHub action versions used in CI ([#132](https://github.com/cryptomator/integrations-linux/pull/132))
 
### Fixed
* Wrong error codes returned in some cases ([#207](https://github.com/cryptomator/fuse-nio-adapter/pull/207))


## [5.1.0] - 2025-09-18
### Added
* Support fsync (#94)

### Changed
* Update `org.cryptomator:integrations-api` from 1.5.1 to 1.7.0
* Update `com.github.ben-manes.caffeine:caffeine` from 3.2.0 to 3.2.2

### Fixed
* MacFuseMountProvider does not find macFUSE library (#190)


## [5.0.5] - 2025-04-09
### Changed
* Extend list of known libfuse locations in LinuxFuseMountProvider

### Fixed
* MountFailedException thrown when using with libfuse 3.17.1 (aka ABI version 4) ([#176](https://github.com/cryptomator/fuse-nio-adapter/issues/176))


## [5.0.4] - 2025-03-24
### Added
* Changelog file
* Support libfuse3-4 on Linux (#174)

### Changed
* Switch build to JDK 23 (fe75b0a0e627ddb7196806cc7c56ece1ef268cc7)

