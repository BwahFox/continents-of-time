package dev.continentsoftime.client;

import dev.continentsoftime.network.AtlasInfoPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client entry point, entirely optional: a client without this mod joins an atlas server and sees vanilla
 * colouring everywhere; with it, each continent gets the visuals its era's generator would have on its own.
 */
public final class ContinentsOfTimeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(AtlasInfoPayload.TYPE, (payload, context) -> ClientAtlas.apply(context.client(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientAtlas.clear());
	}
}
