package com.wfphantom.datagear.mixin;

import com.wfphantom.datagear.engine.ModifierEngine;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void onTakeResult(Player player, ItemStack carried, CallbackInfo ci) {
        if (!carried.isEmpty()) ModifierEngine.INSTANCE.applyPerInstanceModifiers(carried);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void onCreateResult(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack result = menu.getSlot(2).getItem();
        if (!result.isEmpty()) ModifierEngine.INSTANCE.applyPerInstanceModifiers(result);
    }
}
