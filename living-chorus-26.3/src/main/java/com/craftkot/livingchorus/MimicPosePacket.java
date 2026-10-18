package com.craftkot.livingchorus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * START/UPDATE/STOP packet for a single flower. No world seed or hidden
 * dormant-mimic list is exposed to the client.
 */
public record MimicPosePacket(BlockPos flower, Vec3 target, int packedProfile,
                              int chargeTicks, int recoverTicks, boolean dead,
                              byte kind, byte operation) implements CustomPacketPayload {
    public static final byte START = 0, UPDATE = 1, STOP = 2;
    public static final byte PLAYER_BITE = 0, ENDERMITE_FEED = 1;
    public static final Type<MimicPosePacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(LivingChorus.MOD_ID, "mimic_pose"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MimicPosePacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public MimicPosePacket decode(RegistryFriendlyByteBuf buf) {
                BlockPos flower = buf.readBlockPos();
                Vec3 target = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                int profile = buf.readVarInt();
                int charge = buf.readVarInt();
                int recover = buf.readVarInt();
                boolean dead = buf.readBoolean();
                byte kind = buf.readByte();
                byte op = buf.readByte();
                return new MimicPosePacket(flower, target, profile, charge, recover, dead, kind, op);
            }
            @Override
            public void encode(RegistryFriendlyByteBuf buf, MimicPosePacket data) {
                buf.writeBlockPos(data.flower());
                buf.writeDouble(data.target().x);
                buf.writeDouble(data.target().y);
                buf.writeDouble(data.target().z);
                buf.writeVarInt(data.packedProfile());
                buf.writeVarInt(data.chargeTicks());
                buf.writeVarInt(data.recoverTicks());
                buf.writeBoolean(data.dead());
                buf.writeByte(data.kind());
                buf.writeByte(data.operation());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC);
    }
}
