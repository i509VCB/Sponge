package me.i509.mod.spunbric.launch.plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import org.spongepowered.common.launch.plugin.DummyPluginContainer;
import org.spongepowered.common.launch.plugin.SpongePluginManager;
import org.spongepowered.plugin.PluginContainer;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public final class FabricPluginManager implements SpongePluginManager {
	private final FabricLoader loader = FabricLoader.getInstance();
	private final Map<ModContainer, PluginContainer> pluginsByModContainer = new IdentityHashMap<>();
	/**
	 * Only Sponge plugins have instances.
	 * Fabric mods have no "Main class"
	 */
	private final Map<Object, PluginContainer> pluginInstances = new IdentityHashMap<>();

	@Override
	public Optional<PluginContainer> fromInstance(Object instance) {
		return Optional.ofNullable(this.pluginInstances.get(instance));
	}

	@Override
	public Optional<PluginContainer> getPlugin(String id) {
		return this.loader.getModContainer(id).map(this.pluginsByModContainer::get);
	}

	@Override
	public Collection<PluginContainer> getPlugins() {
		return Collections.unmodifiableCollection(this.pluginsByModContainer.values());
	}

	@Override
	public boolean isLoaded(String id) {
		return this.loader.isModLoaded(id);
	}

	@Override
	public void addPlugin(PluginContainer plugin) {
	}

	@Override
	public void addDummyPlugin(DummyPluginContainer plugin) {

	}
}
