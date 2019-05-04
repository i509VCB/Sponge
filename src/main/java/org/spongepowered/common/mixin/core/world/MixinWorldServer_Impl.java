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
package org.spongepowered.common.mixin.core.world;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import co.aikar.timings.TimingHistory;
import co.aikar.timings.WorldTimingsHandler;
import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEventData;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntitySkeletonHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.profiler.Profiler;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardSaveData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumLightType;
import net.minecraft.world.Explosion;
import net.minecraft.world.GameType;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.gen.ChunkGeneratorEnd;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.SessionLockException;
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.world.storage.WorldSavedDataStorage;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.ScheduledBlockUpdate;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.manipulator.DataManipulator;
import org.spongepowered.api.data.persistence.DataFormats;
import org.spongepowered.api.effect.sound.record.RecordType;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.Transform;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.CauseStackManager.StackFrame;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.action.LightningEvent;
import org.spongepowered.api.event.block.NotifyNeighborBlockEvent;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.api.event.entity.ConstructEntityEvent;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.world.ChangeWorldWeatherEvent;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.scheduler.ScheduledUpdate;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.util.PositionOutOfBoundsException;
import org.spongepowered.api.world.BlockChangeFlag;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.GeneratorType;
import org.spongepowered.api.world.GeneratorTypes;
import org.spongepowered.api.world.LocatableBlock;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.PortalAgent;
import org.spongepowered.api.world.gen.BiomeGenerator;
import org.spongepowered.api.world.gen.GeneratorType;
import org.spongepowered.api.world.gen.GeneratorTypes;
import org.spongepowered.api.world.gen.WorldGenerator;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.api.world.storage.WorldStorage;
import org.spongepowered.api.world.teleport.PortalAgentType;
import org.spongepowered.api.world.teleport.PortalAgentTypes;
import org.spongepowered.api.world.weather.Weather;
import org.spongepowered.api.world.weather.Weathers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.block.SpongeBlockSnapshot;
import org.spongepowered.common.config.SpongeConfig;
import org.spongepowered.common.config.category.WorldCategory;
import org.spongepowered.common.config.type.GeneralConfigBase;
import org.spongepowered.common.config.type.WorldConfig;
import org.spongepowered.common.data.util.DataQueries;
import org.spongepowered.common.entity.EntityUtil;
import org.spongepowered.common.event.ShouldFire;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.event.tracking.IPhaseState;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseData;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.TrackingUtil;
import org.spongepowered.common.event.tracking.phase.general.GeneralPhase;
import org.spongepowered.common.event.tracking.phase.generation.GenerationPhase;
import org.spongepowered.common.event.tracking.phase.plugin.BasicPluginContext;
import org.spongepowered.common.event.tracking.phase.plugin.PluginPhase;
import org.spongepowered.common.event.tracking.phase.tick.TickPhase;
import org.spongepowered.common.interfaces.IMixinChunk;
import org.spongepowered.common.interfaces.IMixinNextTickListEntry;
import org.spongepowered.common.interfaces.block.IMixinBlock;
import org.spongepowered.common.interfaces.block.IMixinBlockEventData;
import org.spongepowered.common.interfaces.block.tile.IMixinTileEntity;
import org.spongepowered.common.interfaces.data.IMixinCustomDataHolder;
import org.spongepowered.common.interfaces.entity.IMixinEntity;
import org.spongepowered.common.interfaces.server.management.IMixinPlayerChunkMap;
import org.spongepowered.common.interfaces.util.math.IMixinBlockPos;
import org.spongepowered.common.interfaces.world.IMixinDimension;
import org.spongepowered.common.interfaces.world.IMixinWorldInfo;
import org.spongepowered.common.interfaces.world.IMixinWorldServer;
import org.spongepowered.common.interfaces.world.gen.IMixinChunkProviderServer;
import org.spongepowered.common.interfaces.world.gen.IPopulatorProvider;
import org.spongepowered.common.mixin.plugin.entityactivation.interfaces.IModData_Activation;
import org.spongepowered.common.mixin.plugin.entitycollisions.interfaces.IModData_Collisions;
import org.spongepowered.common.registry.provider.DirectionFacingProvider;
import org.spongepowered.common.registry.type.world.BlockChangeFlagRegistryModule;
import org.spongepowered.common.util.NonNullArrayList;
import org.spongepowered.common.util.SpongeHooks;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.world.WorldUtil;
import org.spongepowered.common.world.border.PlayerBorderListener;
import org.spongepowered.common.world.gen.SpongeTerrainGenerator;
import org.spongepowered.common.world.type.SpongeWorldType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nullable;

@Mixin(WorldServer.class)
@Implements(@Interface(iface = IMixinWorldServer.class, prefix = "worldServer$", unique = true))
public abstract class MixinWorldServer_Impl extends MixinWorld_Impl implements IMixinWorldServer {

    private static final String PROFILER_SS = "Lnet/minecraft/profiler/Profiler;startSection(Ljava/lang/String;)V";
    private static final String PROFILER_ESS = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V";

    private static final EnumSet<EnumFacing> NOTIFY_DIRECTIONS = EnumSet.of(EnumFacing.WEST, EnumFacing.EAST, EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH);

    private final Map<net.minecraft.entity.Entity, Vector3d> rotationUpdates = new HashMap<>();
    private SpongeTerrainGenerator spongegen;
    private SpongeConfig<? extends GeneralConfigBase> activeConfig;
    private long weatherStartTime;
    private Weather prevWeather;
    protected WorldTimingsHandler timings;
    private int chunkGCTickCount = 0;
    private int chunkGCLoadThreshold = 0;
    private int chunkGCTickInterval = 600;
    private long chunkUnloadDelay = 30000;
    private boolean weatherThunderEnabled = true;
    private boolean weatherIceAndSnowEnabled = true;
    private int dimensionId;
    private IMixinChunkProviderServer mixinChunkProviderServer;
    @Nullable private NextTickListEntry tmpScheduledObj;

    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private Set<NextTickListEntry> pendingTickListEntriesHashSet;
    @Shadow @Final private TreeSet<NextTickListEntry> pendingTickListEntriesTreeSet;
    @Shadow @Final private PlayerChunkMap playerChunkMap;
    @Shadow @Final @Mutable private Teleporter worldTeleporter;
    @Shadow @Final private WorldServer.ServerBlockEventList[] blockEventQueue;
    @Shadow private int blockEventCacheIndex;
    @Shadow private int updateEntityTick;

