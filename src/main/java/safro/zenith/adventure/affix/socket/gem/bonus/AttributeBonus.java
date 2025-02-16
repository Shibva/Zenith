package safro.zenith.adventure.affix.socket.gem.bonus;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemStack;
import safro.zenith.Zenith;
import safro.zenith.adventure.affix.socket.gem.GemClass;
import safro.zenith.adventure.affix.socket.gem.GemItem;
import safro.zenith.adventure.loot.LootRarity;
import safro.zenith.api.placebo.codec.EnumCodec;
import safro.zenith.api.placebo.util.StepFunction;

import java.util.Map;
import java.util.function.BiConsumer;

public class AttributeBonus extends GemBonus {

	//Formatter::off
	public static Codec<AttributeBonus> CODEC = RecordCodecBuilder.create(inst -> inst
		.group(
			gemClass(),
			Registry.ATTRIBUTE.byNameCodec().fieldOf("attribute").forGetter(a -> a.attribute),
			new EnumCodec<>(Operation.class).fieldOf("operation").forGetter(a -> a.operation),
			VALUES_CODEC.fieldOf("values").forGetter(a -> a.values))
			.apply(inst, AttributeBonus::new)
		);
	//Formatter::on

	protected final Attribute attribute;
	protected final Operation operation;
	protected final Map<LootRarity, StepFunction> values;

	public AttributeBonus(GemClass gemClass, Attribute attr, Operation op, Map<LootRarity, StepFunction> values) {
		super(Zenith.loc("attribute"), gemClass);
		this.attribute = attr;
		this.operation = op;
		this.values = values;
	}

	@Override
	public void addModifiers(ItemStack gem, LootRarity rarity, int facets, BiConsumer<Attribute, AttributeModifier> map) {
		map.accept(this.attribute, read(gem, rarity, facets));
	}

	@Override
	public Component getSocketBonusTooltip(ItemStack gem, LootRarity rarity, int facets) {
		return GemItem.toComponent(this.attribute, read(gem, rarity, facets));
	}

	@Override
	public int getMaxFacets(LootRarity rarity) {
		return this.values.get(rarity).steps();
	}

	@Override
	public AttributeBonus validate() {
		Preconditions.checkNotNull(this.attribute, "Invalid AttributeBonus with null attribute");
		Preconditions.checkNotNull(this.operation, "Invalid AttributeBonus with null operation");
		Preconditions.checkNotNull(this.values, "Invalid AttributeBonus with null values");
		return this;
	}

	@Override
	public boolean supports(LootRarity rarity) {
		return this.values.containsKey(rarity);
	}

	@Override
	public int getNumberOfUUIDs() {
		return 1;
	}

	public AttributeModifier read(ItemStack gem, LootRarity rarity, int facets) {
		return new AttributeModifier(GemItem.getUUIDs(gem).get(0), "apoth.gem_modifier", this.values.get(rarity).getForStep(facets), this.operation);
	}

	@Override
	public Codec<? extends GemBonus> getCodec() {
		return CODEC;
	}

}
