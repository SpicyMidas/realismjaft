package com.seba.reverie.registry;

import java.util.function.Supplier;

import com.seba.reverie.Reverie;
import com.seba.reverie.dream.DreamData;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Reverie.MOD_ID);

    public static final Supplier<AttachmentType<DreamData>> DREAM = ATTACHMENTS.register("dream_session",
            () -> AttachmentType.builder(DreamData::new).serialize(DreamData.CODEC).copyOnDeath().build());

    private ModAttachments() {
    }
}
