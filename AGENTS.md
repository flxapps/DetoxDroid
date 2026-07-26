# DetoxDroid – Agent Notes

## Build & Verify

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests (pure-JVM logic tests: schedule rules, usage stats, scroll calibration)
```

Note: there is no ktlint Gradle task in this project (Android lint runs as part of the build with `abortOnError=false`).

Run a single test class:
```bash
./gradlew testDebugUnitTest --tests "com.flx_apps.digitaldetox.ExampleUnitTest"
```

## Toolchain Quirks

- **Kotlin plugin 2.2.10** with `languageVersion = "1.9"` and `DataObjects` language feature enabled.
- **JDK 17** required for compilation. Set `JAVA_HOME` to JDK 17 before running Gradle.
- **KSP** is used for Hilt and Room (not kapt). There is no ktlint Gradle task in this project.
- **Compose compiler** is integrated via the Kotlin Compose plugin (`org.jetbrains.kotlin.plugin.compose`) — no manual `composeOptions.kotlinCompilerExtensionVersion` needed.

## Architecture

Single-module Android app (`:app`, package `com.flx_apps.digitaldetox`).

```
AccessibilityService  →  FeaturesProvider  →  [Feature objects]
```

- **Features are Kotlin `object` singletons** extending `abstract class Feature`. Never make a feature a `class`.
- Capabilities are composed via **interface delegation** (`by`): `OnAppOpenedSubscriptionFeature`, `OnScrollEventSubscriptionFeature`, `OnScreenTurnedOffSubscriptionFeature`, `SupportsScheduleFeature`, `SupportsAppExceptionsFeature`, `ScreenTimeTrackingFeature`, `NeedsPermissionsFeature`.
- To add a feature: create `object` in `features/`, extend `Feature`, implement interfaces, register in `FeaturesProvider.featureList`.
- Feature `id` = class simple name (via `Feature.createId`). Used as DataStore key prefix for all its properties.

## Persistence

Feature state uses `DataStoreProperty` — a property delegate backed by `DataStore<Preferences>`. Key pattern: `"${id}_propertyName"`. Transformers: `EnumStorePropertyTransformer` for enums, `SetStorePropertyTransformer` for sets. Schedule rules serialise as `"1|3|5,09:00,18:00"`.

## UI

- **Jetpack Compose + Material 3**
- **Navigation**: `dev.olshevski.navigation:reimagined` — **not** Jetpack Navigation Compose. Use `NavController` / `NavBackHandler` from this library.
- **DI**: Hilt (`hiltViewModel()`, `@AndroidEntryPoint`, kapt).
- Feature UI lives in `ui/screens/feature/{feature_name}/` as `*FeatureSettingsSection` + `*FeatureSettingsViewModel`.

## Key Conventions

- Files prefixed with `+` (e.g. `+Feature.kt`, `+FeaturesProvider.kt`) are package index/primary files — this pins them to top of directory listings.
- `FeaturesProvider.activeFeatures` is cached and refreshed at most once per minute. Call `FeaturesProvider.reloadActiveFeatures()` to force a refresh after state changes.
- Use **Timber** for all logging. Debug builds plant `CachingDebugTree` writing to `InMemoryLogStore` (visible in-app).
- Elevated permissions via **Shizuku** (`ShizukuUtils`) or **root shell** (`RootShellCommand`).
- **PR titles** must follow conventional commits: `type(scope): description` (types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`).

## Localization

String resource names use `_` between scopes and camelCase inside a scope
(`feature_doomScrolling_timeUntilWarning_description`). Never dots — they silently become `_` in
`R.string`, so the XML name and the Kotlin reference drift apart.

- `values/` is `en-US` (declared in `res/resources.properties`); translations go in `values-<lang>/`.
- `androidResources.generateLocaleConfig = true` builds `locales_config.xml` and wires
  `android:localeConfig` into the manifest. **Adding a locale needs no manifest or Gradle change** —
  just the `values-XX/` folder.
- Mark brand names, URLs and shell commands `translatable="false"` instead of copying them into
  every locale.
