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

### GitHub Releases

A GitHub Release is created from a merged `release/<versionName>` (based on `dev`) or `hotfix/<versionName>` (based on `master`) pull request into `master`. The release automation creates an annotated `v<versionName>` tag, publishes signed APK/AAB assets and `SHA256SUMS`, then merges `master` back into `dev`.

Before opening a release PR, increment `versionCode` and SemVer `versionName` in `app/build.gradle`, then create non-placeholder notes for both locales under `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`. `python3 scripts/release_tool.py prepare` creates missing placeholders; `python3 scripts/release_tool.py validate` checks a release branch.

Configure these GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Repository Actions permissions and branch rules must allow `github-actions[bot]` to create tags/releases and push the post-release `master`-to-`dev` merge. In GitHub **Settings → Actions → General → Workflow permissions**, select **Read and write permissions** (the repository currently reports the default as read-only), and permit the bot in branch rules if rules are added later. If a tag exists but publishing failed, run **Publish Tagged Android Release** manually with that immutable tag. This phase does not use SCP/SSH delivery, Fastlane, or Google Play uploads.

### Automatic deployment

GitHub Release publishing is automated as described above. Google Play delivery is not configured yet.


## Todo

- when synchronization is not possible make sure no data lost
- implement interactive tests
  - copy solution from simple text editor
- make sure tests are green on pipeline
