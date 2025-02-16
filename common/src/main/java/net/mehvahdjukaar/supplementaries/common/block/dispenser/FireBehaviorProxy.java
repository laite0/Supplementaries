package net.mehvahdjukaar.supplementaries.common.block.dispenser;

import net.mehvahdjukaar.moonlight.api.util.DispenserHelper;
import net.mehvahdjukaar.supplementaries.common.block.fire_behaviors.IFireItemBehavior;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;

public class FireBehaviorProxy extends DispenserHelper.AdditionalDispenserBehavior {

    private final IFireItemBehavior inner;
    private final float power;
    private final int uncertainty;
    private final boolean isProjectile;

    public FireBehaviorProxy(Item item, IFireItemBehavior inner, float power, int uncertainty, boolean isProjectile) {
        super(item);
        this.inner = inner;
        this.power = power;
        this.uncertainty = uncertainty;
        this.isProjectile = isProjectile;
    }

    @Override
    protected InteractionResultHolder<ItemStack> customBehavior(BlockSource source, ItemStack stack) {
        ServerLevel level = source.getLevel();
        Position position = DispenserBlock.getDispensePosition(source);
        Direction direction = source.getBlockState().getValue(DispenserBlock.FACING);
        Vec3 firePos = new Vec3(position.x(), position.y(), position.z());

        if (inner.fire(stack, level, firePos, new Vec3(direction.step()), power,
                1, uncertainty, null)) {
            stack.shrink(1);
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    protected void playSound(BlockSource source, boolean success) {
        if (isProjectile) {
            super.playSound(source, success);
        } else source.getLevel().levelEvent(LevelEvent.SOUND_DISPENSER_PROJECTILE_LAUNCH, source.getPos(), 0);
    }
}
