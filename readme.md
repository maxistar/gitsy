# Git Sync

## Integration Tests

- integration tests should run manually, before you should select a folder ant the run integration test
- make sure uri of selected folder in unit test properly hardcoded
- test runs only ones after each run it should be selected again!

## Integrations Tests with appium

- start appium
- 

## Gradle

`./gradlew test`

## Daily background synchronization

Settings can enable one best-effort synchronization attempt per eligible project each day. The
feature is disabled by default with 02:00 local time preselected. Android WorkManager keeps the
schedule across restarts and requires a connected network, but battery and device policies may
delay execution beyond the selected time.

Long operations show an ongoing foreground notification. Results are stored in the project list;
the first release does not post separate success or failure notifications and does not retry a
failed project during the same daily run. Authentication, SSH trust/key, and revoked folder access
must be resolved in the app. Charging-only, Wi-Fi-only, unmetered-network, and per-project schedules
are not available in this release.


## Deployment

### Manual Deployment

- update version in [build.gradle](app/build.gradle)
- use menu item "Build" -> "Generate Signed App Bundle of Apk"

### Automatic deployment

@TODO


## Todo

- when synchronization is not possible make sure no data lost
- implement interactive tests
  - copy solution from simple text editor
- make sure tests are green on pipeline
