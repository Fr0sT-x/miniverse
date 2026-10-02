package dev.frost.miniverse.minigame.impl.microfrenzy.rule;

import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMapConfig;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl.*;

import java.util.*;

public final class MicroRuleRegistry {
    private static final List<MicroRule> ALL_RULES = new ArrayList<>();

    static {
        register(new StatueRule());
        register(new RapidCrouchRule());
        register(new JumpCountRule());
        register(new LookUpRule());
        register(new LookDownRule());
        register(new SpinRule());
        register(new PunchFriendRule());
        register(new DropItemRule());
        register(new ReversePsychologyRule());
        register(new CenterStageRule());
        register(new ColorRushRule());
        register(new HighGroundRule());
        register(new AnvilDodgeRule());
        register(new TargetShootRule());
        register(new CollectCoinRule());
        register(new HotPotatoRule());
        register(new QuickMathRule());
        register(new EatFoodRule());
        register(new EquipArmorRule());
        register(new KeepMovingRule());
        register(new FindOddItemRule());
        register(new BlastRadiusRule());
        register(new SnowballFightRule());
        register(new SimonSaysRule());
        register(new LawnMowerRule());
        register(new ChickenHuntRule());
        register(new StareDownRule());
        register(new SweeperBarRule());
        register(new CountMobsRule());
        register(new WordScrambleRule());
        register(new EchoRule());
        register(new ColorRouletteRule());
        register(new FishingHookRule());
        register(new MortarStrikeRule());
        register(new MlgBucketRule());
        register(new MusicalBoatsRule());
        register(new StopClockRule());
        register(new DarknessButtonRule());
    }

    private MicroRuleRegistry() {}

    public static synchronized void register(MicroRule rule) {
        if (rule != null) {
            ALL_RULES.add(rule);
        }
    }

    public static List<MicroRule> getAllRules() {
        return List.copyOf(ALL_RULES);
    }

    public static List<MicroRule> getApplicableRules(MicroFrenzyMapConfig mapConfig) {
        List<MicroRule> applicable = new ArrayList<>();
        for (MicroRule rule : ALL_RULES) {
            if (rule.isApplicable(mapConfig)) {
                applicable.add(rule);
            }
        }
        return List.copyOf(applicable);
    }
}
