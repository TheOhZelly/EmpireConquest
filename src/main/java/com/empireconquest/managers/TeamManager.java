package com.empireconquest.managers;

import com.empireconquest.EmpireConquest;
import com.empireconquest.objects.EmpTeam;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {

    private final EmpireConquest plugin;
    private final Map<UUID, EmpTeam> teams = new HashMap<>();
    private final File teamsFile;
    private YamlConfiguration teamsConfig;

    public TeamManager(EmpireConquest plugin) {
        this.plugin    = plugin;
        this.teamsFile = new File(plugin.getDataFolder(), "teams.yml");
        load();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void load() {
        if (!teamsFile.exists()) {
            plugin.saveResource("teams.yml", false);
        }
        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
        teams.clear();

        if (!teamsConfig.contains("teams") || teamsConfig.getConfigurationSection("teams") == null) return;

        for (String key : teamsConfig.getConfigurationSection("teams").getKeys(false)) {
            try {
                UUID uuid     = UUID.fromString(key);
                EmpTeam team  = EmpTeam.fromString(teamsConfig.getString("teams." + key));
                if (team != null) teams.put(uuid, team);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in teams.yml: " + key);
            }
        }
    }

    /** Persist a single entry immediately (write-through). */
    private void saveEntry(UUID uuid, EmpTeam team) {
        teamsConfig.set("teams." + uuid.toString(), team.name());
        try {
            teamsConfig.save(teamsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save teams.yml: " + e.getMessage());
        }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /** Returns the team for a player, or null if unassigned. */
    public EmpTeam getTeam(UUID uuid) {
        return teams.get(uuid);
    }

    /**
     * Assigns a player to a team permanently.
     * Write-through: updates disk immediately.
     */
    public void assignTeam(UUID uuid, EmpTeam team) {
        teams.put(uuid, team);
        saveEntry(uuid, team);
    }

    /** Returns all UUIDs currently mapped to the given team. */
    public List<UUID> getTeamPlayers(EmpTeam team) {
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, EmpTeam> e : teams.entrySet()) {
            if (e.getValue() == team) result.add(e.getKey());
        }
        return result;
    }

    /** Returns true if the player has been assigned to any team. */
    public boolean isAssigned(UUID uuid) {
        return teams.containsKey(uuid);
    }

    /**
     * Balance: assign all online unassigned players using the configured
     * Spanish:Aztec ratio. Existing assignments are never changed.
     *
     * @param unassigned list of UUIDs not yet in any team
     */
    public void balance(List<UUID> unassigned) {
        int ratioSpanish = plugin.getConfig().getInt("ratio-spanish", 1);
        int ratioAztec   = plugin.getConfig().getInt("ratio-aztec",   3);
        int total        = ratioSpanish + ratioAztec;

        int currentSpanish = getTeamPlayers(EmpTeam.SPANISH).size();
        int currentAztec   = getTeamPlayers(EmpTeam.AZTEC).size();

        Collections.shuffle(unassigned);

        for (UUID uuid : unassigned) {
            // Pick whichever team is furthest below its ratio share
            double spanishShare = (double) currentSpanish / Math.max(1, currentSpanish + currentAztec);
            double targetShare  = (double) ratioSpanish / total;

            EmpTeam assigned = spanishShare <= targetShare ? EmpTeam.SPANISH : EmpTeam.AZTEC;
            assignTeam(uuid, assigned);

            if (assigned == EmpTeam.SPANISH) currentSpanish++;
            else currentAztec++;
        }
    }
}
