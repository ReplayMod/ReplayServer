package com.replaymod.replayserver.selectors;

import com.replaymod.replayserver.IReplayDatabase;
import com.replaymod.replayserver.IUser;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ZipReplayFile;
import com.replaymod.replaystudio.studio.ReplayStudio;
import org.spacehq.mc.protocol.data.message.TextMessage;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A replay database serving replay ids as files from a configurable folder.
 */
public class FileReplayDatabase implements IReplayDatabase {
    private static final Logger logger = Logger.getLogger(FileReplayDatabase.class.getName());

    private final File folder;

    public FileReplayDatabase() {
        folder = new File(System.getProperty("filereplaydatabase.folder", "replays"));
        if (!folder.exists()) {
            throw new IllegalArgumentException("Folder does not exists: " + folder.getAbsolutePath());
        }
    }

    @Override
    public ReplayFile getReplayFile(IUser user, String id) {
        ReplayStudio replayStudio = new ReplayStudio();
        replayStudio.setWrappingEnabled(false); // Server does not support wrapping

        File file = new File(folder, id);
        if (!file.exists() || !file.isFile()) {
            logger.info("User disconnected due to non existant replay: " + file.getAbsolutePath());
            user.kick(new TextMessage("No such replay: " + id));
            return null;
        }

        try {
            return new ZipReplayFile(replayStudio, new File(folder, id));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error creating replay file with id " + id, e);
            user.kick(new TextMessage("Replay file corrupted: " + id));
            return null;
        }
    }
}
