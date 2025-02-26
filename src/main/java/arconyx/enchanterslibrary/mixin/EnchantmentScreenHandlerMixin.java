package arconyx.enchanterslibrary.mixin;

import arconyx.enchanterslibrary.WeightedEnchantmentLevelEntry;
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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.util.Util;
import net.minecraft.util.collection.Weighting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin {
	@Unique
	private final List<Map.Entry<Enchantment, Integer>> nearbyEnchantments = new ArrayList<>();
	@Unique
	private static final Logger log = LoggerFactory.getLogger(EnchantmentScreenHandlerMixin.class);

	/**
	 * Reset the nearby enchantments list before {@link EnchantmentScreenHandlerMixin#modifyPower(int, ItemStack, World, BlockPos, BlockPos)}
	 * starts modifying it.
	 */
	@Inject(method = "method_17411", at = @At(value = "HEAD"))
	public void clearNearbyEnchantments(ItemStack itemStack, World world, BlockPos pos, CallbackInfo ci) {
		nearbyEnchantments.clear();
	}

	/**
	 * Update the running total of bookshelves in range of an enchanting table.
	 * This is called for block in the region around the table that passes the
	 * {@link EnchantingTableBlock#canAccessPowerProvider(World, BlockPos, BlockPos)} check
	 *
	 * @param power     the current value of the running total variable
	 * @param world     the world
	 * @param itemStack the item in the enchanting table enchantment slot
	 * @param pos       position of the enchanting table
	 * @param blockPos  an offset from the enchanting table
	 * @return the new value of the running total variable
	 */
	@SuppressWarnings("DiscouragedShift") // Yes it's brittle but we need it
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
		int dp = getPowerFromBlock(world, pos, blockPos);
		return power + dp;
	}

	// Used for debug logging
	@WrapOperation(method = "method_17411", at = @At(value = "INVOKE", target = "Lnet/minecraft/enchantment/EnchantmentHelper;calculateRequiredExperienceLevel(Lnet/minecraft/util/math/random/Random;IILnet/minecraft/item/ItemStack;)I"))
	public int getPower(Random random, int slotIndex, int bookshelfCount, ItemStack stack, Operation<Integer> original) {
		log.debug("Final bookshelf count is {}", bookshelfCount);
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
	private int getPowerFromBlock(World world, BlockPos tablePos, BlockPos providerOffset) {
		BlockPos powerBlockPos = tablePos.add(providerOffset);
        log.debug("Checking block at {}", powerBlockPos);
		BlockEntity powerBlockEntity = world.getBlockEntity(powerBlockPos);
		if (powerBlockEntity instanceof ChiseledBookshelfBlockEntity bookshelf) {
			int filledSlots = bookshelf.getOpenSlotCount();
			int power = filledSlots / 3;
			log.debug("Power at {} is {} (from {} filled slots)", powerBlockPos, power, filledSlots);
			if (filledSlots > 0) {
				this.nearbyEnchantments.addAll(getEnchantmentsAtBlock(bookshelf).toList());
			}
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
		return original.call(stack, slot, level * 3 / 2);
	}

	/**
	 * Returns a stream of values representing all the enchantment/level pairs on items on this bookshelf
	 *
	 * @param bookshelf a ChiseledBookshelfBlockEntity
	 * @return stream of map entries with enchantment as the key and level as the value
	 */
	@Unique
	private Stream<Map.Entry<Enchantment, Integer>> getEnchantmentsAtBlock(ChiseledBookshelfBlockEntity bookshelf) {
		return IntStream.range(0, bookshelf.size())
				.mapToObj(bookshelf::getStack)
				.flatMap(itemStack -> EnchantmentHelper.get(itemStack).entrySet().stream());
	}

	/**
	 * A reimplementation of the {@link EnchantmentHelper#generateEnchantments(Random, ItemStack, int, boolean)}
	 * method based on details from the Minecraft wiki. This allows us to modify the list of possible enchantments
	 * based on contextual information like the world position.
	 *
	 * <p>We use a redirect because the original method is static and doesn't have access to the context or other
	 * class fields.</p>
	 *
	 * <p><b>This function must be kept up to date with changes in the vanilla function else behaviour will diverge
	 * (non-fatally (probably)).</b></p>
	 *
	 * @param random          the prng source
	 * @param stack           the item stack being enchanting
	 * @param level           the base enchantment level
	 * @param treasureAllowed are treasure enchantments allowed
	 * @return a list of the enchantments, with levels, that will be applied to the item
	 */
	@Redirect(method = "generateEnchantments", at = @At(value = "INVOKE", target = "Lnet/minecraft/enchantment/EnchantmentHelper;generateEnchantments(Lnet/minecraft/util/math/random/Random;Lnet/minecraft/item/ItemStack;IZ)Ljava/util/List;"))
	private List<EnchantmentLevelEntry> generateEnchantmentsIncludingNearbyBooks(Random random, ItemStack stack, int level, boolean treasureAllowed) {
		// get item enchantability
		Item item = stack.getItem();
		int enchantability = item.getEnchantability();
		if (enchantability <= 0) {
			return new ArrayList<>();
		}

		// modify enchantment level based on enchantability
		int upperBound = enchantability / 4 + 1;
		int levelWithEnchantability = level + random.nextInt(upperBound) + random.nextInt(upperBound) + 1;

		// find a random enchantment bonus between 0.85 and 1.15
		// we choose the two part source to match the triangular distribution of the vanilla code.
		float randomBonusCoefficient = (random.nextFloat() + random.nextFloat() - 1) * 0.15F + 1;

		// apply the bonus and clamp the value to avoid weirdness
		int finalLevel = MathHelper.clamp(Math.round(levelWithEnchantability * randomBonusCoefficient), 1, Integer.MAX_VALUE);

		List<EnchantmentLevelEntry> possibleEnchantments = EnchantmentHelper.getPossibleEntries(finalLevel, stack, treasureAllowed);

		// This is the custom stuff

		// This filters enchantments by compatibility and then counts the number of occurrences of an enchantment/level
		// pairing. We operate on Map.Entry objects because they have reasonable equality logic defined and EnchantedLevelEntries
		// don't.
		// It might be more efficient to apply the filter when constructing the initial list but this seems cleaner
		var additionalEnchantmentsCount = this.nearbyEnchantments.stream()
				.filter(enchantmentLevelEntry -> enchantmentLevelEntry.getKey().isAcceptableItem(stack))
				.collect(Collectors.groupingBy(entry -> entry, Collectors.counting()));

		List<WeightedEnchantmentLevelEntry> additionalEnchantments = additionalEnchantmentsCount.entrySet().stream().map(
				entry -> new WeightedEnchantmentLevelEntry(entry.getKey(), entry.getValue().intValue())
		).toList();

		additionalEnchantments.forEach(entry -> log.debug("Entry has enchantment {} with weight {}", entry.enchantment.getName(entry.level), entry.getWeight()));

		// Add enchantments to possibilities
		possibleEnchantments.addAll(additionalEnchantments);

		// Done with the custom stuff
		// If we have no possible enchantments return an empty list
		if (possibleEnchantments.isEmpty()) {
			return possibleEnchantments;
		}

		// Select first enchantment
		List<EnchantmentLevelEntry> selectedEnchantments = new ArrayList<>();
		Weighting.getRandom(random, possibleEnchantments).ifPresent(selectedEnchantments::add);

		// Iteratively add more enchantments until we run out or get unlucky
		while (random.nextInt(50) < (finalLevel + 1)) {
			// I don't think we need to check for emptiness here because the iterator will just return immediately
			EnchantmentHelper.removeConflicts(possibleEnchantments, Util.getLast(selectedEnchantments));
			if (possibleEnchantments.isEmpty()) {
				break;
			}
			Weighting.getRandom(random, possibleEnchantments).ifPresent(selectedEnchantments::add);
			finalLevel /= 2;
		}

		return selectedEnchantments;
	}
}