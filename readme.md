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