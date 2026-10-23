# Route panel — checklist 05.02

The user approved dragging route selections down to see the map and back up to restore options. This is a standalone Compose component, not a combined app. It keeps its drag handle visible, leaves content scrolling/selection independent and provides equivalent accessibility actions. Handle dimensions are a presentation choice, not a legal rule.

Five tests exercise native Robolectric Compose dragging, restoring options, accessibility actions, bounds at different sizes and invalid input. Run `python3 components/route-panel/ci/run.py` with Java 17, Gradle 8.14.3 and Android SDK 36. The workflow neither assembles nor publishes an app APK.

The earlier private app preview uses a fixed Surface and has no comparable executed gesture baseline. Report that as missing baseline evidence, never invent 0/5 or count unrelated routing helper tests as this feature. The candidate must still be wired into the private app preview using this exact source hash; component success is not full-app integration proof or legal certification.
