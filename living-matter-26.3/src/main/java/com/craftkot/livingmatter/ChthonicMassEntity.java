package com.craftkot.livingmatter;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.ValueInput;
import net.minecraft.nbt.ValueOutput;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Real Minecraft hostile mob assembled from infected vanilla world blocks.
 * A larger source colony means a physically larger, stronger chthonic beast.
 */
public final class ChthonicMassEntity extends Monster {
    public static final int MIN_BLOCKS = 4, MAX_BLOCKS = 96;
    private static final EntityDataAccessor<Integer> MASS =
        SynchedEntityData.defineId(ChthonicMassEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MATERIAL =
        SynchedEntityData.defineId(ChthonicMassEntity.class, EntityDataSerializers.INT);

    public ChthonicMassEntity(EntityType<? extends ChthonicMassEntity> type, Level level) {
        super(type, level);
        this.xpReward = 7;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MASS, MIN_BLOCKS);
        builder.define(MATERIAL, MimicKind.STONE.ordinal());
    }

    public int mass() { return Math.max(MIN_BLOCKS, Math.min(MAX_BLOCKS, entityData.get(MASS))); }
    public int material() { return entityData.get(MATERIAL); }
    public int tier() {
        int n = mass();
        return n >= 64 ? 4 : n >= 32 ? 3 : n >= 12 ? 2 : 1;
    }

    public void assemble(int blocks, MimicKind kind) {
        entityData.set(MASS, Math.max(MIN_BLOCKS, Math.min(MAX_BLOCKS,blocks)));
        entityData.set(MATERIAL, kind.ordinal());
        this.refreshDimensions();
        if (getAttribute(Attributes.MAX_HEALTH) != null)
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(14 + mass() * .95);
        if (getAttribute(Attributes.ATTACK_DAMAGE) != null)
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.5 + Math.sqrt(mass()) * 1.1);
        if (getAttribute(Attributes.MOVEMENT_SPEED) != null)
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(
                Math.max(.16, .34 - Math.log1p(mass())*.035));
        if (getAttribute(Attributes.FOLLOW_RANGE) != null)
            getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(20 + tier()*7);
        this.setHealth(this.getMaxHealth());
        this.xpReward = 4 + mass()/4;
        this.setPersistenceRequired();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float width = switch (tier()) {
            case 4 -> 4.2f; case 3 -> 3.2f; case 2 -> 2.2f; default -> 1.2f;
        };
        float height = switch (tier()) {
            case 4 -> 4.8f; case 3 -> 3.8f; case 2 -> 2.8f; default -> 1.8f;
        };
        return EntityDimensions.scalable(width,height);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 110)
            .add(Attributes.MOVEMENT_SPEED, .22)
            .add(Attributes.ATTACK_DAMAGE, 12)
            .add(Attributes.FOLLOW_RANGE, 48)
            .add(Attributes.KNOCKBACK_RESISTANCE, .55);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0,new FloatGoal(this));
        this.goalSelector.addGoal(1,new MeleeAttackGoal(this,1.0D,true));
        this.goalSelector.addGoal(6,new WaterAvoidingRandomStrollGoal(this,.7D));
        this.goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,10.0F));
        this.goalSelector.addGoal(8,new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1,new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server && tickCount % 18 == 0) {
            double r = tier() * .45;
            server.sendParticles(ParticleTypes.SOUL, getX(),getY()+tier(),getZ(),
                Math.max(1,tier()),r,.2,r,.006);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("LivingMatterBlockMass",mass());
        output.putInt("LivingMatterMaterial",material());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int blocks=input.getInt("LivingMatterBlockMass").orElse(MIN_BLOCKS);
        int kind=input.getInt("LivingMatterMaterial").orElse(0);
        MimicKind[] kinds=MimicKind.values();
        assemble(blocks,kinds[Math.floorMod(kind,kinds.length)]);
    }
}
