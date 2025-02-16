package safro.zenith.adventure.affix.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import safro.zenith.adventure.affix.Affix;
import safro.zenith.adventure.affix.AffixType;
import safro.zenith.adventure.affix.socket.gem.bonus.GemBonus;
import safro.zenith.adventure.loot.LootCategory;
import safro.zenith.adventure.loot.LootRarity;
import safro.zenith.api.placebo.json.PSerializer;
import safro.zenith.api.placebo.util.StepFunction;

import java.util.Map;
import java.util.function.Consumer;

public class SpectralShotAffix extends Affix {

	//Formatter::off
	public static final Codec<SpectralShotAffix> CODEC = RecordCodecBuilder.create(inst -> inst
		.group(
			GemBonus.VALUES_CODEC.fieldOf("values").forGetter(a -> a.values))
			.apply(inst, SpectralShotAffix::new)
		);
	//Formatter::on
	public static final PSerializer<SpectralShotAffix> SERIALIZER = PSerializer.fromCodec("Spectral Shot Affix", CODEC);

	protected final Map<LootRarity, StepFunction> values;

	public SpectralShotAffix(Map<LootRarity, StepFunction> values) {
		super(AffixType.ABILITY);
		this.values = values;
	}

	@Override
	public boolean canApplyTo(ItemStack stack, LootRarity rarity) {
		return LootCategory.forItem(stack).isRanged() && this.values.containsKey(rarity);
	}

	@Override
	public void addInformation(ItemStack stack, LootRarity rarity, float level, Consumer<Component> list) {
		list.accept(Component.translatable("affix." + this.getId() + ".desc", ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(100 * getTrueLevel(rarity, level))).withStyle(ChatFormatting.YELLOW));
	}

	@Override
	public void onArrowFired(ItemStack stack, LootRarity rarity, float level, LivingEntity user, AbstractArrow arrow) {
		if (user.level.random.nextFloat() <= getTrueLevel(rarity, level)) {
			if (!user.level.isClientSide) {
				ArrowItem arrowitem = (ArrowItem) Items.SPECTRAL_ARROW;
				AbstractArrow spectralArrow = arrowitem.createArrow(user.level, ItemStack.EMPTY, user);
				spectralArrow.shoot(user.getXRot(), user.getYRot(), 0.0F, 2.0F, 1.0F);
				this.cloneMotion(arrow, spectralArrow);
				spectralArrow.setCritArrow(arrow.isCritArrow());
				spectralArrow.setBaseDamage(arrow.getBaseDamage());
				spectralArrow.setKnockback(arrow.getKnockback());
				spectralArrow.setRemainingFireTicks(arrow.getRemainingFireTicks());
				spectralArrow.pickup = Pickup.CREATIVE_ONLY;
				arrow.level.addFreshEntity(spectralArrow);
			}
		}
	}

	private void cloneMotion(AbstractArrow src, AbstractArrow dest) {
		dest.setDeltaMovement(src.getDeltaMovement().scale(1));
		dest.setYRot(src.getYRot());
		dest.setXRot(src.getXRot());
		dest.yRotO = dest.yRotO;
		dest.xRotO = dest.xRotO;
	}

	private float getTrueLevel(LootRarity rarity, float level) {
		return this.values.get(rarity).get(level);
	}

	@Override
	public PSerializer<? extends Affix> getSerializer() {
		return SERIALIZER;
	}

}
