# Publishing with JitPack

The repository publishes the `lib` release AAR, sources, Javadoc, POM, and Gradle module metadata. Only the library publication is installed; `app` and `hidden-api-stubs` are not published.

## Configuration

The root [jitpack.yml](../jitpack.yml) selects JDK 17, makes the wrapper executable, and runs:

```sh
./gradlew :lib:publishReleasePublicationToMavenLocal
```

JitPack collects the installed Maven artifacts. Its `JITPACK`, `GROUP`, `ARTIFACT`, and `VERSION` environment variables select the publication coordinates. For repository `android-notes/ViewCapture` and tag `v0.1.0`, they produce:

```groovy
implementation 'com.github.android-notes:ViewCapture:v0.1.0'
```

The library POM declares HiddenApiBypass as a runtime dependency. Compile-only framework declarations are excluded. Ordinary local builds publish as `com.wanjian.capture:view-capture:0.1.0-SNAPSHOT`.

See the official [Android publishing guide](https://docs.jitpack.io/android/) and [build environment and custom commands](https://docs.jitpack.io/building/).

## Release a version

1. With JDK 17 and Android SDK 34 installed, validate the library and its local publication. On Windows, use `gradlew.bat`.

   ```sh
   ./gradlew :lib:lintRelease :lib:publishReleasePublicationToMavenLocal
   ```

2. Commit and push the library and publishing configuration to GitHub. Choose a new version tag; `v0.1.0` below is an example.

   ```sh
   git tag v0.1.0
   git push origin v0.1.0
   ```

3. Open the [JitPack project page](https://jitpack.io/#android-notes/ViewCapture), look up the tag, and request its build. Check the build log and confirm success before announcing the coordinate. Use a new tag for each subsequent release.

4. Add the repository and dependency from the README to the consuming project. Gradle resolves HiddenApiBypass transitively from Maven Central.

The checked-in configuration prepares publishing; a GitHub tag and successful remote JitPack build are still required. No GitHub Packages token is needed to consume a public JitPack artifact.
