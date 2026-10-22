package com.craftkot.livingmatter;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-authorized admin summary; never reveals infection to nonoperators. */
public record AdminPanelPacket(int totalInfected, int nearbyInfected,
                               int totalBrains, int nearbyBrains,
                               int nearbyMonsters, boolean highlighting,
                               List<BlockPos> closest) implements CustomPacketPayload {
    public static final Type<AdminPanelPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(LivingMatter.MOD_ID, "admin_panel"));
    public static final StreamCodec<RegistryFriendlyByteBuf,AdminPanelPacket> CODEC = new StreamCodec<>() {
        @Override public AdminPanelPacket decode(RegistryFriendlyByteBuf b) {
            int all=b.readVarInt(), near=b.readVarInt(), brains=b.readVarInt(),
                local=b.readVarInt(), monsters=b.readVarInt();
            boolean highlight=b.readBoolean();
            int n=b.readVarInt();
            if (n<0||n>24) throw new IllegalArgumentException("Invalid admin panel positions: "+n);
            List<BlockPos> positions=new ArrayList<>(n);
            for(int i=0;i<n;i++) positions.add(b.readBlockPos());
            return new AdminPanelPacket(all,near,brains,local,monsters,
                highlight,List.copyOf(positions));
        }
        @Override public void encode(RegistryFriendlyByteBuf b,AdminPanelPacket d) {
            b.writeVarInt(d.totalInfected());
            b.writeVarInt(d.nearbyInfected());
            b.writeVarInt(d.totalBrains());
            b.writeVarInt(d.nearbyBrains());
            b.writeVarInt(d.nearbyMonsters());
            b.writeBoolean(d.highlighting());
            b.writeVarInt(d.closest().size());
            for(BlockPos pos:d.closest()) b.writeBlockPos(pos);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE,CODEC);
    }
}
