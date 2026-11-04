package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.Effect;
import dev.chraclette.skyanimations.render.Cutscene.Cue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Maps cutscene cues to vanilla sounds. Only you hear them. */
final class Sounds {

    private Sounds() {
    }

    static void play(Cue cue, Effect effect, float pitch, float volume) {
        Config.Data cfg = Config.get();
        if (!cfg.sound) return;
        if (!cfg.music && (cue == Cue.NOTE || cue == Cue.BELL)) return;
        float v = volume * cfg.volume;
        switch (cue) {
            case WHOOSH -> sound(SoundEvents.PLAYER_ATTACK_SWEEP, pitch, v * 0.7f);
            case THUMP -> sound(SoundEvents.WOOD_PLACE, pitch, v);
            case CHOP -> {
                sound(SoundEvents.PLAYER_ATTACK_STRONG, pitch, v);
                sound(SoundEvents.AXE_STRIP, pitch * 0.9f, v);
            }
            case SQUISH -> sound(SoundEvents.SLIME_SQUISH, pitch, v);
            case SPLIT -> sound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.4f, v * 0.45f);
            case MAGIC -> magic(effect, v);
            case REVEAL -> sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, pitch, v * 0.8f);
            case LEVELUP -> sound(SoundEvents.PLAYER_LEVELUP, pitch, v * 0.7f);
            case FIREWORK -> sound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, pitch, v);
            case TWINKLE -> sound(SoundEvents.FIREWORK_ROCKET_TWINKLE, pitch, v);
            case NOTE -> sound(SoundEvents.NOTE_BLOCK_PLING, pitch, v);
            case BELL -> sound(SoundEvents.NOTE_BLOCK_BELL, pitch, v);
            case TOTEM -> sound(SoundEvents.TOTEM_USE, pitch, v * 0.6f);
            case POP -> sound(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, v);
            case BUZZ -> sound(SoundEvents.SILVERFISH_AMBIENT, pitch, v);
            case SUCK -> sound(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, pitch, v);
            case SLURP -> sound(SoundEvents.GENERIC_DRINK, pitch, v);
            case RATTLE -> sound(SoundEvents.PISTON_EXTEND, pitch, v * 0.6f);
            case SPIT -> sound(SoundEvents.CHICKEN_EGG, pitch * 0.8f, v);
            case FEVER -> {
                sound(SoundEvents.FIRECHARGE_USE, 0.8f, v);
                sound(SoundEvents.BLAZE_SHOOT, 1.2f, v * 0.6f);
            }
            case FEVER_END -> sound(SoundEvents.FIRE_EXTINGUISH, 1.0f, v * 0.7f);
            case TOAST -> sound(SoundEvents.ITEM_PICKUP, pitch, v);
            case PAGE -> sound(SoundEvents.BOOK_PAGE_TURN, pitch, v);
            case SLAP -> {
                sound(SoundEvents.SLIME_BLOCK_PLACE, 1.2f, v);
                sound(SoundEvents.PLAYER_LEVELUP, 1.6f, v * 0.6f);
            }
            case PEST_ALERT -> {
                sound(SoundEvents.SILVERFISH_AMBIENT, 1.4f, v);
                sound(SoundEvents.NOTE_BLOCK_BIT, 1.6f, v * 0.6f);
            }
        }
    }

    private static void magic(Effect effect, float v) {
        switch (effect) {
            case ARCANE -> sound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, v);
            case GOLDEN -> sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, v);
            case DYE -> sound(SoundEvents.BREWING_STAND_BREW, 1.2f, v);
            case BLOOD -> sound(SoundEvents.WARDEN_HEARTBEAT, 1.0f, v);
            case VOID -> sound(SoundEvents.ENDERMAN_TELEPORT, 0.8f, v);
            case VENOM -> sound(SoundEvents.SPIDER_AMBIENT, 1.2f, v * 0.7f);
            case INFERNO -> sound(SoundEvents.BLAZE_SHOOT, 0.9f, v * 0.8f);
            case BONE -> sound(SoundEvents.SKELETON_AMBIENT, 0.8f, v * 0.8f);
            case HARVEST -> sound(SoundEvents.CROP_BREAK, 1.0f, v);
            case OCEAN -> sound(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.0f, v);
            case STARS -> sound(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.2f, v * 0.8f);
        }
    }

    /** Accepts both SoundEvent and Holder<SoundEvent> constants (the vanilla class mixes them). */
    private static void sound(Object sound, float pitch, float volume) {
        if (volume <= 0.001f) return;
        SoundEvent event;
        if (sound instanceof SoundEvent e) {
            event = e;
        } else if (sound instanceof Holder<?> h && h.value() instanceof SoundEvent e) {
            event = e;
        } else {
            return;
        }
        float p = Math.max(0.5f, Math.min(2.0f, pitch));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, p, Math.min(1.0f, volume)));
    }
}
