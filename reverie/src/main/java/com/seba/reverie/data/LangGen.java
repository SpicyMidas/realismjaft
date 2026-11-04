package com.seba.reverie.data;

import com.seba.reverie.Reverie;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

final class LangGen extends LanguageProvider {
    private final boolean italian;

    LangGen(PackOutput output, String locale) {
        super(output, Reverie.MOD_ID, locale);
        this.italian = locale.equals("it_it");
    }

    @Override
    protected void addTranslations() {
        Lang.all((key, en, it) -> add(key, italian ? it : en));
    }
}
