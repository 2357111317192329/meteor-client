/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.entity;

import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.Items;
import java.util.Comparator;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public enum SortPriority implements Comparator<Entity> {
    LowestDistance(Comparator.comparingDouble(PlayerUtils::squaredDistanceTo)),
    HighestDistance((e1, e2) -> Double.compare(PlayerUtils.squaredDistanceTo(e2), PlayerUtils.squaredDistanceTo(e1))),
    LowestHealth(SortPriority::sortHealth),
    HighestHealth((e1, e2) -> sortHealth(e2, e1)),
    ClosestAngle(SortPriority::sortAngle),
    HighestDamage(SortPriority::sortDamage);
    private final Comparator<Entity> comparator;

    SortPriority(Comparator<Entity> comparator) {
        this.comparator = comparator;
    }

    @Override
    public int compare(Entity o1, Entity o2) {
        return comparator.compare(o1, o2);
    }
    private static int sortHealth(Entity e1, Entity e2) {
        boolean e1l = e1 instanceof LivingEntity;
        boolean e2l = e2 instanceof LivingEntity;

        if (!e1l && !e2l) return 0;
        else if (e1l && !e2l) return 1;
        else if (!e1l) return -1;

        return Float.compare(((LivingEntity) e1).getHealth(), ((LivingEntity) e2).getHealth());
    }

    private static int sortAngle(Entity e1, Entity e2) {
        boolean e1l = e1 instanceof LivingEntity;
        boolean e2l = e2 instanceof LivingEntity;

        if (!e1l && !e2l) return 0;
        else if (e1l && !e2l) return 1;
        else if (!e1l) return -1;

        double e1yaw = Math.abs(Rotations.getYaw(e1) - mc.player.getYaw());
        double e2yaw = Math.abs(Rotations.getYaw(e2) - mc.player.getYaw());

        double e1pitch = Math.abs(Rotations.getPitch(e1) - mc.player.getPitch());
        double e2pitch = Math.abs(Rotations.getPitch(e2) - mc.player.getPitch());

        return Double.compare(e1yaw * e1yaw + e1pitch * e1pitch, e2yaw * e2yaw + e2pitch * e2pitch);
    }
    private static int sortDamage(Entity e1, Entity e2) {
        boolean p1 = e1 instanceof PlayerEntity;
        boolean p2 = e2 instanceof PlayerEntity;

        if (p1 && !p2) return -1; 
        if (!p1 && p2) return 1;  
        if (p1 && p2) return 0;  
        double dmg1 = getAttackDamage(e1);
        double dmg2 = getAttackDamage(e2);
        if (Double.compare(dmg2, dmg1) == 0) {
            double dist1 = PlayerUtils.squaredDistanceTo(e1);
            double dist2 = PlayerUtils.squaredDistanceTo(e2);
            return Double.compare(dist1, dist2); 
        }
        return Double.compare(dmg2, dmg1); 
    }
    public static double getAttackDamage(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return 0.0;
        };
        double total=0.0;
        ItemStack mainHand = living.getEquippedStack(EquipmentSlot.MAINHAND);
        if (!mainHand.isEmpty()) {
            total=DamageUtils.getAttackDamage(living,mc.player,mainHand);
        }
        else{
            total=DamageUtils.getAttackDamage(living,mc.player);
        }
        double baseDamage;
        if(entity instanceof CreeperEntity creeper){
            Vec3d explosionPos = creeper.getPos();
            float power = creeper.isCharged() ? 6.0f : 3.0f;
            baseDamage = DamageUtils.explosionDamage(mc.player, explosionPos, power, true);
        } 
        else if(entity.getType() == EntityType.DROWNED){
            if(!mainHand.isEmpty() && mainHand.isOf(Items.TRIDENT)){
				baseDamage=DamageUtils.calculateReductions(8.0f,mc.player,mc.world.getDamageSources().trident(entity,entity));
			}
			else{
				baseDamage=total;
			}
			if(living.isBaby()){
				baseDamage*=1.1;
			}
		}
        else if(entity.getType() == EntityType.ELDER_GUARDIAN){
			baseDamage=DamageUtils.calculateReductions(3.0f,mc.player,mc.world.getDamageSources().magic());
			baseDamage=Math.max(baseDamage,(double) DamageUtils.calculateReductions(8.0f,mc.player,mc.world.getDamageSources().mobAttack(living)));
			baseDamage*=1.1;
		} 
        else if(entity.getType() == EntityType.ENDER_DRAGON){
			baseDamage=DamageUtils.calculateReductions(6.0f,mc.player,mc.world.getDamageSources().dragonBreath());
			baseDamage=Math.max(baseDamage,(double) DamageUtils.calculateReductions(10.0f,mc.player,mc.world.getDamageSources().mobAttack(living)));
		}
        else if(entity.getType() == EntityType.EVOKER){
			baseDamage=DamageUtils.calculateReductions(6.0f,mc.player,mc.world.getDamageSources().indirectMagic(entity,entity));
			baseDamage=Math.max(baseDamage,(double) DamageUtils.calculateReductions(9.0f,mc.player,mc.world.getDamageSources().mobAttack(living)));
		}
        else if(entity.getType() == EntityType.GHAST){
			DamageSource fireball = DamageUtils.createFireballDamageSource(mc.world,entity);
			baseDamage=DamageUtils.calculateReductions(6.0f,mc.player,fireball);
			baseDamage=Math.max(baseDamage,(double) DamageUtils.explosionDamage(mc.player, mc.player.getPos(),1.0f, false));
		}
        else if(entity.getType() == EntityType.GUARDIAN) baseDamage=6.0;
        else if(entity.getType() == EntityType.LLAMA) baseDamage=1.0;
        else if(entity.getType() == EntityType.PILLAGER) baseDamage=5.0;
        else if(entity.getType() == EntityType.PUFFERFISH) baseDamage=3.0;
        else if(entity.getType() == EntityType.SHULKER) baseDamage=4.0;
        else if(entity.getType() == EntityType.SKELETON) baseDamage=5.3;
        else if(entity.getType() == EntityType.STRAY) baseDamage=5.3;
        else if(entity.getType() == EntityType.VILLAGER) baseDamage=8.0;
        else if(entity.getType() == EntityType.WITCH) baseDamage=24.0;
        else if(entity.getType() == EntityType.WITHER_SKELETON) baseDamage=11.3;
        else if(entity.getType() == EntityType.WITHER) baseDamage=41.7;
        else if(entity.getType() == EntityType.BEE) baseDamage=11.3;
        else if(entity.getType() == EntityType.AXOLOTL) baseDamage=0.0;
        else if(entity.getType() == EntityType.FOX) baseDamage=0.0;
        else if(entity.getType() == EntityType.FROG) baseDamage=0.0;
        else if(entity.getType() == EntityType.MAGMA_CUBE) baseDamage=2*total;
        else if(entity.getType() == EntityType.SLIME) baseDamage=2*total;
        else baseDamage=total;
        //ChatUtils.info(entity.getName().getString());
        //ChatUtils.info(Double.toString(baseDamage));
        return baseDamage;
    }
}
