package com.seba.reverie.dreamland;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/**
 * Where the islands are. Space is split into vertical layers and a 96-block grid; for every (layer, cell) a
 * hash of seed+cell+layer decides whether an island exists, its centre, size, shape and biome. Everything is
 * a pure function of the seed, so any chunk can be generated alone, in any order, on any thread.
 */
public final class IslandField {
    public static final int CELL = 96;
    public static final int REGION = 720;

    public enum Shape { PLATEAU, SPIRES, RING, INVERTED, STAIRS, ARCHIPELAGO, GREAT_BED, SEA_OF_DOORS, CLOUD }

    /** Island biomes; VOID is the open air between them. Order matters for the biome source codec. */
    public enum Kind { PILLOW_FIELDS, HOLLOW_SPIRES, MIRROR_MARSH, SANDGLASS_DUNES, EYE_MEADOW, UPSIDE_DOWN_TOWN, CLOUDREACH, VOID }

    record Layer(int centerY, int jitterY, double presence, int minR, int maxR) {
    }

    static final Layer[] LAYERS = {
            new Layer(92, 14, 0.55, 14, 64),
            new Layer(170, 16, 0.55, 14, 58),
            new Layer(248, 16, 0.50, 12, 50),
            new Layer(324, 8, 0.38, 5, 14),
    };

    public record Spire(double x, double z, int baseY, int height, double radius, double phase, boolean hollow, boolean down) {
    }

    public record Blob(double x, double y, double z, double radius) {
    }

    public record Islet(double x, double z, int topY, int radius, int topThick) {
    }

    /** A straight line of Pillow Cloud between two cloud islets. */
    public record Bridge(double x0, double y0, double z0, double x1, double y1, double z1) {
    }

    public static final class Island {
        public final long id;
        public final int layer;
        public final int cx;
        public final int cy;
        public final int cz;
        public final int radius;
        public final int topThick;
        public final double bottomFactor;
        public final Shape shape;
        public final Kind kind;
        public final long seed;
        public final List<Spire> spires = new ArrayList<>();
        public final List<Blob> rocks = new ArrayList<>();
        public final List<Islet> islets = new ArrayList<>();
        /** STAIRS: direction and length of the staircase to nowhere. */
        public final double stairAngle;
        public final int stairLength;
        int minX;
        int maxX;
        int minZ;
        int maxZ;
        int minY;
        int maxY;

        Island(long id, int layer, int cx, int cy, int cz, int radius, int topThick, double bottomFactor, Shape shape, Kind kind,
               long seed, double stairAngle, int stairLength) {
            this.id = id;
            this.layer = layer;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.radius = radius;
            this.topThick = topThick;
            this.bottomFactor = bottomFactor;
            this.shape = shape;
            this.kind = kind;
            this.seed = seed;
            this.stairAngle = stairAngle;
            this.stairLength = stairLength;
        }

        public boolean touchesColumnRange(int x0, int z0, int x1, int z1) {
            return maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1;
        }

        public boolean contains(int x, int y, int z, int margin) {
            return x >= minX - margin && x <= maxX + margin && z >= minZ - margin && z <= maxZ + margin
                    && y >= minY - margin && y <= maxY + margin * 3;
        }

        public int minY() {
            return minY;
        }

        public int maxY() {
            return maxY;
        }
    }

    private final long seed;
    private final ImprovedNoise edge;
    private final ImprovedNoise surface;
    private final ImprovedNoise under;
    private final ImprovedNoise ore;
    private final Lru cache = new Lru();

    public IslandField(long seed) {
        this.seed = seed;
        this.edge = new ImprovedNoise(new XoroshiroRandomSource(seed ^ 0x5EED_ED6EL));
        this.surface = new ImprovedNoise(new XoroshiroRandomSource(seed ^ 0x5EED_50F7L));
        this.under = new ImprovedNoise(new XoroshiroRandomSource(seed ^ 0x5EED_0DE7L));
        this.ore = new ImprovedNoise(new XoroshiroRandomSource(seed ^ 0x5EED_04E5L));
    }

    public long seed() {
        return seed;
    }

    // ------------------------------------------------------------------ hashing

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    long hash(long a, long b, long c) {
        return mix(seed ^ mix(a * 0x9E3779B97F4A7C15L ^ mix(b * 0xC2B2AE3D27D4EB4FL ^ mix(c + 0x165667B19E3779F9L))));
    }

    static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    // ------------------------------------------------------------------ regions (biomes)

