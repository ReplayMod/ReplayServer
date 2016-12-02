package com.replaymod.replayserver;

import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.util.Location;
import org.spacehq.mc.protocol.MinecraftConstants;
import org.spacehq.mc.protocol.data.game.values.MessageType;
import org.spacehq.mc.protocol.data.game.values.PlayerListEntry;
import org.spacehq.mc.protocol.data.game.values.PlayerListEntryAction;
import org.spacehq.mc.protocol.data.game.values.entity.player.GameMode;
import org.spacehq.mc.protocol.data.message.Message;
import org.spacehq.mc.protocol.data.message.TextMessage;
import org.spacehq.mc.protocol.packet.ingame.server.ServerChatPacket;
import org.spacehq.mc.protocol.packet.ingame.server.ServerDisconnectPacket;
import org.spacehq.mc.protocol.packet.ingame.server.ServerPlayerListEntryPacket;
import org.spacehq.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import org.spacehq.packetlib.Session;
import org.spacehq.packetlib.event.session.DisconnectedEvent;
import org.spacehq.packetlib.event.session.PacketReceivedEvent;
import org.spacehq.packetlib.event.session.SessionAdapter;
import org.spacehq.packetlib.packet.Packet;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReplayUser extends SessionAdapter implements Runnable, IUser, Executor {
    public static final String SESSION_FLAG = "replay_user";
    private static final Logger logger = Logger.getLogger(ReplayUser.class.getName());

    private final ReplayServer server;
    private final Session session;
    private final Queue<Packet> packetQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Runnable> workerThreadQueue = new ConcurrentLinkedQueue<>();

    private Thread workerThread;
    private ReplaySession replaySession;

    public ReplayUser(ReplayServer server, Session session) {
        this.server = server;
        this.session = session;

        session.setFlag(SESSION_FLAG, this);
        session.addListener(this);
    }

    @Override
    public void sendPacket(Packet packet) {
        session.send(packet);
    }

    @Override
    public void sendMessage(Message message, MessageType messageType) {
        sendPacket(new ServerChatPacket(message, messageType));
    }

    @Override
    public void kick(Message message) {
        sendPacket(new ServerDisconnectPacket(message));
        session.disconnect(message.getFullText(), true);
    }

    @Override
    public IReplaySession getReplaySession() {
        return replaySession;
    }

    @Override
    public Session getSession() {
        return session;
    }

    @Override
    public boolean isConnected() {
        return session.isConnected();
    }

    @Override
    public void teleport(Location location) {
        sendPacket(new ServerPlayerPositionRotationPacket(
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()
        ));
    }

    @Override
    public void packetReceived(PacketReceivedEvent event) {
        packetQueue.offer(event.getPacket());
        workerThread.interrupt();
    }

    @Override
    public void disconnected(DisconnectedEvent event) {
        if (replaySession != null) {
            try {
                replaySession.close();
                replaySession = null;
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Error closing replay session:", t);
            }
        }
    }

    @Override
    public void run() {
        try {
            workerThread = Thread.currentThread();
            while (isConnected()) {
                // Handle incoming packets
                while (!packetQueue.isEmpty()) {
                    server.notifyPacketHandlers(this, packetQueue.poll());
                }

                // Handle queued tasks
                while (!workerThreadQueue.isEmpty()) {
                    workerThreadQueue.poll().run();
                }

                long sleep = 100;
                if (replaySession != null) {
                    // Send replay data
                    sleep = replaySession.process(System.currentTimeMillis());
                    if (sleep == 0) {
                        // Paused, sleep 100ms or until we get a new packet and are interrupted (which is more likely)
                        sleep = 100;
                    }
                }
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException ignored) {
                }
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Exception in user worker loop:", t);
            kick(new TextMessage("Internal Server Error"));
        }
    }

    @Override
    public void execute(Runnable runnable) {
        workerThreadQueue.offer(runnable);
        workerThread.interrupt();
    }

    protected void init(ReplayFile replayFile) {
        logger.fine("Initializing session for " + this + " with replay " + replayFile);
        replaySession = new ReplaySession(this, replayFile);

        // We need to send a player list entry for the spectator to be able to no-clip
        // This will inevitably show the spectator player as the last (?) player in the tablist, however there isn't any
        // sane way around this.
        sendPacket(new ServerPlayerListEntryPacket(PlayerListEntryAction.ADD_PLAYER, new PlayerListEntry[]{
                new PlayerListEntry(session.getFlag(MinecraftConstants.PROFILE_KEY), GameMode.SPECTATOR)
        }));
    }
}
