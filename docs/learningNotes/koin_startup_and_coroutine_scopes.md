# Koin startup, `Application`, and coroutine scopes

A walkthrough of how the app actually boots, written up from a Q&A session
tracing the code from `MainActivity` backward. Kept here for whenever this
needs re-explaining, or for whoever ports the equivalent iOS startup flow.

## Starting point: "the code begins with MainActivity" — not quite

It's tempting to think of `MainActivity` as the app's entry point, since
it's the first *screen*. But `StreakSeasonApp` (the `Application`
subclass) runs earlier — Android creates it once per process, before any
Activity exists — and that's where the dependency-injection container
actually gets set up.

### What does `class StreakSeasonApp : Application()` actually say?

Every Android component — Activity, Service, and the app object itself —
is declared in `AndroidManifest.xml`, because the **OS**, not your code, is
responsible for constructing them; you never call `StreakSeasonApp()`
yourself. Declaring the class and pointing
`<application android:name=".StreakSeasonApp">` at it tells Android:
"instead of a plain default `Application` object, build *this* subclass as
the one global object representing the whole running app."

`Application` is a `Context` (same base type `Activity` has, for reading
resources and system services) but with **process lifetime** instead of
**screen lifetime** — created once, lives until the process dies, no
matter how many Activities come and go. That's exactly why Koin gets
started here and not inside `MainActivity`: if `startKoin()` ran in an
Activity's `onCreate()`, a second Activity instance (e.g. after rotation)
would call it again and crash — Koin refuses to start twice in one
process.

### The actual startup order

1. Android creates the process, then instantiates `StreakSeasonApp`.
2. Android calls `StreakSeasonApp.onCreate()`:
   - `startKoin { modules(appModules) }` runs. This does **not** build
     anything yet — every module is written as `single { ... }`, which is
     lazy. This step only *registers the recipes* ("here's how to build an
     `AppDatabase`," "here's how to build a `Clock`").
   - `if (isDebuggable()) seedScope.launch { seeder.seedIfNeeded() }` runs.
     `seeder: DebugSeeder by inject()` is the **first** real ask Koin gets
     — this is the moment the whole chain (`AppDatabase` → DAOs → `Clock`
     → `ArcRepository` → `DebugSeeder`) actually gets built, in dependency
     order, and cached. It's launched on a background coroutine, so
     `onCreate()` doesn't wait for it to finish.
3. `StreakSeasonApp.onCreate()` returns. Android creates `MainActivity`.
4. `MainActivity`'s class body runs: `private val todayViewModel: TodayViewModel by inject()`.
   This line does **not** fetch anything yet — `by inject()` is a lazy
   delegate. It only asks Koin the first time `todayViewModel` is *read*.
5. Android calls `MainActivity.onCreate()`. Inside `setContent { ... }`,
   `TodayRoute(viewModel = todayViewModel, ...)` reads `todayViewModel` for
   the first time — *that's* the moment Koin actually builds (or reuses,
   if the seeding coroutine got there first) the singleton `TodayViewModel`.
6. Building `TodayViewModel` runs its `init { }` block, which starts its
   own coroutine to find the active arc and begin collecting `Resolution`
   updates.
7. Compose renders with whatever `TodayUiState` exists right now
   (`Loading`), and redraws automatically as the ViewModel's state changes.

Steps 2's seeding coroutine and steps 5-6's `TodayViewModel` construction
race each other in the background with no guaranteed order — it doesn't
matter, because Koin's singleton creation is safe either way, and both
paths end up sharing the same cached `ArcRepository`/`Clock`.

### `onCreate()` — what it is

`onCreate()` is a **lifecycle callback**: a method the OS calls at a
specific, guaranteed point, that you override to hook your own setup into
that moment.

- `Application.onCreate()` fires once, right after the `Application`
  object is constructed, before any Activity/Service exists — the first
  possible moment for app-wide setup.
- `Activity.onCreate(savedInstanceState: Bundle?)` fires once per Activity
  *instance* — first launch, or a fresh instance if the system destroyed
  and recreated it. `savedInstanceState` is how small UI state survives
  that recreation (`null` here, on a clean launch).
- Calling `super.onCreate()` first is convention/contract: the base class
  does its own required internal setup, and yours runs after it, not
  instead of it.

### The full render chain — a correction

It's *not* `MainActivity` → `TodayScreen`. It's:

```
MainActivity
  -> TodayRoute(viewModel = todayViewModel)      // stateful wrapper
       -> val state by viewModel.state.collectAsState()
       -> TodayScreen(state, onToggleBinary = ..., ...)   // stateless
```

`TodayScreen` never sees the `TodayViewModel` itself — only a plain
`TodayUiState` value and function callbacks. That split is deliberate: it's
what lets `TodayScreen` be driven entirely by fake data in a `@Preview`,
with no ViewModel, no Koin, and no database involved.

