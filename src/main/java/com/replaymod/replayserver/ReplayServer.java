package com.replaymod.replayserver;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.replaymod.replayserver.selectors.FileReplayDatabase;
import com.replaymod.replayserver.selectors.FixedReplaySelector;
import com.replaymod.replaystudio.replay.ReplayFile;
import org.spacehq.mc.protocol.MinecraftConstants;
import org.spacehq.mc.protocol.MinecraftProtocol;
import org.spacehq.mc.protocol.ServerLoginHandler;
import org.spacehq.mc.protocol.data.message.TextMessage;
import org.spacehq.mc.protocol.packet.ingame.client.ClientChatPacket;
import org.spacehq.packetlib.Server;
import org.spacehq.packetlib.SessionFactory;
import org.spacehq.packetlib.event.server.*;
import org.spacehq.packetlib.packet.Packet;
import org.spacehq.packetlib.packet.PacketProtocol;
import org.spacehq.packetlib.tcp.TcpSessionFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class ReplayServer extends Server implements ServerListener {
    public static void main(String[] args) throws InterruptedException, IllegalAccessException, InstantiationException, ClassNotFoundException {
        // TODO config
        String host = "localhost";
        int port = 25566;
        ReplayServer server = new ReplayServer(host, port, MinecraftProtocol.class, new TcpSessionFactory());
        server.bind();

        // TODO read stdin for commands
        Thread.sleep(Long.MAX_VALUE);
    }

    private final Logger logger = Logger.getLogger(getClass().getName());
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final List<IPacketHandler> packetHandlers = new ArrayList<>();

    public ReplayServer(String host, int port, Class<? extends PacketProtocol> protocol, SessionFactory factory) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        super(host, port, protocol, factory);

        // TODO config
        String selector = FixedReplaySelector.class.getName();
        String database = FileReplayDatabase.class.getName();
        List<String> packetHandlers = Collections.emptyList();
        // TODO create simple command handler

        IReplaySelector replaySelector = (IReplaySelector) Class.forName(selector).newInstance();
        IReplayDatabase replayDatabase = (IReplayDatabase) Class.forName(database).newInstance();
        for (String packetHandler : packetHandlers) {
            this.packetHandlers.add((IPacketHandler) Class.forName(packetHandler).newInstance());
        }

        // TODO config
        setGlobalFlag(MinecraftConstants.VERIFY_USERS_KEY, false);
        setGlobalFlag(MinecraftConstants.SERVER_COMPRESSION_THRESHOLD, 100);

        setGlobalFlag(MinecraftConstants.SERVER_LOGIN_HANDLER_KEY, (ServerLoginHandler) session -> {
            ReplayUser user = session.getFlag(ReplayUser.SESSION_FLAG);
            ListenableFuture<String> idFuture = replaySelector.getReplayId(user);
            idFuture.addListener(() -> {
                String id = Futures.getUnchecked(idFuture);
                logger.finer(() -> "Replay id for user " + user + " determined to be " + id);
                if (id == null) {
                    user.kick(new TextMessage("No such replay."));
                } else {
                    ReplayFile replayFile = replayDatabase.getReplayFile(user, id);
                    logger.finer(() -> "Replay for user " + user + " fetched from database: " + replayFile);
                    if (replayFile == null) {
                        user.kick(new TextMessage("Replay file not found."));
                    } else {
                        user.init(replayFile);
                    }
                }
            }, user);
        });

        addListener(this);
    }

    @Override
    public void serverBound(ServerBoundEvent event) {
        logger.info("Server bound");
    }

    @Override
    public void serverClosing(ServerClosingEvent event) {
        logger.info("Server closing");
    }

    @Override
    public void serverClosed(ServerClosedEvent event) {
        logger.info("Server closed");
    }

    @Override
    public void sessionAdded(SessionAddedEvent event) {
        logger.info("New session: " + event.getSession());
        threadPool.submit(new ReplayUser(this, event.getSession()));
    }

    @Override
    public void sessionRemoved(SessionRemovedEvent event) {
        logger.info("Session removed: " + event.getSession());
    }

    protected void notifyPacketHandlers(ReplayUser user, Packet packet) {
        for (IPacketHandler packetHandler : packetHandlers) {
            packetHandler.handleMessage(user, packet);
        }
        if (packet instanceof ClientChatPacket) {
            String message = ((ClientChatPacket) packet).getMessage();
            if (message.equals(".")) {
                user.getReplaySession().setPaused(!user.getReplaySession().isPaused());
            } else {
                user.getReplaySession().setSpeed(3);
            }
        }
    }
}
