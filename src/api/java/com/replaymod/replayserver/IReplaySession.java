package com.replaymod.replayserver;

import com.replaymod.replaystudio.replay.ReplayFile;

/**
 * A replay session.
 */
public interface IReplaySession {
    /**
     * Returns the user in this session.
     * @return The user
     */
    IUser getUser();

    /**
     * Returns the replay file played in this session.
     * @return The replay file
     */
    ReplayFile getReplayFile();

    /**
     * Returns the timestamp in the replay.
     * @return Timestamp in milliseconds
     */
    int getTime();

    /**
     * Set the timestamp in the replay.
     *
     * Note that this method has to process all packets from the current timestamp to the target one or in case of
     * jumping backwards in time, every packet from the start to the target timestamp.
     *
     * To not transmit unnecessary world changes, the packets can first be compacted on the server side before sending.
     * This will however put significant load on the server as it has to retain a part of the replay in memory while
     * processing it (the world in particular and other misc. packets).
     *
     * To prevent the player from getting stuck inside a Downloading Terrain screen, this may jump further than actually
     * specified. The actual timestamp may subsequently be obtained by calling {@link #getTime()}.
     *
     * @param compact Whether packets should be compacted on the server side before sending
     */
    void setTime(int time, boolean compact);

    /**
     * Returns the speed at which the replay is played.
     * This value is ignored while the replay {@link #isPaused()}.
     * @return The speed, 1 being normal, 2 being twice as fast, 0.5 being half as fast
     */
    double getSpeed();

    /**
     * Sets the speed at which the replay is played.
     * This value is ignored while the replay {@link #isPaused()}.
     * @param speed The speed, 1 being normal, 2 being twice as fast, 0.5 being half as fast
     */
    void setSpeed(double speed);

    /**
     * Returns whether the playback is currently paused.
     * @return {@code true} if playback is paused, {@code false} otherwise
     */
    boolean isPaused();

    /**
     * Sets whether the playback should be paused.
     * @param paused {@code true} if playback should be paused, {@code false} otherwise
     */
    void setPaused(boolean paused);
}
