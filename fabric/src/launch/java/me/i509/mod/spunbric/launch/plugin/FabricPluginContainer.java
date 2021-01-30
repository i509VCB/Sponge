package me.i509.mod.spunbric.launch.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.metadata.PluginMetadata;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;


/**
 * Fabric metadata semantics:
 *
 * <p>Custom values are converted to the map form for access in {@link PluginMetadata#getExtraMetadata()}.
 * Depending on the custom value's {@link CustomValue.CvType type}, the value stored in the map may be one of the following:
 * <ul>
 * <li>{@link CustomValue.CvType#OBJECT CvType.OBJECT} -> {@link Map}</li>
 * <li>{@link CustomValue.CvType#ARRAY CvType.ARRAY} -> {@link List}</li>
 * <li>{@link CustomValue.CvType#STRING CvType.STRING} -> {@link String}</li>
 * <li>{@link CustomValue.CvType#NUMBER CvType.NUMBER} -> {@link Number}</li>
 * <li>{@link CustomValue.CvType#BOOLEAN CvType.BOOLEAN} -> {@code boolean}</li>
 * <li>{@link CustomValue.CvType#NULL CvType.NULL} -> {@code null}</li>
 * </ul>
 *
 * Array and object custom values also have their child custom values converted to object form using the above diagram.
 *
 * <p>Things that do not map:
 * <ul>
 * <li>
 * License from Fabric's metadata.
 * Sponge has no recognition of Licenses in their metadata, not really an issue<
 * /li>
 * <li>
 * "main-class" from Sponge's metadata. Fabric recognizes no "main class".
 * An entrypoint isn't a main class either.
 * This is also required metadata, so there is an incompatibility there which needs to be resolved.
 * </li>
 * </ul>
 */
public final class FabricPluginContainer implements PluginContainer {
	private final ModContainer modContainer;
	private final PluginMetadata metadata;
	private final Logger logger;
	private Object instance;

	protected FabricPluginContainer(ModContainer modContainer, @Nullable SpongePluginModExtensions extensions) {
		this.modContainer = modContainer;
		this.logger = LogManager.getLogger(this.modContainer.getMetadata().getId());
		this.metadata = ModPluginMetadataCreator.create(modContainer.getMetadata(), extensions);
	}

	@Override
	public PluginMetadata getMetadata() {
		return this.metadata;
	}

	@Override
	public Path getPath() {
		return this.modContainer.getRootPath();
	}

	@Override
	public Logger getLogger() {
		return this.logger;
	}

	@Override
	public Object getInstance() {
		return this.instance;
	}

	public void setInstance(Object instance) {
		this.instance = instance;
	}

	@Override
	public Optional<URL> locateResource(URL relative) {
		try {
			return Optional.of(this.modContainer.getRootPath().resolve(relative.toString()).toUri().toURL());
		} catch (MalformedURLException e) {
			return Optional.empty();
		}
	}

	@Override
	public Optional<InputStream> openResource(URL relative) {
		final Path resolved = this.modContainer.getRootPath().resolve(relative.toString());

		if (Files.exists(resolved)) {
			try {
				return Optional.of(Files.newInputStream(resolved));
			} catch (IOException ignored) {
			}
		}

		return Optional.empty();
	}
}
