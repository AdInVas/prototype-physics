package net.adinvas.prototype_physics.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class RagdollModeChangeEvent extends Event {
    public enum Mode { SILENT, PRECISE }

    private final ServerPlayer player;
    private final Mode newMode;

    public RagdollModeChangeEvent(ServerPlayer player, Mode newMode) {
        this.player = player;
        this.newMode = newMode;
    }

    public ServerPlayer getPlayer() { return player; }
    public Mode getNewMode() { return newMode; }

    @Override
    public boolean isCancelable() { return true; }
}
