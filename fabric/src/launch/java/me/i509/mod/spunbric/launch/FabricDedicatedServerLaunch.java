package me.i509.mod.spunbric.launch;

import com.google.inject.Stage;
import me.i509.mod.spunbric.launch.plugin.FabricPluginEngine;

public final class FabricDedicatedServerLaunch extends FabricLaunch {
	protected FabricDedicatedServerLaunch(FabricPluginEngine pluginEngine, Stage injectionStage) {
		super(pluginEngine, injectionStage);
	}

	@Override
	public boolean isDedicatedServer() {
		return true;
	}
}
