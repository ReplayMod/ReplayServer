package com.replaymod.replayserver.selectors;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.replaymod.replayserver.IReplaySelector;
import com.replaymod.replayserver.IUser;

/**
 * A replay selector that always returns the same configurable replay.
 */
public class FixedReplaySelector implements IReplaySelector {
    private final String theId;

    public FixedReplaySelector() {
        theId = System.getProperty("fixedreplayselector.id", "replay.mcpr");
    }

    @Override
    public ListenableFuture<String> getReplayId(IUser user) {
        return Futures.immediateFuture(theId);
    }
}
