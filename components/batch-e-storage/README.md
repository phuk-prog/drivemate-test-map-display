# Batch E category 12 public diagnostics

User preference source: private APP_CHECKLIST.md at baf6c06de2c3027e2ea7563e9f9e4dacafbbd147, requirements12.01–12.29. Only selected source components are published; combined application, personal data, signing and keys are excluded.

Initial preserved original source run40086485362 measured6/8; identical eight tests candidate run40086727105 measured8/8. Source hashes and individual outcomes are in results/verified-comparison-40086727105.json. Failed-transfer calls count and user-visible failure text were the two original failures. Storage exact-boundary and saved-byte checks passed originally.

The same eight assertions rerun against immutable original and candidate sources. The storage shortfall assertion was strengthened to require the complete shortfall sentence; that revised suite also runs both phases. Separate six per-area tests exercise actual new OfflineAreaCleanup and intentionally have no invented baseline score.

Android tests compile actual RegionalMaps, OfflinePackagesService, OfflineInventory, OfflinePackagesPanel and download/storage classes. Manifest/publisher/native map/session boundaries are explicitly synthetic. Real WorkManager/Robolectric exercise queue constraints, persisted failure/retention, retry and automatic-update failure guards. Compilation and synthetic tests do not prove rendered Compose UI, genuine network transitions, native map usage/LRU, complete map downloads or final APK.

Mandatory OS foreground notification stays minimal and does not show percentage/progress. In-app persistent download progress belongs in Offline Maps.

Requirements12.03/12.04 (true actual-use LRU),12.11 (download shortfall inside removal dialog),12.12–12.14 (smaller named regions/rectangle/inline route and fit options) remain incomplete. Per-area cleanup and lifecycle changes remain candidates until Android and app integration gates pass. All29 remain unaccepted.
