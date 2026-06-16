package dev.frost.miniverse;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import java.lang.reflect.Constructor;
public class TestPacket {
    public static void main(String[] args) {
        for (Constructor<?> c : PlayerRespawnS2CPacket.class.getConstructors()) {
            System.out.println(c);
        }
    }
}
