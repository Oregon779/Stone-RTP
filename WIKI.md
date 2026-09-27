# Stone RTP – Wiki

Stone RTP ist ein Zufallsteleport-Plugin (RTP) für **Paper-Server**. Spieler landen per `/rtp` an einer
sicheren, zufälligen Position – mit Welt-Auswahlmenü, animiertem Countdown, Cooldowns, optionalen
Vault-Kosten, Sperrzeiten und RTP-Zonen.

> Stand: Version **1.0.0** · Paper **26.2** · Java **25**

## Inhalt

- [Überblick](#überblick)
- [Installation und Voraussetzungen](#installation-und-voraussetzungen)
- [Schnellstart](#schnellstart)
- [Befehle](#befehle)
- [Berechtigungen](#berechtigungen)
- [Konfiguration (config.yml)](#konfiguration-configyml)
- [Welten, sichere Ortssuche und Nether](#welten-sichere-ortssuche-und-nether)
- [Sperrzeiten](#sperrzeiten)
- [RTP-Zonen](#rtp-zonen)
- [Countdown und Effekte](#countdown-und-effekte)
- [Nachrichten und Sprachen](#nachrichten-und-sprachen)
- [Dateien und Datenspeicherung](#dateien-und-datenspeicherung)
- [Performance-Tipps für große Server](#performance-tipps-für-große-server)
- [Häufige Fragen und Fehlerbehebung](#häufige-fragen-und-fehlerbehebung)
- [Für Entwickler](#für-entwickler)

---

## Überblick

| Feature | Kurz erklärt |
|---|---|
| `/rtp` | Zufallsteleport in eine Welt – direkt per Weltname oder über das Welt-Auswahlmenü. |
| Welt-Auswahlmenü | Truhen-GUI mit drei Buttons (Overworld, Nether, End), frei gestaltbar. |
| Sichere Ortssuche | Nie in Lava, Wasser, Kakteen, gesperrten Biomen oder außerhalb der Weltgrenze. Läuft im Hintergrund, der Server friert nicht ein. |
| Nether-Unterstützung | Landet in Höhlen *unter* der Nether-Decke, nie auf dem Dach – auch mit Modpack-/Datapack-Nethern wie Incendium. |
| Countdown (Warmup) | Mehrstufige Partikel- und Sound-Show vor dem Teleport, bricht bei Bewegung oder Schaden ab. |
| Cooldown | Wartezeit zwischen zwei Teleports, mit kürzeren Zeiten für Ränge (VIP, MVP …). |
| Kosten | Optionaler Preis pro Teleport über Vault, mit automatischer Rückerstattung bei Abbruch. |
| Sperrzeiten | Tägliche Zeitfenster ohne RTP, z. B. 14:00–18:00 Uhr. |
| RTP-Zonen | Bereiche, die jeden darin Stehenden nach X Sekunden zufällig teleportieren. |
| `/back` | Zurück zur Position vor dem letzten Zufallsteleport. |
| Auto-RTP beim Joinen | Optional: neue Spieler beim ersten Betreten automatisch teleportieren. |
| Mehrsprachig | Deutsch und Englisch mitgeliefert, alle Texte frei anpassbar (Farbcodes, Hex, MiniMessage). |
| Update-Checker | Prüft Modrinth auf neue Versionen und informiert Admins beim Joinen. |

---

## Installation und Voraussetzungen

**Voraussetzungen**

- Paper-Server **26.2** (Folia wird nicht unterstützt)
- Java **25**
- Optional: **Vault** plus ein Economy-Plugin (z. B. EssentialsX), wenn Teleports Geld kosten sollen

**Installation**

1. `Stone RTP-1.0.0.jar` in den Ordner `plugins/` legen.
2. Server **neu starten** (kein `/reload` und kein PlugMan – das lässt alte Plugin-Reste im Speicher).
3. Beim ersten Start entsteht der Ordner `plugins/StoneRTP/` mit `config.yml` und `languages/`.

**Updates**

JAR austauschen und neu starten. Neue Einstellungen werden automatisch in deine `config.yml` und die
Sprachdateien eingefügt – deine eigenen Werte werden dabei nie überschrieben. Welten und
Cooldown-Gruppen, die du bewusst gelöscht hast, bleiben gelöscht.

---

## Schnellstart

1. **Weltnamen prüfen:** In `config.yml` unter `worlds:` müssen die Namen genau deinen Weltordnern
   entsprechen (Standard: `world`, `world_nether`, `world_the_end`). Groß- und Kleinschreibung zählt.
2. **Suchbereich festlegen:** Pro Welt `center-x`/`center-z` (meist der Spawn) und
   `radius-min`/`radius-max` setzen.
3. **Sprache wählen:** `language: de` für Deutsch.
4. `/stonertp reload` ausführen und mit `/rtp` testen.

> Tipp: Als OP hast du automatisch die Bypass-Rechte – Countdown, Cooldown und Kosten greifen bei dir
> nicht. Zum Testen einen normalen Spieler-Account benutzen oder die Rechte entziehen.

---

## Befehle

### Für Spieler

| Befehl | Berechtigung | Beschreibung |
|---|---|---|
| `/rtp` | `stonertp.use` + `stonertp.gui` | Öffnet das Welt-Auswahlmenü. |
| `/rtp <welt>` | `stonertp.use` | Zufallsteleport direkt in diese Welt. |
| `/rtp cancel` | `stonertp.use` | Bricht einen laufenden Countdown ab und erstattet die Kosten. |
| `/back` | `stonertp.back` | Zurück zur Position vor dem letzten Zufallsteleport. |

Aliase für `/rtp`: `/wild`, `/randomtp`. Die Tab-Vervollständigung zeigt nur Welten, in denen RTP
aktiviert ist.

### Für Admins

| Befehl | Beschreibung |
|---|---|
| `/rtp player <spieler> [welt]` | Teleportiert einen anderen Spieler zufällig. Ohne Weltangabe in seine aktuelle Welt. |
| `/stonertp reload` | Lädt Config, Sprachdateien, Sperrzeiten und Zonen neu. |
| `/stonertp toggle <overworld\|nether\|end\|weltname> [on\|off]` | Schaltet RTP für eine Welt an oder aus. Ohne `on`/`off` wird der Zustand umgedreht. |
| `/stonertp blocktime add <von> <bis>` | Neue tägliche Sperrzeit, z. B. `add 14:00 18:00`, `add 14 18` oder `add 22:00-02:00`. |
| `/stonertp blocktime remove <nummer>` | Sperrzeit löschen (Nummer aus `list`). |
| `/stonertp blocktime list` | Alle Sperrzeiten und die aktuelle Uhrzeit des Servers anzeigen. |
| `/stonertp zone wand` | Gibt dir den Zonen-Auswahlstab. |
| `/stonertp zone create <name> [sekunden]` | Erstellt eine RTP-Zone aus deiner Auswahl. |
| `/stonertp zone delete <name>` | Löscht eine Zone. |
| `/stonertp zone list` | Listet alle Zonen auf. |
| `/stonertp checkupdate` | Sucht sofort auf Modrinth nach einer neuen Version. |
| `/stonertp help` | Zeigt die Befehlsübersicht. |

Alle `/stonertp`-Befehle (Alias `/srtp`) und `/rtp player` brauchen `stonertp.admin`.

**Besonderheiten von `/rtp player`:** Der Admin-Teleport kostet das Ziel nichts, ignoriert dessen
Cooldown und läuft ohne Countdown (das Ziel kann sich also nicht "herauslaufen"). Fehler, z. B. eine
unbekannte Welt, bekommt der Admin angezeigt. Während einer Sperrzeit funktioniert er nur, wenn der
**Admin** `stonertp.bypass.blocktime` hat (OPs haben das standardmäßig).

---

## Berechtigungen

| Berechtigung | Standard | Erlaubt |
|---|---|---|
| `stonertp.use` | alle | `/rtp` benutzen. |
| `stonertp.gui` | alle | Das Welt-Auswahlmenü öffnen (`/rtp` ohne Argumente). |
| `stonertp.back` | alle | `/back` benutzen. |
| `stonertp.admin` | OP | Alle Admin-Befehle, den Zonen-Stab, `/rtp player` und Update-Hinweise beim Joinen. |
| `stonertp.bypass.cooldown` | OP | Kein Cooldown zwischen Teleports. |
| `stonertp.bypass.warmup` | OP | Kein Countdown, sofortiger Teleport. |
| `stonertp.bypass.cost` | OP | Teleports sind kostenlos. |
| `stonertp.bypass.blocktime` | OP | RTP auch während Sperrzeiten. |
| `stonertp.bypass.zone` | **niemand** | RTP-Zonen teleportieren diesen Spieler nie (z. B. Team am Spawn). Auch OPs haben das nicht automatisch. |

### Cooldown-Gruppen (Rang-Rechte)

Zusätzlich kannst du in `config.yml` unter `cooldown.groups` eigene Rechte mit kürzerem Cooldown
anlegen. Mitgeliefert sind zwei Beispiele:

| Berechtigung | Cooldown |
|---|---|
| `stonertp.cooldown.vip` | 15 Sekunden |
| `stonertp.cooldown.mvp` | 5 Sekunden |

Hat ein Spieler mehrere passende Rechte, gilt der **kürzeste** Wert. Namen und Anzahl sind frei
wählbar. Beispiel mit LuckPerms:

```
/lp group vip permission set stonertp.cooldown.vip true
```

---

## Konfiguration (config.yml)

Die `config.yml` liegt unter `plugins/StoneRTP/config.yml`. Nach Änderungen `/stonertp reload`
ausführen. Jeder Abschnitt ist in der Datei selbst kommentiert – hier die wichtigsten Einstellungen.

### Sprache und Update-Checker

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `language` | `en` | Sprache aller Meldungen: `en`, `de` oder eine eigene (siehe [Nachrichten und Sprachen](#nachrichten-und-sprachen)). |
| `update-checker.enabled` | `true` | Auf Modrinth nach neuen Versionen suchen. |
| `update-checker.check-interval-minutes` | `60` | Prüfintervall (mindestens 5 Minuten). |

### Cooldown

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `cooldown.enabled` | `true` | Cooldown an/aus. |
| `cooldown.seconds` | `30` | Wartezeit zwischen zwei Teleports. |
| `cooldown.groups` | vip: 15, mvp: 5 | Kürzere Cooldowns für Ränge. Mit `groups: {}` komplett abschalten. |

Der Cooldown startet erst nach einem **erfolgreichen** Teleport. Er wird im Arbeitsspeicher gehalten
und nach einem Server-Neustart zurückgesetzt.

### Warmup (Countdown)

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `warmup.enabled` | `true` | Countdown vor dem Teleport. |
| `warmup.seconds` | `5` | Dauer in Sekunden. `0` schaltet den Countdown ab. |
| `warmup.cancel-on-move` | `true` | Abbruch, wenn sich der Spieler bewegt. |
| `warmup.move-cancel-threshold` | `0.6` | Erlaubte Bewegung in Blöcken, bevor abgebrochen wird. |
| `warmup.cancel-on-damage` | `true` | Abbruch, wenn der Spieler Schaden nimmt. |

Auch ein Teleport durch etwas anderes (z. B. `/home`, `/spawn`, Portal) bricht den Countdown ab.
Schaden, den ein anderes Plugin bereits verhindert hat (z. B. Spawn-Schutz), zählt nicht.

### Kosten (Vault)

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `cost.enabled` | `false` | Teleports kosten Geld. |
| `cost.amount` | `100.0` | Preis pro Teleport. |

Funktioniert nur mit **Vault und einem Economy-Plugin**, sonst werden Kosten ignoriert (die Konsole
sagt beim Start Bescheid). Abgebucht wird beim Start des Teleports. Zurückerstattet wird automatisch
bei: `/rtp cancel`, Abbruch durch Bewegung oder Schaden, Disconnect während des Countdowns, keiner
gefundenen sicheren Position, fehlgeschlagenem Teleport und Plugin-/Server-Stopp mitten im Countdown.
Lehnt das Economy-Plugin die Abbuchung ab, startet kein Teleport.

### Countdown-Anzeige (notification)

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `notification.type` | `ACTIONBAR` | Wo der Countdown erscheint: `CHAT`, `ACTIONBAR`, `TITLE` oder `BOSSBAR`. |
| `notification.messages.*` | – | Der Text pro Anzeigeart. Platzhalter: `{seconds}`. |
| `notification.title-timing.*` | 5 / 40 / 10 | Ein-, Anzeige- und Ausblendzeit in Ticks (nur `TITLE`). |
| `notification.bossbar.color` | `YELLOW` | `PINK`, `BLUE`, `RED`, `GREEN`, `YELLOW`, `PURPLE`, `WHITE`. |
| `notification.bossbar.style` | `NOTCHED_10` | `PROGRESS`, `NOTCHED_6`, `NOTCHED_10`, `NOTCHED_12`, `NOTCHED_20`. |

### Effekte

Siehe [Countdown und Effekte](#countdown-und-effekte).

### Sichere Ortssuche (safe-location)

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `max-attempts` | `30` | So viele Zufallspunkte werden höchstens geprüft. |
| `min-y` / `max-y` | `-58` / `300` | Erlaubter Höhenbereich für die Landestelle. |
| `avoid-water` | `true` | Nie im oder auf Wasser landen. |
| `avoid-lava` | `true` | Nie in oder auf Lava landen. |
| `respect-world-border` | `true` | Nur Punkte innerhalb der Weltgrenze. |
| `unsafe-materials` | Lava, Feuer, Kaktus … | Blöcke, auf denen man nie landet. |
| `blacklisted-biomes` | Ozeane, The Void | Biome, in denen man nie landet. `[]` erlaubt alle. |

Details dazu stehen unter [Welten, sichere Ortssuche und Nether](#welten-sichere-ortssuche-und-nether).

### Auto-RTP beim Joinen (on-join)

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `on-join.enabled` | `false` | Spieler beim Joinen automatisch teleportieren. |
| `on-join.only-first-join` | `true` | Nur beim allerersten Betreten des Servers. |
| `on-join.world` | `world` | Zielwelt. |
| `on-join.delay-ticks` | `40` | Verzögerung nach dem Login (20 Ticks = 1 Sekunde). |

Hinweis: Der Auto-RTP verhält sich wie ein normales `/rtp` – Countdown, Cooldown, Kosten und
Sperrzeiten gelten also auch hier.

### RTP-Zonen (zones)

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `zones.default-interval-seconds` | `10` | Wartezeit in der Zone, wenn beim Erstellen keine angegeben wird. |
| `zones.wand-material` | `BLAZE_ROD` | Item, das als Auswahlstab dient. |

Anleitung siehe [RTP-Zonen](#rtp-zonen).

### Welten (worlds)

| Einstellung | Bedeutung |
|---|---|
| `enabled` | RTP in dieser Welt an/aus (auch per `/stonertp toggle`). |
| `center-x` / `center-z` | Mittelpunkt des Suchbereichs, meist der Spawn. |
| `radius-min` | Mindestabstand zum Mittelpunkt. |
| `radius-max` | Höchstabstand zum Mittelpunkt. Muss größer als `radius-min` sein. |
| `search-mode` | `AUTO`, `SURFACE` oder `CAVE` – siehe unten. |

Eine Welt, die hier fehlt, ist für RTP gesperrt. Gelöschte Einträge bleiben gelöscht.

### Welt-Auswahlmenü (gui)

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `gui.enabled` | `true` | Menü an/aus. Aus: `/rtp` ohne Welt zeigt einen Hinweis. |
| `gui.size` | `27` | Anzahl Slots: 9, 18, 27, 36, 45 oder 54. |
| `gui.sound-open` / `gui.sound-click` | – | Sounds beim Öffnen und Klicken. |
| `gui.<overworld\|nether\|end>.world` | – | Welche Welt der Button öffnet. |
| `gui.<…>.slot` | 11 / 13 / 15 | Position im Menü (0 = oben links). |
| `gui.<…>.material` | Grasblock, Netherrack, Endstein | Symbol des Buttons. |
| `gui.<…>.head-texture` | leer | Optional ein Base64-Spielerkopf (z. B. von minecraft-heads.com) statt Material. |
| `gui.<…>.glow` | `false` | Verzauberungs-Glanz. |
| `gui.<…>.click-sound` | – | Eigener Klick-Sound pro Button. |
| `gui.filler.enabled` / `materials` / `corner-material` | an / schwarz / grau | Füllglas im Hintergrund, eigene Farbe für die vier Ecken. |

Deaktivierte oder nicht geladene Welten erscheinen automatisch als Barriere mit "Deaktiviert"-Text.
Die Beschriftungen stehen in den Sprachdateien (`gui.*`), dort sind die Platzhalter `{cost}` und
`{cooldown}` in der Lore möglich.

---

## Welten, sichere Ortssuche und Nether

**So wird ein Landeplatz gesucht:** Das Plugin wählt einen zufälligen Punkt in einem Ring um den
Mittelpunkt der Welt (zwischen `radius-min` und `radius-max`) und prüft ihn. Ein Platz ist sicher, wenn

- der Boden fest ist und nicht in `unsafe-materials` steht,
- Füße und Kopf frei sind (Luft, Gras oder Farn),
- kein Wasser oder keine Lava im Weg ist,
- das Biom nicht gesperrt ist und der Punkt innerhalb der Weltgrenze und `min-y`/`max-y` liegt.

Klappt es nicht, wird ein neuer Punkt probiert – bis zu `max-attempts`-mal. Die Suche startet schon zu
Beginn des Countdowns, sodass der Teleport am Ende sofort passiert. Vor dem Teleport werden die
umliegenden Chunks geladen, damit man nicht in eine leere Welt fällt.

### search-mode: Oberfläche oder Höhle

| Modus | Verhalten | Für |
|---|---|---|
| `AUTO` (Standard) | Wählt automatisch: `CAVE` für Welten mit Decke, sonst `SURFACE`. | Fast immer richtig. |
| `SURFACE` | Landet auf dem höchsten Block (Bäume werden ignoriert). | Overworld, End. |
| `CAVE` | Sucht von unten den ersten sicheren Höhlenboden **unter** der Decke. Landet nie auf dem Dach und nie direkt auf Grundgestein. | Nether und andere Welten mit Decke. |

`AUTO` erkennt auch **Custom- und Modpack-Nether** (z. B. Incendium) über die Dimensionseinstellung
"hat eine Decke". Sollte eine eigene Dimension mit Decke trotzdem nicht erkannt werden, setze für
diese Welt `search-mode: CAVE`.

---

## Sperrzeiten

Mit Sperrzeiten kannst du RTP zu bestimmten Tageszeiten verbieten, z. B. von 14 bis 18 Uhr.

```
/stonertp blocktime add 14:00 18:00
/stonertp blocktime list
/stonertp blocktime remove 1
```

- Zeiten über Mitternacht funktionieren: `22:00-02:00`.
- Mehrere Fenster sind möglich. Grenzen direkt aneinander (14–16 und 16–18), zeigt das Plugin als
  Wiedereröffnung korrekt 18:00 an.
- Gesperrt wird **alles**: `/rtp`, das Menü, Auto-RTP beim Joinen und RTP-Zonen. Spieler sehen, ab wann
  es wieder geht.
- Ausnahme: Spieler mit `stonertp.bypass.blocktime` (standardmäßig OPs).

**Zeitzone:** Das Plugin nutzt die Uhr des Servers. Viele Hoster laufen auf UTC statt deutscher Zeit –
`/stonertp blocktime list` zeigt dir, welche Uhrzeit das Plugin gerade sieht. Stimmt sie nicht, in der
`config.yml` eintragen:

```yaml
blocked-times:
  timezone: "Europe/Berlin"
```

Die Sperrzeiten werden in `config.yml` unter `blocked-times.periods` gespeichert und können dort auch
von Hand gepflegt werden (Format `"HH:MM-HH:MM"`).

---

## RTP-Zonen

Eine RTP-Zone ist ein Quader in der Welt (z. B. ein Bereich am Spawn). Wer darin stehen bleibt, wird
nach einer eingestellten Zeit zufällig teleportiert – in der Welt, in der die Zone liegt.

### Zone erstellen

1. `/stonertp zone wand` – du bekommst den Auswahlstab (bei vollem Inventar fällt er vor dir auf den Boden).
2. **Linksklick** auf einen Block setzt Ecke 1, **Rechtsklick** auf einen Block setzt Ecke 2. Die zwei
   Ecken sind gegenüberliegende Punkte des Quaders; der Chat zeigt dir die Größe an.
3. `/stonertp zone create <name> [sekunden]`, z. B. `/stonertp zone create spawn 10`.

Zonennamen dürfen Buchstaben, Zahlen, `-` und `_` enthalten (maximal 32 Zeichen). Die Höhe zählt mit:
Wähle die Ecken so, dass Spieler im Bereich auch tatsächlich drinstehen.

### So verhält sich eine Zone

- Jeder Spieler hat seinen **eigenen Countdown** in der Actionbar, sobald er die Zone betritt.
- Wer die Zone verlässt, bekommt einen Hinweis; der Countdown beginnt beim nächsten Betreten neu.
- Zonen-Teleports sind **kostenlos**, ignorieren den Cooldown und haben keinen zusätzlichen Countdown.
  Der Teleport setzt aber den Cooldown für ein anschließendes `/rtp`.
- Während einer Sperrzeit pausiert die Zone ("pausiert bis …").
- Die Welt der Zone muss unter `worlds:` stehen und aktiviert sein, sonst bleibt die Zone inaktiv
  (beim Erstellen gibt es eine Warnung).
- Spieler mit `stonertp.bypass.zone` werden nie teleportiert.

### zones.yml

Zonen werden in `plugins/StoneRTP/zones.yml` gespeichert:

```yaml
zones:
  spawn:
    world: world
    min-x: -10
    min-y: 60
    min-z: -10
    max-x: 10
    max-y: 80
    max-z: 10
    interval-seconds: 10
```

Von Hand geändert werden kann sie auch – danach `/stonertp reload`.

---

## Countdown und Effekte

Während des Countdowns baut sich eine mehrstufige Partikel-Show um den Spieler auf. Jede Stufe fügt
eine Ebene hinzu:

| Stufe | Zeitpunkt (bei 5 s) | Was passiert |
|---|---|---|
| 1 | Sekunde 1–2 | Boden-Ring und Doppel-Helix, steigender Harfenton. |
| 2 | Sekunde 3 | Hüft- und Kopfringe, aufsteigende Funken, einmaliger Glockenton und Summen. |
| 3 | Sekunde 4 | Farbwechsel zu Violett/Gold, doppelte Drehgeschwindigkeit, einwärts drehender Wirbel. |
| 4 | Sekunde 5 | Alles zieht sich zusammen, eine weiße Säule schießt durch den Spieler. |

Beim **Abflug** gibt es einen Lichtblitz, einen Feuerwerksring und einen Sound. Bei der **Ankunft**
folgen Blitz, Partikelexplosion, Lichtsäule, sich ausdehnende Ringe, eine Helix und ein kurzer
Partikelregen.

Alles ist unter `effects:` in der `config.yml` einstellbar: Anzahl der Punkte, Radien, Höhen,
Drehgeschwindigkeiten, Farben (Hex, z. B. `"#FFD166"`), Partikelarten und Sounds. Jede Ebene lässt sich
mit `enabled: false` abschalten.

- **Partikel:** Namen wie `END_ROD`, `FLASH`, `REVERSE_PORTAL`.
- **Sounds:** der Konstantenname (`BLOCK_NOTE_BLOCK_HARP`) oder der Minecraft-Schlüssel
  (`block.note_block.harp`). Ungültige Namen werden einmal in der Konsole gemeldet und durch den
  Standard ersetzt.
- **Sichtbarkeit:** Die Partikel sehen alle Spieler im Umkreis von 32 Blöcken – wie bei Vanilla.

---

## Nachrichten und Sprachen

Alle Texte liegen in `plugins/StoneRTP/languages/<sprache>/messages.yml` (`en` und `de` sind dabei).
Nur die Countdown-Texte stehen in der `config.yml` unter `notification.messages`, weil dort auch
festgelegt wird, *wo* der Countdown erscheint.

**Eigene Sprache:** Ordner `languages/fr/` mit einer `messages.yml` anlegen und `language: fr` setzen.
Fehlende Texte werden automatisch aus der englischen Datei genommen.

**Farben und Formatierung** – alles in derselben Nachricht kombinierbar:

| Format | Beispiel |
|---|---|
| Legacy-Farbcodes | `&a`, `&l`, `&7` |
| Hex-Farben | `&#FF00AA` |
| MiniMessage | `<gradient:#FFC24A:#FF7A00>Text</gradient>`, `<bold>` |

**Die wichtigsten Platzhalter:**

| Platzhalter | Wo |
|---|---|
| `{seconds}` | Countdown-Texte, Zonen-Countdown |
| `{world}`, `{x}`, `{y}`, `{z}` | Erfolgsmeldung nach dem Teleport |
| `{time}` | Cooldown-Meldung (z. B. "1m 24s") |
| `{cost}` | Kosten-Meldungen, GUI-Lore |
| `{cooldown}` | GUI-Lore |
| `{until}` | Sperrzeit-Meldungen |
| `{player}` | Meldungen zu `/rtp player` |
| `{attempts}` | "Keine sichere Position gefunden" |
| `{version}`, `{current}`, `{behind}` | Update-Hinweis |

Was Spieler selbst eintippen (z. B. einen Weltnamen), wird nie als Formatierung ausgewertet.

---

## Dateien und Datenspeicherung

| Datei / Daten | Inhalt | Dauerhaft? |
|---|---|---|
| `config.yml` | Alle Einstellungen, Sperrzeiten, Welt-Schalter | ja |
| `languages/*/messages.yml` | Texte | ja |
| `zones.yml` | RTP-Zonen | ja |
| Cooldowns | Wer wann zuletzt teleportiert wurde | nein – Reset bei Neustart |
| `/back`-Positionen | Letzte Startposition je Spieler (max. 5000) | nein – Reset bei Neustart |

**Sicherheit beim Speichern:** Dateien werden erst in eine Zwischendatei geschrieben und dann ersetzt.
Ein Absturz mitten im Speichern hinterlässt also nie eine leere Datei.

**Tippfehler in einer Datei:** Enthält `config.yml`, eine Sprachdatei oder `zones.yml` einen
YAML-Fehler, wird die Datei **nicht angefasst**. Die Konsole meldet den Fehler, und das Plugin läuft mit
den eingebauten Standardwerten weiter bzw. hält Zonen vorübergehend an. Fehler beheben und
`/stonertp reload` ausführen. Solange `config.yml` fehlerhaft ist, werden Änderungen per Befehl nicht
gespeichert, damit deine Datei nicht überschrieben wird.

---

## Performance-Tipps für große Server

Das Plugin ist für Server mit mehreren hundert Spielern ausgelegt: Die Ortssuche läuft im Hintergrund,
Teleports und Chunk-Laden sind asynchron, abgebrochene Suchen werden sofort beendet, und Partikel
gehen nur an Spieler in der Nähe. Für sehr volle Server:

- **Suchbereich vorgenerieren** (z. B. mit dem Plugin *Chunky*, Radius = `radius-max`). Das Erzeugen
  neuer Chunks ist der mit Abstand teuerste Teil eines Zufallsteleports.
- **Partikel reduzieren**, wenn viele Spieler gleichzeitig am Spawn teleportieren:
  `effects.countdown.update-interval-ticks: 3` oder `4` spart ein bis zwei Drittel der Partikel,
  weniger `points` pro Ebene oder einzelne Ebenen mit `enabled: false` noch mehr.
- **Nicht zu viele Biome sperren** und `max-attempts` nicht unnötig hoch setzen – jeder Fehlversuch
  lädt einen weiteren Chunk.
- Zum Messen den Profiler *spark* verwenden.

---

## Häufige Fragen und Fehlerbehebung

**Spieler landen auf der Nether-Decke.**
Das ist ab Version 1.0.0 behoben. Wird eine eigene Dimension nicht als "mit Decke" erkannt, für diese
Welt `search-mode: CAVE` setzen.

**"Keine sichere Position gefunden".**
Der Bereich enthält zu wenig sichere Stellen: `max-attempts` erhöhen, weniger Biome sperren (in
Ozean-lastigen Gegenden!), `radius-min`/`radius-max` und `min-y`/`max-y` prüfen, Weltgrenze beachten.

**Die Welt ist "deaktiviert", obwohl sie existiert.**
Der Weltname unter `worlds:` muss exakt dem Ordnernamen entsprechen (auch Groß-/Kleinschreibung),
`enabled: true` sein und `radius-max` größer als `radius-min`.

**Kosten werden nicht abgezogen.**
Vault *und* ein Economy-Plugin müssen installiert sein, `cost.enabled: true`. OPs haben
`stonertp.bypass.cost` und zahlen nie.

**Bei mir gibt es keinen Countdown und keinen Cooldown.**
Als OP hast du die Bypass-Rechte. Mit einem normalen Account testen.

**Eine Zone teleportiert nicht.**
Prüfen: Welt unter `worlds:` aktiviert? Läuft gerade eine Sperrzeit? Hat der Spieler
`stonertp.bypass.zone`? Steht er wirklich im Quader (auch in der Höhe)? `/stonertp zone list` zeigt die
Koordinaten.

**Die Sperrzeit gilt zur falschen Uhrzeit.**
`blocked-times.timezone` auf `"Europe/Berlin"` setzen (siehe [Sperrzeiten](#sperrzeiten)).

**Meine Config-Änderungen wirken nicht, in der Konsole steht ein Fehler.**
YAML-Syntaxfehler (oft Einrückung oder fehlende Anführungszeichen). Das Plugin nutzt solange die
Standardwerte, deine Datei bleibt unverändert. Fehler beheben, dann `/stonertp reload`.

**Spieler ohne Rechte sehen "Unknown or incomplete command".**
Das ist normales Paper-Verhalten: Befehle ohne Berechtigung werden ausgeblendet.

**Nach einem Neustart können alle sofort wieder `/rtp` benutzen.**
Cooldowns werden nicht gespeichert und beginnen nach einem Neustart neu.

---

## Für Entwickler

- **Build:** Java 25 und Maven, `mvn package` erzeugt `target/Stone RTP-1.0.0.jar`.
- **Tests:** `mvn package` führt automatisch die Test-Suite aus (JUnit + MockBukkit, simuliert einen
  kompletten Paper-Server mit Spielern, Welten und Vault-Economy).
- **Abhängigkeiten:** `paper-api` und `VaultAPI` liegen nicht auf Maven Central, sondern auf
  `repo.papermc.io` bzw. `jitpack.io`. Ohne Zugriff darauf müssen sie mit `mvn install:install-file`
  lokal installiert werden.
- Architektur, Konventionen und Build-Details stehen in [`CLAUDE.md`](CLAUDE.md).
