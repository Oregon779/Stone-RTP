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
- Dependencies: `io.papermc.paper:paper-api` (provided, pinned to
  `26.2.build.124-stable` from `repo.papermc.io` — deliberately not a
  version range, which can't be resolved offline) and
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
- Tests: JUnit + MockBukkit (`mockbukkit-v26.2`, from Maven Central) under
  `src/test`; `mvn package` runs them. `PluginTestBase` boots a mock server
  and loads the plugin (so `StoneRTP` must stay non-`final`). MockBukkit
  leaves some Paper APIs unimplemented, so tests use `addWorld()` /
  `addPlayer()` from the base class (`AsyncChunkWorldMock`,
  `AsyncTeleportPlayerMock` fill in `getChunkAtAsync`/`teleportAsync`) and
  `FakeEconomy` (proxy-based Vault economy). A MockBukkit
  `UnimplementedOperationException` shows up as a *skipped* test, not a
  failure — treat new skips as missing test support, not a pass.

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
    teleport. Tracks in-flight attempts in `Map<UUID, TeleportRequest>`;
    a request stays there until the teleport itself starts, and every
    async step re-checks `active.get(uuid) == request` before acting.
    Three entry points: `startRTP` (player), `startZoneRTP` and
    `startForcedRTP` (`/rtp player`) — the latter two skip cost, cooldown
    and warmup. Money is only ever refunded if the Vault withdrawal
    actually succeeded; quit, cancel and plugin disable all refund.
  - `SafeLocationFinder` — async candidate search (random point in the
    configured annulus, world-border/biome/material checks), retried up
    to `safe-location.max-attempts` times. Async chunk load via
    `getChunkAtAsync`, but all block/biome reads happen back on the main
    thread inside `runTask`. Per-world `search-mode` (`SearchMode`):
    SURFACE uses the heightmap top; CAVE scans upward for a floor below
    the roof (never on top of it); AUTO picks CAVE for `hasCeiling()` /
    NETHER worlds so datapack/modded nethers work too.
  - `BlockedTimeManager` — daily `TimeWindow`s (from
    `blocked-times.periods`, may wrap midnight) during which
    `TeleportManager` refuses to start any RTP.
  - `ZoneManager` — cuboid `RTPZone`s persisted in `zones.yml` (data, not
    settings — kept out of config.yml), the selection wand (PDC-tagged
    item) and a once-per-second task that RTPs players who stayed inside
    a zone for its interval via `TeleportManager#startZoneRTP` (no
    cost/cooldown/warmup).
  - `EffectManager` — the particle/sound "show" during warmup and on
    arrival/departure; entirely config-driven (colors, radii, particle
    types all come from `config.yml`).
  - `CooldownManager`, `EconomyManager` (Vault), `BackLocationManager`,
    `NotificationManager` (actionbar/title/bossbar/chat countdown
    display), `GUIManager`, `UpdateChecker` (polls Modrinth).
- `model/` — small value types: `RTPWorldSettings`, `RTPZone`,
  `TimeWindow` (records), `TeleportRequest` (mutable, holds the in-flight
  `BukkitTask`s and location `CompletableFuture`), `MessageDisplayType`,
  `SearchMode` (enums).
- `config/ConfigUpdater` — merges new keys from the bundled default
  config/messages into the on-disk file without touching existing values.
  If the file doesn't parse it throws instead of rewriting it; callers
  then run on the bundled defaults and never save over the broken file
  (`ConfigManager` read-only mode, `ZoneManager#isStorageBroken`).
  User-owned sections (`worlds`, `cooldown.groups`) are passed as
  free-form and are never re-populated once the admin removes entries.
- `util/YamlFiles#saveAtomically` — every YAML write goes to a temp file
  and is renamed over the target, so a crash can't leave an empty file.
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
  (inventories, teleporting, sending messages). Use `whenComplete`, not
  `thenAccept`, on futures that gate a player's request, so a failure
  can't leave the request stuck forever.
- Player-typed text that ends up in a message placeholder goes through
  `MessageManager#escape` (no MiniMessage tag injection).
- Hot paths (move/click/damage listeners, per-tick effect frames) must
  exit early and avoid per-call parsing: config-derived sounds,
  particles, colours and cooldown groups are cached per (re)load, and
  particles go only to viewers resolved once per frame
  (`EffectManager#viewersNear`, same 32-block radius as vanilla).
- Never close/open inventories directly inside `InventoryClickEvent`;
  schedule it for the next tick.
