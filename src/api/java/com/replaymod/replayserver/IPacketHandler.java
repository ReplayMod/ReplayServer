package com.replaymod.replayserver;

import org.spacehq.packetlib.packet.Packet;

/**
 * Handles packets received from users.
 */
public interface IPacketHandler {
    /**
     * Handle a packet received from a user.
     * All packet handlers are called sequentially, one after another.
     * Be aware that performing blocking operations during this method will prevent the replay from being played
     * properly.
     * @param packet The packet
     */
    void handleMessage(IUser user, Packet packet);
}
