package dev.frost.miniverse.minigame.core.swap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class DerangementAssignment<T> {
    public Map<T, T> assign(Collection<T> values, Map<T, ? extends Collection<T>> recentTargets) {
        List<T> sources = values == null ? List.of() : new ArrayList<>(values);
        sources.removeIf(Objects::isNull);
        if (sources.size() < 2) {
            return Map.of();
        }

        Map<T, Set<T>> recent = new HashMap<>();
        if (recentTargets != null) {
            recentTargets.forEach((source, targets) -> recent.put(source, targets == null ? Set.of() : new HashSet<>(targets)));
        }

        Map<T, T> best = Map.of();
        int bestPenalty = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 96; attempt++) {
            List<T> targets = new ArrayList<>(sources);
            Collections.shuffle(targets, ThreadLocalRandom.current());
            if (!isDerangement(sources, targets)) {
                rotateIntoDerangement(sources, targets);
            }
            if (!isDerangement(sources, targets)) {
                continue;
            }

            Map<T, T> candidate = new LinkedHashMap<>();
            int penalty = 0;
            for (int i = 0; i < sources.size(); i++) {
                T source = sources.get(i);
                T target = targets.get(i);
                candidate.put(source, target);
                if (recent.getOrDefault(source, Set.of()).contains(target)) {
                    penalty++;
                }
            }
            if (penalty == 0) {
                return candidate;
            }
            if (penalty < bestPenalty) {
                best = candidate;
                bestPenalty = penalty;
            }
        }
        return best.isEmpty() ? cycleDerangement(sources) : best;
    }

    private static <T> boolean isDerangement(List<T> sources, List<T> targets) {
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).equals(targets.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static <T> void rotateIntoDerangement(List<T> sources, List<T> targets) {
        for (int i = 0; i < targets.size(); i++) {
            if (!sources.get(i).equals(targets.get(i))) {
                continue;
            }
            int swapIndex = (i + 1) % targets.size();
            Collections.swap(targets, i, swapIndex);
        }
    }

    private static <T> Map<T, T> cycleDerangement(List<T> sources) {
        List<T> targets = new ArrayList<>(sources);
        Collections.shuffle(targets, ThreadLocalRandom.current());
        Collections.rotate(targets, 1);
        Map<T, T> assignment = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            assignment.put(sources.get(i), targets.get(i));
        }
        return assignment;
    }
}
