package dev.frost.miniverse;

import net.minecraft.predicate.entity.DamageSourcePredicate;
import java.lang.reflect.Method;

public class PrintMethods {
    public static void main() {
        for (Method m : DamageSourcePredicate.Builder.class.getMethods()) {
            System.out.println(m.getName() + " " + m.getParameterCount());
        }
    }
}
