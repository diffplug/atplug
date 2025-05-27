# AtPlug releases

## [Unreleased]
### Fixed
- `FindPlugsTask.classesFolders` changed from `@CompileClasspath` to `@Classpath`, hopefully fixing up-to-date problem. ([#79](https://github.com/diffplug/atplug/pull/79))

## [1.2.1] - 2025-02-04
### Fixed
- `FindPlugsTask` had an up-to-date issue in projects with mixed Kotlin and Java. ([#68](https://github.com/diffplug/atplug/pull/68))

## [1.2.0] - 2025-01-27
### Added
- Cacheability, incremental task, and huge speed improvement. ([#66](https://github.com/diffplug/atplug/pull/66))
  - `plugGenerate` refactored into `plugFind` followed by `plugGenerate` 
### Changed
- Bump required JVM from 11 to 17. ([#63](https://github.com/diffplug/atplug/pull/63))
- Detect Kotlin version rather than harcode it. ([#64](https://github.com/diffplug/atplug/pull/64))

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
