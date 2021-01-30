package me.i509.mod.spunbric.launch.plugin;

import org.spongepowered.plugin.metadata.PluginDependency;

import net.fabricmc.loader.api.metadata.ModMetadata;

/**
 * An interface which holds additional Sponge plugin specific metadata that is not present in {@link ModMetadata}.
 * This is parsed at mod load time but stored for building metadata
 */
public interface SpongePluginModExtensions {
	String getMainClass();

	PluginDependency.LoadOrder getDependencyLoadOrder(String modId);
}
