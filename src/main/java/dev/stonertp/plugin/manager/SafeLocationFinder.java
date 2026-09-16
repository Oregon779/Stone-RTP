package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.model.RTPWorldSettings;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class SafeLocationFinder {
    private final StoneRTP plugin;

    public SafeLocationFinder(StoneRTP plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Location> find(World world, RTPWorldSettings settings) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        SearchContext context = buildContext(world);
        attempt(world, settings, context, 1, result);
        return result;
    }

    private SearchContext buildContext(World world) {
        ConfigManager cfg = plugin.getConfigManager();
        int minY = Math.max(world.getMinHeight(), cfg.getSafeLocationMinY());
        int maxY = Math.min(world.getMaxHeight() - 1, cfg.getSafeLocationMaxY());
        return new SearchContext(
                cfg.getSafeLocationMaxAttempts(),
                minY,
                maxY,
                cfg.isRespectWorldBorder(),
                cfg.isAvoidWater(),
                cfg.isAvoidLava(),
                unsafeMaterials(),
                blacklistedBiomes()
        );
    }

    private void attempt(World world, RTPWorldSettings settings, SearchContext context, int attemptNumber, CompletableFuture<Location> result) {
        if (attemptNumber > context.maxAttempts()) {
            result.complete(null);
            return;
        }

        int[] xz = randomPointInAnnulus(settings);

        if (context.respectBorder()) {
            WorldBorder border = world.getWorldBorder();
            if (!border.isInside(new Location(world, xz[0], 64, xz[1]))) {
                attempt(world, settings, context, attemptNumber + 1, result);
                return;
            }
        }

        int chunkX = xz[0] >> 4;
        int chunkZ = xz[1] >> 4;

        world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location safe = resolveSurface(world, xz[0], xz[1], context);
            if (safe != null) {
                result.complete(safe);
            } else {
                attempt(world, settings, context, attemptNumber + 1, result);
            }
        }));
    }

    private int[] randomPointInAnnulus(RTPWorldSettings settings) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = settings.radiusMin() + random.nextDouble() * Math.max(1, settings.radiusMax() - settings.radiusMin());
        int x = settings.centerX() + (int) Math.round(Math.cos(angle) * distance);
        int z = settings.centerZ() + (int) Math.round(Math.sin(angle) * distance);
        return new int[]{x, z};
    }

    private Location resolveSurface(World world, int x, int z, SearchContext context) {
        int groundY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (groundY < context.minY() || groundY > context.maxY()) {
            return null;
        }

        if (!context.blacklistedBiomes().isEmpty() && context.blacklistedBiomes().contains(world.getBiome(x, groundY, z))) {
            return null;
        }

        Block ground = world.getBlockAt(x, groundY, z);
        Block feet = world.getBlockAt(x, groundY + 1, z);
        Block head = world.getBlockAt(x, groundY + 2, z);

        if (!ground.getType().isSolid()) {
            return null;
        }
        if (context.unsafeMaterials().contains(ground.getType())) {
            return null;
        }
        if (context.avoidWater() && (ground.isLiquid() || feet.isLiquid())) {
            return null;
        }
        if (context.avoidLava() && (ground.getType() == Material.LAVA || feet.getType() == Material.LAVA)) {
            return null;
        }
        if (!isPassable(feet.getType()) || !isPassable(head.getType())) {
            return null;
        }

        return new Location(world, x + 0.5, groundY + 1, z + 0.5);
    }

    private boolean isPassable(Material material) {
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || material.name().endsWith("GRASS")
                || material.name().endsWith("FERN");
    }

    private Set<Biome> blacklistedBiomes() {
        Set<Biome> biomes = new HashSet<>();
        for (String name : plugin.getConfigManager().getBlacklistedBiomes()) {
            NamespacedKey key = NamespacedKey.minecraft(name.trim().toLowerCase());
            Biome biome = Registry.BIOME.get(key);
            if (biome != null) {
                biomes.add(biome);
            } else {
                plugin.getLogger().warning("Invalid biome '" + name + "' in safe-location.blacklisted-biomes, skipping.");
            }
        }
        return biomes;
    }

    private Set<Material> unsafeMaterials() {
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

    private record SearchContext(int maxAttempts, int minY, int maxY, boolean respectBorder, boolean avoidWater,
                                  boolean avoidLava, Set<Material> unsafeMaterials, Set<Biome> blacklistedBiomes) {
    }
}
