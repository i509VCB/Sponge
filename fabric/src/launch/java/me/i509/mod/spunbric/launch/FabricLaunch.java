package me.i509.mod.spunbric.launch;

import com.google.inject.Stage;
import me.i509.mod.spunbric.launch.plugin.FabricPluginEngine;
import me.i509.mod.spunbric.launch.plugin.FabricPluginManager;
import org.spongepowered.common.applaunch.plugin.PluginEngine;
import org.spongepowered.common.launch.Launch;
import org.spongepowered.plugin.PluginContainer;

public abstract class FabricLaunch extends Launch {
	private final Stage injectionStage;

	protected FabricLaunch(final FabricPluginEngine pluginEngine, final Stage injectionStage) {
		super(pluginEngine, new FabricPluginManager());
		this.injectionStage = injectionStage;
	}

	@Override
	public final boolean isVanilla() {
		return false;
	}

	@Override
	public final Stage getInjectionStage() {
		return this.injectionStage;
	}

	@Override
	public final PluginContainer getPlatformPlugin() {
		return this.getPluginManager().getPlugin("spunbric").orElseThrow(RuntimeException::new);
	}

	@Override
	protected final void createPlatformPlugins(PluginEngine engine) {
		// No need, loader's early launch will do this for us
	}

	@Override
	public FabricPluginManager getPluginManager() {
		return (FabricPluginManager) this.pluginManager;
	}
}
