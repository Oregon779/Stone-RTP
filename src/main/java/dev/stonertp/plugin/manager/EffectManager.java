package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.StoneRTP;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class EffectManager {

    private static final double STAGE2_START = 0.4;
    private static final double STAGE3_START = 0.6;
    private static final double STAGE4_START = 0.8;
    // Same radius World#spawnParticle uses for non-forced particles, so exactly the same players see the effects.
    private static final double VIEW_DISTANCE = 32.0;

    private final StoneRTP plugin;
    private final Map<String, Integer> rotation = new HashMap<>();
    private final Set<Particle> warnedParticles = new HashSet<>();
    private final Map<String, List<Color>> colorCache = new HashMap<>();

    public EffectManager(StoneRTP plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        colorCache.clear();
        warnedParticles.clear();
    }

    public boolean isCountdownEnabled() {
        return plugin.getConfigManager().getBoolean("effects.countdown.enabled", true);
    }

    public long getCountdownUpdateIntervalTicks() {
        return Math.max(1, plugin.getConfigManager().getInt("effects.countdown.update-interval-ticks", 2));
    }

    private double progressFor(int secondsLeft, int totalSeconds) {
        if (totalSeconds <= 1) {
            return 1.0;
        }
        double progress = 1.0 - ((double) secondsLeft / (double) totalSeconds);
        return Math.max(0.0, Math.min(1.0, progress));
    }

    // World#spawnParticle scans every player in the world per particle; resolving viewers once per frame is far cheaper.
    private Collection<Player> viewersNear(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return List.of();
        }
        double maxDistanceSquared = VIEW_DISTANCE * VIEW_DISTANCE;
        return world.getNearbyPlayers(center, VIEW_DISTANCE,
                viewer -> viewer.getLocation().distanceSquared(center) <= maxDistanceSquared);
    }

    public void playCountdownSound(Player player, int secondsLeft, int totalSeconds) {
        ConfigManager cfg = plugin.getConfigManager();
        double progress = progressFor(secondsLeft, totalSeconds);
        Location loc = player.getLocation();

        Sound harp = cfg.getSound("effects.countdown.stage1.sound", Sound.BLOCK_NOTE_BLOCK_HARP);
        float harpVolume = (float) cfg.getDouble("effects.countdown.stage1.sound-volume", 0.8);
        float harpPitch = (float) (0.5 + 0.5 * progress);
        player.playSound(loc, harp, harpVolume, harpPitch);

        if (progress >= STAGE2_START) {
            String key = player.getUniqueId() + ":stage2-signal";
            if (!rotation.containsKey(key)) {
                rotation.put(key, 1);
                Sound chime = cfg.getSound("effects.countdown.stage2.signal-sound", Sound.BLOCK_AMETHYST_BLOCK_CHIME);
                float chimeVolume = (float) cfg.getDouble("effects.countdown.stage2.signal-volume", 0.9);
                player.playSound(loc, chime, chimeVolume, 1.0f);
            }
            double hum = (progress - STAGE2_START) / (1.0 - STAGE2_START);
            Sound beacon = cfg.getSound("effects.countdown.stage2.hum-sound", Sound.BLOCK_BEACON_POWER_SELECT);
            float beaconVolume = (float) (cfg.getDouble("effects.countdown.stage2.hum-volume", 0.6) * (0.4 + 0.6 * hum));
            player.playSound(loc, beacon, beaconVolume, 1.0f);
        }

        if (progress >= STAGE3_START) {
            double local = (progress - STAGE3_START) / (1.0 - STAGE3_START);
            Sound illusioner = cfg.getSound("effects.countdown.stage3.sound", Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS);
            float volume = (float) (cfg.getDouble("effects.countdown.stage3.sound-volume", 0.7) * (0.3 + 0.7 * local));
            player.playSound(loc, illusioner, volume, 1.1f);
        }
    }

    public void playCountdownFrame(Player player, int secondsLeft, int totalSeconds) {
        if (!isCountdownEnabled()) {
            return;
        }
        Collection<Player> viewers = viewersNear(player.getLocation());
        if (viewers.isEmpty()) {
            return;
        }
        double progress = progressFor(secondsLeft, totalSeconds);
        List<Color> colors = progress < STAGE3_START ? colors("effects.countdown.stage1.colors") : colors("effects.countdown.stage3.colors");
        double speedMultiplier = progress >= STAGE3_START ? 2.0 : 1.0;
        double collapse = collapseFactor(progress);

        drawGroundRing(viewers, player, colors, speedMultiplier, collapse);
        drawHelix(viewers, player, colors, speedMultiplier, collapse);

        if (progress >= STAGE2_START) {
            drawHipHeadRings(viewers, player, colors, speedMultiplier, collapse);
            drawRisingSparks(viewers, player, collapse);
        }

        if (progress >= STAGE3_START) {
            double vortexProgress = (progress - STAGE3_START) / (1.0 - STAGE3_START);
            drawImplosionVortex(viewers, player, vortexProgress);
        }

        if (progress >= STAGE4_START) {
            drawWhiteColumn(viewers, player);
        }
    }

    private double collapseFactor(double progress) {
        if (progress < STAGE4_START) {
            return 1.0;
        }
        double local = (progress - STAGE4_START) / (1.0 - STAGE4_START);
        return Math.max(0.05, 1.0 - local * 0.95);
    }

    private List<Color> colors(String path) {
        return colorCache.computeIfAbsent(path, key -> {
            List<Color> parsed = new ArrayList<>();
            for (String hex : plugin.getConfigManager().getStringList(key)) {
                parsed.add(parseColor(hex, Color.WHITE));
            }
            return List.copyOf(parsed);
        });
    }

    private void drawGroundRing(Collection<Player> viewers, Player player, List<Color> colors, double speedMultiplier, double collapse) {
        ConfigManager cfg = plugin.getConfigManager();
        String path = "effects.countdown.ground-ring";
        int points = Math.max(6, cfg.getInt(path + ".points", 32));
        double radius = cfg.getDouble(path + ".radius", 1.5) * collapse;
        double heightOffset = cfg.getDouble(path + ".height-offset", 0.05);
        int rotationStep = (int) Math.round(cfg.getInt(path + ".rotation-speed-degrees", 6) * speedMultiplier);
        double size = cfg.getDouble(path + ".size", 1.0);

        int angle = advanceRotation(player, "ground", rotationStep);
        drawRing(viewers, player, colors, points, radius, heightOffset, angle, Particle.END_ROD, size);
    }

    private void drawHelix(Collection<Player> viewers, Player player, List<Color> colors, double speedMultiplier, double collapse) {
        ConfigManager cfg = plugin.getConfigManager();
        String path = "effects.countdown.helix";
        int strands = Math.max(1, cfg.getInt(path + ".strands", 2));
        int pointsPerStrand = Math.max(4, cfg.getInt(path + ".points", 18));
        double loops = cfg.getDouble(path + ".loops", 2.0);
        double radius = cfg.getDouble(path + ".radius", 0.6) * collapse;
        double height = cfg.getDouble(path + ".height", 2.2);
        int rotationStep = (int) Math.round(cfg.getInt(path + ".rotation-speed-degrees", 16) * speedMultiplier);
        double size = cfg.getDouble(path + ".size", 0.8);

        int rotationAngle = advanceRotation(player, "helix", rotationStep);
        Location base = player.getLocation();
        Location point = base.clone();

        for (int s = 0; s < strands; s++) {
            double strandOffset = s * (360.0 / strands);
            for (int i = 0; i < pointsPerStrand; i++) {
                double t = (double) i / pointsPerStrand;
                double angle = Math.toRadians(rotationAngle + strandOffset + 360.0 * loops * t);
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                double y = t * height * (0.4 + 0.6 * collapse);
                point.setX(base.getX() + x);
                point.setY(base.getY() + y);
                point.setZ(base.getZ() + z);
                Color color = cyclingColor(colors, i, Color.WHITE);
                spawnDust(viewers, point, color, size);
            }
        }
    }

    private void drawHipHeadRings(Collection<Player> viewers, Player player, List<Color> colors, double speedMultiplier, double collapse) {
        ConfigManager cfg = plugin.getConfigManager();

        String hp = "effects.countdown.hip-ring";
        int hipPoints = Math.max(6, cfg.getInt(hp + ".points", 24));
        double hipRadius = cfg.getDouble(hp + ".radius", 1.0) * collapse;
        double hipHeight = cfg.getDouble(hp + ".height", 0.9);
        int hipRotationStep = -(int) Math.round(cfg.getInt(hp + ".rotation-speed-degrees", 10) * speedMultiplier);
        double hipSize = cfg.getDouble(hp + ".size", 1.0);
        int hipAngle = advanceRotation(player, "hip", hipRotationStep);
        drawRing(viewers, player, colors, hipPoints, hipRadius, hipHeight, hipAngle, null, hipSize);

        String hd = "effects.countdown.head-ring";
        int headPoints = Math.max(6, cfg.getInt(hd + ".points", 24));
        double headRadius = cfg.getDouble(hd + ".radius", 0.8) * collapse;
        double headHeight = cfg.getDouble(hd + ".height", 1.9);
        int headRotationStep = -(int) Math.round(cfg.getInt(hd + ".rotation-speed-degrees", 10) * speedMultiplier);
        double headSize = cfg.getDouble(hd + ".size", 1.0);
        int headAngle = advanceRotation(player, "head", headRotationStep);
        drawRing(viewers, player, colors, headPoints, headRadius, headHeight, headAngle, null, headSize);
    }

    private void drawRisingSparks(Collection<Player> viewers, Player player, double collapse) {
        ConfigManager cfg = plugin.getConfigManager();
        String path = "effects.countdown.sparks";
        if (!cfg.getBoolean(path + ".enabled", true)) {
            return;
        }
        Particle particle = cfg.getParticle(path + ".particle", Particle.ELECTRIC_SPARK);
        int columns = Math.max(2, cfg.getInt(path + ".columns", 4));
        int stepsPerColumn = Math.max(2, cfg.getInt(path + ".steps", 4));
        double radius = cfg.getDouble("effects.countdown.helix.radius", 0.6) * collapse;
        double height = cfg.getDouble(path + ".height", 2.2) * (0.4 + 0.6 * collapse);

        int rotationAngle = advanceRotation(player, "sparks", cfg.getInt(path + ".rotation-speed-degrees", 20));
        Location base = player.getLocation();
        Location point = base.clone();

        for (int c = 0; c < columns; c++) {
            double angle = Math.toRadians(rotationAngle + (360.0 / columns) * c);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            for (int step = 0; step < stepsPerColumn; step++) {
                double t = (double) step / stepsPerColumn;
                point.setX(base.getX() + x);
                point.setY(base.getY() + t * height);
                point.setZ(base.getZ() + z);
                safeSpawn(viewers, particle, point, 1, 0, 0, 0, 0);
            }
        }
    }

    private void drawImplosionVortex(Collection<Player> viewers, Player player, double vortexProgress) {
        ConfigManager cfg = plugin.getConfigManager();
        String path = "effects.countdown.vortex";
        if (!cfg.getBoolean(path + ".enabled", true)) {
            return;
        }
        Particle particle = cfg.getParticle(path + ".particle", Particle.REVERSE_PORTAL);
        int points = Math.max(8, cfg.getInt(path + ".points", 30));
        double maxRadius = cfg.getDouble(path + ".start-radius", 3.0);
        double heightOffset = cfg.getDouble(path + ".height-offset", 1.0);
        int rotationStep = cfg.getInt(path + ".rotation-speed-degrees", 28);

        double radius = Math.max(0.1, maxRadius * (1.0 - vortexProgress));
        int scaledStep = (int) Math.round(rotationStep * (1.0 + vortexProgress * 2.0));
        int rotationAngle = advanceRotation(player, "vortex", scaledStep);
        Location base = player.getLocation().add(0, heightOffset, 0);
        Location point = base.clone();

        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians(rotationAngle + (360.0 / points) * i);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            point.setX(base.getX() + x);
            point.setZ(base.getZ() + z);
            safeSpawn(viewers, particle, point, 1, 0, 0, 0, 0);
        }
    }

    private void drawWhiteColumn(Collection<Player> viewers, Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        String path = "effects.countdown.column";
        if (!cfg.getBoolean(path + ".enabled", true)) {
            return;
        }
        Particle particle = cfg.getParticle(path + ".particle", Particle.END_ROD);
        int points = Math.max(4, cfg.getInt(path + ".points", 14));
        double belowFeet = cfg.getDouble(path + ".below", 1.0);
        double aboveHead = cfg.getDouble(path + ".above", 2.5);

        Location base = player.getLocation();
        Location point = base.clone();
        double totalHeight = belowFeet + aboveHead;
        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            point.setY(base.getY() + (-belowFeet + t * totalHeight));
            safeSpawn(viewers, particle, point, 1, 0.02, 0, 0.02, 0);
        }
    }

    private void drawRing(Collection<Player> viewers, Player player, List<Color> colors, int points, double radius, double heightOffset,
                          int rotationAngle, Particle secondaryParticle, double size) {
        Location base = player.getLocation().add(0, heightOffset, 0);
        Location point = base.clone();
        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians(rotationAngle + (360.0 / points) * i);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            point.setX(base.getX() + x);
            point.setZ(base.getZ() + z);
            if (secondaryParticle != null && i % 4 == 0) {
                safeSpawn(viewers, secondaryParticle, point, 1, 0, 0, 0, 0);
            } else {
                Color color = cyclingColor(colors, i, Color.WHITE);
                spawnDust(viewers, point, color, size);
            }
        }
    }

    public void playDeparture(Location origin) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.getBoolean("effects.departure.enabled", true)) {
            return;
        }
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Collection<Player> viewers = viewersNear(origin);

        if (cfg.getBoolean("effects.departure.flash.enabled", true)) {
            Particle flash = cfg.getParticle("effects.departure.flash.particle", Particle.FLASH);
            int count = cfg.getInt("effects.departure.flash.count", 1);
            safeSpawn(viewers, flash, origin.clone().add(0, 1, 0), count, 0.1, 0.1, 0.1, 0);
        }

        if (cfg.getBoolean("effects.departure.ring.enabled", true)) {
            Particle ring = cfg.getParticle("effects.departure.ring.particle", Particle.FIREWORK);
            int points = cfg.getInt("effects.departure.ring.points", 36);
            double radius = cfg.getDouble("effects.departure.ring.radius", 1.6);
            for (int i = 0; i < points; i++) {
                double angle = Math.toRadians((360.0 / points) * i);
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                Location point = origin.clone().add(x, 0.1, z);
                safeSpawn(viewers, ring, point, 1, 0, 0, 0, 0.05);
            }
        }

        if (cfg.getBoolean("effects.departure.sound-enabled", true)) {
            Sound totem = cfg.getSound("effects.departure.totem-sound", Sound.ITEM_TOTEM_USE);
            float totemVolume = (float) cfg.getDouble("effects.departure.totem-volume", 1.0);
            world.playSound(origin, totem, totemVolume, 1.0f);

            Sound teleport = cfg.getSound("effects.departure.teleport-sound", Sound.ENTITY_ENDERMAN_TELEPORT);
            float teleportVolume = (float) cfg.getDouble("effects.departure.teleport-volume", 1.0);
            world.playSound(origin, teleport, teleportVolume, 1.0f);
        }
    }

    public void clearRotation(Player player) {
        String prefix = player.getUniqueId() + ":";
        rotation.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void playArrival(Player player) {
        clearRotation(player);
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.getBoolean("effects.arrival.enabled", true)) {
            return;
        }

        List<Color> colors = colors("effects.arrival.colors");
        Collection<Player> viewers = viewersNear(player.getLocation());

        playArrivalFlashAndBurst(viewers, player);
        playArrivalSounds(player);
        playLightPillar(viewers, player, colors);
        scheduleArrivalRingExpansion(player, colors);
        playArrivalHelixColumn(viewers, player, colors);
        scheduleArrivalRain(player, colors);
    }

    private void playArrivalFlashAndBurst(Collection<Player> viewers, Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        Location loc = player.getLocation().add(0, 1, 0);

        Particle flash = cfg.getParticle("effects.arrival.flash-particle", Particle.FLASH);
        int flashCount = cfg.getInt("effects.arrival.flash-count", 1);
        safeSpawn(viewers, flash, loc, flashCount, 0.1, 0.1, 0.1, 0);

        Particle burstFlash = cfg.getParticle("effects.arrival.burst-flash-particle", Particle.EXPLOSION_EMITTER);
        int burstFlashCount = cfg.getInt("effects.arrival.burst-flash-count", 1);
        safeSpawn(viewers, burstFlash, loc, burstFlashCount, 0.1, 0.1, 0.1, 0);

        String path = "effects.arrival.burst";
        if (!cfg.getBoolean(path + ".enabled", true)) {
            return;
        }
        Particle sparkle = cfg.getParticle(path + ".particle", Particle.TOTEM_OF_UNDYING);
        int count = cfg.getInt(path + ".count", 80);
        double spread = cfg.getDouble(path + ".spread", 0.6);
        double upwardVelocity = cfg.getDouble(path + ".upward-velocity", 0.3);
        safeSpawn(viewers, sparkle, loc, count, spread, spread, spread, upwardVelocity);
    }

    private void playLightPillar(Collection<Player> viewers, Player player, List<Color> colors) {
        ConfigManager cfg = plugin.getConfigManager();
        String path = "effects.arrival.pillar";
        if (!cfg.getBoolean(path + ".enabled", true)) {
            return;
        }
        int rings = Math.max(4, cfg.getInt(path + ".rings", 10));
        int pointsPerRing = Math.max(4, cfg.getInt(path + ".points-per-ring", 8));
        double radius = cfg.getDouble(path + ".radius", 0.5);
        double height = cfg.getDouble(path + ".height", 4.0);
        double size = cfg.getDouble(path + ".size", 1.3);

        Location base = player.getLocation();
        for (int ring = 0; ring < rings; ring++) {
            double t = (double) ring / rings;
            double y = t * height;
            for (int i = 0; i < pointsPerRing; i++) {
                double angle = Math.toRadians((360.0 / pointsPerRing) * i);
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                Location point = base.clone().add(x, y, z);
                Color color = cyclingColor(colors, ring, Color.WHITE);
                spawnDust(viewers, point, color, size);
            }
        }
    }

    private void playArrivalSounds(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        Location loc = player.getLocation();

        Sound chime = cfg.getSound("effects.arrival.chime-sound", Sound.BLOCK_AMETHYST_BLOCK_CHIME);
        float chimeVolume = (float) cfg.getDouble("effects.arrival.chime-volume", 0.9);
        player.playSound(loc, chime, chimeVolume, 1.0f);

        if (cfg.getBoolean("effects.arrival.settle-sound-enabled", true)) {
            Sound settle = cfg.getSound("effects.arrival.settle-sound", Sound.BLOCK_AMETHYST_CLUSTER_BREAK);
            float settleVolume = (float) cfg.getDouble("effects.arrival.settle-volume", 0.5);
            long delay = cfg.getInt("effects.arrival.settle-delay-ticks", 8);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.playSound(player.getLocation(), settle, settleVolume, 1.3f);
                }
            }, delay);
        }
    }

    private void scheduleArrivalRingExpansion(Player player, List<Color> colors) {
        ConfigManager cfg = plugin.getConfigManager();
        String path = "effects.arrival.ring";
        if (!cfg.getBoolean(path + ".enabled", true)) {
            return;
        }
        int points = Math.max(8, cfg.getInt(path + ".points", 36));
        double maxRadius = cfg.getDouble(path + ".max-radius", 3.0);
        double ringGap = cfg.getDouble(path + ".ring-gap", 0.6);
        int steps = Math.max(2, cfg.getInt(path + ".steps", 6));
        long stepDelayTicks = Math.max(1, cfg.getInt(path + ".step-delay-ticks", 2));
        double size = cfg.getDouble(path + ".size", 1.0);

        for (int step = 1; step <= steps; step++) {
            double outerRadius = maxRadius * step / steps;
            double innerRadius = Math.max(0, outerRadius - ringGap);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Collection<Player> viewers = viewersNear(player.getLocation());
                drawExpandingRing(viewers, player, colors, points, outerRadius, size);
                if (innerRadius > 0.2) {
                    drawExpandingRing(viewers, player, colors, points, innerRadius, size);
                }
            }, step * stepDelayTicks);
        }
    }

    private void drawExpandingRing(Collection<Player> viewers, Player player, List<Color> colors, int points, double radius, double size) {
        Location base = player.getLocation();
        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians((360.0 / points) * i);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location point = base.clone().add(x, 0.15, z);
            Color color = cyclingColor(colors, i, Color.WHITE);
            spawnDust(viewers, point, color, size);
        }
    }

    private void playArrivalHelixColumn(Collection<Player> viewers, Player player, List<Color> colors) {
        ConfigManager cfg = plugin.getConfigManager();
        String path = "effects.arrival.helix";
        if (!cfg.getBoolean(path + ".enabled", true)) {
            return;
        }
        int strands = Math.max(1, cfg.getInt(path + ".strands", 3));
        int pointsPerStrand = Math.max(4, cfg.getInt(path + ".points", 36));
        double loops = cfg.getDouble(path + ".loops", 3.0);
        double radius = cfg.getDouble(path + ".radius", 0.9);
        double height = cfg.getDouble(path + ".height", 3.5);
        double size = cfg.getDouble(path + ".size", 1.1);

        Location base = player.getLocation();
        for (int s = 0; s < strands; s++) {
            double strandOffset = s * (360.0 / strands);
            for (int i = 0; i < pointsPerStrand; i++) {
                double t = (double) i / pointsPerStrand;
                double angle = Math.toRadians(strandOffset + 360.0 * loops * t);
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                double y = t * height;
                Location point = base.clone().add(x, y, z);
                Color color = cyclingColor(colors, i, Color.WHITE);
                spawnDust(viewers, point, color, size);
            }
        }
    }

    private void scheduleArrivalRain(Player player, List<Color> colors) {
        ConfigManager cfg = plugin.getConfigManager();
        String path = "effects.arrival.rain";
        if (!cfg.getBoolean(path + ".enabled", true)) {
            return;
        }
        Particle particle = cfg.getParticle(path + ".particle", Particle.END_ROD);
        int waves = Math.max(1, cfg.getInt(path + ".waves", 14));
        int perWave = Math.max(1, cfg.getInt(path + ".particles-per-wave", 6));
        double radius = cfg.getDouble(path + ".radius", 1.5);
        double startHeight = cfg.getDouble(path + ".start-height", 3.5);
        long waveDelayTicks = Math.max(1, cfg.getInt(path + ".wave-delay-ticks", 2));

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int wave = 1; wave <= waves; wave++) {
            int waveIndex = wave;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Location base = player.getLocation();
                Collection<Player> viewers = viewersNear(base);
                for (int i = 0; i < perWave; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double r = random.nextDouble() * radius;
                    double x = Math.cos(angle) * r;
                    double z = Math.sin(angle) * r;
                    Location point = base.clone().add(x, startHeight, z);
                    if (i % 2 == 0) {
                        safeSpawn(viewers, particle, point, 0, 0, -0.03, 0, 1);
                    } else {
                        Color color = cyclingColor(colors, waveIndex + i, Color.WHITE);
                        safeSpawn(viewers, Particle.DUST, point, 0, 0, -0.03, 0, 1,
                                new Particle.DustOptions(color, 1.2f));
                    }
                }
            }, wave * waveDelayTicks);
        }
    }

    private void spawnDust(Collection<Player> viewers, Location point, Color color, double size) {
        safeSpawn(viewers, Particle.DUST, point, 1, 0, 0, 0, 0, new Particle.DustOptions(color, (float) size));
    }

    private void safeSpawn(Collection<Player> viewers, Particle particle, Location point, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        safeSpawn(viewers, particle, point, count, offsetX, offsetY, offsetZ, extra, null);
    }

    private void safeSpawn(Collection<Player> viewers, Particle particle, Location point, int count, double offsetX, double offsetY,
                           double offsetZ, double extra, Object explicitData) {
        if (viewers.isEmpty()) {
            return;
        }
        try {
            Object data = explicitData;
            if (data == null) {
                Class<?> dataType = particle.getDataType();
                if (dataType == Color.class) {
                    data = Color.WHITE;
                } else if (dataType == Particle.DustOptions.class) {
                    data = new Particle.DustOptions(Color.WHITE, 1.0f);
                }
            }
            for (Player viewer : viewers) {
                if (data != null) {
                    viewer.spawnParticle(particle, point, count, offsetX, offsetY, offsetZ, extra, data);
                } else {
                    viewer.spawnParticle(particle, point, count, offsetX, offsetY, offsetZ, extra);
                }
            }
        } catch (Exception ex) {
            if (warnedParticles.add(particle)) {
                plugin.getLogger().warning("Particle " + particle.name() + " could not be spawned on this server ("
                        + ex.getClass().getSimpleName() + ") - skipping it from now on. Pick a different particle in config.yml.");
            }
        }
    }

    private Color cyclingColor(List<Color> colors, int index, Color fallback) {
        if (colors == null || colors.isEmpty()) {
            return fallback;
        }
        return colors.get(((index % colors.size()) + colors.size()) % colors.size());
    }

    private Color parseColor(String hex, Color fallback) {
        if (hex == null) {
            return fallback;
        }
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            return Color.fromRGB(Integer.parseInt(clean, 16));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private int advanceRotation(Player player, String key, int step) {
        String rotationKey = player.getUniqueId() + ":" + key;
        return rotation.merge(rotationKey, step, Integer::sum);
    }
}
