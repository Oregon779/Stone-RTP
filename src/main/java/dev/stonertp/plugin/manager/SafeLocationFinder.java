package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.model.RTPWorldSettings;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

public class SafeLocationFinder {
    private static final int NO_FLOOR = Integer.MIN_VALUE;

    private final StoneRTP plugin;
    private Set<Material> unsafeMaterials = Set.of();
    private Set<Biome> blacklistedBiomes = Set.of();

    public SafeLocationFinder(StoneRTP plugin) {
        this.plugin = plugin;
        this.unsafeMaterials = parseUnsafeMaterials();
        this.blacklistedBiomes = parseBlacklistedBiomes();
    }

    // Parsed once per (re)load instead of on every search, which also stops a typo from logging a warning per /rtp.
    public void reload() {
        unsafeMaterials = parseUnsafeMaterials();
        blacklistedBiomes = parseBlacklistedBiomes();
    }

    public CompletableFuture<Location> find(World world, RTPWorldSettings settings) {
        return find(world, settings, () -> true);
    }

    // stillWanted lets a cancelled/abandoned teleport stop the search instead of loading chunks for nobody.
    public CompletableFuture<Location> find(World world, RTPWorldSettings settings, BooleanSupplier stillWanted) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        SearchContext context = buildContext(world, settings);
        attempt(world, settings, context, stillWanted, 1, result);
        return result;
    }

    private SearchContext buildContext(World world, RTPWorldSettings settings) {
        ConfigManager cfg = plugin.getConfigManager();
        int minY = Math.max(world.getMinHeight(), cfg.getSafeLocationMinY());
        int maxY = Math.min(world.getMaxHeight() - 1, cfg.getSafeLocationMaxY());
        return new SearchContext(
                cfg.getSafeLocationMaxAttempts(),
                minY,
                maxY,
                isCaveWorld(world, settings),
                cfg.isRespectWorldBorder(),
                cfg.isAvoidWater(),
                cfg.isAvoidLava(),
                unsafeMaterials,
                blacklistedBiomes
        );
    }

    // Dimensions with a roof (vanilla Nether, but also datapack/modpack nethers such as Incendium) report
    // the roof as their highest block, so the surface heightmap would put players on top of it.
    private boolean isCaveWorld(World world, RTPWorldSettings settings) {
        return switch (settings.searchMode()) {
            case CAVE -> true;
            case SURFACE -> false;
            case AUTO -> world.hasCeiling() || world.getEnvironment() == World.Environment.NETHER;
        };
    }

    private void attempt(World world, RTPWorldSettings settings, SearchContext context, BooleanSupplier stillWanted,
                         int attemptNumber, CompletableFuture<Location> result) {
        if (attemptNumber > context.maxAttempts() || !stillWanted.getAsBoolean()) {
            result.complete(null);
            return;
        }

        int[] xz = randomPointInAnnulus(settings);

        if (context.respectBorder()) {
            WorldBorder border = world.getWorldBorder();
            if (!border.isInside(new Location(world, xz[0], 64, xz[1]))) {
                attempt(world, settings, context, stillWanted, attemptNumber + 1, result);
                return;
            }
        }

        int chunkX = xz[0] >> 4;
        int chunkZ = xz[1] >> 4;

        // whenComplete + try/catch: any failure must still complete the future, or the player's request hangs forever.
        world.getChunkAtAsync(chunkX, chunkZ).whenComplete((chunk, error) -> {
            if (!plugin.isEnabled()) {
                result.complete(null);
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    Location safe = error == null && stillWanted.getAsBoolean()
                            ? resolveLocation(world, xz[0], xz[1], context)
                            : null;
                    if (safe != null) {
                        result.complete(safe);
                    } else {
                        attempt(world, settings, context, stillWanted, attemptNumber + 1, result);
                    }
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Safe-location search in " + world.getName() + " failed: " + ex);
                    result.complete(null);
                }
            });
        });
    }

    private int[] randomPointInAnnulus(RTPWorldSettings settings) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = settings.radiusMin() + random.nextDouble() * Math.max(1, settings.radiusMax() - settings.radiusMin());
        int x = settings.centerX() + (int) Math.round(Math.cos(angle) * distance);
        int z = settings.centerZ() + (int) Math.round(Math.sin(angle) * distance);
        return new int[]{x, z};
    }

    private Location resolveLocation(World world, int x, int z, SearchContext context) {
        int groundY = context.caveMode() ? findCaveFloorY(world, x, z, context) : findSurfaceY(world, x, z, context);
        if (groundY == NO_FLOOR) {
            return null;
        }
        if (!context.blacklistedBiomes().isEmpty() && context.blacklistedBiomes().contains(world.getBiome(x, groundY, z))) {
            return null;
        }
        return new Location(world, x + 0.5, groundY + 1, z + 0.5);
    }

    private int findSurfaceY(World world, int x, int z, SearchContext context) {
        int groundY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (groundY < context.minY() || groundY > context.maxY()) {
            return NO_FLOOR;
        }
        return isSafeSpot(world, x, groundY, z, context) ? groundY : NO_FLOOR;
    }

    // Scans upward for the first standable floor that still has the roof above the player's head.
    private int findCaveFloorY(World world, int x, int z, SearchContext context) {
        int roofY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int topGroundY = Math.min(context.maxY(), roofY - 3);
        for (int y = context.minY(); y <= topGroundY; y++) {
            if (isSafeSpot(world, x, y, z, context)) {
                return y;
            }
        }
        return NO_FLOOR;
    }

    private boolean isSafeSpot(World world, int x, int groundY, int z, SearchContext context) {
        Block ground = world.getBlockAt(x, groundY, z);
        Material groundType = ground.getType();
        if (!groundType.isSolid() || context.unsafeMaterials().contains(groundType)) {
            return false;
        }
        if (context.caveMode() && groundType == Material.BEDROCK) {
            return false;
        }

        Block feet = world.getBlockAt(x, groundY + 1, z);
        Block head = world.getBlockAt(x, groundY + 2, z);
        if (!isPassable(feet.getType()) || !isPassable(head.getType())) {
            return false;
        }
        if (context.avoidWater() && (ground.isLiquid() || feet.isLiquid())) {
            return false;
        }
        return !context.avoidLava() || (groundType != Material.LAVA && feet.getType() != Material.LAVA);
    }

    private boolean isPassable(Material material) {
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || material.name().endsWith("GRASS")
                || material.name().endsWith("FERN");
    }

    private Set<Biome> parseBlacklistedBiomes() {
        Set<Biome> biomes = new HashSet<>();
        Registry<Biome> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        for (String name : plugin.getConfigManager().getBlacklistedBiomes()) {
            NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
            Biome biome = key != null ? registry.get(key) : null;
            if (biome != null) {
                biomes.add(biome);
            } else {
                plugin.getLogger().warning("Invalid biome '" + name + "' in safe-location.blacklisted-biomes, skipping.");
            }
        }
        return biomes;
    }

    private Set<Material> parseUnsafeMaterials() {
        Set<Material> materials = new HashSet<>();
        for (String name : plugin.getConfigManager().getUnsafeMaterials()) {
            try {
                materials.add(Material.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid material '" + name + "' in safe-location.unsafe-materials, skipping.");
            }
        }
        return materials;
    }

    private record SearchContext(int maxAttempts, int minY, int maxY, boolean caveMode, boolean respectBorder,
                                 boolean avoidWater, boolean avoidLava, Set<Material> unsafeMaterials,
                                 Set<Biome> blacklistedBiomes) {
    }
}
