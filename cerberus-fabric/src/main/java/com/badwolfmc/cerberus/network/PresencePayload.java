package com.badwolfmc.cerberus.network;

import com.badwolfmc.guardian.protocol.GuardianProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PresencePayload(byte[] bytes) implements CustomPacketPayload {
    public static final Type<PresencePayload> TYPE =
        new Type<>(Identifier.parse(GuardianProtocol.PRESENCE_CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, PresencePayload> CODEC =
        CustomPacketPayload.codec(PresencePayload::write, PresencePayload::new);

    private PresencePayload(FriendlyByteBuf buffer) {
        this(readRemaining(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        if (bytes.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Guardian presence payload too large");
        }
        buffer.writeBytes(bytes);
    }

    private static byte[] readRemaining(FriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length <= 0 || length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid Guardian presence payload length: " + length);
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return bytes;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
