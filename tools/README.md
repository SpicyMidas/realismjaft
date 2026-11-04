Generators for the committed binary assets (run from the repo root, needs Python 3 + Pillow):

    python tools/agent_item_texture.py   src/main/resources/assets/agentbuddy/textures/item/agent.png   preview.png
    python tools/agent_entity_texture.py src/main/resources/assets/agentbuddy/textures/entity/agent.png preview.png
    python tools/empty_structure.py      src/main/resources/data/agentbuddy/structure/empty.nbt
    python tools/empty_structure.py      src/main/resources/data/agentbuddy/structure/corridor.nbt 5 6 20

The entity texture follows the box UV layout in `client/AgentModel.java`; both are placeholders until a Blockbench model replaces them.
