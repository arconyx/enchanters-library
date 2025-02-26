package arconyx.enchanterslibrary;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.util.collection.Weight;

public class WeightedEnchantmentLevelEntry extends EnchantmentLevelEntry {
    private final Weight weight;

    public WeightedEnchantmentLevelEntry(Enchantment enchantment, int level, int weight) {
        super(enchantment, level);
        this.weight = Weight.of(weight);
    }

    public WeightedEnchantmentLevelEntry(EnchantmentLevelEntry entry, int weight) {
        super(entry.enchantment, entry.level);
        this.weight = Weight.of(weight);
    }

    @Override
    public Weight getWeight() {
        return this.weight;
    }
}
