# Triagemagic plugin for IntelliJ IDEA

<!-- Plugin description -->
Triagemagic is a IntelliJ plugin to help you triage issues related to [Flutter](https://github.com/flutter/flutter).

This plugin depends on the [Flutter Intellij](https://github.com/flutter/flutter-intellij) plugin to work.
<!-- Plugin description end -->

### Features

- [x] Run Flutter project on multiple channels
- [x] Run Flutter project on multiple devices
- [x] Format logs
- [x] Upgrade All Flutter channels
- [x] Copy `flutter doctor -v`
- [x] Copy Triage response template

<img src="/screenshots/static_image.png">

### Current Limitations

- [ ] Currently, it is not possible to run multiple commands in parallel.
    - [ ] Can't build MacOS app on multiple channels at the same time (probably a limitation from XCode).
- [ ] Flutter Plugin does not invoke the process listener in release builds.
- [ ] In debug mode, apps are being started in paused state. See [flutter-intellij/issues/5461](https://github.com/flutter/flutter-intellij/issues/5461).
    - This only affect Bazel projects (most Flutter projects works fine). Current workaround: use PROFILE mode.

### Important

- [ ] This project need tests (unit tests at least), to make sure everything will work after changes are made.

### Contributing

This project uses Kotlin. To contribute to this Project you must have an IntelliJ IDE (Community, Ultimate or Android Studio) installed, then:

1. Fork this repository.
2. Clone your fork.
3. Once you open the project, you must wait until your IDE downloads all the required dependencies.
4. To run the project, select `Run Plugin` in the run configuration dropdown and hit Debug.