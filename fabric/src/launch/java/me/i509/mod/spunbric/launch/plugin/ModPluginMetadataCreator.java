package me.i509.mod.spunbric.launch.plugin;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.plugin.metadata.PluginContributor;
import org.spongepowered.plugin.metadata.PluginDependency;
import org.spongepowered.plugin.metadata.PluginLinks;
import org.spongepowered.plugin.metadata.PluginMetadata;

import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

final class ModPluginMetadataCreator {
	static PluginMetadata create(ModMetadata metadata, @Nullable SpongePluginModExtensions extensions) {
		final PluginMetadata.Builder builder = PluginMetadata.builder();

		// Required metadata
		builder.setLoader("fabricloader");
		builder.setId(metadata.getId());
		builder.setVersion(metadata.getVersion().getFriendlyString());

		if (extensions != null) {
			builder.setMainClass(extensions.getMainClass());
		} else {
			// FIXME: Fabric Loader recognizes no "main" class
			//builder.setMainClass();
			throw new AssertionError("TODO: Fabric mods do not have a main class, plugin-spi needs to change for that");
		}

		// Dependency logic
		builder.setDependencies(Collections.unmodifiableList(createPluginDependencies(metadata, extensions)));

		// Viewable metadata
		if (!metadata.getName().isEmpty()) {
			builder.setName(metadata.getName());
		}

		if (!metadata.getDescription().isEmpty()) {
			builder.setDescription(metadata.getDescription());
		}

		// Authorship/Contributors -> Contributors
		builder.setContributors(Collections.unmodifiableList(createPluginContributors(metadata)));

		// ContactInformation -> Links
		builder.setLinks(createLinks(metadata.getContact()));

		// CustomValues -> Extra metadata
		builder.setExtraMetadata(Collections.unmodifiableMap(createExtraMetadata(metadata)));

		// TODO: Sponge does not recognize license as a field?
		return builder.build();
	}

	private static List<PluginDependency> createPluginDependencies(ModMetadata metadata, @Nullable SpongePluginModExtensions extensions) {
		final List<PluginDependency> pluginDependencies = new ArrayList<>();

		// "Depends" refers to required dependencies
		for (ModDependency dependency : metadata.getDepends()) {
			final PluginDependency.Builder builder = PluginDependency.builder();
			builder.setId(dependency.getModId());
			builder.setOptional(false);

			if (true) {
				// FIXME: More detailed error message PR has stuff for version from mod dep
				//  https://github.com/FabricMC/fabric-loader/pull/277
				//  Linked PR exposes the "version predicate" which could be used to obtain the version requirements for a dependency
				//  plugin-spi and meta need to explain the semantics behind the plugin dependency version in order to have a clean conversion
				// builder.setVersion()
				throw new AssertionError("Fabric Loader needs to add a method to get the version of a mod dependency which was specified");
			}

			// Fabric Mods have no real set load order.
			// Sponge plugins carry this extra metadata when loaded as fabric mods via the extensions object
			if (extensions != null) {
				builder.setLoadOrder(extensions.getDependencyLoadOrder(dependency.getModId()));
			}

			pluginDependencies.add(builder.build());
		}

		// TODO: Suggests is not a soft-depend for say, so should it be even included as an optional dependency?
		return pluginDependencies;
	}

	private static List<PluginContributor> createPluginContributors(ModMetadata metadata) {
		// A key distinction between Fabric and Sponge's metadata is the fact Sponge only recognizes contributors while Fabric recognizes authors and contributors
		// For sponge, all authors are classified as contributors with the description "Author"
		final List<PluginContributor> contributors = new ArrayList<>();

		// Authors
		for (Person author : metadata.getAuthors()) {
			final PluginContributor.Builder builder = PluginContributor.builder();
			builder.setName(author.getName());
			builder.setDescription("Author");

			contributors.add(builder.build());
		}

		// Contributors
		for (Person contributor : metadata.getContributors()) {
			final PluginContributor.Builder builder = PluginContributor.builder();
			builder.setName(contributor.getName());

			contributors.add(builder.build());
		}

		return contributors;
	}

	private static PluginLinks createLinks(ContactInformation contact) {
		// "homepage", "sources" and "issues" are all officially recognized keys in Fabric's ContactInformation
		final PluginLinks.Builder builder = PluginLinks.builder();

		contact.get("homepage")
				.map(ModPluginMetadataCreator::toUrl)
				.ifPresent(builder::setHomepage);

		// Fabric's key is "sources"
		contact.get("sources")
				.map(ModPluginMetadataCreator::toUrl)
				.ifPresent(builder::setSource);

		contact.get("issues")
				.map(ModPluginMetadataCreator::toUrl)
				.ifPresent(builder::setIssues);

		return builder.build();
	}

	@Nullable
	private static URL toUrl(String spec) {
		try {
			return new URL(spec);
		} catch (MalformedURLException ignored) {
			return null;
		}
	}

	private static Map<String, Object> createExtraMetadata(ModMetadata metadata) {
		// We need to convert Fabric's CustomValues into a Map form for sponge to use.
		final Map<String, Object> extra = new LinkedHashMap<>();

		for (Map.Entry<String, CustomValue> entry : metadata.getCustomValues().entrySet()) {
			extra.put(entry.getKey(), toObjectForm(entry.getValue()));
		}

		return extra;
	}

	@Nullable
	private static Object toObjectForm(CustomValue value) {
		switch (value.getType()) {
			case OBJECT:
				final Map<String, Object> mapValues = new LinkedHashMap<>();

				for (Map.Entry<String, CustomValue> entry : value.getAsObject()) {
					mapValues.put(entry.getKey(), toObjectForm(value));
				}

				return mapValues;
			case ARRAY:
				// Lets assume a list is fine
				final List<Object> arrayValues = new ArrayList<>();

				for (CustomValue entry : value.getAsArray()) {
					arrayValues.add(toObjectForm(entry));
				}

				return arrayValues;
			case STRING:
				return value.getAsString();
			case NUMBER:
				return value.getAsNumber();
			case BOOLEAN:
				return value.getAsBoolean();
			case NULL:
				// Represent as a null literal
				return null;
		}

		throw new RuntimeException("Invalid Custom value? This seems like a problem with Fabric Loader");
	}
}

