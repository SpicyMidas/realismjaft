package dev.agentbuddy;

import com.mojang.logging.LogUtils;
import dev.agentbuddy.entity.AgentEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

@Mod(AgentBuddy.MODID)
public class AgentBuddy {
    public static final String MODID = "agentbuddy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AgentBuddy(IEventBus modEventBus) {
        ModRegistries.register(modEventBus);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::addToCreativeTab);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModRegistries.AGENT.get(), AgentEntity.createAttributes().build());
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(ModRegistries.AGENT_ITEM);
    }
}
