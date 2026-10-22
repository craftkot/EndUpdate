package com.craftkot.livingmatter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.ArrayList;
import java.util.List;

/** A complete connected organism moves one world cell; clients lerp all
 * its EXISTING cubes together with their unchanged vanilla textures. */
public record MatterWalkPacket(List<BlockPos> from, List<String> textures,
                               byte dx, byte dz, int duration) implements CustomPacketPayload {
    public static final Type<MatterWalkPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(LivingMatter.MOD_ID, "walk_pose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MatterWalkPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public MatterWalkPacket decode(RegistryFriendlyByteBuf buf) {
                int n = buf.readVarInt();
                if (n < 1 || n > 8) throw new IllegalArgumentException("Bad walking body: " + n);
                ArrayList<BlockPos> positions = new ArrayList<>();
                ArrayList<String> textures = new ArrayList<>();
                for (int i=0;i<n;i++) {
                    positions.add(buf.readBlockPos());
                    textures.add(buf.readUtf(128));
                }
                byte x = buf.readByte(), z = buf.readByte();
                int duration = buf.readVarInt();
                if (Math.abs(x)+Math.abs(z) != 1 || duration < 1 || duration > 40)
                    throw new IllegalArgumentException("Invalid walking motion");
                return new MatterWalkPacket(List.copyOf(positions),List.copyOf(textures),
                    x,z,duration);
            }
            @Override public void encode(RegistryFriendlyByteBuf buf, MatterWalkPacket data) {
                buf.writeVarInt(data.from().size());
                for (int i=0;i<data.from().size();i++) {
                    buf.writeBlockPos(data.from().get(i));
                    buf.writeUtf(data.textures().get(i));
                }
                buf.writeByte(data.dx());
                buf.writeByte(data.dz());
                buf.writeVarInt(data.duration());
            }
        };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC);
    }
}
