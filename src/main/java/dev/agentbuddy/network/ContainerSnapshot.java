package dev.agentbuddy.network;

import dev.agentbuddy.AgentBuddy;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Server -> client: a read-only copy of a container's slots for the snapshot screen. */
public record ContainerSnapshot(String containerType, BlockPos pos, List<ItemStack> stacks) implements CustomPacketPayload {
    public static final Type<ContainerSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AgentBuddy.MODID, "container_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerSnapshot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), ContainerSnapshot::containerType,
            BlockPos.STREAM_CODEC, ContainerSnapshot::pos,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, ContainerSnapshot::stacks,
            ContainerSnapshot::new);

    @Override
    public Type<ContainerSnapshot> type() {
        return TYPE;
    }
}
