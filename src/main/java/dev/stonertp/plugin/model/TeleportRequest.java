package dev.stonertp.plugin.model;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;

public class TeleportRequest {
    private final String worldName;
    private final Location startLocation;
    private final double costCharged;
    private BukkitTask task;
    private BukkitTask effectsTask;
    private Object bossBarHandle;
    private CompletableFuture<Location> locationFuture;

    public TeleportRequest(String worldName, Location startLocation, double costCharged) {
        this.worldName = worldName;
        this.startLocation = startLocation;
        this.costCharged = costCharged;
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

    public BukkitTask getTask() {
        return task;
    }

    public void setTask(BukkitTask task) {
        this.task = task;
    }

    public BukkitTask getEffectsTask() {
        return effectsTask;
    }

    public void setEffectsTask(BukkitTask effectsTask) {
        this.effectsTask = effectsTask;
    }

    public Object getBossBarHandle() {
        return bossBarHandle;
    }

    public void setBossBarHandle(Object bossBarHandle) {
        this.bossBarHandle = bossBarHandle;
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
        }
        if (effectsTask != null) {
            effectsTask.cancel();
        }
    }
}
