package za.co.neroland.neromaps.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataCompat;

import za.co.neroland.neromaps.NeroMapsCommon;
import za.co.neroland.neromaps.text.NameFilter;

/**
 * NeroMaps' one player-keyed store, {@code neromaps:players} (decision 008), read only through Core's
 * {@code SavedDataRecovery} (see {@link PlayerStore#get}). Keyed by UUID; each value is a {@link PlayerRecord}.
 *
 * <p>All mutation goes through the methods here, on the server thread, and each marks the store dirty. Erasure removes
 * the player's record <em>and</em> every grant to them and request from them in every other record, in one call.
 * A player's live position is never stored here or anywhere else.
 */
public final class MapPlayerState extends SavedData {

    /** Bumped on any incompatible change to the stored shape; older files are migrated on load. */
    public static final int SAVE_VERSION = 1;

    public static final Codec<MapPlayerState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("version", SAVE_VERSION).forGetter(s -> SAVE_VERSION),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerRecord.CODEC).optionalFieldOf("players", Map.of())
                    .forGetter(s -> s.players)
    ).apply(i, MapPlayerState::new));

    public static final SavedDataType<MapPlayerState> TYPE = SavedDataCompat.type(
            Identifier.fromNamespaceAndPath(NeroMapsCommon.MOD_ID, "players"), MapPlayerState::new, CODEC);

    /** The recovery label (logs, telemetry, backup file name). */
    public static final String STORE = "players";

    private final Map<UUID, PlayerRecord> players;
    private final int loadedVersion;

    public MapPlayerState() {
        this(SAVE_VERSION, Map.of());
    }

    private MapPlayerState(int version, Map<UUID, PlayerRecord> players) {
        this.loadedVersion = version;
        this.players = new HashMap<>(players);
        this.players.values().removeIf(PlayerRecord::isEmpty);
    }

    /** The schema version the data was read with (tests and migration). */
    public int loadedVersion() {
        return loadedVersion;
    }

    public void changed() {
        setDirty();
    }

    // --- records --------------------------------------------------------------------------------------------

    public Optional<PlayerRecord> find(UUID player) {
        return Optional.ofNullable(players.get(player));
    }

    /** The player's record, created on first use. */
    public PlayerRecord record(UUID player) {
        return players.computeIfAbsent(player, k -> new PlayerRecord());
    }

    public int size() {
        return players.size();
    }

    public void forEach(BiConsumer<UUID, PlayerRecord> visitor) {
        players.forEach(visitor);
    }

    // --- waypoints ------------------------------------------------------------------------------------------

    /** Why a waypoint change was refused, or OK. */
    public enum Result { OK, LIMIT, NAME_EMPTY, NAME_DENIED, NOT_FOUND }

    /** Adds a waypoint, assigning its id. {@code w.id()} is ignored. */
    public Result addWaypoint(UUID player, Waypoint w, int limit, String denyList, int[] idOut) {
        NameFilter.Verdict v = NameFilter.check(w.name(), denyList);
        if (v != NameFilter.Verdict.OK) {
            return v == NameFilter.Verdict.EMPTY ? Result.NAME_EMPTY : Result.NAME_DENIED;
        }
        PlayerRecord r = record(player);
        if (r.waypoints.size() >= limit) {
            return Result.LIMIT;
        }
        int id = r.nextId++;
        r.waypoints.add(new Waypoint(id, w.set(), w.name(), w.colour(), w.icon(), w.dimension(), w.x(), w.y(), w.z(),
                false, w.beam(), w.distanceLabel()));
        if (idOut != null && idOut.length > 0) {
            idOut[0] = id;
        }
        setDirty();
        return Result.OK;
    }

    /** Replaces the waypoint with {@code w.id()}; its shared flag is kept. */
    public Result updateWaypoint(UUID player, Waypoint w, String denyList) {
        NameFilter.Verdict v = NameFilter.check(w.name(), denyList);
        if (v != NameFilter.Verdict.OK) {
            return v == NameFilter.Verdict.EMPTY ? Result.NAME_EMPTY : Result.NAME_DENIED;
        }
        PlayerRecord r = players.get(player);
        if (r == null) {
            return Result.NOT_FOUND;
        }
        for (int i = 0; i < r.waypoints.size(); i++) {
            Waypoint old = r.waypoints.get(i);
            if (old.id() == w.id()) {
                r.waypoints.set(i, w.withShared(old.shared()));
                setDirty();
                return Result.OK;
            }
        }
        return Result.NOT_FOUND;
    }

    /** Deletes a waypoint and every grant that shared it. */
    public Result removeWaypoint(UUID player, int id) {
        PlayerRecord r = players.get(player);
        if (r == null || !r.waypoints.removeIf(w -> w.id() == id)) {
            return Result.NOT_FOUND;
        }
        r.grants.removeIf(g -> g.scope().waypointId() == id);
        setDirty();
        return Result.OK;
    }

    /** Records a death point (newest first, last three) when both the policy and the player allow it. */
    public boolean addDeath(UUID player, DeathPoint point, boolean policyAllows) {
        if (!policyAllows) {
            return false;
        }
        PlayerRecord existing = players.get(player);
        if (existing != null && !existing.deathWaypoints) {
            return false;
        }
        PlayerRecord r = record(player);
        r.deaths.add(0, point);
        while (r.deaths.size() > DeathPoint.KEEP) {
            r.deaths.remove(r.deaths.size() - 1);
        }
        setDirty();
        return true;
    }

    public boolean removeDeath(UUID player, int index) {
        PlayerRecord r = players.get(player);
        if (r == null || index < 0 || index >= r.deaths.size()) {
            return false;
        }
        r.deaths.remove(index);
        setDirty();
        return true;
    }

    // --- privacy --------------------------------------------------------------------------------------------

    /** Sets the death-waypoint toggle; turning it off also clears the stored death points. */
    public void setDeathWaypoints(UUID player, boolean on) {
        PlayerRecord r = record(player);
        r.deathWaypoints = on;
        if (!on) {
            r.deaths.clear();
        }
        setDirty();
    }

    /** Sets whether share offers reach this player; turning it off drops the pending ones. */
    public void setAcceptRequests(UUID player, boolean on) {
        PlayerRecord r = record(player);
        r.acceptRequests = on;
        if (!on) {
            r.incoming.clear();
        }
        setDirty();
    }

    // --- exploration ----------------------------------------------------------------------------------------

    public boolean markExplored(UUID player, String dimension, int chunkX, int chunkZ, int maxRegions) {
        boolean changed = record(player).explored.mark(dimension, chunkX, chunkZ, maxRegions);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    // --- consent (raw list access for ConsentService) -------------------------------------------------------

    List<Grant> grantsOf(UUID player) {
        PlayerRecord r = players.get(player);
        return r == null ? List.of() : r.grants;
    }

    List<ShareRequest> incomingOf(UUID player) {
        PlayerRecord r = players.get(player);
        return r == null ? List.of() : r.incoming;
    }

    // --- erasure, export, conformance -----------------------------------------------------------------------

    /**
     * Erases {@code player}: their record, every grant to them and every request from them in every other record.
     * Returns the players whose records changed (for live revocation notices). Tolerates an unknown player.
     */
    public List<UUID> erase(UUID player) {
        List<UUID> touched = new ArrayList<>();
        boolean changed = players.remove(player) != null;
        for (Map.Entry<UUID, PlayerRecord> e : players.entrySet()) {
            PlayerRecord r = e.getValue();
            boolean t = r.grants.removeIf(g -> g.to().equals(player));
            t |= r.incoming.removeIf(q -> q.from().equals(player));
            if (t) {
                touched.add(e.getKey());
                changed = true;
            }
        }
        players.values().removeIf(PlayerRecord::isEmpty);
        if (changed) {
            setDirty();
        }
        return touched;
    }

    /** True when anything in the store still refers to {@code player} (Core's {@code ErasureConformance} probe). */
    public boolean retains(UUID player) {
        if (players.containsKey(player)) {
            return true;
        }
        for (PlayerRecord r : players.values()) {
            for (Grant g : r.grants) {
                if (g.to().equals(player)) {
                    return true;
                }
            }
            for (ShareRequest q : r.incoming) {
                if (q.from().equals(player)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Everything about {@code player} as JSON: their own record only. Counterparty ids appear only as this player's
     * own grant and request entries; nothing is read from another player's record.
     */
    public JsonObject export(UUID player) {
        JsonObject out = new JsonObject();
        out.addProperty("store", NeroMapsCommon.MOD_ID + ":" + STORE);
        out.addProperty("version", SAVE_VERSION);
        PlayerRecord r = players.get(player);
        if (r == null) {
            out.add("record", new JsonObject());
            return out;
        }
        out.add("record", PlayerRecord.CODEC.encodeStart(JsonOps.INSTANCE, r).result()
                .orElseGet(JsonObject::new));
        JsonArray note = new JsonArray();
        note.add("Live position is never stored. Explored areas are 32 x 32-chunk region bitsets.");
        out.add("notes", note);
        return out;
    }
}
