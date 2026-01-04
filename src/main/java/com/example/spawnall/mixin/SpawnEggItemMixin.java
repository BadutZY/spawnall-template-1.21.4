package com.example.spawnall.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin untuk SpawnEggItem:
 * - Izinkan SEMUA spawn egg berinteraksi dengan spawner
 * - Di SEMUA mode (Vanilla atau Custom)
 * - Bahkan di Peaceful difficulty
 */
@Mixin(SpawnEggItem.class)
public abstract class SpawnEggItemMixin {

    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void allowAllSpawnEggsOnSpawner(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (context.getWorld().isClient) {
            return;
        }

        BlockState state = context.getWorld().getBlockState(context.getBlockPos());

        // Jika target adalah spawner
        if (state.isOf(Blocks.SPAWNER)) {
            // SELALU IZINKAN spawn egg untuk berinteraksi dengan spawner
            // Tidak peduli:
            // - Mod ON/OFF (Vanilla/Custom mode)
            // - Difficulty (Easy/Normal/Hard/Peaceful)
            // - Time (Day/Night)
            //
            // Event handler di UniversalSpawnerMod yang akan tentukan
            // apakah spawner dikonfigurasi dengan vanilla atau custom rules
            cir.setReturnValue(ActionResult.PASS);
        }
    }
}