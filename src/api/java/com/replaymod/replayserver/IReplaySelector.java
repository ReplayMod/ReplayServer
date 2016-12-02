package com.replaymod.replayserver;

import com.google.common.util.concurrent.ListenableFuture;
import org.spacehq.mc.protocol.data.message.Message;

/**
 * Selects the replay to use for a particular session.
 */
public interface IReplaySelector {
    /**
     * Returns the unique id of the replay to be used for this session of the specified user.
     * If no replay id can be determined, the user shall be kicked using {@link IUser#kick(Message)} and the future shall
     * be resolved to {@code null}.
     * @param user The connected user
     * @return Future for the id of the replay
     */
    ListenableFuture<String> getReplayId(IUser user);
}
