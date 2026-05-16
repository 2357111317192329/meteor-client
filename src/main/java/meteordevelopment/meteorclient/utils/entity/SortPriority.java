/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.entity;

import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;

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

        double e1yaw = Math.abs(Rotations.getYaw(e1) - mc.player.getYRot());
        double e2yaw = Math.abs(Rotations.getYaw(e2) - mc.player.getYRot());

        double e1pitch = Math.abs(Rotations.getPitch(e1) - mc.player.getXRot());
        double e2pitch = Math.abs(Rotations.getPitch(e2) - mc.player.getXRot());

        return Double.compare(e1yaw * e1yaw + e1pitch * e1pitch, e2yaw * e2yaw + e2pitch * e2pitch);
    }
    private static int sortDamage(Entity e1, Entity e2) {
        boolean p1 = e1 instanceof Player;
        boolean p2 = e2 instanceof Player;

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
        ItemStack mainHand = living.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!mainHand.isEmpty()) {
            total=DamageUtils.getAttackDamage(living,mc.player,mainHand);
        }
        else{
            total=DamageUtils.getAttackDamage(living,mc.player);
        }
        double baseDamage=0;
        if(entity instanceof Creeper creeper){
            Vec3 explosionPos = creeper.position();
            float power = creeper.isPowered() ? 6 : 3;
            baseDamage = DamageUtils.explosionDamage(mc.player, explosionPos, power, true);
        } 
        else if(entity.getType() == EntityType.DROWNED){
            if(!mainHand.isEmpty() && mainHand.is(Items.TRIDENT)){
				baseDamage=DamageUtils.calculateReductions(8,mc.player,mc.level.damageSources().trident(entity,entity));
			}
			else{
				baseDamage=total;
			}
			if(living.isBaby()){
				baseDamage*=1.1;
			}
		}
        else if(entity.getType() == EntityType.ELDER_GUARDIAN){
			baseDamage=DamageUtils.calculateReductions(3,mc.player,mc.level.damageSources().magic());
			baseDamage=Math.max(baseDamage,(double) DamageUtils.calculateReductions(8,mc.player,mc.level.damageSources().mobAttack(living)));
			baseDamage*=1.1;
		} 
        else if(entity.getType() == EntityType.ENDER_DRAGON){
			baseDamage=DamageUtils.calculateReductions(6,mc.player,mc.level.damageSources().dragonBreath());
			baseDamage=Math.max(baseDamage,(double) DamageUtils.calculateReductions(10,mc.player,mc.level.damageSources().mobAttack(living)));
		}
        else if(entity.getType() == EntityType.EVOKER){
			baseDamage=DamageUtils.calculateReductions(6,mc.player,mc.level.damageSources().indirectMagic(entity,entity));
			baseDamage=Math.max(baseDamage,(double) DamageUtils.calculateReductions(9,mc.player,mc.level.damageSources().mobAttack(living)));
		}
        else if(entity.getType() == EntityType.GHAST){
			if(!mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)){
				DamageSource fireball = DamageUtils.createFireballDamageSource(mc.level,entity);
			    baseDamage=DamageUtils.calculateReductions(6,mc.player,fireball);
			}
			baseDamage=Math.max(baseDamage,(double) DamageUtils.explosionDamage(mc.player, mc.player.position(),1, false));
		}
        else if(entity.getType() == EntityType.GUARDIAN){
			DamageUtils.Vec4f damages = new DamageUtils.Vec4f(0,1,1,4.5f);
			baseDamage=DamageUtils.calculateReductions(damages,mc.player,mc.level.damageSources().magic());
			baseDamage=Math.max(baseDamage,(double) DamageUtils.calculateReductions(6,mc.player,mc.level.damageSources().mobAttack(living)));
		}
        else if(entity.getType() == EntityType.LLAMA){
            baseDamage=DamageUtils.calculateReductions(1,mc.player,mc.level.damageSources().spit(entity,living));
        }
        else if(entity.getType() == EntityType.PILLAGER){
            if(!mainHand.isEmpty() && mainHand.is(Items.CROSSBOW)){
                DamageSource arrow = DamageUtils.createArrowDamageSource(mc.level,entity);
				baseDamage=DamageUtils.calculateReductions(11,mc.player,arrow);
			}
			else{
				baseDamage=0.0;
			}
        }
        else if(entity.getType() == EntityType.PUFFERFISH){
            baseDamage=DamageUtils.calculateReductions(3,mc.player,mc.level.damageSources().mobAttack(living));
            baseDamage+=0.8;//poison 1
            baseDamage*=1.1;//poison 
        } 
        else if(entity.getType() == EntityType.SHULKER){
            baseDamage=DamageUtils.calculateReductions(4,mc.player,mc.level.damageSources().mobProjectile(entity,living));
            baseDamage*=1.1;//levitation
        }
        else if(entity.getType() == EntityType.SKELETON){
            if(!mainHand.isEmpty() && mainHand.is(Items.BOW)){
                DamageUtils.Vec4f arrbase0 = new DamageUtils.Vec4f(0,2.11f,2.22f,2.33f);
                int powerlevel = Utils.getEnchantmentLevel(mainHand, Enchantments.POWER);
                DamageUtils.Vec4f arrbase1;
                if(powerlevel>0){
                    float increase = 0.5f*powerlevel+0.5f;
                    DamageUtils.Vec4f incrvec = new DamageUtils.Vec4f(0,increase,increase,increase);
                    arrbase1=arrbase0.add(incrvec);
                }
                else{
                    arrbase1=arrbase0;
                }
                float arrspeed = 1.6f; // blocks per tick
                DamageUtils.Vec4f arrowdamage = arrbase1.mul(arrspeed).ceil();
                DamageSource arrow = DamageUtils.createArrowDamageSource(mc.level,entity);
				baseDamage=DamageUtils.calculateReductions(arrowdamage,mc.player,arrow);
                int flamelevel = Utils.getEnchantmentLevel(mainHand, Enchantments.FLAME);
                if(mc.player.isInWater() || mc.player.isInWaterOrRain() || mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)){
                    flamelevel=0;
                }
                int knockbacklevel = Utils.getEnchantmentLevel(mainHand, Enchantments.KNOCKBACK);
                if((flamelevel+knockbacklevel)>0){
                    baseDamage*=1.1;
                    //ChatUtils.info("special");
                }
			}
			else{
				baseDamage=total;
			}
        }
        else if(entity.getType() == EntityType.STRAY){
            if(!mainHand.isEmpty() && mainHand.is(Items.BOW)){
                DamageUtils.Vec4f arrbase0 = new DamageUtils.Vec4f(0,2.11f,2.22f,2.33f);
                int powerlevel = Utils.getEnchantmentLevel(mainHand, Enchantments.POWER);
                DamageUtils.Vec4f arrbase1;
                if(powerlevel>0){
                    float increase = 0.5f*powerlevel+0.5f;
                    DamageUtils.Vec4f incrvec = new DamageUtils.Vec4f(0,increase,increase,increase);
                    arrbase1=arrbase0.add(incrvec);
                }
                else{
                    arrbase1=arrbase0;
                }
                float arrspeed = 1.6f; // blocks per tick
                DamageUtils.Vec4f arrowdamage = arrbase1.mul(arrspeed).ceil();
                DamageSource arrow = DamageUtils.createArrowDamageSource(mc.level,entity);
				baseDamage=DamageUtils.calculateReductions(arrowdamage,mc.player,arrow);
                int flamelevel = Utils.getEnchantmentLevel(mainHand, Enchantments.FLAME);
                if(mc.player.isInWater() || mc.player.isInWaterOrRain() || mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)){
                    flamelevel=0;
                }
                int knockbacklevel = Utils.getEnchantmentLevel(mainHand, Enchantments.KNOCKBACK);
                if((flamelevel+knockbacklevel)>0){
                    baseDamage*=1.1;
                    //ChatUtils.info("special");
                }
                baseDamage*=1.1;//slowness
			}
			else{
				baseDamage=total;
			}
        }
        else if(entity.getType() == EntityType.BOGGED){
            if(!mainHand.isEmpty() && mainHand.is(Items.BOW)){
                DamageUtils.Vec4f arrbase0 = new DamageUtils.Vec4f(0,2.11f,2.22f,2.33f);
                int powerlevel = Utils.getEnchantmentLevel(mainHand, Enchantments.POWER);
                DamageUtils.Vec4f arrbase1;
                if(powerlevel>0){
                    float increase = 0.5f*powerlevel+0.5f;
                    DamageUtils.Vec4f incrvec = new DamageUtils.Vec4f(0,increase,increase,increase);
                    arrbase1=arrbase0.add(incrvec);
                }
                else{
                    arrbase1=arrbase0;
                }
                float arrspeed = 1.6f; // blocks per tick
                DamageUtils.Vec4f arrowdamage = arrbase1.mul(arrspeed).ceil();
                DamageSource arrow = DamageUtils.createArrowDamageSource(mc.level,entity);
				baseDamage=DamageUtils.calculateReductions(arrowdamage,mc.player,arrow);
                int flamelevel = Utils.getEnchantmentLevel(mainHand, Enchantments.FLAME);
                if(mc.player.isInWater() || mc.player.isInWaterOrRain() || mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)){
                    flamelevel=0;
                }
                int knockbacklevel = Utils.getEnchantmentLevel(mainHand, Enchantments.KNOCKBACK);
                if((flamelevel+knockbacklevel)>0){
                    baseDamage*=1.1;
                    //ChatUtils.info("special");
                }
                baseDamage+=0.8;//poison 1
                baseDamage*=1.1;//poison
			}
			else{
				baseDamage=total;
			}
        }
        else if(entity.getType() == EntityType.VILLAGER){
            baseDamage=0.0;
        }
        else if(entity.getType() == EntityType.WITCH){
            baseDamage=DamageUtils.calculateReductions(6,mc.player,mc.level.damageSources().magic());
            baseDamage*=1.1;//slowness
            baseDamage+=0.8;//poison 1
            baseDamage*=1.1;//poison
            baseDamage*=1.1;//weakness
        }
        else if(entity.getType() == EntityType.WITHER_SKELETON){
            baseDamage=total;
            baseDamage+=0.5;//wither 1
            baseDamage*=1.1;//wither effect
            baseDamage*=1.1;//black heart
        }
        else if(entity.getType() == EntityType.WITHER){
			DamageSource witherSkull = DamageUtils.createwitherSkullDamageSource(mc.level,entity);
			baseDamage=DamageUtils.calculateReductions(8,mc.player,witherSkull);
			baseDamage=Math.max(baseDamage,(double) DamageUtils.explosionDamage(mc.player, mc.player.position(),1, false));
            baseDamage+=1.0;//wither 2
            baseDamage*=1.1;//wither effect
        }
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
