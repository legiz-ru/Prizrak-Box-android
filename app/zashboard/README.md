# Zashboard

The mihomo dashboard shown in the app (Settings → Active Tunnel → Zashboard,
after Debug → Zashboard is on). Bundled as built, unchanged, in the APK's
assets under `zashboard/`: the app serves these files itself at
`https://appassets.androidplatform.net/zashboard/`, the page talks to the
core's controller on `127.0.0.1:9090` with Zashboard's own secret.

- Source: https://github.com/Zephyruso/zashboard (MIT, `assets/zashboard/LICENSE`)
- Release: v3.29.1, `dist-no-fonts.zip` (system fonts, nothing loaded from
  a CDN for the page itself)
- SHA-256 of the zip: 21371cd111b6b3d87774f3ea3ddeacdcb0f6aff8690a16d652daa3ea6e3b0145

Updates itself: every build of the app runs the `updateZashboard` task first
(before `preBuild`), which asks GitHub for the latest release and, when it is
newer than `assets/zashboard/VERSION`, downloads its `dist-no-fonts.zip`,
checks it against the SHA-256 GitHub publishes for the file, and puts it in
place of the old one (`LICENSE` kept; `VERSION` — shown in About while
Debug → Zashboard is on — and the two lines above updated). Commit the
changed files with the build. Offline or on any mismatch the current copy
stays and the build prints a warning. `ZASHBOARD_AUTO_UPDATE=0` skips the
check. The same logic as `ThirdParty/update-zashboard.sh` of the iOS app.