    /** Large coherent biome regions: nearest jittered region centre wins. */
    public Kind regionKind(int x, int z) {
        int rx = Math.floorDiv(x, REGION);
        int rz = Math.floorDiv(z, REGION);
        double best = Double.MAX_VALUE;
        long bestHash = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = hash(rx + dx, 991, rz + dz);
                double px = (rx + dx) * (double) REGION + REGION * (0.15 + 0.7 * unit(h));
                double pz = (rz + dz) * (double) REGION + REGION * (0.15 + 0.7 * unit(mix(h)));
                double d = (px - x) * (px - x) + (pz - z) * (pz - z);
                if (d < best) {
                    best = d;
                    bestHash = h;
                }
            }
        }
        double r = unit(mix(bestHash ^ 0xB10E));
        if (r < 0.30) return Kind.PILLOW_FIELDS;
        if (r < 0.48) return Kind.HOLLOW_SPIRES;
        if (r < 0.62) return Kind.MIRROR_MARSH;
        if (r < 0.74) return Kind.SANDGLASS_DUNES;
        if (r < 0.87) return Kind.EYE_MEADOW;
        return Kind.UPSIDE_DOWN_TOWN;
    }

    // ------------------------------------------------------------------ islands

    public static int layerCount() {
        return LAYERS.length;
    }

    /** Layers whose islands can reach height {@code y} (with a margin). */
    public static boolean layerNear(int layer, int y, int margin) {
        Layer l = LAYERS[layer];
        return y >= l.centerY - 130 - margin && y <= l.centerY + 90 + margin;
    }

    public Island island(int layer, int cellX, int cellZ) {
        long key = ((long) layer << 58) ^ (((long) cellX & 0x1FFFFFFFL) << 29) ^ ((long) cellZ & 0x1FFFFFFFL);
        Object cached = cache.get(key);
        if (cached != null) return cached == Lru.NONE ? null : (Island) cached;
        Island island = build(layer, cellX, cellZ);
        cache.put(key, island == null ? Lru.NONE : island);
        return island;
    }

    /** All islands whose horizontal bounds touch [x0,x1]x[z0,z1]. */
    public List<Island> islandsNear(int x0, int z0, int x1, int z1) {
        List<Island> out = new ArrayList<>(4);
        int c0x = Math.floorDiv(x0, CELL) - 1;
        int c1x = Math.floorDiv(x1, CELL) + 1;
        int c0z = Math.floorDiv(z0, CELL) - 1;
        int c1z = Math.floorDiv(z1, CELL) + 1;
        for (int layer = 0; layer < LAYERS.length; layer++) {
            for (int cx = c0x; cx <= c1x; cx++) {
                for (int cz = c0z; cz <= c1z; cz++) {
                    Island is = island(layer, cx, cz);
                    if (is != null && is.touchesColumnRange(x0, z0, x1, z1)) out.add(is);
                }
            }
        }
        return out;
    }

    /** The island whose volume (with margin) contains the point, or null. */
    public Island islandAt(int x, int y, int z, int margin) {
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        for (int layer = 0; layer < LAYERS.length; layer++) {
            if (!layerNear(layer, y, margin)) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Island is = island(layer, cellX + dx, cellZ + dz);
                    if (is != null && is.contains(x, y, z, margin)) return is;
                }
            }
        }
        return null;
    }

    private Island build(int layer, int cellX, int cellZ) {
        Layer l = LAYERS[layer];
        long h = hash(cellX, layer * 7919L + 13, cellZ);
        if (unit(h) >= l.presence) return null;
        long h2 = mix(h);
        long h3 = mix(h2);
        long h4 = mix(h3);
        int cx = cellX * CELL + CELL / 2 + (int) Math.round((unit(h2) - 0.5) * 60);
        int cz = cellZ * CELL + CELL / 2 + (int) Math.round((unit(h3) - 0.5) * 60);
        int cy = l.centerY + (int) Math.round((unit(h4) - 0.5) * 2 * l.jitterY);
        double sizeRoll = unit(mix(h4));
        int radius = l.minR + (int) Math.round(Math.pow(sizeRoll, 1.7) * (l.maxR - l.minR));
        int topThick = 3 + (int) (unit(mix(h ^ 0x70)) * 7);
        double bottomFactor = 0.6 + unit(mix(h ^ 0xB0)) * 0.7;
        boolean cloudLayer = layer == LAYERS.length - 1;
        Kind kind = cloudLayer ? Kind.CLOUDREACH : regionKind(cx, cz);
        Shape shape = cloudLayer ? Shape.CLOUD : pickShape(kind, unit(mix(h ^ 0x5A)), radius);
        if (shape == Shape.GREAT_BED) radius = Math.max(radius, 26);
        if (shape == Shape.SEA_OF_DOORS) {
            radius = Math.max(radius, 20);
            topThick = 3;
        }
        if (shape == Shape.SPIRES) radius = Math.max(radius, 16);
        if (kind == Kind.UPSIDE_DOWN_TOWN && shape == Shape.PLATEAU) {
            // A broad, shallow underside: the "ceiling" the town hangs from.
            radius = Math.max(radius, 26);
            bottomFactor = 0.22;
        }
        if (cloudLayer) {
            topThick = 2 + (int) (unit(mix(h ^ 0x70)) * 2);
            bottomFactor = 0.25;
        }
        double stairAngle = unit(mix(h ^ 0x57A1)) * Math.PI * 2;
        int stairLength = shape == Shape.STAIRS ? 26 + (int) (unit(mix(h ^ 0x57A2)) * 18) : 0;
        if (shape == Shape.STAIRS) radius = Math.min(radius, 22);
        Island is = new Island(h, layer, cx, cy, cz, radius, topThick, bottomFactor, shape, kind, mix(h ^ 0xD3C0), stairAngle, stairLength);
        decorateShape(is);
        computeBounds(is);
        return is;
    }

    private static Shape pickShape(Kind kind, double r, int radius) {
        switch (kind) {
            case HOLLOW_SPIRES:
                return r < 0.55 ? Shape.SPIRES : r < 0.85 ? Shape.PLATEAU : r < 0.95 ? Shape.INVERTED : Shape.RING;
            case UPSIDE_DOWN_TOWN:
                return r < 0.85 ? Shape.PLATEAU : Shape.INVERTED;
            case MIRROR_MARSH:
                return r < 0.35 ? Shape.RING : r < 0.93 ? Shape.PLATEAU : Shape.ARCHIPELAGO;
            default:
                if (r < 0.46) return Shape.PLATEAU;
                if (r < 0.56) return Shape.RING;
                if (r < 0.66) return Shape.INVERTED;
                if (r < 0.78) return Shape.ARCHIPELAGO;
                if (r < 0.87) return Shape.STAIRS;
                if (r < 0.92) return Shape.SPIRES;
                if (r < 0.955) return radius >= 18 ? Shape.GREAT_BED : Shape.PLATEAU;
                return Shape.SEA_OF_DOORS;
        }
    }

    private void decorateShape(Island is) {
        long h = is.seed;
        if (is.shape == Shape.SPIRES) {
            int count = 3 + (int) (unit(mix(h ^ 1)) * 5);
            boolean hollowBiome = is.kind == Kind.HOLLOW_SPIRES;
            for (int i = 0; i < count; i++) {
                long s = mix(h ^ (i * 0x1F1FL + 7));
                double a = unit(s) * Math.PI * 2;
                double d = unit(mix(s)) * is.radius * 0.55;
                double radius = 2.2 + unit(mix(s ^ 3)) * 2.6;
                int height = 18 + (int) (unit(mix(s ^ 5)) * 40);
                is.spires.add(new Spire(is.cx + Math.cos(a) * d, is.cz + Math.sin(a) * d, is.cy, height, radius,
                        unit(mix(s ^ 9)) * Math.PI * 2, hollowBiome && radius >= 3.0, false));
            }
            if (hollowBiome) {
                int down = 1 + (int) (unit(mix(h ^ 11)) * 3);
                for (int i = 0; i < down; i++) {
                    long s = mix(h ^ (i * 0x2E2EL + 17));
                    double a = unit(s) * Math.PI * 2;
                    double d = unit(mix(s)) * is.radius * 0.35;
                    is.spires.add(new Spire(is.cx + Math.cos(a) * d, is.cz + Math.sin(a) * d, is.cy - is.topThick, 14 + (int) (unit(mix(s ^ 5)) * 26),
                            1.8 + unit(mix(s ^ 3)) * 2.0, unit(mix(s ^ 9)) * Math.PI * 2, false, true));
                }
            }
        }
        if (is.shape == Shape.ARCHIPELAGO) {
            int count = 4 + (int) (unit(mix(h ^ 21)) * 5);
            for (int i = 0; i < count; i++) {
                long s = mix(h ^ (i * 0x3D3DL + 29));
                double a = unit(s) * Math.PI * 2;
                double d = is.radius + 6 + unit(mix(s)) * 16;
                int r = 3 + (int) (unit(mix(s ^ 1)) * 6);
                is.islets.add(new Islet(is.cx + Math.cos(a) * d, is.cz + Math.sin(a) * d,
                        is.cy + (int) Math.round((unit(mix(s ^ 2)) - 0.5) * 12), r, 2 + (int) (unit(mix(s ^ 4)) * 3)));
            }
        }
        // Small free-floating rocks near most islands.
        int rocks = is.shape == Shape.CLOUD ? 0 : (int) (unit(mix(h ^ 31)) * 4);
        for (int i = 0; i < rocks; i++) {
            long s = mix(h ^ (i * 0x4C4CL + 37));
            double a = unit(s) * Math.PI * 2;
            double d = is.radius * (0.9 + unit(mix(s)) * 0.6);
            is.rocks.add(new Blob(is.cx + Math.cos(a) * d, is.cy - 6 + (unit(mix(s ^ 1)) - 0.3) * 30, is.cz + Math.sin(a) * d,
                    1.3 + unit(mix(s ^ 2)) * 2.0));
        }
    }

    private static void computeBounds(Island is) {
        int reach = (int) Math.ceil(is.radius * 1.2) + 2;
        is.minX = is.cx - reach;
        is.maxX = is.cx + reach;
        is.minZ = is.cz - reach;
        is.maxZ = is.cz + reach;
        is.minY = is.cy - is.topThick - (int) Math.ceil(is.radius * is.bottomFactor) - 8;
        is.maxY = is.cy + 6;
        if (is.shape == Shape.INVERTED) is.maxY = is.cy + (int) Math.ceil(is.radius * is.bottomFactor) + 6;
        if (is.shape == Shape.GREAT_BED) is.maxY = is.cy + 16;
        for (Spire s : is.spires) {
            int r = (int) Math.ceil(s.radius()) + 3;
            is.minX = Math.min(is.minX, (int) s.x() - r);
            is.maxX = Math.max(is.maxX, (int) s.x() + r);
            is.minZ = Math.min(is.minZ, (int) s.z() - r);
            is.maxZ = Math.max(is.maxZ, (int) s.z() + r);
            if (s.down()) is.minY = Math.min(is.minY, s.baseY() - s.height() - 2);
            else is.maxY = Math.max(is.maxY, s.baseY() + s.height() + 2);
        }
        for (Islet i : is.islets) {
            is.minX = Math.min(is.minX, (int) i.x() - i.radius() - 2);
            is.maxX = Math.max(is.maxX, (int) i.x() + i.radius() + 2);
            is.minZ = Math.min(is.minZ, (int) i.z() - i.radius() - 2);
            is.maxZ = Math.max(is.maxZ, (int) i.z() + i.radius() + 2);
            is.maxY = Math.max(is.maxY, i.topY() + 2);
            is.minY = Math.min(is.minY, i.topY() - i.radius() - 4);
        }
        for (Blob b : is.rocks) {
            int r = (int) Math.ceil(b.radius()) + 1;
            is.minX = Math.min(is.minX, (int) b.x() - r);
            is.maxX = Math.max(is.maxX, (int) b.x() + r);
            is.minZ = Math.min(is.minZ, (int) b.z() - r);
            is.maxZ = Math.max(is.maxZ, (int) b.z() + r);
            is.minY = Math.min(is.minY, (int) b.y() - r);
            is.maxY = Math.max(is.maxY, (int) b.y() + r);
        }
        if (is.shape == Shape.STAIRS) {
            double ex = is.cx + Math.cos(is.stairAngle) * (is.radius + is.stairLength);
            double ez = is.cz + Math.sin(is.stairAngle) * (is.radius + is.stairLength);
            is.minX = Math.min(is.minX, (int) ex - 3);
            is.maxX = Math.max(is.maxX, (int) ex + 3);
            is.minZ = Math.min(is.minZ, (int) ez - 3);
            is.maxZ = Math.max(is.maxZ, (int) ez + 3);
            is.maxY = Math.max(is.maxY, is.cy + is.stairLength + 6);
        }
    }

    // ------------------------------------------------------------------ column profiles

    /** Top surface y of the plateau body at (x,z), or Integer.MIN_VALUE outside. */
    public int plateauTop(Island is, int x, int z) {
        double d = normDist(is, x, z);
        if (d >= 1) return Integer.MIN_VALUE;
        double n = surface.noise(x * 0.06, is.layer * 31.0, z * 0.06);
        int top = is.cy + (int) Math.round(n * 1.4);
        if (is.shape == Shape.SEA_OF_DOORS || is.shape == Shape.GREAT_BED) top = is.cy;
        double shoulder = 0.7 + 0.06 * edge.noise(x * 0.13, 211, z * 0.13);
        if (d > shoulder) {
            double t = (d - shoulder) / (1 - shoulder);
            top -= (int) Math.round(t * t * (is.topThick + 2));
        }
        return top;
    }

    /** Bottom y of the island body at (x,z) (inverted cone with noise and a few hanging points). */
    public int plateauBottom(Island is, int x, int z) {
        double d = normDist(is, x, z);
        if (d >= 1) return Integer.MAX_VALUE;
        double cone = Math.pow(1 - d, 1.35) * is.radius * is.bottomFactor;
        double n = under.noise(x * 0.09, is.layer * 17.0, z * 0.09);
        double spike = under.noise(x * 0.31, 400 + is.layer, z * 0.31);
        double extra = spike > 0.42 ? (spike - 0.42) * 14 : 0;
        return is.cy - is.topThick - (int) Math.round(cone + n * 3.0 + extra);
    }

    /** Distance from the centre over the (noisy) radius: 0 at the centre, 1 at the edge. */
    public double normDist(Island is, int x, int z) {
        double dx = x - is.cx;
        double dz = z - is.cz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double wobble = 1 + 0.18 * edge.noise(x * 0.045, is.layer * 13.0, z * 0.045);
        return dist / (is.radius * wobble);
    }

    public boolean ringHole(Island is, int x, int z) {
        if (is.shape != Shape.RING) return false;
        double d = normDist(is, x, z);
        return d < 0.42 + 0.06 * edge.noise(x * 0.1, 77, z * 0.1);
    }

    public boolean isOre(int x, int y, int z) {
        return ore.noise(x * 0.11, y * 0.11, z * 0.11) > 0.62;
    }

    public static double spireRadiusAt(Spire s, int y) {
        int rel = s.down() ? s.baseY() - y : y - s.baseY();
        if (rel < 0 || rel > s.height()) return -1;
        double t = rel / (double) s.height();
        return s.radius() * (1 - 0.75 * t) + 0.6;
    }

    public static double spireCenterOffsetX(Spire s, int y) {
        return Math.cos(y * 0.09 + s.phase()) * 1.6 * Mth.clamp(Math.abs(y - s.baseY()) / 10.0, 0, 1);
    }

    public static double spireCenterOffsetZ(Spire s, int y) {
        return Math.sin(y * 0.09 + s.phase()) * 1.6 * Mth.clamp(Math.abs(y - s.baseY()) / 10.0, 0, 1);
    }

    /** Highest solid y of any island at (x,z), or Integer.MIN_VALUE. */
    public int highestTop(int x, int z) {
        int best = Integer.MIN_VALUE;
        for (Island is : islandsNear(x, z, x, z)) {
            int top = topOf(is, x, z);
            if (top > best) best = top;
        }
        return best;
    }

    /** Approximate top surface of one island at a column (used for heights, homes, doors). */
    public int topOf(Island is, int x, int z) {
        int best = Integer.MIN_VALUE;
        if (is.shape == Shape.INVERTED) {
            double d = normDist(is, x, z);
            if (d < 1) best = is.cy + (int) Math.round(Math.pow(1 - d, 1.35) * is.radius * is.bottomFactor * 0.8);
        } else if (is.shape != Shape.SPIRES || normDist(is, x, z) < 0.62) {
            int top = plateauTop(is, x, z);
            if (top != Integer.MIN_VALUE && !ringHole(is, x, z)) best = top;
            else if (top != Integer.MIN_VALUE) best = top - 1;
        }
        for (Islet i : is.islets) {
            double dx = x - i.x();
            double dz = z - i.z();
            if (dx * dx + dz * dz < i.radius() * i.radius()) best = Math.max(best, i.topY());
        }
        return best;
    }

    /** Thread-safe, bounded LRU (striped) for per-cell island data. */
    private static final class Lru {
        static final Object NONE = new Object();
        private static final int STRIPES = 16;
        private static final int PER_STRIPE = 512;
        @SuppressWarnings("unchecked")
        private final Map<Long, Object>[] maps = new Map[STRIPES];

        Lru() {
            for (int i = 0; i < STRIPES; i++) {
                maps[i] = new LinkedHashMap<>(PER_STRIPE, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Long, Object> eldest) {
                        return size() > PER_STRIPE;
                    }
                };
            }
        }

        private Map<Long, Object> stripe(long key) {
            return maps[(int) (mix(key) & (STRIPES - 1))];
        }

        Object get(long key) {
            Map<Long, Object> m = stripe(key);
            synchronized (m) {
                return m.get(key);
            }
        }

        void put(long key, Object value) {
            Map<Long, Object> m = stripe(key);
            synchronized (m) {
                m.put(key, value);
            }
        }
    }
}
