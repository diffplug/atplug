# AtPlug releases

## [Unreleased]
### Changed
- Bump all dependencies to latest.

## [1.1.1] - 2024-07-06
### Changed
- Bump all dependencies to latest, especially Kotlin to 2.0. ([#22](https://github.com/diffplug/atplug/pull/22))

## [1.1.0] - 2023-04-06
### Added
- Add methods for taking `KClass` instead of just `Class` to prepare for Kotlin Multiplatform. ([#8](https://github.com/diffplug/atplug/pull/8))

## [1.0.1] - 2022-12-31
### Fixed
- Fixing `EOFException` Gradle daemon problem brought the `ZipException` problem back, now fixed. ([#6](https://github.com/diffplug/atplug/pull/6))

## [1.0.0] - 2022-12-30
### Fixed
- The Gradle daemon would throw `EOFException` when adding new components in a warm daemon due to [Jar URL caching](https://stackoverflow.com/questions/36517604/closing-a-jarurlconnection), now fixed. ([#4](https://github.com/diffplug/atplug/pull/4))

## [0.1.1] - 2022-02-19
### Fixed
- The Gradle daemon would throw `ZipException` during the second invocation of metadata generation, now fixed.

## [0.1.0] - 2022-02-16
- Migration to open source is WIP.
