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

### Preferred deployment: GitHub Releases

The preferred release path is automated. It starts with a versioned release PR and ends with a signed GitHub Release, a Google Play closed-testing upload, and synchronization of the released `master` commit back into `dev`.

The executable sources of truth are [`release-config.json`](release-config.json), [`scripts/release_tool.py`](scripts/release_tool.py), and these workflows:

- [Validate Android Release PR](.github/workflows/release-pr.yml)
- [Create Android Release](.github/workflows/create-release.yml)
- [Publish Tagged Android Release](.github/workflows/android-release.yml)
- [Sync Released Master to Dev](.github/workflows/sync-master-to-dev.yml)

#### 1. Prepare a release branch

Regular releases use `release/<versionName>` branches based on `dev`:

```bash
git fetch origin dev master --tags
git switch dev
git pull --ff-only origin dev
git switch -c release/0.1.4
```

Hotfixes use `hotfix/<versionName>` branches based on `master`:

```bash
git fetch origin master --tags
git switch master
git pull --ff-only origin master
git switch -c hotfix/0.1.4
```

Choose a new SemVer `versionName`. Increase `versionCode` in [`app/build.gradle`](app/build.gradle); it must be greater than the version code in every previous `v*` release tag. Update both values before running the validation commands.

#### 2. Prepare and validate release metadata

The release tool creates missing changelog placeholders for the version currently in `app/build.gradle`:

```bash
python3 scripts/release_tool.py prepare
```

Replace the placeholders with real release notes in both required locales:

```text
fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
fastlane/metadata/android/ru-RU/changelogs/<versionCode>.txt
```

Run the local checks before opening the PR:

```bash
python3 scripts/release_tool.py validate
python3 -m unittest scripts/test_release_tool.py
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease --no-daemon
```

`validate` checks SemVer, the branch name and base branch, a strictly increasing `versionCode`, an unused tag name, and non-empty non-placeholder changelogs. The release PR must target `master`.

#### 3. Open and merge the release PR

Open a PR from `release/<versionName>` or `hotfix/<versionName>` into `master`. The release-specific workflow validates the metadata and builds release APK/AAB artifacts. Merge only after the repository's required checks and reviews pass.

Do not create the release tag manually. After a qualifying PR is merged, `Create Android Release` verifies the merge identity and required checks, then creates one annotated immutable tag named `v<versionName>`.

#### 4. Publish the release

The tagged-release workflow checks out the immutable tag and builds:

- a signed `gitsy-<versionName>.apk`;
- a signed `gitsy-<versionName>.aab`;
- `SHA256SUMS` covering both artifacts.

It then creates the GitHub Release and uploads the verified AAB to the Google Play closed-testing `alpha` track. The workflow uploads no APK to Google Play, no production release, and no screenshots or store images.

After publication succeeds, the release orchestration merges `master` back into `dev` with a no-fast-forward merge.

#### 5. Verify the result

Check the GitHub Release assets and checksum manifest, then confirm that the matching `versionCode` is visible in the Play Console `alpha` track. The GitHub Release and Google Play closed-testing upload are separate outputs; a successful GitHub Release does not by itself mean that the app is in production.

### Release prerequisites and secrets

GitHub Actions must have write permission for the release automation. In **Settings → Actions → General → Workflow permissions**, select **Read and write permissions**, and allow `github-actions[bot]` to create tags/releases and push the post-release merge if branch rules are configured.

Configure these GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `GOOGLE_PLAY_SERVICE_ACCOUNT`

`GOOGLE_PLAY_SERVICE_ACCOUNT` contains the compact service-account JSON encoded as base64. The service account must have access to the `me.maxistar.gitsy` Play Console app, the **Google Play Android Developer API** must be enabled, and the `alpha` closed-testing track must exist. The workflows decode signing and service-account material only into temporary runner files; do not commit or paste these values into the repository.

### Recovery for an existing immutable tag

If tag creation succeeded but publication did not complete, manually run **Publish Tagged Android Release** from GitHub Actions and provide the existing immutable tag, for example `v0.1.4`. The workflow validates the annotated tag and its release identity before publishing. If a complete GitHub Release already exists, it downloads the assets and verifies `SHA256SUMS` instead of replacing them.

If publication succeeds but the original orchestration did not synchronize branches, manually run **Sync Released Master to Dev** with the same tag. It verifies that the tag resolves to a commit reachable from `master` before pushing the merge to `dev`.

### Manual local deployment

Android Studio can still be used as a local fallback: update the version in [`app/build.gradle`](app/build.gradle), then use **Build → Generate Signed Bundle / APK**. This produces a local signed artifact only; it does not create the annotated tag, GitHub Release, checksum manifest, Google Play upload, or `master`-to-`dev` synchronization performed by the preferred automated path.


## Todo

- when synchronization is not possible make sure no data lost
- implement interactive tests
  - copy solution from simple text editor
- make sure tests are green on pipeline
