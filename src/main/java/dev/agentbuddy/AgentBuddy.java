package dev.agentbuddy;

import com.mojang.logging.LogUtils;
import dev.agentbuddy.entity.AgentEntity;
import dev.agentbuddy.network.ModNetwork;
import dev.agentbuddy.server.AgentService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(AgentBuddy.MODID)
public class AgentBuddy {
    public static final String MODID = "agentbuddy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AgentBuddy(IEventBus modEventBus) {
        ModRegistries.register(modEventBus);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::addToCreativeTab);
        modEventBus.addListener(ModNetwork::register);
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> AgentService.start(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> AgentService.stop());
        NeoForge.EVENT_BUS.addListener(this::onXpPickup);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModRegistries.AGENT.get(), AgentEntity.createAttributes().build());
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(ModRegistries.AGENT_ITEM);
    }

    /** Credits are earned from collected XP (AGENT_SPEC.md section 6); the player keeps the XP. */
    private void onXpPickup(PlayerXpEvent.PickupXp event) {
        if (event.getEntity() instanceof ServerPlayer player) AgentService.get().onXpPickup(player, event.getOrb().getValue());
    }
}
