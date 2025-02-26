package arconyx.enchanterslibrary.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChiseledBookshelfBlockEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin {
	@Shadow
	@Final
	private ScreenHandlerContext context;

	@Unique
	private static final Logger log = LoggerFactory.getLogger(EnchantmentScreenHandlerMixin.class);

	/**
	 * Update the running total of bookshelves in range of an enchanting table.
	 * This is called for block in the region around the table that passes the
	 * <code>EnchantingTableBlock.canAccessPowerProvider</code> check
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
					shift = At.Shift.BY, // shift into the true branch of the if statement
					by = 2 // CHECK THIS IN THE BYTECODE AFTER EVERY MINECRAFT UPDATE
			)
	)
	public int modifyPower(int power, ItemStack itemStack, World world, BlockPos pos, @Local(ordinal = 1) BlockPos blockPos) {
		log.debug("Called for block {}", pos.add(blockPos));
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
	 *   <li>For blocks we ignore, like regular bookshelves, we can just return zero</li>
	 * 	 <li>For blocks where we want to override the contribution, like chiseled bookshelves, we must subtract one
	 * 	 to cancel out the original loop.</li>
	 * </ul>
	 *
	 * @param world the world
	 * @param tablePos position of the enchanting table
	 * @param providerOffset offset from the enchanting table
	 * @return the amount this block contributes to the bookshelf count, relative to the default
	 */
	@Unique
	private int powerFromBlock(World world, BlockPos tablePos, BlockPos providerOffset) {
		BlockPos powerBlockPos = tablePos.add(providerOffset);
        log.debug("Checking block at {}", powerBlockPos);
		BlockEntity powerBlockEntity = world.getBlockEntity(powerBlockPos);
		if (powerBlockEntity instanceof ChiseledBookshelfBlockEntity bookshelf) {
			int filledSlots = bookshelf.size() - bookshelf.getOpenSlotCount();
			int power = filledSlots / 3;
			log.debug("Power at {} is {} (from {} filled slots)", powerBlockPos, power, filledSlots);
			// we reduce the power by one because EnchantmentScreenHandler adds 1 inside the loop
			return power - 1;
		}
		log.debug("Block is not a chiseled bookshelf (block is {})", world.getBlockState(powerBlockPos).getBlock());
		return 0;
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
		int inflated_level = level * 3 / 2;
		List<EnchantmentLevelEntry> availableEnchantments = original.call(stack, slot, inflated_level);

		this.context.run((World world, BlockPos tablePosition) -> {
			var additionalEnchantments = nearbyEnchantments(world, tablePosition); // Consider caching in a class property so we can generate it during onContentChanged
			additionalEnchantments.forEach(enchantmentLevelPair -> {

			});
		});

		return availableEnchantments;
	}

	@Unique
	private Stream<Map.Entry<Enchantment, Integer>> nearbyEnchantments(World world, BlockPos tablePosition) {
		var enchantments = EnchantingTableBlock.POWER_PROVIDER_OFFSETS.stream().unordered()
				.filter(
						offset -> EnchantingTableBlock.canAccessPowerProvider(world, tablePosition, offset)
				)
				.map(blockPos -> world.getBlockEntity(tablePosition.add(blockPos)))
				.filter(ChiseledBookshelfBlockEntity.class::isInstance)
				.map(ChiseledBookshelfBlockEntity.class::cast)
				.flatMap(bookshelf -> IntStream.range(0, bookshelf.size())
						.mapToObj(bookshelf::getStack)
						.flatMap(itemStack -> EnchantmentHelper.get(itemStack).entrySet().stream())
				);
		return enchantments;
	}
}