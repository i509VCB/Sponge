package org.spongepowered.common.data.processor.entity;

import com.google.common.base.Optional;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldSettings;
import org.spongepowered.api.data.DataHolder;
import org.spongepowered.api.data.DataPriority;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.manipulator.entity.GameModeData;
import org.spongepowered.api.entity.player.gamemode.GameMode;
import org.spongepowered.api.service.persistence.InvalidDataException;
import org.spongepowered.common.data.SpongeDataProcessor;
import org.spongepowered.common.data.manipulator.entity.SpongeGameModeData;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.spongepowered.common.data.DataTransactionBuilder.builder;
import static org.spongepowered.common.data.DataTransactionBuilder.errorData;
import static org.spongepowered.common.data.DataTransactionBuilder.fail;
import static org.spongepowered.common.data.util.DataUtil.checkDataExists;

public class SpongeGameModeDataProcessor implements SpongeDataProcessor<GameModeData> {
    @Override
    public Optional<GameModeData> getFrom(DataHolder dataHolder) {
        if (!(dataHolder instanceof EntityPlayerMP)) {
            return Optional.absent();
        } else {
            final GameModeData data = create();
            return fillData(dataHolder, data, DataPriority.DATA_HOLDER);
        }
    }

    @Override
    public Optional<GameModeData> fillData(DataHolder dataHolder, GameModeData manipulator, DataPriority priority) {
        if (!(dataHolder instanceof EntityPlayerMP)) {
            return Optional.absent();
        }
        switch (checkNotNull(priority)) {
            case DATA_HOLDER:
            case PRE_MERGE:
                manipulator.setGameMode((GameMode)(Object)((EntityPlayerMP) dataHolder).theItemInWorldManager.getGameType());
                return Optional.of(manipulator);
            default:
                return Optional.of(manipulator);
        }
    }

    @Override
    public DataTransactionResult setData(DataHolder dataHolder, GameModeData manipulator, DataPriority priority) {
        if (!(dataHolder instanceof EntityPlayerMP)) {
            return fail(manipulator);
        }
        switch (checkNotNull(priority)) {
            case DATA_HOLDER:
            case PRE_MERGE:
                return builder().reject(manipulator).result(DataTransactionResult.Type.SUCCESS).build();
            case DATA_MANIPULATOR:
            case POST_MERGE:
                final GameMode oldGameMode = (GameMode) (Object)((EntityPlayerMP) dataHolder).theItemInWorldManager.getGameType();
                final GameModeData oldData = create().setGameMode(oldGameMode);
                ((EntityPlayerMP) dataHolder).setGameType((WorldSettings.GameType)(Object)manipulator.getGameMode());
                return builder().replace(oldData).result(DataTransactionResult.Type.SUCCESS).build();
            default:
                return errorData(manipulator);
        }
    }

    @Override
    public boolean remove(DataHolder dataHolder) {
        return false;
    }

    @Override
    public Optional<GameModeData> build(DataView container) throws InvalidDataException {
        checkDataExists(container, SpongeGameModeData.GAME_MODE);
        final GameMode gameMode = (GameMode) container.get(SpongeGameModeData.GAME_MODE).get();
        return Optional.of(create().setGameMode(gameMode));
    }

    @Override
    public GameModeData create() {
        return new SpongeGameModeData();
    }

    @Override
    public Optional<GameModeData> createFrom(DataHolder dataHolder) {
        if (!(dataHolder instanceof EntityPlayerMP)) {
            return Optional.absent();
        } else {
            final GameModeData data = create();
            return fillData(dataHolder, data, DataPriority.DATA_HOLDER);
        }
    }
}