    @Shadow protected abstract void saveLevel();
    @Shadow public abstract boolean fireBlockEvent(BlockEventData event);
    @Shadow public abstract void createBonusChest();
    @Shadow @Nullable public abstract net.minecraft.entity.Entity getEntityFromUuid(UUID uuid);
    @Shadow public abstract PlayerChunkMap getPlayerChunkMap();
    @Shadow public abstract ChunkProviderServer getChunkProvider();
    @Shadow public abstract void playerCheckLight();
    @Shadow public abstract BlockPos adjustPosToNearbyEntity(BlockPos pos);
    @Shadow public boolean canAddEntity(net.minecraft.entity.Entity entityIn) {
        return false; // Shadowed
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/Dimension;setWorld(Lnet/minecraft/world/World;)V"))
    private void onSetWorld(Dimension dimension, World worldIn) {
        // Guarantees no mod has changed our worldInfo.
        // Mods such as FuturePack replace worldInfo with a custom one for separate world time.
        // This change is not needed as all worlds in Sponge use separate save handlers.
        WorldInfo originalWorldInfo = worldIn.getWorldInfo();
        dimension.setWorld(worldIn);
        this.worldInfo = originalWorldInfo;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstruct(MinecraftServer server, ISaveHandler saveHandlerIn, WorldInfo info, int dimensionId, Profiler profilerIn,
        CallbackInfo callbackInfo) {
        if (info == null) {
            SpongeImpl.getLogger().warn("World constructed without a WorldInfo! This will likely cause problems. Subsituting dummy info.",
                    new RuntimeException("Stack trace:"));
            this.worldInfo = new WorldInfo(new WorldSettings(0, GameType.NOT_SET, false, false, WorldType.DEFAULT),
                    "sponge$dummy_world");
        }
        // Checks to make sure no mod has changed our worldInfo and if so, reverts back to original.
        // Mods such as FuturePack replace worldInfo with a custom one for separate world time.
        // This change is not needed as all worlds use separate save handlers.
        this.worldInfo = info;
        this.timings = new WorldTimingsHandler((WorldServer) (Object) this);
        this.dimensionId = dimensionId;
        this.prevWeather = this.getWeather();
        this.weatherStartTime = this.worldInfo.getGameTime();
        ((World) (Object) this).getWorldBorder().addListener(new PlayerBorderListener(this.getMinecraftServer(), dimensionId));
        PortalAgentType portalAgentType = ((WorldProperties) this.worldInfo).getPortalAgentType();
        if (!portalAgentType.equals(PortalAgentTypes.DEFAULT)) {
            try {
                this.worldTeleporter = (Teleporter) portalAgentType.getPortalAgentClass().getConstructor(new Class<?>[] {WorldServer.class})
                        .newInstance(new Object[] {this});
            } catch (Exception e) {
                SpongeImpl.getLogger().log(Level.ERROR, "Could not create PortalAgent of type " + portalAgentType.getKey()
                        + " for world " + this.getName() + ": " + e.getMessage() + ". Falling back to default...");
            }
        }

        this.updateWorldGenerator();
        // Need to set the active config before we call it.
        this.chunkGCLoadThreshold = SpongeHooks.getActiveConfig((WorldServer) (Object) this).getConfig().getWorld().getChunkGCLoadThreshold();
        this.chunkGCTickInterval = this.getActiveConfig().getConfig().getWorld().getChunkGCTickInterval();
        this.weatherIceAndSnowEnabled = this.getActiveConfig().getConfig().getWorld().canWeatherIceAndSnow();
        this.weatherThunderEnabled = this.getActiveConfig().getConfig().getWorld().canWeatherAndThunder();
        this.updateEntityTick = 0;
        this.mixinChunkProviderServer = ((IMixinChunkProviderServer) this.getChunkProvider());
        this.setMemoryViewDistance(this.chooseViewDistanceValue(this.getActiveConfig().getConfig().getWorld().getViewDistance()));
    }

    @Redirect(method = "init", at = @At(value = "NEW", target = "net/minecraft/world/storage/WorldSavedDataStorage"))
    private WorldSavedDataStorage onCreateWorldSavedDataStorage(ISaveHandler saveHandler) {
        WorldServer overWorld = WorldManager.getWorldByDimensionId(0).orElse(null);
        // if overworld has loaded, use its mapstorage
        if (this.dimensionId != 0 && overWorld != null) {
            return overWorld.getMapStorage();
        }

        // if we are loading overworld, create a new mapstorage
        return new WorldSavedDataStorage(saveHandler);
    }

    // The following two redirects work around the fact that 'onCreateMapStorage' causes all worlds to share a single MapStorage.
    // Worlds other than the Overworld have scoreboard created, but they are never used. Therefore, we need to ensure that these unused scoreboards
    // are not saved into the global MapStorage when non-Overworld worlds are initialized.

    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet.minecraft.world.storage.WorldSavedDataStorage;setData(Ljava/lang/String;Lnet/minecraft/world/storage/WorldSavedData;)V"))
    private void onMapStorageSetData(WorldSavedDataStorage storage, String name, WorldSavedData data) {
        if (name.equals("scoreboard") && this.dimensionId != 0) {
            return;
        }
        storage.setData(name, data);
    }

    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/scoreboard/ScoreboardSaveData;setScoreboard(Lnet/minecraft/scoreboard/Scoreboard;)V"))
    private void onSetSaveDataScoreboard(ScoreboardSaveData scoreboardSaveData, Scoreboard scoreboard) {
        if (this.dimensionId != 0) {
            return;
        }
        scoreboardSaveData.setScoreboard(scoreboard);
    }

    @Inject(method = "createSpawnPosition", at = @At(value = "HEAD"))
    private void onCreateBonusChest(CallbackInfo ci) {
        GenerationPhase.State.TERRAIN_GENERATION.createPhaseContext()
                .source(this)
                .buildAndSwitch();
    }


    @Inject(method = "createSpawnPosition", at = @At(value = "RETURN"))
    private void onCreateBonusChestEnd(CallbackInfo ci) {
        PhaseTracker.getInstance().getCurrentContext().close();
    }

    @Inject(method = "createSpawnPosition(Lnet/minecraft/world/WorldSettings;)V", at = @At("HEAD"), cancellable = true)
    private void onCreateSpawnPosition(WorldSettings settings, CallbackInfo ci) {
        GeneratorType generatorType = (GeneratorType) settings.getTerrainType();

        if ((generatorType != null && generatorType.equals(GeneratorTypes.THE_END)) || ((((WorldServer) (Object) this)).getChunkProvider().chunkGenerator instanceof ChunkGeneratorEnd)) {
            this.worldInfo.setSpawn(new BlockPos(100, 50, 0));
            ci.cancel();
        }
    }

    @Override
    public boolean isProcessingExplosion() {
        return this.processingExplosion;
    }

    @Override
    public boolean isMinecraftChunkLoaded(int x, int z, boolean allowEmpty) {
        return this.isChunkLoaded(x, z, allowEmpty);
    }

    @Override
    public SpongeConfig<WorldConfig> getConfig() {
        return ((IMixinWorldInfo) this.worldInfo).getOrCreateConfig();
    }

    @Override
    public SpongeConfig<? extends GeneralConfigBase> getActiveConfig() {
        return this.activeConfig;
    }

    @Override
    public void setActiveConfig(SpongeConfig<? extends GeneralConfigBase> config) {
        this.activeConfig = config;
        // update cached settings
        this.chunkGCLoadThreshold = this.activeConfig.getConfig().getWorld().getChunkGCLoadThreshold();
        this.chunkGCTickInterval = this.activeConfig.getConfig().getWorld().getChunkGCTickInterval();
        this.weatherIceAndSnowEnabled = this.activeConfig.getConfig().getWorld().canWeatherIceAndSnow();
        this.weatherThunderEnabled = this.activeConfig.getConfig().getWorld().canWeatherAndThunder();
        this.chunkUnloadDelay = this.activeConfig.getConfig().getWorld().getChunkUnloadDelay() * 1000;
        if (this.getChunkProvider() != null) {
            final int maxChunkUnloads = this.activeConfig.getConfig().getWorld().getMaxChunkUnloads();
            this.mixinChunkProviderServer.setMaxChunkUnloads(maxChunkUnloads < 1 ? 1 : maxChunkUnloads);
            this.mixinChunkProviderServer.setDenyChunkRequests(this.activeConfig.getConfig().getWorld().getDenyChunkRequests());
            for (net.minecraft.entity.Entity entity : this.loadedEntityList) {
                if (entity instanceof IModData_Activation) {
                    ((IModData_Activation) entity).requiresActivationCacheRefresh(true);
                }
                if (entity instanceof IModData_Collisions) {
                    ((IModData_Collisions) entity).requiresCollisionsCacheRefresh(true);
                }
            }
        }
    }

    @Override
    public void updateWorldGenerator() {

        // Get the default generator for the world type
        DataContainer generatorSettings = this.getProperties().getGeneratorSettings();

        SpongeWorldGenerator newGenerator = this.createWorldGenerator(generatorSettings);
        // If the base generator is an IChunkProvider which implements
        // IPopulatorProvider we request that it add its populators not covered
        // by the base generation populator
        if (newGenerator.getBaseGenerationPopulator() instanceof IChunkGenerator) {
            // We check here to ensure that the IPopulatorProvider is one of our mixed in ones and not
            // from a mod chunk provider extending a provider that we mixed into
            if (WorldGenConstants.isValid((IChunkGenerator) newGenerator.getBaseGenerationPopulator(), IPopulatorProvider.class)) {
                ((IPopulatorProvider) newGenerator.getBaseGenerationPopulator()).addPopulators(newGenerator);
            }
        } else if (newGenerator.getBaseGenerationPopulator() instanceof IPopulatorProvider) {
            // If its not a chunk provider but is a populator provider then we call it as well
            ((IPopulatorProvider) newGenerator.getBaseGenerationPopulator()).addPopulators(newGenerator);
        }

        for (WorldGeneratorModifier modifier : this.getProperties().getGeneratorModifiers()) {
            modifier.modifyWorldGenerator(this.getProperties(), generatorSettings, newGenerator);
        }

        this.spongegen = this.createChunkGenerator(newGenerator);
        this.spongegen.setGenerationPopulators(newGenerator.getGenerationPopulators());
        this.spongegen.setPopulators(newGenerator.getPopulators());
        this.spongegen.setBiomeOverrides(newGenerator.getBiomeSettings());

        ChunkProviderServer chunkProviderServer = this.getChunkProvider();
        chunkProviderServer.chunkGenerator = this.spongegen;
    }

    @Override
    public SpongeTerrainGenerator createChunkGenerator(SpongeWorldGenerator newGenerator) {
        return new SpongeTerrainGenerator((net.minecraft.world.World) (Object) this, newGenerator.getBaseGenerationPopulator(),
                newGenerator.getBiomeGenerator());
    }

    @Override
    public SpongeWorldGenerator createWorldGenerator(DataContainer settings) {
        // Minecraft uses a string for world generator settings
        // This string can be a JSON string, or be a string of a custom format

        // Try to convert to custom format
        Optional<String> optCustomSettings = settings.getString(DataQueries.WORLD_CUSTOM_SETTINGS);
        if (optCustomSettings.isPresent()) {
            return this.createWorldGenerator(optCustomSettings.get());
        }

        String jsonSettings = "";
        try {
            jsonSettings = DataFormats.JSON.write(settings);
        } catch (Exception e) {
            SpongeImpl.getLogger().warn("Failed to convert settings from [{}] for GeneratorType [{}] used by World [{}].", settings,
                    ((net.minecraft.world.World) (Object) this).getWorldType(), this, e);
        }

        return this.createWorldGenerator(jsonSettings);
    }

    @Override
    public SpongeWorldGenerator createWorldGenerator(String settings) {
        final WorldServer worldServer = (WorldServer) (Object) this;
        final WorldType worldType = worldServer.getWorldType();
        final IChunkGenerator chunkGenerator;
        final BiomeProvider biomeProvider;
        if (worldType instanceof SpongeWorldType) {
            chunkGenerator = ((SpongeWorldType) worldType).createChunkGenerator(worldServer, settings);
            biomeProvider = ((SpongeWorldType) worldType).createBiomeProvider(worldServer);
        } else {
            final IChunkGenerator currentGenerator = this.getChunkProvider().chunkGenerator;
            if (currentGenerator != null) {
                chunkGenerator = currentGenerator;
            } else {
                final WorldProvider worldProvider = worldServer.provider;
                ((IMixinDimension) worldProvider).setGeneratorSettings(settings);
                chunkGenerator = worldProvider.createChunkGenerator();
            }
            biomeProvider = worldServer.provider.biomeProvider;
        }
        return new SpongeWorldGenerator(worldServer, (BiomeGenerator) biomeProvider, SpongeGenerationPopulator.of(worldServer, chunkGenerator));
    }

    @Override
    public WorldGenerator getWorldGenerator() {
        return this.spongegen;
    }

    /**
     * @author blood - July 1st, 2016
     * @author gabizou - July 1st, 2016 - Update to 1.10 and cause tracking
     *
     * @reason Added chunk and block tick optimizations, timings, cause tracking, and pre-construction events.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    @Overwrite
    protected void updateBlocks() {
        this.playerCheckLight();

        if (this.worldInfo.getTerrainType() == WorldType.DEBUG_ALL_BLOCK_STATES)
        {
            Iterator<net.minecraft.world.chunk.Chunk> iterator1 = this.playerChunkMap.getChunkIterator();

            while (iterator1.hasNext())
            {
                iterator1.next().tick(false);
            }
            return; // Sponge: Add return
        }
        // else // Sponge - Remove unnecessary else
        // { //

        int i = this.shadow$getGameRules().getInt("randomTickSpeed");
        boolean flag = this.isRaining();
        boolean flag1 = this.isThundering();
        // this.profiler.startSection("pollingChunks"); // Sponge - Don't use the profiler

        final PhaseTracker phaseTracker = PhaseTracker.getInstance(); // Sponge - get the cause tracker

        // Sponge: Use SpongeImplHooks for Forge
        for (Iterator<net.minecraft.world.chunk.Chunk> iterator =
             SpongeImplHooks.getChunkIterator((WorldServer) (Object) this); iterator.hasNext(); ) // this.profiler.endSection()) // Sponge - don't use the profiler
        {
            // this.profiler.startSection("getChunk"); // Sponge - Don't use the profiler
            net.minecraft.world.chunk.Chunk chunk = iterator.next();
            final net.minecraft.world.World world = chunk.getWorld();
            int j = chunk.x * 16;
            int k = chunk.z * 16;
            // this.profiler.endStartSection("checkNextLight"); // Sponge - Don't use the profiler
            this.timings.updateBlocksCheckNextLight.startTiming(); // Sponge - Timings
            chunk.enqueueRelightChecks();
            this.timings.updateBlocksCheckNextLight.stopTiming(); // Sponge - Timings
            // this.profiler.endStartSection("tickChunk"); // Sponge - Don't use the profiler
            this.timings.updateBlocksChunkTick.startTiming(); // Sponge - Timings
            chunk.tick(false);
            this.timings.updateBlocksChunkTick.stopTiming(); // Sponge - Timings
            // Sponge start - if surrounding neighbors are not loaded, skip
            if (!((IMixinChunk) chunk).areNeighborsLoaded()) {
                continue;
            }
            // Sponge end
            // this.profiler.endStartSection("thunder"); // Sponge - Don't use the profiler
            // Sponge start
            this.timings.updateBlocksThunder.startTiming();

            //if (this.provider.canDoLightning(chunk) && flag && flag1 && this.rand.nextInt(100000) == 0) // Sponge - Add SpongeImplHooks for forge
            if (this.weatherThunderEnabled && SpongeImplHooks.canDoLightning(this.dimension, chunk) && flag && flag1 && this.rand.nextInt(100000) == 0)
            {
                try (final PhaseContext<?> context = TickPhase.Tick.WEATHER.createPhaseContext().source(this)) {
                    context.buildAndSwitch();
                    // Sponge end
                    this.updateLCG = this.updateLCG * 3 + 1013904223;
                    int l = this.updateLCG >> 2;
                    BlockPos blockpos = this.adjustPosToNearbyEntity(new BlockPos(j + (l & 15), 0, k + (l >> 8 & 15)));

                    if (this.isRainingAt(blockpos)) {
                        DifficultyInstance difficultyinstance = this.getDifficultyForLocation(blockpos);

                        // Sponge - create a transform to be used for events
                        final Transform<org.spongepowered.api.world.World>
                            transform =
                            new Transform<>(this, VecHelper.toVector3d(blockpos).toDouble());

                        if (world.getGameRules().getBoolean("doMobSpawning") && this.rand.nextDouble() < (double)difficultyinstance.getAdditionalDifficulty() * 0.01D) {
                            // Sponge Start - Throw construction events
                            try (StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
                                frame.pushCause(this.getWeather());
                                frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.WEATHER);
                                ConstructEntityEvent.Pre
                                    constructEntityEvent =
                                    SpongeEventFactory
                                        .createConstructEntityEventPre(frame.getCurrentCause(), EntityTypes.HORSE, transform);
                                SpongeImpl.postEvent(constructEntityEvent);
                                if (!constructEntityEvent.isCancelled()) {
                                    // Sponge End
                                    EntitySkeletonHorse entityhorse = new EntitySkeletonHorse((WorldServer) (Object) this);
                                    entityhorse.setTrap(true);
                                    entityhorse.setGrowingAge(0);
                                    entityhorse.setPosition(blockpos.getX(), blockpos.getY(), blockpos.getZ());
                                    this.spawnEntity(entityhorse);
                                    // Sponge Start - Throw a construct event for the lightning
                                }

                                ConstructEntityEvent.Pre
                                    lightning =
                                    SpongeEventFactory
                                        .createConstructEntityEventPre(frame.getCurrentCause(), EntityTypes.LIGHTNING_BOLT,
                                            transform);
                                SpongeImpl.postEvent(lightning);
                                if (!lightning.isCancelled()) {
                                    LightningEvent.Pre lightningPre = SpongeEventFactory.createLightningEventPre(frame.getCurrentCause());
                                    if (!SpongeImpl.postEvent(lightningPre)) {
                                        // Sponge End
                                        this.addWeatherEffect(new EntityLightningBolt(world, (double) blockpos.getX(), (double) blockpos.getY(),
                                                (double) blockpos.getZ(), true));
                                    }
                                } // Sponge - Brackets.
                            }
                        } else {
                            // Sponge start - Throw construction event for lightningbolts
                            try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
                                frame.pushCause(this.getWeather());
                                frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.WEATHER);
                                ConstructEntityEvent.Pre
                                    event =
                                    SpongeEventFactory.createConstructEntityEventPre(frame.getCurrentCause(),
                                        EntityTypes.LIGHTNING_BOLT, transform);
                                SpongeImpl.postEvent(event);
                                if (!event.isCancelled()) {
                                    LightningEvent.Pre lightningPre = SpongeEventFactory.createLightningEventPre(frame.getCurrentCause());
                                    if (!SpongeImpl.postEvent(lightningPre)) {
                                        // Sponge End
                                        this.addWeatherEffect(new EntityLightningBolt(world, (double) blockpos.getX(), (double) blockpos.getY(),
                                                (double) blockpos.getZ(), true));
                                    }
                                } // Sponge - Brackets.
                            }
                        }
                    }
                } // Sponge - brackets
                // Sponge End

            }

            this.timings.updateBlocksThunder.stopTiming(); // Sponge - Stop thunder timing
            this.timings.updateBlocksIceAndSnow.startTiming(); // Sponge - Start thunder timing
            // this.profiler.endStartSection("iceandsnow"); // Sponge - don't use the profiler

            // if (this.rand.nextInt(16) == 0) // Sponge - Rewrite to use our boolean, and forge hook
            if (this.weatherIceAndSnowEnabled && SpongeImplHooks.canDoRainSnowIce(this.dimension, chunk) && this.rand.nextInt(16) == 0)
            {
                // Sponge Start - Enter weather phase for snow and ice and flooding.
                try (final PhaseContext<?> context = TickPhase.Tick.WEATHER.createPhaseContext()
                        .source(this)) {
                    context.buildAndSwitch();
                    // Sponge End
                    this.updateLCG = this.updateLCG * 3 + 1013904223;
                    int j2 = this.updateLCG >> 2;
                    BlockPos blockpos1 = this.getPrecipitationHeight(new BlockPos(j + (j2 & 15), 0, k + (j2 >> 8 & 15)));
                    BlockPos blockpos2 = blockpos1.down();

                    if (this.canBlockFreezeNoWater(blockpos2)) {
                        this.setBlockState(blockpos2, Blocks.ICE.getDefaultState());
                    }

                    if (flag && this.canSnowAt(blockpos1, true)) {
                        this.setBlockState(blockpos1, Blocks.SNOW_LAYER.getDefaultState());
                    }

                    if (flag && this.getBiome(blockpos2).canRain()) {
                        this.getBlockState(blockpos2).getBlock().fillWithRain((WorldServer) (Object) this, blockpos2);
                    }
                } // Sponge - brackets
            }

            this.timings.updateBlocksIceAndSnow.stopTiming(); // Sponge - Stop ice and snow timing
            this.timings.updateBlocksRandomTick.startTiming(); // Sponge - Start random block tick timing
            // this.profiler.endStartSection("tickBlocks"); // Sponge - Don't use the profiler

            if (i > 0)
            {
                for (ExtendedBlockStorage extendedblockstorage : chunk.getBlockStorageArray())
                {
                    if (extendedblockstorage != net.minecraft.world.chunk.Chunk.NULL_BLOCK_STORAGE && extendedblockstorage.needsRandomTick())
                    {
                        for (int i1 = 0; i1 < i; ++i1)
                        {
                            this.updateLCG = this.updateLCG * 3 + 1013904223;
                            int j1 = this.updateLCG >> 2;
                            int k1 = j1 & 15;
                            int l1 = j1 >> 8 & 15;
                            int i2 = j1 >> 16 & 15;
                            IBlockState iblockstate = extendedblockstorage.get(k1, i2, l1);
                            Block block = iblockstate.getBlock();
                            // this.profiler.startSection("randomTick"); // Sponge - Don't use the profiler

                            if (block.getTickRandomly())
                            {
                                // Sponge start - capture random tick
                                // Remove the random tick for cause tracking
                                // block.randomTick(this, new BlockPos(k1 + j, i2 + extendedblockstorage.getYLocation(), l1 + k), iblockstate, this.rand);

                                BlockPos pos = new BlockPos(k1 + j, i2 + extendedblockstorage.getYLocation(), l1 + k);
                                IMixinBlock spongeBlock = (IMixinBlock) block;
                                spongeBlock.getTimingsHandler().startTiming();
                                final PhaseData currentTuple = phaseTracker.getCurrentPhaseData();
                                final IPhaseState phaseState = currentTuple.state;
                                if (phaseState.alreadyCapturingBlockTicks(currentTuple.context)) {
                                    block.randomTick(world, pos, iblockstate, this.rand);
                                } else {
                                    TrackingUtil.randomTickBlock(phaseTracker, this, block, pos, iblockstate, this.rand);
                                }
                                spongeBlock.getTimingsHandler().stopTiming();
                                // Sponge end
                            }

                            // this.profiler.endSection(); // Sponge - Don't use the profiler
                        }
                    }
                }
            }
        }

        this.timings.updateBlocksRandomTick.stopTiming(); // Sponge - Stop random block timing
        // this.profiler.endSection(); // Sponge - Don't use the profiler
        // } // Sponge- Remove unecessary else
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/storage/WorldInfo;setDifficulty(Lnet/minecraft/world/EnumDifficulty;)V"))
    private void syncDifficultyDueToHardcore(WorldInfo worldInfo, EnumDifficulty newDifficulty) {
        WorldManager.adjustWorldForDifficulty(WorldUtil.asNative((IMixinWorldServer) this), newDifficulty, false);
    }

    @Redirect(method = "updateBlockTick", at = @At(value = "INVOKE", target= "Lnet/minecraft/world/WorldServer;isAreaLoaded(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;)Z"))
    private boolean onBlockTickIsAreaLoaded(WorldServer worldIn, BlockPos fromPos, BlockPos toPos) {
        int posX = fromPos.getX() + 8;
        int posZ = fromPos.getZ() + 8;
        // Forge passes the same block position for forced chunks
        if (fromPos.equals(toPos)) {
            posX = fromPos.getX();
            posZ = fromPos.getZ();
        }
        final net.minecraft.world.chunk.Chunk chunk = this.mixinChunkProviderServer.getLoadedChunkWithoutMarkingActive(posX >> 4, posZ >> 4);
        return chunk != null && ((IMixinChunk) chunk).areNeighborsLoaded();
    }

    /**
     * @author gabizou - July 8th, 2018
     * @reason Performs a check on the block update to take place whether it will be
     * immediately scheduled, and then whether we need to enter {@link TickPhase.Tick#BLOCK} for
     * the scheduled update. Likewise, this will check whether scheduled updates are immediate
     * for this method call and then flip the flag off to avoid nested recursion.
     *
     * @param block The block to update
     * @param worldIn The world server, otherwise known as "this" object
     * @param pos The position
     * @param state The block state
     * @param rand The random, otherwise known as "this.rand"
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(
        method = "updateBlockTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;updateTick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V"
        )
    )
    private void spongeBlockUpdateTick(Block block, World worldIn, BlockPos pos, IBlockState state, Random rand) {
        if (this.scheduledUpdatesAreImmediate) {
            /*
            The reason why we are first checking and then resetting the immediate updates flag is
            because Vanilla will allow block updates to be performed "immediately", but certain blocks
            will recursively update neighboring blocks/change neighboring blocks such that it can cause
            a near infinite recursion in a "blob" of re-entrance. This avoids nested immediate block updates
            within the same method call of the immediate block update.
            See: https://github.com/SpongePowered/SpongeForge/issues/2273 for further explanation
             */
            this.scheduledUpdatesAreImmediate = false;
        }
        final PhaseData data = PhaseTracker.getInstance().getCurrentPhaseData();
        final IPhaseState<?> phaseState = data.state;
        if (((IPhaseState) phaseState).alreadyCapturingBlockTicks(data.context) || ((IPhaseState) phaseState).ignoresBlockUpdateTick(data.context)) {
            block.updateTick(worldIn, pos, state, rand);
            return;
        }
        TrackingUtil.updateTickBlock(this, block, pos, state, rand);

    }

