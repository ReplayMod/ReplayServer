package com.replaymod.replayserver;

import com.replaymod.replaystudio.replay.ReplayFile;

/**
 * Given a unique key (or name), instances of this interface provide the replay associated with that id.
 */
public interface IReplayDatabase {
    /**
     * Returns a replay file given its unique id.
     * The returned replay file may be readonly and must support all methods until {@link ReplayFile#close()} is called.
     * If the replay file cannot be found, {@code null} shall be returned and the user shall be kicked.
     * @param id Unique id of the replay file
     * @param user The connecting user
     * @return The replay file
     */
    ReplayFile getReplayFile(IUser user, String id);
}
