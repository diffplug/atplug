# AtPlug releases

## [Unreleased]
### Fixed
- The Gradle daemon would throw `EOFException` when adding new components in a warm daemon due to [Jar URL caching](https://stackoverflow.com/questions/36517604/closing-a-jarurlconnection), now fixed. ([#4](https://github.com/diffplug/atplug/pull/4))

## [0.1.1] - 2022-02-19
### Fixed
- The Gradle daemon would throw `ZipException` during the second invocation of metadata generation, now fixed.

## [0.1.0] - 2022-02-16
- Migration to open source is WIP.
