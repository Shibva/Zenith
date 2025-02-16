package safro.zenith.adventure.affix;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import safro.zenith.adventure.AdventureModule;
import safro.zenith.adventure.affix.socket.gem.bonus.GemBonus;
import safro.zenith.adventure.loot.LootCategory;
import safro.zenith.adventure.loot.LootRarity;
import safro.zenith.api.placebo.codec.EnumCodec;
import safro.zenith.api.placebo.json.PSerializer;
import safro.zenith.api.placebo.util.StepFunction;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Helper class for affixes that modify attributes, as the apply method is the same for most of those.
 */
public class AttributeAffix extends Affix {

	//Formatter::off
	public static final Codec<AttributeAffix> CODEC = RecordCodecBuilder.create(inst -> inst
		.group(
			Registry.ATTRIBUTE.byNameCodec().fieldOf("attribute").forGetter(a -> a.attribute),
			new EnumCodec<>(Operation.class).fieldOf("operation").forGetter(a -> a.operation),
			GemBonus.VALUES_CODEC.fieldOf("values").forGetter(a -> a.values),
			LootCategory.SET_CODEC.fieldOf("types").forGetter(a -> a.types))
			.apply(inst, AttributeAffix::new)
		);
	//Formatter::on
	public static final PSerializer<AttributeAffix> SERIALIZER = PSerializer.fromCodec("Attribute Affix", CODEC);

	protected final Attribute attribute;
	protected final Operation operation;
	protected final Map<LootRarity, StepFunction> values;
	protected final Set<LootCategory> types;

	protected transient final Map<LootRarity, ModifierInst> modifiers;

	public AttributeAffix(Attribute attr, Operation op, Map<LootRarity, StepFunction> values, Set<LootCategory> types) {
		super(AffixType.STAT);
		this.attribute = attr;
		this.operation = op;
		this.values = values;
		this.types = types;
		this.modifiers = values.entrySet().stream().map(entry -> Pair.of(entry.getKey(), new ModifierInst(attr, op, entry.getValue(), new HashMap<>()))).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
	}

	@Override
	public void addInformation(ItemStack stack, LootRarity rarity, float level, Consumer<Component> list) {
	};

	@Override
	public void addModifiers(ItemStack stack, LootRarity rarity, float level, EquipmentSlot type, BiConsumer<Attribute, AttributeModifier> map) {
		LootCategory cat = LootCategory.forItem(stack);
		if (cat.isNone()) {
			AdventureModule.LOGGER.debug("Attempted to apply the attributes of affix {} on item {}, but it is not an affix-compatible item!", this.getId(), stack.getHoverName().getString());
			return;
		}
		ModifierInst modif = this.modifiers.get(rarity);
		if (modif.attr == null) {
			AdventureModule.LOGGER.debug("The affix {} has attempted to apply a null attribute modifier to {}!", this.getId(), stack.getHoverName().getString());
			return;
		}
		for (EquipmentSlot slot : cat.getSlots(stack)) {
			if (slot == type) {
				map.accept(modif.attr, modif.build(slot, this.getId(), level));
			}
		}
	}

	@Override
	public boolean canApplyTo(ItemStack stack, LootRarity rarity) {
		LootCategory cat = LootCategory.forItem(stack);
		if (cat.isNone()) return false;
		return (this.types.isEmpty() || this.types.contains(cat)) && this.modifiers.containsKey(rarity);
	};

	@Override
	public PSerializer<? extends Affix> getSerializer() {
		return SERIALIZER;
	}

	public record ModifierInst(Attribute attr, Operation op, StepFunction valueFactory, Map<EquipmentSlot, UUID> cache) {

		public AttributeModifier build(EquipmentSlot slot, ResourceLocation id, float level) {
			return new AttributeModifier(this.cache.computeIfAbsent(slot, k -> UUID.randomUUID()), "affix:" + id, this.valueFactory.get(level), this.op);
		}
	}

}