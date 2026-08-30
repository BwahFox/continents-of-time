package dev.continentsoftime.mixin.client;

import dev.continentsoftime.client.screen.AtlasPresetEditor;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the Continents of Time world type a Customize button: vanilla keeps its preset editors in an immutable
 * map, so the lookup is answered here for our preset (Moderner Beta does the same for its own; each only speaks
 * for its own preset, so the two coexist).
 */
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
	@Shadow
	public abstract WorldCreationUiState.WorldTypeEntry getWorldType();

	@Inject(method = "getPresetEditor", at = @At("RETURN"), cancellable = true)
	private void continentsoftime$atlasEditor(CallbackInfoReturnable<PresetEditor> cir) {
		if (AtlasPresetEditor.matches(getWorldType().preset())) {
			cir.setReturnValue(AtlasPresetEditor.editor());
		}
	}
}
