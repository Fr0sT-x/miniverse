package dev.frost.miniverse.minigame.impl.blockshuffle;

import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import net.minecraft.text.Text;

import java.util.Properties;

public final class BlockShuffleSessionBootstrap {
    private BlockShuffleSessionBootstrap() {
    }

    static void register() {
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getSessionBootstrapper().register(new SessionBootstrapper.Handler<BlockShuffleMinigame>() {
            @Override
            public String gameId() {
                return BlockShuffleDefinition.ID;
            }

            @Override
            public Class<BlockShuffleMinigame> runtimeType() {
                return BlockShuffleMinigame.class;
            }

            @Override
            public BlockShuffleMinigame createRuntime() {
                return new BlockShuffleMinigame();
            }

            @Override
            public void applySettings(BlockShuffleMinigame minigame, Properties properties) {
                minigame.applySettings(BlockShuffleSettings.fromProperties(properties));
            }

            @Override
            public MatchLifecycleOptions lifecycleOptions(BlockShuffleMinigame minigame, Properties properties) {
                return MatchLifecycleOptions.defaults(minigame.getName())
                    .withFreezeEnabled(true)
                    .withFreezeSeconds(10)
                    .withReturnSeconds(10)
                    .withStartTitle(
                        Text.literal(minigame.getName()),
                        Text.literal("Find and stand on your assigned block!")
                    );
            }

            @Override
            public boolean canStart(BlockShuffleMinigame minigame) {
                return true;
            }
        });
    }
}
