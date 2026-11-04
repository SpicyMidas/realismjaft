package dev.agentbuddy.brain;

public record Pos(int x, int y, int z) {
    public double distance(Pos o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** 8-way compass direction from this to {@code o} (north = -z). */
    public String directionTo(Pos o) {
        double dx = o.x - x, dz = o.z - z;
        if (dx == 0 && dz == 0) return o.y >= y ? "up" : "down";
        String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        double angle = Math.toDegrees(Math.atan2(-dx, dz)); // 0 = south, 90 = west
        int idx = Math.floorMod(Math.round(angle / 45.0), 8);
        return dirs[idx];
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
