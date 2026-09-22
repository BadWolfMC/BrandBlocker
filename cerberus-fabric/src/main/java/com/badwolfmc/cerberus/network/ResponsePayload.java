package com.badwolfmc.cerberus.network;

import com.badwolfmc.guardian.protocol.GuardianProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ResponsePayload(byte[] bytes) implements CustomPacketPayload {
    public static final Type<ResponsePayload> TYPE =
        new Type<>(Identifier.parse(GuardianProtocol.RESPONSE_CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, ResponsePayload> CODEC =
        CustomPacketPayload.codec(ResponsePayload::write, ResponsePayload::new);

    private ResponsePayload(FriendlyByteBuf buffer) {
        this(readRemaining(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        if (bytes.length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Guardian response payload too large");
        }
        buffer.writeBytes(bytes);
    }

    private static byte[] readRemaining(FriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length <= 0 || length > GuardianProtocol.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid Guardian response payload length: " + length);
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
