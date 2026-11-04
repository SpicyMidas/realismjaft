package dev.agentbuddy;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(AgentBuddy.MODID)
public class AgentBuddy {
    public static final String MODID = "agentbuddy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AgentBuddy(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("agentbuddy common setup done");
    }
}
