package dev.continentsoftime.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//?} else {
/*import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
*///?}

/**
 * The server side of the {@code continentsoftime:atlas_info} channel. Fabric's networking API changed shape in
 * 1.20.5 (typed payloads registered up front, before that raw channel buffers); this is the one place the server
 * sees the difference. The client side is in {@code client.ContinentsOfTimeClient}.
 */
public final class AtlasChannel {
	private AtlasChannel() {}

	/** Mod init: declare the payload type (nothing to declare for raw channels). */
	public static void register() {
		//? if >=1.20.5
		PayloadTypeRegistry.clientboundPlay().register(AtlasInfoPayload.TYPE, AtlasInfoPayload.CODEC);
	}

	/** Send the level's atlas description to the player if their client registered the channel (a vanilla client never did). */
	public static void sendIfListening(ServerPlayer player, ServerLevel level) {
		//? if >=1.20.5 {
		if (ServerPlayNetworking.canSend(player, AtlasInfoPayload.TYPE)) {
			ServerPlayNetworking.send(player, AtlasInfoPayload.of(level));
		}
		//?} else {
		/*if (ServerPlayNetworking.canSend(player, AtlasInfoPayload.ID)) {
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			AtlasInfoPayload.of(level).write(buf);
			ServerPlayNetworking.send(player, AtlasInfoPayload.ID, buf);
		}
		*///?}
	}
}
