package com.empireconquest.managers;

import com.empireconquest.EmpireConquest;
import com.empireconquest.objects.EmpTeam;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {

    private static final String TEAM_SPANISH = "ec_sp";
    private static final String TEAM_AZTEC   = "ec_az";

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
                UUID uuid    = UUID.fromString(key);
                EmpTeam team = EmpTeam.fromString(teamsConfig.getString("teams." + key));
                if (team != null) teams.put(uuid, team);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in teams.yml: " + key);
            }
        }
    }

    private void saveEntry(UUID uuid, EmpTeam team) {
        teamsConfig.set("teams." + uuid.toString(), team.name());
        try {
            teamsConfig.save(teamsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save teams.yml: " + e.getMessage());
        }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    public EmpTeam getTeam(UUID uuid) {
        return teams.get(uuid);
    }

    public void assignTeam(UUID uuid, EmpTeam team) {
        teams.put(uuid, team);
        saveEntry(uuid, team);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            applyNametag(player, Bukkit.getScoreboardManager().getMainScoreboard());
            // If they're on the conquest scoreboard, apply there too
            Scoreboard active = player.getScoreboard();
            if (active != Bukkit.getScoreboardManager().getMainScoreboard()) {
                applyNametag(player, active);
            }
        }
    }

    public List<UUID> getTeamPlayers(EmpTeam team) {
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, EmpTeam> e : teams.entrySet()) {
            if (e.getValue() == team) result.add(e.getKey());
        }
        return result;
    }

    public boolean isAssigned(UUID uuid) {
        return teams.containsKey(uuid);
    }

    public void balance(List<UUID> unassigned) {
        int ratioSpanish = plugin.getConfig().getInt("ratio-spanish", 1);
        int ratioAztec   = plugin.getConfig().getInt("ratio-aztec",   3);
        int total        = ratioSpanish + ratioAztec;

        int currentSpanish = getTeamPlayers(EmpTeam.SPANISH).size();
        int currentAztec   = getTeamPlayers(EmpTeam.AZTEC).size();

        Collections.shuffle(unassigned);

        for (UUID uuid : unassigned) {
            double spanishShare = (double) currentSpanish / Math.max(1, currentSpanish + currentAztec);
            double targetShare  = (double) ratioSpanish / total;

            EmpTeam assigned = spanishShare <= targetShare ? EmpTeam.SPANISH : EmpTeam.AZTEC;
            assignTeam(uuid, assigned);

            if (assigned == EmpTeam.SPANISH) currentSpanish++;
            else currentAztec++;
        }
    }

    // ── Nametag management ────────────────────────────────────────────────────

    /**
     * Creates Spanish (white) and Aztec (gold) nametag teams on the given scoreboard
     * if they don't already exist, then adds all currently assigned online players.
     */
    /** Creates Spanish/Aztec nametag teams on the board if missing. Does NOT add players. */
    public void ensureNametagTeams(Scoreboard board) {
        if (board.getTeam(TEAM_SPANISH) == null) {
            Team t = board.registerNewTeam(TEAM_SPANISH);
            t.color(NamedTextColor.WHITE);
        }
        if (board.getTeam(TEAM_AZTEC) == null) {
            Team t = board.registerNewTeam(TEAM_AZTEC);
            t.color(NamedTextColor.GOLD);
        }
    }

    /** Adds a player to the correct nametag team on the given scoreboard. */
    public void applyNametag(Player player, Scoreboard board) {
        EmpTeam empTeam = getTeam(player.getUniqueId());
        if (empTeam == null) return;
        ensureNametagTeams(board);
        String teamName = empTeam == EmpTeam.SPANISH ? TEAM_SPANISH : TEAM_AZTEC;
        Team t = board.getTeam(teamName);
        if (t != null) t.addEntry(player.getName());
    }

    /** Applies nametag on the main scoreboard (call on join / during PREPARATION). */
    public void applyNametag(Player player) {
        applyNametag(player, Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