Also worth being precise on the earlier claim "TodayViewModel is a
singleton object": it's not a Kotlin `object` (a different, language-level
singleton mechanism). It's an ordinary `class` that Koin is *told* to treat
as a singleton, via `single { TodayViewModel(get(), get()) }` in
`AppModule.kt`. Koin builds it once, on first request, and hands back that
same instance every time after.

## Coroutine scopes

"Scope" turned out to mean two unrelated things in this codebase — worth
untangling on its own.

### What a `CoroutineScope` actually is

Not a thread. Not a queue. It's a small object that answers exactly one
question: *"if I need to cancel everything launched through here, how do I
do that in one shot?"*

Every `scope.launch { ... }` call attaches the new coroutine as a **child**
of that scope's `Job`. This parent-child linking is called **structured
concurrency** — cancel the parent, every child cancels too, automatically,
no matter how many there are or how deep. No orphaned background work
quietly running after nobody needs it.

### How many coroutines can run in one scope?

**Unlimited.** A scope doesn't reserve slots — it's a grouping mechanism
for lifecycle, not a concurrency limiter. From `TodayViewModel`:

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

init {
    scope.launch {                       // coroutine #1
        val id = repository.getActiveArcId()
        ...
        repository.observeToday(id)
            .onEach { ... }
            .launchIn(scope)             // coroutine #2, same scope
    }
}

fun completeBinary(commitmentId: String, done: Boolean) {
    writeEntry(...)                      // scope.launch { ... } -> a NEW
                                          // coroutine, every single tap
}
```

Every tap on a checkbox, stepper, or undo button launches another
independent coroutine on that same one `scope` object. Over the app's
life that could be hundreds — completely normal. The scope's only job:
*if I ever get cancelled, take all of these down with me.*

### What actually limits how many run "at once"

That's the **`Dispatcher`**, a separate axis entirely:

- `Dispatchers.Main` / `Dispatchers.Main.immediate` — the single UI
  thread. Coroutines here don't run *simultaneously* with each other; they
  interleave cooperatively, each running until it hits a suspension point
  (like a Room query), then yielding. This is why `TodayViewModel` uses
  it.
- `Dispatchers.IO` — a large thread pool for blocking work (network,
  disk, database). Many coroutines here genuinely run in parallel.
- `Dispatchers.Default` — a pool sized to the CPU core count, for
  CPU-heavy work.

**The scope decides who gets cancelled together; the dispatcher decides
how many can genuinely execute at the same instant.**

### Why `SupervisorJob()` specifically

By default, a scope's plain `Job()` propagates failure both upward and
sideways: one child throwing cancels the whole job, which cancels every
sibling too — even unrelated ones. On `TodayViewModel.scope`, that would be
a real problem: if one `scope.launch { repository.recordEntry(...) }`
failed for a bad tap, it should not also kill the long-running coroutine
collecting `Resolution` updates and keeping the whole screen alive.
`SupervisorJob()` changes that rule — a failing child only affects itself.

### The actual "types" of scopes, and when to reach for each

| Scope | Cancelled when | Use it when |
|---|---|---|
| `viewModelScope` | A real `androidx.lifecycle.ViewModel` is cleared | You're in a real androidx ViewModel (needs `lifecycle-viewmodel-ktx` — the dependency this project deliberately doesn't have yet, which is why `TodayViewModel` hand-builds an equivalent instead) |
| `lifecycleScope` | An Activity/Fragment is destroyed | Launching work tied to one screen's lifetime |
| `rememberCoroutineScope()` | A composable leaves composition | Launching a coroutine from inside a `@Composable` in response to a click, etc. (Not needed here — `collectAsState()` manages its own internal scope for just *collecting* a flow.) |
| A scope you build yourself: `CoroutineScope(SupervisorJob() + dispatcher)` | Whenever *you* call `.cancel()` — or never | You're a plain class with no framework lifecycle to hook into, like `TodayViewModel` or `StreakSeasonApp`'s `seedScope` here. You own the responsibility to cancel it if it should ever stop. |
| `GlobalScope` | Never, automatically | Generally avoid — nothing ties it to anything, so it's the easiest way to leak background work. Not used anywhere in this project. |
| `coroutineScope { }` / `supervisorScope { }` | Automatically, when the block returns | Inside a `suspend fun`, to launch a few child coroutines and *wait for all of them* before continuing — a temporary, inline grouping, not a long-lived object |

In this codebase, `TodayViewModel` and `StreakSeasonApp` both fall into
the "build it yourself" row — neither is a real ViewModel or Activity —
and both scopes are simply never cancelled, because both objects are Koin
singletons meant to live as long as the app process does anyway.