    @Redirect(method = "updateBlockTick", // really scheduleUpdate
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/NextTickListEntry;setPriority(I)V"))
    private void onCreateScheduledBlockUpdate(NextTickListEntry sbu, int priority) {
        final PhaseTracker phaseTracker = PhaseTracker.getInstance();
        final IPhaseState<?> phaseState = phaseTracker.getCurrentState();

        if (phaseState.ignoresScheduledUpdates()) {
            this.tmpScheduledObj = sbu;
            return;
        }

        sbu.setPriority(priority);
        ((IMixinNextTickListEntry) sbu).setWorld((WorldServer) (Object) this);
        this.tmpScheduledObj = sbu;
    }

    /**
     * @author blood - August 30th, 2016
     *
     * @reason Always allow entity cleanup to occur. This prevents issues such as a plugin
     *         generating chunks with no players causing entities not getting cleaned up.
     */
    @Override
    @Overwrite
    public void tickEntities() {
        // Sponge start
        /*
        if (this.playerEntities.isEmpty()) {
            if (this.updateEntityTick++ >= 300) {
                return;
            }
        } else {
            this.resetUpdateEntityTick();
        }*/

        TrackingUtil.tickDimension(this);
        // Sponge end
        super.updateEntities();
    }

    // This ticks pending updates to blocks, Requires mixin for NextTickListEntry so we use the correct tracking
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "tickUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;updateTick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V"))
    private void onUpdateTick(Block block, net.minecraft.world.World worldIn, BlockPos pos, IBlockState state, Random rand) {
        final PhaseTracker phaseTracker = PhaseTracker.getInstance();
        final PhaseData phaseData = phaseTracker.getCurrentPhaseData();
        final IPhaseState phaseState = phaseData.state;
        if (phaseState.alreadyCapturingBlockTicks(phaseData.context) || phaseState.ignoresBlockUpdateTick(phaseData.context)) {
            block.updateTick(worldIn, pos, state, rand);
            return;
        }
        TrackingUtil.updateTickBlock(this, block, pos, state, rand);
    }

    // TODO: 1.13
    /*@Redirect(method = "tickUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/crash/CrashReportCategory;addBlockInfo(Lnet/minecraft/crash/CrashReportCategory;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)V"))
    private void onBlockInfo(CrashReportCategory category, BlockPos pos, IBlockState state) {
        try {
            CrashReportCategory.addBlockInfo(category, pos, state);
        } catch (NoClassDefFoundError e) {
            SpongeImpl.getLogger().error("An error occurred while adding crash report info!", e);
            SpongeImpl.getLogger().error("Original caught error:", category.crashReport.cause);
            throw new ReportedException(category.crashReport);
        }
    }*/

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "addBlockEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer$ServerBlockEventList;add(Ljava/lang/Object;)Z", remap = false))
    private boolean onAddBlockEvent(ObjectLinkedOpenHashSet<BlockEventData> list, Object obj, BlockPos pos, Block blockIn, int eventId, int eventParam) {
        final BlockEventData blockEventData = (BlockEventData) obj;
        IMixinBlockEventData blockEvent = (IMixinBlockEventData) blockEventData;
        // We fire a Pre event to make sure our captures do not get stuck in a loop.
        // This is very common with pistons as they add block events while blocks are being notified.
        if (blockIn instanceof BlockPistonBase) {
            // We only fire pre events for pistons
            if (SpongeCommonEventFactory.handlePistonEvent(this, list, obj, pos, blockIn, eventId, eventParam)) {
                return false;
            }

            blockEvent.setCaptureBlocks(false);
            // TODO BLOCK_EVENT flag
        } else if (SpongeCommonEventFactory.callChangeBlockEventPre(this, pos).isCancelled()) {
            return false;
        }


        final PhaseTracker phaseTracker = PhaseTracker.getInstance();
        final PhaseData currentPhase = phaseTracker.getCurrentPhaseData();
        final IPhaseState phaseState = currentPhase.state;
        if (phaseState.ignoresBlockEvent()) {
            return list.add((BlockEventData) obj);
        }
        final PhaseContext<?> context = currentPhase.context;

        final LocatableBlock locatable = LocatableBlock.builder()
            .location(new Location<>(this, pos.getX(), pos.getY(), pos.getZ()))
            .state(this.getBlock(pos.getX(), pos.getY(), pos.getZ()))
            .build();

        blockEvent.setTickBlock(locatable);
        phaseState.appendNotifierToBlockEvent(context, this, pos, blockEvent);
        return list.add((BlockEventData) obj);
    }

    // special handling for Pistons since they use their own event system
    @Redirect(method = "sendQueuedBlockEvents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/WorldServer;fireBlockEvent(Lnet/minecraft/block/BlockEventData;)Z"))
    private boolean onFireBlockEvent(net.minecraft.world.WorldServer worldIn, BlockEventData event) {
        final PhaseTracker phaseTracker = PhaseTracker.getInstance();
        final IPhaseState<?> phaseState = phaseTracker.getCurrentState();
        if (phaseState.ignoresBlockEvent()) {
            return fireBlockEvent(event);
        }
        return TrackingUtil.fireMinecraftBlockEvent(worldIn, event);
    }

    // Chunk GC
    @Override
    public void doChunkGC() {
        this.chunkGCTickCount++;

        ChunkProviderServer chunkProviderServer = this.getChunkProvider();
        int chunkLoadCount = this.getChunkProvider().getLoadedChunkCount();
        if (chunkLoadCount >= this.chunkGCLoadThreshold && this.chunkGCLoadThreshold > 0) {
            chunkLoadCount = 0;
        } else if (this.chunkGCTickCount >= this.chunkGCTickInterval && this.chunkGCTickInterval > 0) {
            this.chunkGCTickCount = 0;
        } else {
            return;
        }

        for (net.minecraft.world.chunk.Chunk chunk : chunkProviderServer.getLoadedChunks()) {
            IMixinChunk spongeChunk = (IMixinChunk) chunk;
            if (chunk.unloadQueued || spongeChunk.isPersistedChunk() || !this.dimension.canDropChunk(chunk.x, chunk.z)) {
                continue;
            }

            // If a player is currently using the chunk, skip it
            if (((IMixinPlayerChunkMap) this.getPlayerChunkMap()).isChunkInUse(chunk.x, chunk.z)) {
                continue;
            }

            // If we reach this point the chunk leaked so queue for unload
            chunkProviderServer.queueUnload(chunk);
            SpongeHooks.logChunkGCQueueUnload(chunkProviderServer.world, chunk);
        }
    }


    @Inject(method = "saveLevel", at = @At("HEAD"))
    private void onSaveLevel(CallbackInfo ci) {
        // Always call the provider's onWorldSave method as we do not use WorldServerMulti
        for (WorldServer worldServer : this.server.getWorlds()) {
            worldServer.dimension.onWorldSave();
        }
    }

    /**
     * @author blood - July 20th, 2017
     * @reason This method is critical as it handles world saves and whether to queue chunks for unload if GC is enabled.
     * It has been overwritten to make it easier to manage for future updates.
     *
     * @param all Whether to save all chunks
     * @param progressCallback The save progress callback
     */
    @Overwrite
    public void saveAllChunks(boolean all, @Nullable IProgressUpdate progressCallback) throws MinecraftException
    {
        ChunkProviderServer chunkproviderserver = this.getChunkProvider();

        if (chunkproviderserver.canSave())
        {
            Sponge.getEventManager().post(SpongeEventFactory.createSaveWorldEventPre(Sponge.getCauseStackManager().getCurrentCause(), this));
            if (progressCallback != null)
            {
                progressCallback.displaySavingString("Saving level");
            }

            this.saveLevel();

            if (progressCallback != null)
            {
                progressCallback.displayLoadingString("Saving chunks");
            }

            chunkproviderserver.saveChunks(all);
            Sponge.getEventManager().post(SpongeEventFactory.createSaveWorldEventPost(Sponge.getCauseStackManager().getCurrentCause(), this));

            // The chunk GC handles all queuing for chunk unloads so we return here to avoid it during a save.
            if (this.chunkGCTickInterval > 0) {
                return;
            }

            for (Chunk chunk : Lists.newArrayList(chunkproviderserver.getLoadedChunks()))
            {
                if (chunk != null && !this.playerChunkMap.contains(chunk.x, chunk.z))
                {
                    chunkproviderserver.queueUnload(chunk);
                }
            }
        }
    }

    @Redirect(method = "sendQueuedBlockEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/DimensionType;getId()I"), expect = 0, require = 0)
    private int onGetDimensionIdForBlockEvents(DimensionType dimensionType) {
        return this.getDimensionId();
    }

    @Override
    public Collection<ScheduledUpdate<BlockState>> getScheduledUpdates(int x, int y, int z) {
        BlockPos position = new BlockPos(x, y, z);
        ImmutableList.Builder<ScheduledBlockUpdate> builder = ImmutableList.builder();
        for (NextTickListEntry sbu : this.pendingTickListEntriesTreeSet) {
            if (sbu.position.equals(position)) {
                builder.add((ScheduledBlockUpdate) sbu);
            }
        }
        return builder.build();
    }



    @Redirect(method = "updateAllPlayersSleepingFlag()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/EntityPlayer;isSpectator()Z"))
    private boolean isSpectatorOrIgnored(EntityPlayer entityPlayer) {
        // spectators are excluded from the sleep tally in vanilla
        // this redirect expands that check to include sleep-ignored players as well
        boolean ignore = entityPlayer instanceof Player && ((Player)entityPlayer).isSleepingIgnored();
        return ignore || entityPlayer.isSpectator();
    }

    @Redirect(method = "areAllPlayersAsleep()Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/EntityPlayer;isPlayerFullyAsleep()Z"))
    private boolean isPlayerFullyAsleep(EntityPlayer entityPlayer) {
        // if isPlayerFullyAsleep() returns false areAllPlayerAsleep() breaks its loop and returns false
        // this redirect forces it to return true if the player is sleep-ignored even if they're not sleeping
        boolean ignore = entityPlayer instanceof Player && ((Player)entityPlayer).isSleepingIgnored();
        return ignore || entityPlayer.isPlayerFullyAsleep();
    }

    @Redirect(method = "areAllPlayersAsleep()Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/EntityPlayer;isSpectator()Z"))
    private boolean isSpectatorAndNotIgnored(EntityPlayer entityPlayer) {
        // if a player is marked as a spectator areAllPlayersAsleep() breaks its loop and returns false
        // this redirect forces it to return false if a player is sleep-ignored even if they're a spectator
        boolean ignore = entityPlayer instanceof Player && ((Player)entityPlayer).isSleepingIgnored();
        return !ignore && entityPlayer.isSpectator();
    }



    private void checkBlockBounds(int x, int y, int z) {
        if (!containsBlock(x, y, z)) {
            throw new PositionOutOfBoundsException(new Vector3i(x, y, z), getBlockMin(), getBlockMax());
        }
    }

    /**
     * @author gabizou - April 24th, 2016
     * @reason Needs to redirect the dimension id for the packet being sent to players
     * so that the dimension is correctly adjusted
     *
     * @param id The world provider's dimension id
     * @return True if the spawn was successful and the effect is played.
     */
    // We expect 0 because forge patches it correctly
    @Redirect(method = "addWeatherEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/DimensionType;getId()I"), expect = 0, require = 0)
    private int getDimensionIdForWeatherEffect(DimensionType id) {
        return this.getDimensionId();
    }

    /**
     * @author gabizou - February 7th, 2016
     * @author gabizou - September 3rd, 2016 - Moved from MixinWorld_Impl since WorldServer overrides the method.
     *
     * This will short circuit all other patches such that we control the
     * entities being loaded by chunkloading and can throw our bulk entity
     * event. This will bypass Forge's hook for individual entity events,
     * but the SpongeModEventManager will still successfully throw the
     * appropriate event and cancel the entities otherwise contained.
     *
     * @param entities The entities being loaded
     * @param callbackInfo The callback info
     */
    @Final
    @Inject(method = "loadEntities", at = @At("HEAD"), cancellable = true)
    private void spongeLoadEntities(Collection<net.minecraft.entity.Entity> entities, CallbackInfo callbackInfo) {
        if (entities.isEmpty()) {
            // just return, no entities to load!
            callbackInfo.cancel();
            return;
        }
        List<Entity> entityList = new ArrayList<>();
        for (net.minecraft.entity.Entity entity : entities) {
            // Make sure no entities load in invalid positions
            if (((IMixinBlockPos) entity.getPosition()).isInvalidYPosition()) {
                entity.remove();
                continue;
            }
            if (this.canAddEntity(entity)) {
                entityList.add((Entity) entity);
            }
        }
        try (StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.CHUNK_LOAD);
            frame.pushCause(this);
            SpawnEntityEvent.ChunkLoad chunkLoad = SpongeEventFactory.createSpawnEntityEventChunkLoad(Sponge.getCauseStackManager().getCurrentCause(), Lists.newArrayList(entityList));
            SpongeImpl.postEvent(chunkLoad);
            if (!chunkLoad.isCancelled() && chunkLoad.getEntities().size() > 0) {
                for (Entity successful : chunkLoad.getEntities()) {
                    this.loadedEntityList.add((net.minecraft.entity.Entity) successful);
                    this.onEntityAdded((net.minecraft.entity.Entity) successful);
                }
            }
            // Remove entities from chunk/world that were filtered in event
            // This prevents invisible entities from loading into the world and blocking the position.
            for (Entity entity : entityList) {
                if (!chunkLoad.getEntities().contains(entity)) {
                    ((net.minecraft.world.World) (Object) this).removeEntityDangerously((net.minecraft.entity.Entity) entity);
                }
            }
            callbackInfo.cancel();
        }
    }


    /**
     * @author gabizou, March 12th, 2016
     *
     * Move this into WorldServer as we should not be modifying the client world.
     *
     * Purpose: Rewritten to support capturing blocks
     */
    @Override
    public boolean setBlockState(BlockPos pos, IBlockState newState, int flags) {
        if (!this.isValid(pos)) {
            return false;
        } else if (this.worldInfo.getTerrainType() == WorldType.DEBUG_ALL_BLOCK_STATES) { // isRemote is always false since this is WorldServer
            return false;
        } else {
            // Sponge - reroute to the PhaseTracker
            return PhaseTracker.getInstance().setBlockState(this, pos.toImmutable(), newState, BlockChangeFlagRegistryModule.fromNativeInt(flags));
        }
    }

    @Override
    public boolean setBlockState(BlockPos pos, IBlockState state, BlockChangeFlag flag) {
        if (!this.isValid(pos)) {
            return false;
        } else if (this.worldInfo.getTerrainType() == WorldType.DEBUG_ALL_BLOCK_STATES) { // isRemote is always false since this is WorldServer
            return false;
        } else {
            // Sponge - reroute to the PhaseTracker
            return PhaseTracker.getInstance().setBlockState(this, pos.toImmutable(), state, flag);
        }
    }

    /**
     * @author gabizou - July 25th, 2018
     * @reason Technically an overwrite for {@link World#destroyBlock(BlockPos, boolean)}
     * so that we can artificially capture/associate entity spawns from the proposed block
     * destruction when the actual block event is thrown, whether captures are taking
     * place or not. In the context of "if block changes are not captured", we do still need
     * to associate the drops before the actual block is removed
     *
     * @param pos
     * @param dropBlock
     * @return
     */
    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropBlock) {
        IBlockState iblockstate = this.getBlockState(pos);
        Block block = iblockstate.getBlock();

        if (iblockstate.getMaterial() == Material.AIR) {
            return false;
        }
        // Sponge Start - Fire the change block pre here, before we bother with drops. If the pre is cancelled, just don't bother.
        if (ShouldFire.CHANGE_BLOCK_EVENT_PRE) {
            if (SpongeCommonEventFactory.callChangeBlockEventPre(this, pos).isCancelled()) {
                return false;
            }
        }
        // Sponge End
        this.playEvent(2001, pos, Block.getStateId(iblockstate));

        if (dropBlock) {
            // Sponge Start - since we are going to perform block drops, we need
            // to notify the current phase state and find out if capture pos is to be used.
            final PhaseContext<?> context = PhaseTracker.getInstance().getCurrentContext();
            final IPhaseState<?> state = PhaseTracker.getInstance().getCurrentState();
            final boolean isCapturingBlockDrops = state.alreadyProcessingBlockItemDrops();
            final BlockPos previousPos;
            if (isCapturingBlockDrops) {
                previousPos = context.getCaptureBlockPos().getPos().orElse(null);
                context.getCaptureBlockPos().setPos(pos);
            } else {
                previousPos = null;
            }
            // Sponge End
            block.dropBlockAsItem((WorldServer) (Object) this, pos, iblockstate, 0);
            // Sponge Start
            if (isCapturingBlockDrops) {
                // we need to reset the capture pos because we've been capturing item and entity drops this way.
                context.getCaptureBlockPos().setPos(previousPos);
            }
            // Sponge End

        }

        // Sponge - reduce the call stack by calling the more direct method.
        return this.setBlockState(pos, Blocks.AIR.getDefaultState(), BlockChangeFlags.ALL);
    }

    /**
     * @author gabizou - March 12th, 2016
     *
     * Technically an overwrite to properly track on *server* worlds.
     */
    @Override
    public void neighborChanged(BlockPos pos, Block blockIn, BlockPos otherPos) { // notifyBlockOfStateChange
        final Chunk chunk =
                ((IMixinChunkProviderServer) this.getChunkProvider()).getLoadedChunkWithoutMarkingActive(otherPos.getX() >> 4, otherPos.getZ() >>
                        4);

        // Don't let neighbor updates trigger a chunk load ever
        if (chunk == null) {
            return;
        }

        PhaseTracker.getInstance().notifyBlockOfStateChange(this, pos, blockIn, otherPos);
    }

    /**
     * @author gabizou - March 12th, 2016
     *
     * Technically an overwrite to properly track on *server* worlds.
     */
    @Override
    public void notifyNeighborsOfStateExcept(BlockPos pos, Block blockType, EnumFacing skipSide) {
        if (!isValid(pos)) {
            return;
        }

        final Chunk chunk =
                ((IMixinChunkProviderServer) this.getChunkProvider()).getLoadedChunkWithoutMarkingActive(pos.getX() >> 4, pos.getZ() >>
                        4);

        // Don't let neighbor updates trigger a chunk load ever
        if (chunk == null) {
            return;
        }

        EnumSet<EnumFacing> directions = EnumSet.copyOf(NOTIFY_DIRECTIONS);
        directions.remove(skipSide);
        // Check for listeners.
        if (ShouldFire.NOTIFY_NEIGHBOR_BLOCK_EVENT) {
            final NotifyNeighborBlockEvent event = SpongeCommonEventFactory.callNotifyNeighborEvent(this, pos, directions);
            if (event == null || !event.isCancelled()) {
                final PhaseTracker phaseTracker = PhaseTracker.getInstance();
                for (EnumFacing facing : EnumFacing.values()) {
                    if (event != null) {
                        final Direction direction = DirectionFacingProvider.getInstance().getKey(facing).get();
                        if (!event.getNeighbors().keySet().contains(direction)) {
                            continue;
                        }
                    }

                    phaseTracker.notifyBlockOfStateChange(this, pos.offset(facing), blockType, pos);
                }
            }
            return;
        }

        // Else, we just do vanilla. If there's no listeners, we don't want to spam the notification event
        for (EnumFacing direction : NOTIFY_DIRECTIONS) {
            if (direction == skipSide) {
                continue;
            }
            PhaseTracker.getInstance().notifyBlockOfStateChange(this, pos.offset(direction), blockType, pos);
        }

    }

    /**
     * @author gabizou - March 12th, 2016
     *
     * Technically an overwrite to properly track on *server* worlds.
     */
    @Override
    public void notifyNeighborsOfStateChange(BlockPos pos, Block blockType, boolean updateObserverBlocks) {
        if (!isValid(pos)) {
            return;
        }

        final Chunk chunk =
                ((IMixinChunkProviderServer) this.getChunkProvider()).getLoadedChunkWithoutMarkingActive(pos.getX() >> 4, pos.getZ() >> 4);

        // Don't let neighbor updates trigger a chunk load ever
        if (chunk == null) {
            return;
        }

        if (ShouldFire.NOTIFY_NEIGHBOR_BLOCK_EVENT) {
            final NotifyNeighborBlockEvent event = SpongeCommonEventFactory.callNotifyNeighborEvent(this, pos, NOTIFY_DIRECTIONS);
            if (event == null || !event.isCancelled()) {
                final PhaseTracker phaseTracker = PhaseTracker.getInstance();
                for (EnumFacing facing : EnumFacing.values()) {
                    if (event != null) {
                        final Direction direction = DirectionFacingProvider.getInstance().getKey(facing).get();
                        if (!event.getNeighbors().keySet().contains(direction)) {
                            continue;
                        }
                    }

                    phaseTracker.notifyBlockOfStateChange(this, pos.offset(facing), blockType, pos);
                }
            }
        } else {
            // Else, we just do vanilla. If there's no listeners, we don't want to spam the notification event
            for (EnumFacing direction : NOTIFY_DIRECTIONS) {
                PhaseTracker.getInstance().notifyBlockOfStateChange(this, pos.offset(direction), blockType, pos);
            }
        }

        // Copied over to ensure observers retain functionality.
        if (updateObserverBlocks) {
            this.updateObservingBlocksAt(pos, blockType);
        }
    }

    @Override
    public void onDestroyBlock(BlockPos pos, boolean dropBlock, CallbackInfoReturnable<Boolean> cir) {
        if (SpongeCommonEventFactory.callChangeBlockEventPre(this, pos).isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @Override
    protected void onUpdateWeatherEffect(net.minecraft.entity.Entity entityIn) {
        onCallEntityUpdate(entityIn); // maybe we should combine these injections/redirects?
    }

    @Override
    protected void onUpdateTileEntities(ITickable tile) {
        this.updateTileEntity(tile);
    }

    // separated from onUpdateEntities for TileEntityActivation mixin
    private void updateTileEntity(ITickable tile) {
        final PhaseTracker phaseTracker = PhaseTracker.getInstance();
        final IPhaseState<?> state = phaseTracker.getCurrentState();

        if (state.alreadyCapturingTileTicks()) {
            tile.tick();
            return;
        }

        TrackingUtil.tickTileEntity(this, tile);
    }

    @Override
    protected void onCallEntityUpdate(net.minecraft.entity.Entity entity) {
        final PhaseTracker phaseTracker = PhaseTracker.getInstance();
        final IPhaseState<?> state = phaseTracker.getCurrentState();
        if (state.alreadyCapturingEntityTicks()) {
            entity.tick();
            return;
        }

        TrackingUtil.tickEntity(entity);
        updateRotation(entity);
    }

    @Override
    protected void onCallEntityRidingUpdate(net.minecraft.entity.Entity entity) {
        final PhaseTracker phaseTracker = PhaseTracker.getInstance();
        final IPhaseState<?> state = phaseTracker.getCurrentState();
        if (state.alreadyCapturingEntityTicks()) {
            entity.updateRidden();
            return;
        }

        TrackingUtil.tickRidingEntity(entity);
        updateRotation(entity);
    }

    /**
     * @author gabizou - May 11th, 2018
     * @reason Due to mods attempting to retrieve spawned entity drops in the world,
     * we occasionally have to accomodate those mods by providing insight into the
     * entities that are being captured by the {@link PhaseTracker} in the instance
     * we have an {@link IPhaseState} that is capturing entities. This is only to
     * allow the mod to still retrieve said entities in the "world" that would otherwise
     * be spawned.
     *
     * <p>Note that the entities are also filtered on whether they are being removed
     * during the {@link IPhaseState#unwind(PhaseContext)} process to avoid duplicate
     * entity spawns.</p>
     *
     * @param clazz The entity class
     * @param aabb The axis aligned bounding box
     * @param filter The filter predicate
     * @param <T> The type of entity list
     * @return The list of entities found
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <T extends net.minecraft.entity.Entity> List<T> getEntitiesWithinAABB(Class<? extends T> clazz, AxisAlignedBB aabb,
        @Nullable Predicate<? super T> filter) {
        // Sponge start - get the max entity radius variable from forge
        final double maxEntityRadius = SpongeImplHooks.getWorldMaxEntityRadius(this);
        // j2 == minChunkX
        // k2 == maxChunkX
        // l2 == minChunkZ
        // i3 == maxChunkZ
        int minChunkX = MathHelper.floor((aabb.minX - maxEntityRadius) / 16.0D);
        int maxChunkX = MathHelper.ceil((aabb.maxX + maxEntityRadius) / 16.0D);
        int minChunkZ = MathHelper.floor((aabb.minZ - maxEntityRadius) / 16.0D);
        int maxChunkZ = MathHelper.ceil((aabb.maxZ + maxEntityRadius) / 16.0D);
        // Sponge End
        List<T> list = Lists.newArrayList();

        for (int currentX = minChunkX; currentX < maxChunkX; ++currentX) {
            for (int currentZ = minChunkZ; currentZ < maxChunkZ; ++currentZ) {
                if (this.isChunkLoaded(currentX, currentZ, true)) {
                    this.getChunk(currentX, currentZ).getEntitiesOfTypeWithinAABB(clazz, aabb, list, filter);
                }
            }
        }
        // Sponge Start - check the phase tracker
        final boolean isMainThread = Sponge.isServerAvailable() && Sponge.getServer().onMainThread();
        if (!isMainThread) {
            // Short circuit here if we're not on the main thread. Don't bother with the PhaseTracker off thread.
            return list;
        }
        final PhaseData currentPhase = PhaseTracker.getInstance().getCurrentPhaseData();
        final PhaseContext<?> context = currentPhase.context;
        final IPhaseState<?> state = currentPhase.state;
        if (((IPhaseState) state).doesCaptureEntityDrops(context) || state.doesAllowEntitySpawns()) {
            // We need to check for entity spawns and entity drops. If either are used, we need to offer them up in the lsit, provided
            // they pass the predicate check
            if (((IPhaseState) state).doesCaptureEntityDrops(context)) {
                for (EntityItem entity : context.getCapturedItems()) {
                    // We can ignore the type check because we're already checking the instance class of the entity.
                    if (clazz.isInstance(entity) && entity.getBoundingBox().intersects(aabb) && (filter == null || filter.apply((T) entity))) {
                        list.add((T) entity);
                    }
                }
            }
            if (state.doesCaptureEntitySpawns()) {
                for (Entity entity : context.getCapturedEntities()) {
                    // We can ignore the type check because we're already checking the instance class of the entity.
                    if (clazz.isInstance(entity) && EntityUtil.toNative(entity).getBoundingBox().intersects(aabb) && (filter == null || filter.apply((T) entity))) {
                        list.add((T) entity);
                    }
                }
                if (((IPhaseState) state).doesBulkBlockCapture(context)) {
                    for (net.minecraft.entity.Entity entity : context.getPerBlockEntitySpawnSuppplier().get().values()) {
                        // We can ignore the type check because we're already checking the instance class of the entity.
                        if (clazz.isInstance(entity) && entity.getBoundingBox().intersects(aabb) && (filter == null || filter.apply((T) entity))) {
                            list.add((T) entity);
                        }
                    }
                }
            }

        }
        // Sponge End
        return list;
    }

    // ------------------------ End of Cause Tracking ------------------------------------

    // IMixinWorld_Impl method
    @Override
    public void spongeNotifyNeighborsPostBlockChange(BlockPos pos, IBlockState oldState, IBlockState newState, BlockChangeFlag flags) {
        if (flags.updateNeighbors()) {
            this.notifyNeighborsRespectDebug(pos, newState.getBlock(), true);

            if (newState.hasComparatorInputOverride()) {
                this.updateComparatorOutputLevel(pos, newState.getBlock());
            }
        }
    }

    // IMixinWorld_Impl method
    @Override
    public void addEntityRotationUpdate(net.minecraft.entity.Entity entity, Vector3d rotation) {
        this.rotationUpdates.put(entity, rotation);
    }

    // IMixinWorld_Impl method
    @Override
    public void updateRotation(net.minecraft.entity.Entity entityIn) {
        Vector3d rotationUpdate = this.rotationUpdates.get(entityIn);
        if (rotationUpdate != null) {
            entityIn.rotationPitch = (float) rotationUpdate.getX();
            entityIn.rotationYaw = (float) rotationUpdate.getY();
        }
        this.rotationUpdates.remove(entityIn);
    }

    @Override
    public void onSpongeEntityAdded(net.minecraft.entity.Entity entity) {
        this.onEntityAdded(entity);
        ((IMixinEntity) entity).onJoinWorld();
    }

    @Override
    public void onSpongeEntityRemoved(net.minecraft.entity.Entity entity) {
        this.onEntityRemoved(entity);
    }

    /**
     * @author gabizou March 11, 2016
     *
     * The train of thought for how spawning is handled:
     * 1) This method is called in implementation
     * 2) handleVanillaSpawnEntity is called to associate various contextual SpawnCauses
     * 3) {@link PhaseTracker#spawnEntity(org.spongepowered.api.world.World, Entity)} is called to
     *    check if the entity is to
     *    be "collected" or "captured" in the current {@link PhaseContext} of the current phase
     * 4) If the entity is forced or is captured, {@code true} is returned, otherwise, the entity is
     *    passed along normal spawning handling.
     */
    @Override
    public boolean spawnEntity(net.minecraft.entity.Entity entity) {
        if (PhaseTracker.isEntitySpawnInvalid((Entity) entity)) {
            return true;
        }
        return canAddEntity(entity) && PhaseTracker.getInstance().spawnEntity(this, EntityUtil.fromNative(entity));
    }

    @Override
    public boolean forceSpawnEntity(Entity entity) {
        final net.minecraft.entity.Entity minecraftEntity = (net.minecraft.entity.Entity) entity;
        final int x = minecraftEntity.getPosition().getX();
        final int z = minecraftEntity.getPosition().getZ();
        return forceSpawnEntity(minecraftEntity, x >> 4, z >> 4);
    }

    private boolean forceSpawnEntity(net.minecraft.entity.Entity entity, int chunkX, int chunkZ) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer entityplayer = (EntityPlayer) entity;
            this.playerEntities.add(entityplayer);
            this.updateAllPlayersSleepingFlag();
        }

        if (entity instanceof EntityLightningBolt) {
            this.addWeatherEffect(entity);
            return true;
        }

        this.getChunk(chunkX, chunkZ).addEntity(entity);
        this.loadedEntityList.add(entity);
        this.onSpongeEntityAdded(entity);
        return true;
    }


    @Override
    public SpongeBlockSnapshot createSpongeBlockSnapshot(IBlockState state, IBlockState extended, BlockPos pos, BlockChangeFlag updateFlag) {
        this.builder.reset();
        this.builder.blockState((BlockState) state)
                .extendedState((BlockState) extended)
                .worldId(this.getUniqueId())
                .position(VecHelper.toVector3i(pos));
        Optional<UUID> creator = getCreator(pos.getX(), pos.getY(), pos.getZ());
        Optional<UUID> notifier = getNotifier(pos.getX(), pos.getY(), pos.getZ());
        if (creator.isPresent()) {
            this.builder.creator(creator.get());
        }
        if (notifier.isPresent()) {
            this.builder.notifier(notifier.get());
        }
        if (state.getBlock() instanceof ITileEntityProvider) {
            // We MUST only check to see if a TE exists to avoid creating a new one.
            final net.minecraft.tileentity.TileEntity te = this.getChunk(pos).getTileEntity(pos, net.minecraft.world.chunk.Chunk.EnumCreateEntityType.CHECK);
            if (te != null) {
                TileEntity tile = (TileEntity) te;
                for (DataManipulator<?, ?> manipulator : ((IMixinCustomDataHolder) tile).getCustomManipulators()) {
                    this.builder.add(manipulator);
                }
                NBTTagCompound nbt = new NBTTagCompound();
                // Some mods like OpenComputers assert if attempting to save robot while moving
                try {
                    te.writeToNBT(nbt);
                    this.builder.unsafeNbt(nbt);
                }
                catch(Throwable t) {
                    // ignore
                }
            }
        }
        this.builder.flag(updateFlag);
        return new SpongeBlockSnapshot(this.builder);
    }

    /**
     * @author gabizou - September 10th, 2016
     * @author gabizou - September 21st, 2017 - Update for PhaseContext refactor.
     * @reason Due to the amount of changes, and to ensure that Forge's events are being properly
     * thrown, we must overwrite to have our hooks in place where we need them to be and when.
     * Likewise, since the event context is very ambiguously created, we may have an entity
     * coming in, or no entity, the explosion must always have a "source" in some context.
     *
     * @param entityIn The entity that caused the explosion
     * @param x The x position
     * @param y The y position
     * @param z The z position
     * @param strength The strength of the explosion, determines what blocks can be destroyed
     * @param isFlaming Whether fire will be caused from the explosion
     * @param isSmoking Whether blocks will break
     * @return The explosion
     */
    @Overwrite
    public Explosion newExplosion(@Nullable net.minecraft.entity.Entity entityIn, double x, double y, double z, float strength, boolean isFlaming,
            boolean isSmoking) {
        Explosion explosion = new Explosion((WorldServer) (Object) this, entityIn, x, y, z, strength, isFlaming, isSmoking);

        // Sponge Start - all the remaining behavior is in triggerInternalExplosion().
        explosion = triggerInternalExplosion((org.spongepowered.api.world.explosion.Explosion) explosion, e -> GeneralPhase.State.EXPLOSION
                .createPhaseContext()
                .explosion(e)
                .potentialExplosionSource((WorldServer) (Object) this, entityIn));
        // Sponge End
        return explosion;
    }

    /**
     * @author gabizou - August 4th, 2016
     * @author blood - May 11th, 2017 - Forces chunk requests if TE is ticking.
     * @reason Rewrites the check to be inlined to {@link IMixinBlockPos}.
     *
     * @param pos The position
     * @return The block state at the desired position
     */
    @Override
    public IBlockState getBlockState(BlockPos pos) {
        // Sponge - Replace with inlined method
        // if (this.isOutsideBuildHeight(pos)) // Vanilla
        if (((IMixinBlockPos) pos).isInvalidYPosition()) {
            // Sponge end
            return Blocks.AIR.getDefaultState();
        } else {
            // ExtraUtilities 2 expects to get the proper chunk while mining or it gets stuck in infinite loop
            // TODO add TE config to disable/enable chunk loads
            final boolean forceChunkRequests = this.mixinChunkProviderServer.getForceChunkRequests();
            final PhaseTracker phaseTracker = PhaseTracker.getInstance();
            final IPhaseState<?> currentState = phaseTracker.getCurrentState();
            if (currentState == TickPhase.Tick.TILE_ENTITY) {
                ((IMixinChunkProviderServer) this.getChunkProvider()).setForceChunkRequests(true);
            }
            net.minecraft.world.chunk.Chunk chunk = this.getChunk(pos);
            this.mixinChunkProviderServer.setForceChunkRequests(forceChunkRequests);
            return chunk.getBlockState(pos);
        }
    }

    /**
     * @author gabizou - August 4th, 2016
     * @reason Overrides the same method from MixinWorld_Lighting that redirects
     * {@link #isAreaLoaded(BlockPos, int, boolean)} to simplify the check to
     * whether the chunk's neighbors are loaded. Since the passed radius is always
     * 17, the check is simply checking for whether neighboring chunks are loaded
     * properly.
     *
     * @param thisWorld This world
     * @param pos The block position to check light for
     * @param radius The radius, always 17
     * @param allowEmtpy Whether to allow empty chunks, always false
     * @param lightType The light type
     * @param samePosition The block position to check light for, again.
     * @return True if the chunk is loaded and neighbors are loaded
     */
    @Override
    public boolean spongeIsAreaLoadedForCheckingLight(World thisWorld, BlockPos pos, int radius, boolean allowEmtpy, EnumLightType lightType,
            BlockPos samePosition) {
        final Chunk chunk = this.mixinChunkProviderServer.getLoadedChunkWithoutMarkingActive(pos.getX() >> 4, pos.getZ() >> 4);
        return !(chunk == null || !((IMixinChunk) chunk).areNeighborsLoaded());
    }

    /**
     * @author gabizou - April 8th, 2016
     *
     * Instead of providing chunks which has potential chunk loads,
     * we simply get the chunk directly from the chunk provider, if it is loaded
     * and return the light value accordingly.
     *
     * @param pos The block position
     * @return The light at the desired block position
     */
    @Override
    public int getLight(BlockPos pos) {
        if (pos.getY() < 0) {
            return 0;
        } else {
            if (pos.getY() >= 256) {
                pos = new BlockPos(pos.getX(), 255, pos.getZ());
            }
            // Sponge Start - Use our hook to get the chunk only if it is loaded
            // return this.getChunk(pos).getLightSubtracted(pos, 0);
            final Chunk chunk = this.mixinChunkProviderServer.getLoadedChunkWithoutMarkingActive(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk == null ? 0 : chunk.getLightSubtracted(pos, 0);
            // Sponge End
        }
    }

    /**
     * @author gabizou - April 8th, 2016
     *
     * @reason Rewrites the chunk accessor to get only a chunk if it is loaded.
     * This avoids loading chunks from file or generating new chunks
     * if the chunk didn't exist, when the only function of this method is
     * to get the light for the given block position.
     *
     * @param pos The block position
     * @param checkNeighbors Whether to check neighboring block lighting
     * @return The light value at the block position, if the chunk is loaded
     */
    @Override
    public int getLight(BlockPos pos, boolean checkNeighbors) {
        if (((IMixinBlockPos) pos).isValidXZPosition()) { // Sponge - Replace with inlined method
            if (checkNeighbors && this.getBlockState(pos).useNeighborBrightness()) {
                int i1 = this.getLight(pos.up(), false);
                int i = this.getLight(pos.east(), false);
                int j = this.getLight(pos.west(), false);
                int k = this.getLight(pos.south(), false);
                int l = this.getLight(pos.north(), false);

                if (i > i1) {
                    i1 = i;
                }

                if (j > i1) {
                    i1 = j;
                }

                if (k > i1) {
                    i1 = k;
                }

                if (l > i1) {
                    i1 = l;
                }

                return i1;
            } else if (pos.getY() < 0) {
                return 0;
            } else {
                if (pos.getY() >= 256) {
                    pos = new BlockPos(pos.getX(), 255, pos.getZ());
                }

                // Sponge - Gets only loaded chunks, unloaded chunks will not get loaded to check lighting
                // Chunk chunk = this.getChunk(pos);
                // return chunk.getLightSubtracted(pos, this.skylightSubtracted);
                final Chunk chunk = this.mixinChunkProviderServer.getLoadedChunkWithoutMarkingActive(pos.getX() >> 4, pos.getZ() >> 4);
                return chunk == null ? 0 : chunk.getLightSubtracted(pos, this.getSkylightSubtracted());
                // Sponge End
            }
        } else {
            return 15;
        }
    }

    /**
     * @author gabizou - August 4th, 2016
     * @reason Rewrites the check to be inlined to {@link IMixinBlockPos}.
     *
     * @param type The type of sky lighting
     * @param pos The position
     * @return The light for the defined sky type and block position
     */
    @Override
    public int getLightFor(EnumLightType type, BlockPos pos) {
        if (pos.getY() < 0) {
            pos = new BlockPos(pos.getX(), 0, pos.getZ());
        }

        // Sponge Start - Replace with inlined method to check
        // if (!this.isValid(pos)) // vanilla
        if (!((IMixinBlockPos) pos).isValidPosition()) {
            // Sponge End
            return type.defaultLightValue;
        } else {
            Chunk chunk = this.mixinChunkProviderServer.getLoadedChunkWithoutMarkingActive(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                return type.defaultLightValue;
            }
            return chunk.getLightFor(type, pos);
        }
    }

    @Override
    public boolean isLightLevel(Chunk chunk, BlockPos pos, int level) {
        if (((IMixinBlockPos) pos).isValidPosition()) {
            if (this.getBlockState(pos).useNeighborBrightness()) {
                if (this.getLight(pos.up(), false) >= level) {
                    return true;
                }
                if (this.getLight(pos.east(), false) >= level) {
                    return true;
                }
                if (this.getLight(pos.west(), false) >= level) {
                    return true;
                }
                if (this.getLight(pos.south(), false) >= level) {
                    return true;
                }
                if (this.getLight(pos.north(), false) >= level) {
                    return true;
                }
                return false;
            } else {
                if (pos.getY() >= 256) {
                    pos = new BlockPos(pos.getX(), 255, pos.getZ());
                }

                return chunk.getLightSubtracted(pos, this.getSkylightSubtracted()) >= level;
            }
        } else {
            return true;
        }
    }

    /**
     * @author amaranth - April 25th, 2016
     * @reason Avoid 25 chunk map lookups per entity per tick by using neighbor pointers
     *
     * @param xStart X block start coordinate
     * @param yStart Y block start coordinate
     * @param zStart Z block start coordinate
     * @param xEnd X block end coordinate
     * @param yEnd Y block end coordinate
     * @param zEnd Z block end coordinate
     * @param allowEmpty Whether empty chunks should be accepted
     * @return If the chunks for the area are loaded
     */
    @Override
    public boolean isAreaLoaded(int xStart, int yStart, int zStart, int xEnd, int yEnd, int zEnd, boolean allowEmpty) {
        if (yEnd < 0 || yStart > 255) {
            return false;
        }

        xStart = xStart >> 4;
        zStart = zStart >> 4;
        xEnd = xEnd >> 4;
        zEnd = zEnd >> 4;

        net.minecraft.world.chunk.Chunk base = this.mixinChunkProviderServer.getLoadedChunkWithoutMarkingActive(xStart, zStart);
        if (base == null) {
            return false;
        }

        IMixinChunk currentColumn = (IMixinChunk) base;
        for (int i = xStart; i <= xEnd; i++) {
            if (currentColumn == null) {
                return false;
            }

            IMixinChunk currentRow = currentColumn;
            for (int j = zStart; j <= zEnd; j++) {
                if (currentRow == null) {
                    return false;
                }

                if (!allowEmpty && ((net.minecraft.world.chunk.Chunk) currentRow).isEmpty()) {
                    return false;
                }

                currentRow = (IMixinChunk) currentRow.getNeighborChunk(1);
            }

            currentColumn = (IMixinChunk) currentColumn.getNeighborChunk(2);
        }

        return true;
    }

    @Override
    public WorldStorage getWorldStorage() {
        return (WorldStorage) ((WorldServer) (Object) this).getChunkProvider();
    }

    @Override
    public PortalAgent getPortalAgent() {
        return (PortalAgent) this.worldTeleporter;
    }

    @Redirect(method = "canAddEntity", at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", remap = false))
    private void onCanAddEntityLogWarn(Logger logger, String message, Object param1, Object param2) {
        // don't log anything to avoid useless spam
    }

    /**
     * @author blood - October 3rd, 2017
     * @reason Rewrites the check to avoid loading chunks.
     *
     * @param x The chunk x position
     * @param z The chunk z position
     * @param allowEmpty Whether empty chunks are allowed
     * @return If chunk is loaded
     */
    @Overwrite
    public boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        final IMixinChunk spongeChunk = (IMixinChunk) this.mixinChunkProviderServer.getLoadedChunkWithoutMarkingActive(x, z);
        return spongeChunk != null && (!spongeChunk.isQueuedForUnload() || spongeChunk.isPersistedChunk());
    }

    @Override
    public void markChunkDirty(BlockPos pos, net.minecraft.tileentity.TileEntity unusedTileEntity)
    {
        if (unusedTileEntity == null) {
            super.markChunkDirty(pos, unusedTileEntity);
            return;
        }
        final IMixinTileEntity spongeTileEntity = (IMixinTileEntity) unusedTileEntity;
        final IMixinChunk chunk = spongeTileEntity.getActiveChunk();
        if (chunk != null) {
            chunk.markChunkDirty();
        }
    }

    /**************************** TIMINGS ***************************************/
    /*
    The remaining of these overridden methods are all injectors into World#updateEntities() to where
    the exact fine tuning of where the methods are invoked, the call stack is precisely emulated as
    if this were an overwrite. The injections themselves are sensitive in some regards, but mostly
    will remain just fine.
     */


    @Override
    public void startEntityGlobalTimings() {
        this.timings.entityTick.startTiming();
        co.aikar.timings.TimingHistory.entityTicks += this.loadedEntityList.size();
    }

    @Override
    public void stopTimingForWeatherEntityTickCrash(net.minecraft.entity.Entity updatingEntity) {
        EntityUtil.toMixin(updatingEntity).getTimingsHandler().stopTiming();
    }

    @Override
    public void stopEntityTickTimingStartEntityRemovalTiming() {
        this.timings.entityTick.stopTiming();
        this.timings.entityRemoval.startTiming();
    }

    @Override
    public void stopEntityRemovalTiming() {
        this.timings.entityRemoval.stopTiming();
    }

    @Override
    public void startEntityTickTiming() {
        this.timings.entityTick.startTiming();
    }

    @Override
    public void stopTimingTickEntityCrash(net.minecraft.entity.Entity updatingEntity) {
        EntityUtil.toMixin(updatingEntity).getTimingsHandler().stopTiming();
    }

    @Override
    public void stopEntityTickSectionBeforeRemove() {
       this.timings.entityTick.stopTiming();
    }

    @Override
    public void startEntityRemovalTick() {
        this.timings.entityRemoval.startTiming();
    }

    @Override
    public void startTileTickTimer() {
        this.timings.tileEntityTick.startTiming();
    }

    @Override
    public void stopTimingTickTileEntityCrash(net.minecraft.tileentity.TileEntity updatingTileEntity) {
        ((IMixinTileEntity) updatingTileEntity).getTimingsHandler().stopTiming();
    }

    @Override
    public void stopTileEntityAndStartRemoval() {
        this.timings.tileEntityTick.stopTiming();
        this.timings.tileEntityRemoval.startTiming();
    }

    @Override
    public void stopTileEntityRemovelInWhile() {
        this.timings.tileEntityRemoval.stopTiming();
    }

    @Override
    public void startPendingTileEntityTimings() {
        this.timings.tileEntityPending.startTiming();
    }

    @Override
    public void endPendingTileEntities() {
        this.timings.tileEntityPending.stopTiming();
        TimingHistory.tileEntityTicks += this.loadedTileEntityList.size();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE_STRING", target = PROFILER_ESS, args = "ldc=tickPending") )
    private void onBeginTickBlockUpdate(CallbackInfo ci) {
        this.timings.scheduledBlocks.startTiming();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE_STRING", target = PROFILER_ESS, args = "ldc=tickBlocks") )
    private void onAfterTickBlockUpdate(CallbackInfo ci) {
        this.timings.scheduledBlocks.stopTiming();
        this.timings.updateBlocks.startTiming();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE_STRING", target = PROFILER_ESS, args = "ldc=chunkMap") )
    private void onBeginUpdateBlocks(CallbackInfo ci) {
        this.timings.updateBlocks.stopTiming();
        this.timings.doChunkMap.startTiming();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE_STRING", target = PROFILER_ESS, args = "ldc=village") )
    private void onBeginUpdateVillage(CallbackInfo ci) {
        this.timings.doChunkMap.stopTiming();
        this.timings.doVillages.startTiming();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE_STRING", target = PROFILER_ESS, args = "ldc=portalForcer"))
    private void onBeginUpdatePortal(CallbackInfo ci) {
        this.timings.doVillages.stopTiming();
        this.timings.doPortalForcer.startTiming();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/Profiler;endSection()V"))
    private void onEndUpdatePortal(CallbackInfo ci) {
        this.timings.doPortalForcer.stopTiming();
    }

    /**
     * Seriously, this was stupid.
     */
    @Redirect(method = "tickUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/BlockPos;add(III)Lnet/minecraft/util/math/BlockPos;"))
    private BlockPos redirectNeedlessBlockPosObjectCreation(BlockPos pos, int x, int y, int z) {
        return pos;
    }

    @Redirect(method = "tickUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;scheduleUpdate(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;I)V"))
    private void redirectDontRescheduleBlockUpdates(WorldServer worldServer, BlockPos pos, Block blockIn, int delay) {
    }

    // TIMINGS
    @Inject(method = "tickUpdates", at = @At(value = "INVOKE_STRING", target = PROFILER_SS, args = "ldc=cleaning"))
    private void onTickUpdatesCleanup(boolean flag, CallbackInfoReturnable<Boolean> cir) {
        this.timings.scheduledBlocksCleanup.startTiming();
    }

    @Inject(method = "tickUpdates", at = @At(value = "INVOKE_STRING", target = PROFILER_SS, args = "ldc=ticking"))
    private void onTickUpdatesTickingStart(boolean flag, CallbackInfoReturnable<Boolean> cir) {
        this.timings.scheduledBlocksCleanup.stopTiming();
        this.timings.scheduledBlocksTicking.startTiming();
    }

    @Inject(method = "tickUpdates", at = @At("RETURN"))
    private void onTickUpdatesTickingEnd(CallbackInfoReturnable<Boolean> cir) {
        this.timings.scheduledBlocksTicking.stopTiming();
    }

    @Override
    public WorldTimingsHandler getTimingsHandler() {
        return this.timings;
    }

    /**************************** EFFECT ****************************************/


    @Inject(method = "updateWeather", at = @At(value = "FIELD", target = "Lnet/minecraft/world/WorldServer;prevRainingStrength:F"), cancellable = true)
    private void onAccessPreviousRain(CallbackInfo ci) {
        final Weather weather = getWeather();
        int duration = (int) getRemainingDuration();
        if (!weather.equals(this.prevWeather) && duration > 0) {
            try (StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
                frame.pushCause(this);
                final ChangeWorldWeatherEvent event = SpongeEventFactory.createChangeWorldWeatherEvent(frame.getCurrentCause(), duration, duration,
                        weather, weather, this.prevWeather, this);
                if (Sponge.getEventManager().post(event)) {
                    this.setWeather(this.prevWeather);
                    this.prevWeather = getWeather();
                    ci.cancel();
                } else {
                    if (!weather.equals(event.getWeather()) || duration != event.getDuration()) {
                        this.setWeather(event.getWeather(), event.getDuration());
                        this.weatherStartTime = this.worldInfo.getWorldTotalTime();
                    } else {
                        this.prevWeather = event.getWeather();
                    }
                }
            }
        }
    }



    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("Name", this.worldInfo.getWorldName())
                .add("DimensionId", ((IMixinWorldServer) this).getDimensionId())
                .add("DimensionType", ((org.spongepowered.api.world.DimensionType) (Object) this.dimension.getType()).getKey())
                .add("DimensionTypeId", this.dimension.getType().getId())
                .toString();
    }
}