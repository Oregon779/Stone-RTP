# Stone RTP

Random-teleport plugin for Paper servers. `/rtp` sends a player to a safe,
random location within a configurable ring (min/max radius) around a
per-world center point, with an optional warmup countdown (layered particle
effects), cooldown, and Vault cost. `/back` returns to the pre-teleport
location. `/rtp` with no args opens a 3-button world-picker GUI. `/stonertp`
is the admin command (reload, per-world toggle, help, manual update check).

## Build

- **Java 25**, Maven, `packaging=jar`. Build with `mvn package` (aggregate
  goal is `clean package`, i.e. plain `mvn` also works).
- Dependencies: `io.papermc.paper:paper-api` (provided, resolved as a
  version *range* `[26.2.build,)` from `repo.papermc.io`) and
  `com.github.MilkBowl:VaultAPI:1.7` (provided/soft-depend, resolved from
  `jitpack.io`). Neither is on Maven Central.
- **Sandboxed/offline environments**: both `repo.papermc.io` and
  `jitpack.io` are commonly blocked outbound. If `mvn package` fails at the
  dependency-resolution step, that's almost certainly why — it is not a
  code problem. Fix by installing the two artifacts (and paper-api's own
  transitive deps, `com.mojang:brigadier` and `net.md-5:bungeecord-chat`,
  which aren't on Central either) into the local repo with
  `mvn install:install-file`, or by running the build somewhere with
  network access to those hosts.
- No test suite exists (`src/test` is absent).

## Architecture

Single `JavaPlugin` (`StoneRTP`) wires up plain manager classes in
`onEnable()` and exposes them via getters — there is no DI framework, no
static singletons/service locator. Managers hold their own state
(`Map<UUID, ...>` keyed by player) and reach back into `StoneRTP` for
sibling managers, so the plugin instance is the hub every class is
constructed with.

- `command/` — `CommandExecutor`/`TabCompleter` per command
  (`RTPCommand`, `BackCommand`, `StoneRTPCommand`). Thin: permission
  checks and argument parsing only, delegates all actual work to managers.
- `listener/` — one `Listener` class per Bukkit event of interest
  (join/quit, movement- and damage-based warmup cancellation, GUI
  click/close). Also thin, delegates to managers.
- `manager/` — the actual logic:
  - `ConfigManager` — typed accessors over `config.yml` (no config POJOs).
  - `MessageManager` — loads `languages/<lang>/messages.yml`, converts
    legacy `&`/hex codes to MiniMessage, renders `Component`s.
  - `TeleportManager` — orchestrates one RTP attempt end to end: cooldown
    → cost → safe-location search → warmup countdown → chunk preload →
    teleport. Tracks in-flight attempts in `Map<UUID, TeleportRequest>`.
  - `SafeLocationFinder` — async candidate search (random point in the
    configured annulus, world-border/biome/material checks), retried up
    to `safe-location.max-attempts` times. Async chunk load via
    `getChunkAtAsync`, but all block/biome reads happen back on the main
    thread inside `runTask`.
  - `EffectManager` — the particle/sound "show" during warmup and on
    arrival/departure; entirely config-driven (colors, radii, particle
    types all come from `config.yml`).
  - `CooldownManager`, `EconomyManager` (Vault), `BackLocationManager`,
    `NotificationManager` (actionbar/title/bossbar/chat countdown
    display), `GUIManager`, `UpdateChecker` (polls Modrinth).
- `model/` — small value types: `RTPWorldSettings` (record),
  `TeleportRequest` (mutable, holds the in-flight `BukkitTask`s and
  location `CompletableFuture`), `MessageDisplayType` (enum).
- `config/ConfigUpdater` — merges new keys from the bundled default
  config/messages into the on-disk file without touching existing values
  (comments are not preserved — a `YamlConfiguration` limitation).
- `util/ItemBuilder` — fluent `ItemStack` builder for the GUI (heads,
  glow, PDC tags used to identify which world a GUI item teleports to).

## Conventions

- All player-facing text lives in `config.yml` (countdown display text —
  chat/actionbar/title/bossbar, since *where* and *how* it's shown is
  configured right next to it) or `languages/<lang>/messages.yml`
  (everything else: command feedback, GUI item text, help). Nothing is
  hardcoded in Java.
- Messages support legacy `&` codes, `&#RRGGBB` hex, and MiniMessage tags
  together in the same string; `MessageManager.convertLegacyToMiniMessage`
  does the `&`/hex → MiniMessage translation before `MiniMessage#deserialize`.
- Config keys are read via `ConfigManager` path-string getters
  (`cfg.getInt("effects.countdown.ground-ring.points", 32)` style) rather
  than bound to POJOs — new config knobs are typically added by reading a
  new path with `getXxx(path, default)`, no schema/model class needed.
- New config/message keys placed in the bundled resource files are picked
  up automatically on next load via `ConfigUpdater` — no manual migration
  code required, existing installs get the new key merged in with its
  default value.
- Adventure `Component`/MiniMessage throughout for anything sent to a
  player; no legacy `ChatColor`/String-based messaging.
- Async work (safe-location search, chunk preloading, the Modrinth update
  check) always hops back to the main thread via
  `Bukkit.getScheduler().runTask(...)` before touching Bukkit API state
  (inventories, teleporting, sending messages).
