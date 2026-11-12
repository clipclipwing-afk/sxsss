package dev.carryledger;

import net.fabricmc.api.ClientModInitializer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** /apocalypse and /cmenu open the settings screen (on the next tick, after chat closes). */
public class ClarityMenu implements ClientModInitializer {

    static boolean open;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (open) {
                open = false;
                mc.gui.setScreen(new ClarityScreen());
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("apocalypse").executes(ctx -> {
                open = true;
                return 1;
            }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cmenu").executes(ctx -> {
                open = true;
                return 1;
            }));
        });
    }
}
