package net.adinvas.prototype_physics;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.adinvas.prototype_physics.events.ModEvents;
import net.adinvas.prototype_physics.events.RagdollModeChangeEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ModCommands {

    @SubscribeEvent
    public static void onCommand(RegisterCommandsEvent event){
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("physicsdebug")
                        .requires(src -> src.hasPermission(2)) // OP level 2+
                        .then(Commands.literal("toggle")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    JbulletWorld manager = JbulletWorld.get(player.serverLevel());
                                    PlayerPhysics pp = manager.getPlayerPhys(player);

                                    if (pp == null) {
                                        ctx.getSource().sendFailure(Component.literal("No physics instance found for player."));
                                        return 0;
                                    }

                                    RagdollModeChangeEvent.Mode newMode = (pp.getMode() == PlayerPhysics.Mode.SILENT) ? RagdollModeChangeEvent.Mode.PRECISE : RagdollModeChangeEvent.Mode.SILENT;
                                    if (ModEvents.onRagdollModeChange(player, newMode)) {
                                        PlayerPhysics.Mode modemo = (pp.getMode() == PlayerPhysics.Mode.SILENT)?PlayerPhysics.Mode.PRECISE : PlayerPhysics.Mode.SILENT;
                                        pp.setMode(modemo);
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("Set physics mode to " + newMode), true);
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal("Mode change canceled by another mod."));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("info")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    JbulletWorld manager = JbulletWorld.get(player.serverLevel());
                                    PlayerPhysics pp = manager.getPlayerPhys(player);

                                    if (pp == null) {
                                        ctx.getSource().sendFailure(Component.literal("No physics data for player."));
                                        return 0;
                                    }

                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Mode: " + pp.getMode()
                                                    + " | Active parts: " + 0
                                                    + " | World bodies: " + manager.getWorld().getNumCollisionObjects()), false);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );
    }
}

