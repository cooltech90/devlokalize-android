# DevLokalize for Android

A tiny, read-only Kotlin client for [DevLokalize](https://devlokalize.com). Fetches your project's approved translations at runtime and caches them on-device — no build-time codegen, no bundled `strings.xml` to keep in sync. Zero external dependencies (no OkHttp, no Gson) — just `java.net.HttpURLConnection` and a minimal built-in JSON reader, so it can't version-conflict with whatever your app already uses.

DevLokalize never writes back to your project from this SDK. It's a one-way sync: your translators work in the web app, your app pulls the result.

## Installation

Add the dependency in your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.devlokalize:sdk:1.0.0")
}
```

(Or clone this repo and `include(":devlokalize")` in your `settings.gradle.kts` while we get published to Maven Central.)

## Usage

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DevLokalize.init(this)
        DevLokalize.shared.configure(projectSlug = "my-app", apiToken = "dl_xxxxxxxxxxxxxxxxxxxx")

        // Instant, cache-only — call before your first render so there's no
        // flash of missing strings while the network request is in flight.
        DevLokalize.shared.preload(language = "fr")

        // Fetches the latest translations on a background thread. Safe to
        // call on every launch; on failure (offline, server error) whatever
        // is cached stays in place.
        DevLokalize.shared.refreshAsync(language = "fr") { result ->
            result.onFailure { Log.w("DevLokalize", "refresh failed", it) }
        }
    }
}
```

Look up a string anywhere, any time — it never throws, and falls back to whatever default you pass:

```kotlin
val title = DevLokalize.shared.string("hero.title", component = "homepage", default = "Welcome")
```

### Jetpack Compose

```kotlin
@Composable
fun HeroTitle() {
    Text(DevLokalize.shared.string("hero.title", component = "homepage", default = "Welcome") ?: "")
}
```

### Coroutines

`refresh` is a blocking call — wrap it yourself if you're already on a coroutine:

```kotlin
withContext(Dispatchers.IO) {
    DevLokalize.shared.refresh(language = "fr")
}
```

## Getting an API token

Create one from **Account → API tokens** in your DevLokalize dashboard. API access requires the project owner to be on the Pro plan.

## Where translations come from

`refresh` calls `GET /api/v1/projects/{slug}/bundle/{language}`, which returns every **approved** translation across every component in your project for that language. Untranslated or unreviewed strings are omitted — always pass a `default =` so your UI has something sensible to show either way.

See the [API docs](https://github.com/cooltech90/devlokalize-web/blob/main/docs/API.md) for the full response shape.

## Requirements

Android API 21+ (Android 5.0). Kotlin 1.9+.

## License

MIT
