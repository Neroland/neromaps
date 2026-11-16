package za.co.neroland.neromaps.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neromaps.NeroMapsCommon;

/** The sound events of {@code sounds.json}, registered as variable-range events. */
public final class ModSounds {

    public static final RegistrationProvider<SoundEvent> SOUNDS =
            RegistrationProvider.get(Registries.SOUND_EVENT, NeroMapsCommon.MOD_ID);

    private static final Map<String, RegistryEntry<SoundEvent>> BY_KEY = new LinkedHashMap<>();

    static {
        for (String key : MapIds.SOUNDS) {
            BY_KEY.put(key, SOUNDS.register(key, k -> SoundEvent.createVariableRangeEvent(k.identifier())));
        }
    }

    private ModSounds() {
    }

    /** The registered event for a {@code sounds.json} key, or null. */
    public static @Nullable SoundEvent get(String key) {
        RegistryEntry<SoundEvent> e = BY_KEY.get(key);
        return e == null ? null : e.get();
    }

    /** Classload-forcing no-op. */
    public static void init() {
    }
}
