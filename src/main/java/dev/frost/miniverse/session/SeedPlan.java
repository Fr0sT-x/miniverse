package dev.frost.miniverse.session;

import java.util.concurrent.ThreadLocalRandom;

public record SeedPlan(long overworldSeed, long netherSeed, long endSeed, long rngSeed) {
    public static SeedPlan randomSameSeed() {
        long seed = ThreadLocalRandom.current().nextLong();
        return new SeedPlan(seed, seed, seed, seed);
    }

    public static SeedPlan fixed(long seed) {
        return new SeedPlan(seed, seed, seed, seed);
    }

    public long sharedSeed() {
        return this.overworldSeed;
    }

    public String asSeedString() {
        return Long.toString(this.sharedSeed());
    }
}

