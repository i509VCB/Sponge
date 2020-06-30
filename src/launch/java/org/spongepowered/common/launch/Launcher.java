/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.launch;

import com.google.inject.Guice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.common.launch.plugin.DummyPluginContainer;
import org.spongepowered.common.launch.plugin.PluginLoader;
import org.spongepowered.common.launch.plugin.SpongePluginManager;
import org.spongepowered.plugin.Blackboard;
import org.spongepowered.plugin.PluginEnvironment;
import org.spongepowered.plugin.PluginKeys;
import org.spongepowered.plugin.metadata.PluginMetadata;
import org.spongepowered.plugin.metadata.util.PluginMetadataHelper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public abstract class Launcher {

    public static Launcher INSTANCE;

    private final Logger logger;
    private final PluginEnvironment pluginEnvironment;
    private final SpongePluginManager pluginManager;

    protected Launcher(SpongePluginManager pluginManager) {
        INSTANCE = this;
        this.logger = LogManager.getLogger("Sponge");
        this.pluginEnvironment = new PluginEnvironment();
        this.pluginManager = pluginManager;
    }

    public Logger getLogger() {
        return this.logger;
    }

    public PluginEnvironment getPluginEnvironment() {
        return this.pluginEnvironment;
    }

    public SpongePluginManager getPluginManager() {
        return this.pluginManager;
    }

    public static void launch(final String pluginSpiVersion, final Path baseDirectory, final List<Path> pluginDirectories, final String[] args) {
        Launcher.INSTANCE.launch0(pluginSpiVersion, baseDirectory, pluginDirectories, args);
    }

    protected void launch0(final String pluginSpiVersion, final Path baseDirectory, final List<Path> pluginDirectories, final String[] args) {
        this.populateBlackboard(pluginSpiVersion, baseDirectory, pluginDirectories);
        this.createInternalPlugins();
        this.loadPlugins();
    }

    protected void populateBlackboard(final String pluginSpiVersion, final Path baseDirectory, final List<Path> pluginDirectories) {
        final Blackboard blackboard = this.getPluginEnvironment().getBlackboard();
        blackboard.getOrCreate(PluginKeys.VERSION, () -> pluginSpiVersion);
        blackboard.getOrCreate(PluginKeys.BASE_DIRECTORY, () -> baseDirectory);
        blackboard.getOrCreate(PluginKeys.PLUGIN_DIRECTORIES, () -> pluginDirectories);
        blackboard.getOrCreate(PluginKeys.PARENT_INJECTOR, () -> Guice.createInjector(new LauncherModule()));
    }

    protected void loadPlugins() {
        final PluginLoader pluginLoader = new PluginLoader(this.pluginEnvironment, this.pluginManager);
        pluginLoader.discoverServices();
        pluginLoader.initialize();
        pluginLoader.discoverResources();
        pluginLoader.determineCandidates();
        pluginLoader.createContainers();
    }

    private void createInternalPlugins() {
        final Path gameDirectory = this.pluginEnvironment.getBlackboard().get(PluginKeys.BASE_DIRECTORY).get();
        try {
            final Collection<PluginMetadata> read =
                PluginMetadataHelper.builder().build().read(Launcher.class.getResourceAsStream("META-INF/plugins.json"));
            for (final PluginMetadata metadata : read) {
                this.pluginManager.addPlugin(new DummyPluginContainer(metadata, gameDirectory, this.logger, this));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not load metadata information for the common implementation! This should be impossible!");
        }

        this.createPlatformPlugins(gameDirectory);
    }

    protected abstract void createPlatformPlugins(final Path gameDirectory);
}