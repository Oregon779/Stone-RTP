package dev.stonertp.plugin.model;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;

public class TeleportRequest {
    private final String worldName;
    private final Location startLocation;
    private final double costCharged;
    private final boolean forcedByAdmin;
    private BukkitTask task;
    private BukkitTask effectsTask;
    private CompletableFuture<Location> locationFuture;

    public TeleportRequest(String worldName, Location startLocation, double costCharged, boolean forcedByAdmin) {
        this.worldName = worldName;
        this.startLocation = startLocation;
        this.costCharged = costCharged;
        this.forcedByAdmin = forcedByAdmin;
    }

    public CompletableFuture<Location> getLocationFuture() {
        return locationFuture;
    }

    public void setLocationFuture(CompletableFuture<Location> locationFuture) {
        this.locationFuture = locationFuture;
    }

    public String getWorldName() {
        return worldName;
    }

    public Location getStartLocation() {
        return startLocation;
    }

    public double getCostCharged() {
        return costCharged;
    }

    public boolean isForcedByAdmin() {
        return forcedByAdmin;
    }

    public BukkitTask getTask() {
        return task;
    }

    public void setTask(BukkitTask task) {
        this.task = task;
    }

    public void setEffectsTask(BukkitTask effectsTask) {
        this.effectsTask = effectsTask;
    }

    public void cancelTasks() {
        if (task != null) {
            task.cancel();
        }
        if (effectsTask != null) {
            effectsTask.cancel();
        }
    }
}
