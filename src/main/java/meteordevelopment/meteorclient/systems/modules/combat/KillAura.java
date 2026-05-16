/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class KillAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    // General

    private final Setting<AttackItems> attackWhenHolding = sgGeneral.add(new EnumSetting.Builder<AttackItems>()
        .name("attack-when-holding")
        .description("Only attacks an entity when a specified item is in your hand.")
        .defaultValue(AttackItems.Weapons)
        .build()
    );

    private final Setting<List<Item>> weapons = sgGeneral.add(new ItemListSetting.Builder()
        .name("selected-weapon-types")
        .description("Which types of weapons to attack with (if you select the diamond sword, any type of sword may be used to attack).")
        .defaultValue(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.TRIDENT)
        .filter(FILTER::contains)
        .visible(() -> attackWhenHolding.get() == AttackItems.Weapons)
        .build()
    );

    private final Setting<RotationMode> rotation = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("rotate")
        .description("Determines when you should rotate towards the target.")
        .defaultValue(RotationMode.Always)
        .build()
    );
    private final Setting<Boolean> rotateback = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate-back")
        .description("wether to rotate back after certain ticks")
        .defaultValue(true)
        .visible(() -> ((rotation.get() == RotationMode.Always) || (rotation.get() == RotationMode.OnHit)))
        .build()
    );
    private final Setting<Integer> rotatebackticks = sgGeneral.add(new IntSetting.Builder()
        .name("rotate-back-ticks")
        .description("How ticks to rotate back after roatate")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 20)
        .visible(rotateback::get)
        .build()
    );
    private final Setting<Integer> rotateDelay = sgGeneral.add(new IntSetting.Builder()
        .name("rotatet-delay")
        .description("How many ticks should you wait to hit the entity after rotate")
        .defaultValue(1)
        .min(0)
        .sliderMax(6)
        .visible(() -> ((rotation.get() == RotationMode.Always) || (rotation.get() == RotationMode.OnHit)))
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Switches to an acceptable weapon when attacking the target.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Switches to your previous slot when done attacking the target.")
        .defaultValue(false)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<ShieldMode> shieldMode = sgGeneral.add(new EnumSetting.Builder<ShieldMode>()
        .name("shield-mode")
        .description("""
                What to do when your target is blocking with a shield:
                - Ignore:   Don't attack them if they are blocking
                - Break:    Swap to an axe to disable the shield (Only if Auto Switch is enabled)
                - None:     Attack them as normal
            """)
        .defaultValue(ShieldMode.None)
        .build()
    );

    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-click")
        .description("Only attacks when holding left click.")
        .defaultValue(false)
        .build()
    );
    private final Setting<AnticheatMode> anticheat = sgGeneral.add(new EnumSetting.Builder<AnticheatMode>()
        .name("anti-cheat-type")
        .description("anti-cheat type")
        .defaultValue(AnticheatMode.MODE_3C3U)
        .build()
    );

    private final Setting<Boolean> pauseOnCombat = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-baritone")
        .description("Freezes Baritone temporarily until you are finished attacking the entity.")
        .defaultValue(true)
        .build()
    );

    // Targeting

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to filter targets within range.")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    );

    private final Setting<Integer> maxTargets = sgTargeting.add(new IntSetting.Builder()
        .name("max-targets")
        .description("How many entities to target at once.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );
    private final Setting<Boolean> custumrange = sgTargeting.add(new BoolSetting.Builder()
        .name("custum-range")
        .description("Use custum range instead of player's interaction range.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum range the entity can be to attack it.")
        .defaultValue(3.0)
        .min(0)
        .sliderMax(6)
        .visible(custumrange::get)
        .build()
    );

    private final Setting<EntityAge> passiveMobAgeFilter = sgTargeting.add(new EnumSetting.Builder<EntityAge>()
        .name("passive-mob-age-filter")
        .description("Determines the age of passive mobs to target (animals, villagers).")
        .defaultValue(EntityAge.Adult)
        .build()
    );

    private final Setting<EntityAge> hostileMobAgeFilter = sgTargeting.add(new EnumSetting.Builder<EntityAge>()
        .name("hostile-mob-age-filter")
        .description("Determines the age of hostile mobs to target (zombies, piglins, hoglins, zoglins).")
        .defaultValue(EntityAge.Both)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("Whether or not to attack mobs with a name.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignorePassive = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-passive")
        .description("Will only attack sometimes passive mobs if they are targeting you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-tamed")
        .description("Will avoid attacking mobs you tamed.")
        .defaultValue(false)
        .build()
    );

    // Timing

    private final Setting<Boolean> pauseOnLag = sgTiming.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pauses if the server is lagging.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnUse = sgTiming.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Does not attack while using an item.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnCA = sgTiming.add(new BoolSetting.Builder()
        .name("pause-on-CA")
        .description("Does not attack while CA is placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tpsSync = sgTiming.add(new BoolSetting.Builder()
        .name("TPS-sync")
        .description("Tries to sync attack delay with the server's TPS.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> customDelay = sgTiming.add(new BoolSetting.Builder()
        .name("custom-delay")
        .description("Use a custom delay instead of the vanilla cooldown.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hitDelay = sgTiming.add(new IntSetting.Builder()
        .name("hit-delay")
        .description("How fast you hit the entity in ticks.")
        .defaultValue(11)
        .min(0)
        .sliderMax(60)
        .visible(customDelay::get)
        .build()
    );

    private final Setting<Integer> switchDelay = sgTiming.add(new IntSetting.Builder()
        .name("switch-delay")
        .description("How many ticks to wait before hitting an entity after switching hotbar slots.")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final static ArrayList<Item> FILTER = new ArrayList<>(List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.MACE, Items.DIAMOND_SPEAR, Items.TRIDENT));
    private final List<Entity> targets = new ArrayList<>();
    private int switchTimer, hitTimer;
    private boolean wasPathing = false;
    public boolean roatating = false,waithit = false;
    public boolean attacking, swapped;
    public boolean usingspear = false;
    public boolean shouldusespear = false;
    public static int previousSlot;
    private long time=0,next_rotate=0;
    private float preYaw=0,prePitch=0;
    private int rbtickleft=0,rdtickleft=0;
    private Entity tmptarget;

    public KillAura() {
        super(Categories.Combat, "kill-aura", "Attacks specified entities around you.");
    }

    @Override
    public void onActivate() {
        previousSlot = -1;
        swapped = false;
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        stopAttacking();
    }
    private boolean EntityInSight(Entity target) {
        if (!entityCheck(target)) return false;
        Player player = mc.player;
        if (player == null) return false;
        double reach = (custumrange.get()) ? range.get() : player.entityInteractionRange();
        if (usingspear){
            reach = (custumrange.get()) ? range.get() : 4.5;
        }
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(reach));

        Optional<Vec3> hit = target.getBoundingBox().clip(eyePos, endPos);
        return hit.isPresent();
    }
    @EventHandler
    private void onTick(TickEvent.Post event){
        if(rdtickleft<=0 && waithit){
            if(tmptarget!=null){
                //info("attack");
                attack(tmptarget);
            }
            waithit=false;
        }
        if(rbtickleft<=0 && roatating){
            Realrotate(mc.player.getYRot(),mc.player.getXRot());
        }
        ItemStack mainHand = mc.player.getItemBySlot(EquipmentSlot.MAINHAND);
        if(!mainHand.isEmpty() && mainHand.has(DataComponents.PIERCING_WEAPON)){
            usingspear = true;
        }
        else{
            usingspear = false;
        }
        time++;
        if(rbtickleft>0){
            //info("rbtickleft="+rbtickleft);
            rbtickleft--;
        }
        if(rdtickleft>0){
            //info("rdtickleft="+rdtickleft);
            rdtickleft--;
        }
        
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameType.SPECTATOR) {
            stopAttacking();
            return;
        }
        if (pauseOnUse.get() && (mc.gameMode.isDestroying() || mc.player.isUsingItem())) {
            stopAttacking();
            return;
        }
        if (onlyOnClick.get() && !mc.options.keyAttack.isDown()) {
            stopAttacking();
            return;
        }
        if (TickRate.INSTANCE.getTimeSinceLastTick() >= 1f && pauseOnLag.get()) {
            stopAttacking();
            return;
        }
        if (pauseOnCA.get() && Modules.get().get(CrystalAura.class).isActive() && Modules.get().get(CrystalAura.class).kaTimer > 0) {
            stopAttacking();
            return;
        }
        if (anticheat.get() == AnticheatMode.MODE_onlyOnLook) {
            Entity targeted = mc.crosshairPickEntity;

            if (targeted == null || !entityCheck(targeted)) {
                stopAttacking();
                return;
            }

            targets.clear();
            targets.add(mc.crosshairPickEntity);
        } else {
            targets.clear();
            TargetUtils.getList(targets, this::entityCheck, priority.get(), maxTargets.get());
        }

        if (targets.isEmpty()) {
            stopAttacking();
            return;
        }

        Entity primary = targets.getFirst();
        //info(primary.getName().getString());
        double dist = PlayerUtils.closestdistanceTo(primary);
        //info(primary.getName().getString()+Double.toString(dist)+"格");
        if(dist > mc.player.entityInteractionRange()){
            shouldusespear=true;
        }
        else{
            usingspear=false;
            shouldusespear=false;
            swapwep(primary);
        }
        double upcomedam = SortPriority.getAttackDamage(primary);
        //info(primary.getName().getString()+Double.toString(dist)+"格"+" "+"傷害"+Double.toString(upcomedam));

        if (autoSwitch.get()) {
            swapwep(primary);
        }

        if (!acceptableWeapon(mc.player.getMainHandItem())) {
            stopAttacking();
            return;
        }

        attacking = true;
        Vec3 closestPoint=PlayerUtils.closestPointTo(primary);
        if (rotation.get() == RotationMode.Always)
            Realrotate((float)Rotations.getYaw(closestPoint),(float)Rotations.getPitch(closestPoint));
        if (pauseOnCombat.get() && PathManagers.get().isPathing() && !wasPathing) {
            PathManagers.get().pause();
            wasPathing = true;
        }
        targets.stream().filter(this::entityCheck).filter(this::delayCheck).forEach(this::attack);
    }
    private void swapwep(Entity target){
        FindItemResult weaponResult = new FindItemResult(mc.player.getInventory().getSelectedSlot(), -1);
        if (attackWhenHolding.get() == AttackItems.Weapons){
            weaponResult = InvUtils.find(this::acceptableWeapon, 0, 8);
            if (shouldShieldBreak()) {
                FindItemResult axeResult = InvUtils.find(itemStack -> itemStack.getItem() instanceof AxeItem, 0, 8);
                if (axeResult.found()) weaponResult = axeResult;
            }

            if (!swapped) {
                previousSlot = mc.player.getInventory().getSelectedSlot();
                swapped = true;
            }
            InvUtils.swap(weaponResult.slot(), false);
        }
        else{
            int slot = mc.player.getInventory().getSelectedSlot();
            double tmpdamage = 0;
            double currentDamage = 0;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if(shouldusespear){
                    PiercingWeapon piercing = stack.get(DataComponents.PIERCING_WEAPON);
                    if (piercing == null) {
                        continue;
                    }
                }
                if ((stack.getMaxDamage() - stack.getDamageValue()) > 10){
                    currentDamage = DamageUtils.getAttackDamage(mc.player, target, stack);
                }
                if (currentDamage > tmpdamage) {
                    tmpdamage = currentDamage;
                    slot = i;
                }
            }
            InvUtils.swap(slot, false);
        }
        
    }
    private void Realrotate(float yaw,float pitch) {
        if(roatating){
            mc.player.setYRot(preYaw);
            mc.player.setXRot(prePitch);
            roatating = false;
        }
        else{
            if(time>=next_rotate){
                preYaw = mc.player.getYRot();
                prePitch = mc.player.getXRot();
                mc.player.setYRot(yaw);
                mc.player.setXRot(pitch);
                rdtickleft=rotateDelay.get();
                //info("roatating");
                //Rotations.rotate(yaw, pitch, 0, true, null);
                if(rotateback.get()){
                    roatating = true;
                    rbtickleft=rotatebackticks.get();
                }
                next_rotate=time+1;
            }
            else{
                //info(Long.toString(next_rotate-time)+"ticks left to roatate");
            }
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundSetCarriedItemPacket) {
            switchTimer = switchDelay.get();
        }
    }

    private void stopAttacking() {
        if (!attacking) return;

        attacking = false;
        if (wasPathing) {
            PathManagers.get().resume();
            wasPathing = false;
        }
        if (swapBack.get() && swapped) {
            InvUtils.swap(previousSlot, false);
            swapped = false;
        }
    }

    private boolean shouldShieldBreak() {
        for (Entity target : targets) {
            if (target instanceof Player player) {
                if (player.isBlocking() && shieldMode.get() == ShieldMode.Break) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean entityCheck(Entity entity) {
        Player player = mc.player;
        if (entity.equals(player) || entity.equals(mc.getCameraEntity())) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDeadOrDying()) || !entity.isAlive())
            return false;
        double reach = (custumrange.get()) ? range.get() : player.entityInteractionRange();
        if (usingspear){
            reach = (custumrange.get()) ? range.get() : 4.5;
            Vec3 eye = player.getEyePosition();
            Vec3 closest = PlayerUtils.closestPointTo(entity,false);
            HitResult hit = mc.level.clip(new ClipContext(
                eye,
                closest,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
            ));
            if (hit.getType() == HitResult.Type.BLOCK) return false;
        }
        double dist = PlayerUtils.closestdistanceTo(entity);
        if (dist > reach) {
            return false; 
        }
        //info(entity.getName().getString()+Double.toString(dist)+"格");
        
        if (!entities.get().contains(entity.getType())) return false;
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (ignoreTamed.get()) {
            if (entity instanceof OwnableEntity tameable
                && tameable.getOwner() != null
                && tameable.getOwner().equals(mc.player)
            ) return false;
        }
        if (ignorePassive.get()) {
            if (entity instanceof EnderMan enderman && !enderman.isAngry()) return false;
            if ((entity instanceof Piglin || entity instanceof ZombifiedPiglin || entity instanceof Wolf) && !((Mob) entity).isAggressive())
                return false;
        }
        if (entity instanceof Player otherplayer) {
            if (otherplayer.isCreative()) return false;
            if (!Friends.get().shouldAttack(otherplayer)) return false;
            if (shieldMode.get() == ShieldMode.Ignore && otherplayer.isBlocking()) return false;
            if (otherplayer instanceof FakePlayerEntity fakePlayer && fakePlayer.noHit) return false;
        }
        if (entity instanceof LivingEntity livingEntity) {
            // Hostile mobs with baby variants (zombies, piglins, hoglins, zoglins)
            if (entity instanceof Zombie || entity instanceof Piglin
                || entity instanceof Hoglin || entity instanceof Zoglin) {
                return switch (hostileMobAgeFilter.get()) {
                    case Baby -> livingEntity.isBaby();
                    case Adult -> !livingEntity.isBaby();
                    case Both -> true;
                };
            }
            // Passive mobs with baby variants (animals, villagers)
            if (entity instanceof AgeableMob && (!(entity instanceof Frog || entity instanceof Parrot))) {
                return switch (passiveMobAgeFilter.get()) {
                    case Baby -> livingEntity.isBaby();
                    case Adult -> !livingEntity.isBaby();
                    case Both -> true;
                };
            }
        }
        if (usingspear && dist<=2){
            usingspear=false;
            shouldusespear=false;
            info("do not use spear");
            swapwep(entity);
            return false;
        }
        return true;
    }

    private boolean delayCheck(Entity target) {
        if (!target.isAttackable()) {
            return false;
        }
        if (switchTimer > 0) {
            switchTimer--;
            return false;
        }

        float delay = (customDelay.get()) ? hitDelay.get() : 0.5f;
        if (tpsSync.get()) delay /= (TickRate.INSTANCE.getTickRate() / 20);
        boolean playerReady;
        if (customDelay.get()) {
            if (hitTimer < delay) {
                hitTimer++;
                playerReady = false;
            } else playerReady = true;
        } else playerReady = mc.player.getAttackStrengthScale(delay) >= 1;
        if (target instanceof LivingEntity living) {
            boolean targetReady = living.hurtTime == 0;
            return playerReady && targetReady;
        }
        return playerReady;
    }

    private void attack(Entity target) {
        if(waithit){
            if(usingspear){
                //info("dist="+PlayerUtils.closestdistanceTo(target));
                ItemStack stack = mc.player.getMainHandItem();
                PiercingWeapon piercing = stack.get(DataComponents.PIERCING_WEAPON);
                if (piercing != null) {
                    mc.gameMode.piercingAttack(piercing);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
            }
            else{
                if(EntityInSight(target)){
                    //info("dist="+PlayerUtils.closestdistanceTo(target));
                    mc.gameMode.attack(mc.player, target);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
            }
            waithit=false;
            return;
        }
        if (anticheat.get() == AnticheatMode.MODE_onlyOnLook || anticheat.get() == AnticheatMode.MODE_3C3U){
            if (!EntityInSight(target)){
                Vec3 closestPoint=PlayerUtils.closestPointTo(target);
                Realrotate((float)Rotations.getYaw(closestPoint),(float)Rotations.getPitch(closestPoint));
            }
            if(rdtickleft<=0){
                if(usingspear){
                    //info("dist="+PlayerUtils.closestdistanceTo(target));
                    ItemStack stack = mc.player.getMainHandItem();
                    PiercingWeapon piercing = stack.get(DataComponents.PIERCING_WEAPON);
                    if (piercing != null) {
                        mc.gameMode.piercingAttack(piercing);
                        mc.player.swing(InteractionHand.MAIN_HAND);
                    }
                }
                else{
                    if(EntityInSight(target)){
                        //info("dist="+PlayerUtils.closestdistanceTo(target));
                        mc.gameMode.attack(mc.player, target);
                        mc.player.swing(InteractionHand.MAIN_HAND);
                    }
                }
                waithit=false;
            }
            else{
                tmptarget=target;
                waithit=true;
            }
        }
        else{
            if(!usingspear){
                mc.gameMode.attack(mc.player, target);
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
            else{
                ItemStack stack = mc.player.getMainHandItem();
                PiercingWeapon piercing = stack.get(DataComponents.PIERCING_WEAPON);
                if (piercing != null) {
                    mc.gameMode.piercingAttack(piercing);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
            }
            waithit=false;
        }
        
        
        hitTimer = 0;
        shouldusespear=false;
    }

    private boolean acceptableWeapon(ItemStack stack) {
        if (shouldusespear){
            //info("use spear");
            return (weapons.get().contains(Items.DIAMOND_SPEAR) && stack.is(ItemTags.SPEARS));
        }
        else{
            if(stack.is(ItemTags.SPEARS)){
                return false;
            }
        }        
        if (shouldShieldBreak()) return stack.getItem() instanceof AxeItem;
        if (attackWhenHolding.get() == AttackItems.All) return true;

        if (weapons.get().contains(Items.DIAMOND_SWORD) && stack.is(ItemTags.SWORDS)) return true;
        if (weapons.get().contains(Items.DIAMOND_AXE) && stack.is(ItemTags.AXES)) return true;
        if (weapons.get().contains(Items.DIAMOND_PICKAXE) && stack.is(ItemTags.PICKAXES)) return true;
        if (weapons.get().contains(Items.DIAMOND_SHOVEL) && stack.is(ItemTags.SHOVELS)) return true;
        if (weapons.get().contains(Items.DIAMOND_HOE) && stack.is(ItemTags.HOES)) return true;
        if (weapons.get().contains(Items.MACE) && stack.getItem() instanceof MaceItem) return true;
        if (weapons.get().contains(Items.DIAMOND_SPEAR) && stack.is(ItemTags.SPEARS)) return true;
        return weapons.get().contains(Items.TRIDENT) && stack.getItem() instanceof TridentItem;
    }

    public Entity getTarget() {
        if (!targets.isEmpty()) return targets.getFirst();
        return null;
    }

    @Override
    public String getInfoString() {
        if (!targets.isEmpty()) return EntityUtils.getName(getTarget());
        return null;
    }

    public enum AttackItems {
        Weapons,
        All
    }

    public enum RotationMode {
        Always,
        OnHit,
        None
    }

    public enum ShieldMode {
        Ignore,
        Break,
        None
    }
    public enum AnticheatMode {
        MODE_onlyOnLook("onlyOnLook"),
        MODE_3C3U("3C3U"),
        MODE_any("any");

        private final String title;

        AnticheatMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum EntityAge {
        Baby,
        Adult,
        Both
    }
}
