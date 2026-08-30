package dev.continentsoftime.mixin;

import dev.continentsoftime.network.AtlasInfoPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends {@link AtlasInfoPayload} with every level info the server sends a player (join, respawn, dimension
 * change) — the same moment Moderner Beta sends its own level payload, and after it (Moderner Beta injects at the
 * head of this method; this runs at its tail), so on the client the atlas's climate is installed after Moderner
 * Beta has reset its samplers for the level. Only to clients that registered the channel: vanilla clients get
 * nothing and need nothing.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Inject(method = "sendLevelInfo", at = @At("TAIL"))
	private void continentsoftime$sendAtlasInfo(ServerPlayer player, ServerLevel level, CallbackInfo ci) {
		if (ServerPlayNetworking.canSend(player, AtlasInfoPayload.TYPE)) {
			ServerPlayNetworking.send(player, AtlasInfoPayload.of(level));
		}
	}
}
