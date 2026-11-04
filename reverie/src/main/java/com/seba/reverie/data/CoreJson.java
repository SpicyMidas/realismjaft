package com.seba.reverie.data;

import com.google.gson.JsonObject;

/** Hand-written JSON for recipe types without a vanilla builder. */
final class CoreJson {
    private CoreJson() {
    }

    static void json(JsonGen gen) {
        JsonObject concentrated = new JsonObject();
        concentrated.addProperty("type", "reverie:bottle_returning_shapeless");
        concentrated.addProperty("category", "misc");
        concentrated.add("ingredients", JsonGen.array(JsonGen.item("reverie:melatonia_extract"), JsonGen.item("reverie:melatonia_extract"),
                JsonGen.item("reverie:melatonia_extract"), JsonGen.tag("c:dusts/glowstone")));
        concentrated.add("result", JsonGen.result("reverie:concentrated_melatonia_extract", 1));
        concentrated.addProperty("returned_bottles", 2);
        gen.put("data/reverie/recipe/concentrated_melatonia_extract.json", concentrated);
    }
}
