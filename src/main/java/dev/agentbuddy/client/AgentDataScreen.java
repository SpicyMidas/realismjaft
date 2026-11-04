package dev.agentbuddy.client;

import dev.agentbuddy.network.AgentScreenData;
import java.util.UUID;

/** A screen that shows one agent and refreshes in place when the server sends new {@link AgentScreenData}. */
interface AgentDataScreen {
    UUID agentId();

    void update(AgentScreenData fresh);
}
