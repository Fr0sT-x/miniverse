package dev.frost.miniverse.mixin;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayEntity.TextDisplayEntity.class)
public interface TextDisplayEntityAccessor {
    @Invoker("setText")
    void callSetText(Text text);

    @Invoker("setLineWidth")
    void callSetLineWidth(int lineWidth);

    @Invoker("setDisplayFlags")
    void callSetDisplayFlags(byte flags);

    @Invoker("getDisplayFlags")
    byte callGetDisplayFlags();
}
