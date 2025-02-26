package arconyx.enchanterslibrary;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.util.collection.Weight;

import java.util.Map;

public class WeightedEnchantmentLevelEntry extends EnchantmentLevelEntry {
    private final Weight weight;

    public WeightedEnchantmentLevelEntry(Enchantment enchantment, int level, int weight) {
        super(enchantment, level);
        this.weight = Weight.of(weight);
    }

    public WeightedEnchantmentLevelEntry(Map.Entry<Enchantment, Integer> entry, int weight) {
        this(entry.getKey(), entry.getValue(), weight);
    }

    @Override
    public Weight getWeight() {
        return this.weight;
    }
}
