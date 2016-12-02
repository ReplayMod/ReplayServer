package com.replaymod.replayserver;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.io.ReplayInputStream;
import com.replaymod.replaystudio.replay.ReplayFile;
import org.spacehq.mc.protocol.data.game.values.entity.player.GameMode;
import org.spacehq.mc.protocol.packet.ingame.server.*;
import org.spacehq.mc.protocol.packet.ingame.server.entity.player.*;
import org.spacehq.mc.protocol.packet.ingame.server.window.ServerCloseWindowPacket;
import org.spacehq.mc.protocol.packet.ingame.server.window.ServerOpenWindowPacket;
import org.spacehq.mc.protocol.packet.ingame.server.window.ServerSetSlotPacket;
import org.spacehq.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerNotifyClientPacket;
import org.spacehq.mc.protocol.packet.ingame.server.world.ServerOpenTileEntityEditorPacket;
import org.spacehq.packetlib.packet.Packet;

import java.io.IOException;
import java.util.Set;

public class ReplaySession implements IReplaySession {
    /**
     * Packets that are always filtered from the replay.
     */
    private static final Set<Class> BAD_PACKETS = Sets.newHashSet(
            ServerUpdateHealthPacket.class,
            ServerOpenWindowPacket.class,
            ServerCloseWindowPacket.class,
            ServerSetSlotPacket.class,
            ServerWindowItemsPacket.class,
            ServerOpenTileEntityEditorPacket.class,
            ServerStatisticsPacket.class,
            ServerSetExperiencePacket.class,
            ServerUpdateHealthPacket.class,
            ServerChangeHeldItemPacket.class,
            ServerSwitchCameraPacket.class,
            ServerPlayerAbilitiesPacket.class,
            ServerTitlePacket.class
    );

    private final ReplayUser user;
    private final ReplayFile replayFile;

    private ReplayInputStream inputStream;

    private double speed = 1;
    private boolean paused;

    /**
     * Last timestamp in milliseconds the {@link #process(long)} method was called.
     * Invalid while {@link #paused}.
     */
    private long nowRealTime;

    /**
     * Timestamp when the replay has started, scaled with the current speed.
     * This timestamp changes whenever the speed is changed or when the replay is unpaused.
     * While not paused, the current replay time can be calculated as (nowRealTime - scaledStartTime) * speed
     * Invalid while {@link #paused}.
     */
    private long scaledStartTime = System.currentTimeMillis();

    /**
     * Current replay time.
     * Valid regardless of {@link #paused}.
     */
    private int nowReplayTime;

    /**
     * The next packet to be sent.
     */
    private PacketData nextPacket;

    /**
     * Whether the world has been loaded (and the user is no longer stuck in a dirt screen).
     */
    private boolean hasWorldLoaded;

    public ReplaySession(ReplayUser user, ReplayFile replayFile) {
        this.user = user;
        this.replayFile = replayFile;
    }

    /**
     * Resets the scaled start time, so that now replay time has passed since {@link #nowRealTime}.
     */
    private void resetScaledStartTime() {
        this.scaledStartTime = (long) (nowRealTime - nowReplayTime / speed);
    }

    @Override
    public IUser getUser() {
        return user;
    }

    @Override
    public ReplayFile getReplayFile() {
        return replayFile;
    }

    @Override
    public int getTime() {
        return nowReplayTime;
    }

    @Override
    public void setTime(int time, boolean compact) {

    }

    @Override
    public double getSpeed() {
        return speed;
    }

    @Override
    public void setSpeed(double speed) {
        Preconditions.checkArgument(speed > 0, "Speed must be positive");
        this.speed = speed;
        if (!paused) {
            resetScaledStartTime();
        }
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public void setPaused(boolean paused) {
        if (this.paused ^ paused) {
            if (!paused) {
                nowRealTime = System.currentTimeMillis();
                resetScaledStartTime();
            }
            this.paused = paused;
        }
    }

    protected void close() throws IOException {
        replayFile.close();
    }

    /**
     * Update the current time and send packets accordingly.
     * @return The time in milliseconds until this method should be called again, or 0 if the replay is paused
     */
    protected long process(long now) throws IOException {
        if (paused) {
            return 0;
        }

        // Update current time
        nowRealTime = now;
        int targetReplayTime = (int) ((nowRealTime - scaledStartTime) * speed);

        if (targetReplayTime < nowReplayTime) {
            // Need to restart replay to go backwards in time
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
                nextPacket = null;
            }
        }

        while (true) {
            if (nextPacket == null) {
                if (inputStream == null) {
                    inputStream = replayFile.getPacketData();
                }
                nextPacket = inputStream.readPacket();
                if (nextPacket == null) {
                    // Reached end of replay
                    // TODO event
                    return 0;
                }
            }
            if (nextPacket.getTime() <= targetReplayTime) {
                processPacket(nextPacket.getPacket());
                nextPacket = null;
            } else {
                break;
            }
        }

        nowReplayTime = targetReplayTime;

        return Math.max((long) ((nowReplayTime - nextPacket.getTime()) / speed), 1);
    }

    private void processPacket(Packet packet) {
        if (BAD_PACKETS.contains(packet.getClass())) {
            return;
        }

        if (packet instanceof ServerResourcePackSendPacket) {
            // TODO
        }

        if (packet instanceof ServerJoinGamePacket) {
            ServerJoinGamePacket p = (ServerJoinGamePacket) packet;
            // Change entity id to invalid value and force gamemode to spectator
            packet = new ServerJoinGamePacket(-1789435, p.getHardcore(), GameMode.SPECTATOR, p.getDimension(),
                    p.getDifficulty(), p.getMaxPlayers(), p.getWorldType(), p.getReducedDebugInfo());
        }

        if (packet instanceof ServerRespawnPacket) {
            ServerRespawnPacket p = (ServerRespawnPacket) packet;
            // Force gamemode to spectator
            packet = new ServerRespawnPacket(p.getDimension(), p.getDifficulty(), GameMode.SPECTATOR, p.getWorldType());
        }

        if (packet instanceof ServerPlayerPositionRotationPacket) {
            hasWorldLoaded = true;
        }

        if (packet instanceof ServerNotifyClientPacket) {
            switch (((ServerNotifyClientPacket) packet).getNotification()) {
                case START_RAIN:
                case STOP_RAIN:
                case RAIN_STRENGTH:
                case THUNDER_STRENGTH:
                    break;
                default:
                    return; // Bed message, change gamemode, etc.
            }
        }

        user.sendPacket(packet);
    }
}
