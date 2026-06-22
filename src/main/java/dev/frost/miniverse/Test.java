package dev.frost.miniverse;

import net.minecraft.entity.player.PlayerEntity;
import java.lang.reflect.Method;

public class Test {
    public static void main(String[] args) {
        for (Method m : PlayerEntity.class.getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("respawn")) {
                System.out.println(m);
            }
        }
    }
}
