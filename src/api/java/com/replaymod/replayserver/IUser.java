package com.replaymod.replayserver;

import com.replaymod.replaystudio.util.Location;
import org.spacehq.mc.protocol.data.game.values.MessageType;
import org.spacehq.mc.protocol.data.message.Message;
import org.spacehq.packetlib.Session;
import org.spacehq.packetlib.packet.Packet;

/**
 * A user connected to the replay server.
 */
public interface IUser {
    /**
     * Send a packet to the user.
     * @param packet The packet to send
     */
    void sendPacket(Packet packet);

    /**
     * Sends a chat message to the user.
     * Equivalent to calling {@link #sendMessage(Message, MessageType)} with {@link MessageType#CHAT}.
     * @param message The message
     */
    default void sendMessage(Message message) {
        sendMessage(message, MessageType.CHAT);
    }

    /**
     * Sends a message to the user.
     * @param message The message
     */
    void sendMessage(Message message, MessageType messageType);

    /**
     * Kicks and disconnects the user.
     * @param message The kick message
     */
    void kick(Message message);

    /**
     * Teleports the user to the specified location.
     * @param location The location to teleport to
     */
    void teleport(Location location);

    /**
     * Returns the replay session this use is in.
     * @return The session, may be {@code null} if the user hasn't been assigned a replay yet
     */
    IReplaySession getReplaySession();

    /**
     * Returns the network session of this user.
     * Note: Listeners registered for the session object must never be blocking or interact with any not thread-safe
     * methods as they are called from the network thread.
     * @return The session
     */
    Session getSession();

    /**
     * Returns whether this user is still connected.
     * Once the user disconnects, this function will never return {@code true} again.
     * @return {@code true} if the user is connected, {@code false otherwise}
     */
    boolean isConnected();
}
