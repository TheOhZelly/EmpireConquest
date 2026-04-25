package com.empireconquest.objects;

import org.bukkit.Location;
import org.bukkit.World;

public class CaptureZone {

    private final String name;
    private final Location corner1;
    private final Location corner2;
    private EmpTeam owner;   // null = neutral
    private float progress;  // 0.0 = full Aztec, 1.0 = full Spanish, 0.5 = neutral start
    private boolean active;

    public CaptureZone(String name, Location corner1, Location corner2) {
        this.name     = name;
        this.corner1  = corner1.clone();
        this.corner2  = corner2.clone();
        this.owner    = null;
        this.progress = 0.5f;
        this.active   = false;
    }

    // ── Geometry ──────────────────────────────────────────────────────────────

    public boolean contains(Location loc) {
        World w = corner1.getWorld();
        if (w == null || !w.equals(loc.getWorld())) return false;

        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        return loc.getBlockX() >= minX && loc.getBlockX() <= maxX
            && loc.getBlockY() >= minY && loc.getBlockY() <= maxY
            && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    public Location getCenter() {
        return new Location(
            corner1.getWorld(),
            (corner1.getX() + corner2.getX()) / 2.0,
            (corner1.getY() + corner2.getY()) / 2.0,
            (corner1.getZ() + corner2.getZ()) / 2.0
        );
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getName()      { return name; }
    public Location getCorner1() { return corner1; }
    public Location getCorner2() { return corner2; }

    public EmpTeam getOwner()         { return owner; }
    public void    setOwner(EmpTeam o){ this.owner = o; }

    public float getProgress()          { return progress; }
    public void  setProgress(float p)   { this.progress = Math.max(0f, Math.min(1f, p)); }

    public boolean isActive()           { return active; }
    public void    setActive(boolean a) { this.active = a; }
}