- Marketing screenshots: drop a translated `values-XX/strings_screenshot.xml` and
  `./gradlew :app:generateScreenshots` renders that language into
  `fastlane/metadata/android/<locale>/images/phoneScreenshots/`. No code change needed.

### Translation style — translate the intent, not the words

A 1:1 rendering is the failure mode, not the goal. Keep the *content* and the app's warm, slightly
wry voice; rewrite the sentence freely to whatever a native speaker would actually say.

- German uses informal **du** (lowercase) throughout.
- Prefer verbs to nominalisations, and restructure rather than copying English clause order.
  "See where your time goes" → *"Finde heraus, wie du deine Zeit nutzt"*, not *"Sieh, wohin deine
  Zeit geht"*.
- Use the target language's own idiom for metaphors: the tip jar is a **Kaffeekasse**, not a
  *Trinkgeldglas*.
- Watch for words that look like a translation but mean something else — "one tap" is
  *ein Fingertipp*; *"ein Tipp"* means *a piece of advice*.
- Shorten UI labels. German runs 20–35 % longer, and chips/tiles clip:
  "Detection Sensitivity" → *Empfindlichkeit*, "Allowed Daily Screen Time" → *Tägliches Zeitbudget*.
- Format-arg count and index must match the base string exactly; reorder placeholders when the
  target grammar wants a different order (`%1$d`/`%2$d` are positional, so this is safe).
- Units are not universal: `m` reads as *metres* in German — use `min`. Date patterns localise too
  (`d/M/yyyy` → `d.M.yyyy`).
- Never hardcode user-facing text in Kotlin (including `String.format("%dh %dm", …)`) — unit labels
  belong in resources.

Before finishing, verify parity — every translatable base string present, no format-arg drift:

```bash
python3 - <<'PY'
import re, collections
def parse(p):
    s=open(p,encoding='utf-8').read(); d={}; nt=set()
    for m in re.finditer(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', s, re.S):
        d[m.group(1)]=m.group(3)
        if 'translatable="false"' in m.group(2): nt.add(m.group(1))
    for m in re.finditer(r'<plurals name="([^"]+)">(.*?)</plurals>', s, re.S):
        d[m.group(1)]=' '.join(re.findall(r'<item[^>]*>(.*?)</item>', m.group(2), re.S))
    return d, nt
base, nt = parse('app/src/main/res/values/strings.xml')
tr, _ = parse('app/src/main/res/values-de/strings.xml')   # ← locale under test
need = {k for k in base if k not in nt}
args = lambda v: collections.Counter(re.findall(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]', v))
print('missing:', sorted(need-set(tr)) or 'none')
print('extra  :', sorted(set(tr)-need) or 'none')
print('argdiff:', [k for k in need&set(tr) if args(base[k])!=args(tr[k])] or 'none')
PY
```

Then look at the rendered screenshots — parity passing does not mean the text fits.

## Release Flow

`publish.yaml` is a manual `workflow_dispatch` workflow. It auto-bumps version based on conventional commits since the last tag (`feat` → minor, `fix` → patch, `BREAKING CHANGE` → major), builds a signed APK, creates a GitHub Release with changelog, and pushes a version tag.

## Store Metadata

`fastlane/metadata/android/en-US/` is the **single source of truth** for listing text, images, and changelogs — F-Droid consumes it directly. Changelogs are keyed by `versionCode` (e.g. `changelogs/20400.txt`) and generated by `publish.yaml`.

If Google Play publishing is added (GPP / `com.github.triplet.play`), **do not create a second metadata tree to hand-maintain.** GPP uses a different layout (`app/src/<flavor>/play/`, hyphenated filenames, a `listings/` subfolder, release-notes keyed by *track* — not `versionCode`) and does **not** read fastlane's folder. Convention:

- Keep editing only `fastlane/metadata/…`.
- Generate GPP's `release-notes/en-US/default.txt` in CI from the current `changelogs/<versionCode>.txt` — truncate to Play's **500-char** limit or the upload fails.
- Manage the Play *listing* (title ≤30, short ≤80, full ≤4000 chars, feature graphic) by hand in the Play Console; these limits differ from F-Droid, so the two listings are expected to diverge.

See `PREMIUM_TIER_PLAN.md` §8 for the full publishing rationale.
