package safro.zenith.adventure.affix.effect;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import safro.zenith.adventure.affix.Affix;
import safro.zenith.adventure.affix.AffixHelper;
import safro.zenith.adventure.affix.AffixInstance;
import safro.zenith.adventure.affix.AffixType;
import safro.zenith.adventure.loot.LootCategory;
import safro.zenith.adventure.loot.LootRarity;
import safro.zenith.api.placebo.json.PSerializer;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Teleport Drops
 */
public class TelepathicAffix extends Affix {

	//Formatter::off
	public static final Codec<TelepathicAffix> CODEC = RecordCodecBuilder.create(inst -> inst
		.group(
			LootRarity.CODEC.fieldOf("min_rarity").forGetter(a -> a.minRarity))
			.apply(inst, TelepathicAffix::new)
		);
	//Formatter::on
	public static final PSerializer<TelepathicAffix> SERIALIZER = PSerializer.fromCodec("Telepathic Affix", CODEC);

	public static Vec3 blockDropTargetPos = null;

	protected LootRarity minRarity;

	public TelepathicAffix(LootRarity minRarity) {
		super(AffixType.ABILITY);
		this.minRarity = minRarity;
	}

	@Override
	public boolean canApplyTo(ItemStack stack, LootRarity rarity) {
		LootCategory cat = LootCategory.forItem(stack);
		if (cat.isNone()) return false;
		return (cat.isRanged() || cat.isLightWeapon() || cat.isBreaker()) && rarity.isAtLeast(minRarity);
	}

	@Override
	public void addInformation(ItemStack stack, LootRarity rarity, float level, Consumer<Component> list) {
		LootCategory cat = LootCategory.forItem(stack);
		String type = cat.isRanged() || cat.isWeapon() ? "weapon" : "tool";
		list.accept(Component.translatable("affix." + this.getId() + ".desc." + type).withStyle(ChatFormatting.YELLOW));
	}

	@Override
	public boolean enablesTelepathy() {
		return true;
	}

	@Override
	public PSerializer<? extends Affix> getSerializer() {
		return SERIALIZER;
	}

	public static void drops(DamageSource src, LivingEntity killed, Collection<ItemEntity> drops) {
		boolean canTeleport = false;
		Vec3 targetPos = null;
		if (src.getDirectEntity() instanceof AbstractArrow arrow && arrow.getOwner() != null) {
			canTeleport = AffixHelper.streamAffixes(arrow).anyMatch(AffixInstance::enablesTelepathy);
			targetPos = arrow.getOwner().position();
		} else if (src.getDirectEntity() instanceof LivingEntity living) {
			ItemStack weapon = living.getMainHandItem();
			canTeleport = AffixHelper.streamAffixes(weapon).anyMatch(AffixInstance::enablesTelepathy);
			targetPos = living.position();
		}

		if (canTeleport) {
			for (ItemEntity item : drops) {
				item.setPos(targetPos.x, targetPos.y, targetPos.z);
				item.setPickUpDelay(0);
			}
		}
	}

	public static Affix read(JsonObject obj) {
		return new TelepathicAffix(GSON.fromJson(obj.get("min_rarity"), LootRarity.class));
	}

	public JsonObject write() {
		return new JsonObject();
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(this.minRarity.id());
	}

	public static Affix read(FriendlyByteBuf buf) {
		return new TelepathicAffix(LootRarity.byId(buf.readUtf()));
	}

}
