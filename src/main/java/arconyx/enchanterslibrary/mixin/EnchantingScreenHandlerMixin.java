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

	/**
	 * Update the running total of bookshelves in range of an enchanting table.
	 * This is called for every potential block, after the conditional in the if statement.
	 * This occurs even if the condition returns false so the operations must be safe to run on
	 * arbitrary blocks or air.
	 *
	 * @param power     the current value of the running total variable
	 * @param world     the world
	 * @param itemStack the item in the enchanting table enchantment slot
	 * @param pos       position of the enchanting table
	 * @param blockPos  an offset from the enchanting table
	 * @return the new value of the running total variable
	 */
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

	/**
	 * Calculate how much this block contributes to the bookshelf total.
	 * Because the loop in the original code adds 1 to the total for every block in
	 * #minecraft:enchanted_power_provider we must adjust our value so we don't double count things.
	 * <ul>
	 *   <li>For blocks that aren't in the tag we can return 0 because the loop will be skipping it</li>
	 * 	 <li>For blocks that are in the tag we must subtract one to cancel out the loop</li>
	 * </ul>
	 * <p>In theory, we can also have blocks that contribute here (by returning >0) but aren't in the tag list. But that's
	 * dumb, and they won't show glyphs to the player. Don't do that, just add it to the tag.</p>
	 * <p>If you really want to do it you can just return the value without subtracting anything because the loop
	 * is ignoring it.</p>
	 *
	 * @param world the world
	 * @param tablePos position of the enchanting table
	 * @param providerOffset offset from the enchanting table
	 * @return the amount this block contributes to the bookshelf count, relative to the default
	 */
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
//		ipower--; // enabling this actually breaks things for some reason - I don't think the chiseled bookshelf has the right tag
		return power;
	}

	/**
	 * Modify the effective enchantment level when it is used to generate the list of potential enchantments.
	 * Doesn't affect the final XP cost.
	 * @param stack item to be enchanted
	 * @param slot enchantment slot (0,1,2)
	 * @param level effective enchantment level
	 * @param original the method we're wrapping
	 * @return the result of the original method (a list of enchantments with levels) called with our modified effective level
	 */
	@WrapMethod(method = "generateEnchantments")
	public List<EnchantmentLevelEntry> improveEnchantmentLevel(ItemStack stack, int slot, int level, Operation<List<EnchantmentLevelEntry>> original) {
		return original.call(stack, slot, level * 3 / 2);
	}
}