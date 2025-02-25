package arconyx.enchanterslibrary.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChiseledBookshelfBlockEntity;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantingScreenHandlerMixin {

	@Unique
	private static final Logger log = LoggerFactory.getLogger(EnchantingScreenHandlerMixin.class);

	// FIX: Fires regardless of whether if statement is true or false
	// FIX: Seems to fire three times for every block?!
	@ModifyVariable(
			method = "method_17411",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/block/EnchantingTableBlock;canAccessPowerProvider(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;)Z",
					shift = At.Shift.AFTER
			)
	)
	public int modifyPower(int power, ItemStack itemStack, World world, BlockPos pos, @Local(ordinal = 1) BlockPos blockPos) {
		int dp = powerFromBlock(world, pos, blockPos);
		return power + dp;
	}

	// Used for debug logging
	@WrapOperation(method = "method_17411", at = @At(value = "INVOKE", target = "Lnet/minecraft/enchantment/EnchantmentHelper;calculateRequiredExperienceLevel(Lnet/minecraft/util/math/random/Random;IILnet/minecraft/item/ItemStack;)I"))
	public int getPower(Random random, int slotIndex, int bookshelfCount, ItemStack stack, Operation<Integer> original) {
		log.info("Final bookshelf count is {}", bookshelfCount);
		return original.call(random, slotIndex, bookshelfCount, stack);
	}

	@Unique
	protected int powerFromBlock(World world, BlockPos tablePos, BlockPos providerOffset) {
		BlockPos powerBlockPos = tablePos.add(providerOffset);
        log.debug("Checking block at {}", powerBlockPos);
		BlockEntity powerBlockEntity = world.getBlockEntity(powerBlockPos);
		if (powerBlockEntity == null) {
			log.debug("No block entity here (block is {})", world.getBlockState(powerBlockPos).getBlock());
			return 0;
		} else if (!(powerBlockEntity instanceof ChiseledBookshelfBlockEntity)) {
			log.debug("Block is not a chiseled bookshelf (block is {})", world.getBlockState(powerBlockPos).getBlock());
			return 0;
		}
		ChiseledBookshelfBlockEntity bookshelf = (ChiseledBookshelfBlockEntity) powerBlockEntity;
		int filledSlots = 0;
		for (int slot = 0; slot < 6; slot++) {
			ItemStack stack = bookshelf.getStack(slot);
			if (!stack.isEmpty()) { filledSlots++; }
		}
		int power = filledSlots / 3;
		log.info("Power at {} is {} (from {} filled slots)", powerBlockPos, power, filledSlots);
		// we reduce the power by one because EnchantmentScreenHandler adds 1 inside the loop
//		ipower--; // enabling this actually breaks things for some reason
		return power;
	}

	@WrapMethod(method = "generateEnchantments")
	public List<EnchantmentLevelEntry> improveEnchantmentLevel(ItemStack stack, int slot, int level, Operation<List<EnchantmentLevelEntry>> original) {
		return original.call(stack, slot, level * 3 / 2);
	}
}