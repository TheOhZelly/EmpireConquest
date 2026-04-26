package com.empireconquest.commands;

import com.empireconquest.EmpireConquest;
import com.empireconquest.Phase;
import com.empireconquest.objects.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles all /emp subcommands.
 *
 * OP-required subcommands (empireconquest.admin):
 *   addzone, setsafe, setratio, setlives, start, pause, balance, team, end
 */
public class EmpCommand implements TabExecutor {

    private static final List<String> TOP_LEVEL = List.of(
        "addzone", "setsafe", "setratio", "setlives", "start", "pause", "balance", "team", "end", "give", "capzone", "pvp"
    );

    private static final List<String> GIVE_ITEMS = List.of(
        "musket", "flintlock", "iron_ball", "macuahuitl", "obsidian_blade"
    );

    private final EmpireConquest plugin;

    public EmpCommand(EmpireConquest plugin) {
        this.plugin = plugin;
    }

    // ── Execution ─────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (!sender.hasPermission("empireconquest.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        return switch (sub) {
            case "addzone"  -> cmdAddZone(sender, args);
            case "setsafe"  -> cmdSetSafe(sender, args);
            case "setratio" -> cmdSetRatio(sender, args);
            case "setlives" -> cmdSetLives(sender, args);
            case "start"    -> cmdStart(sender, args);
            case "pause"    -> cmdPause(sender);
            case "balance"  -> cmdBalance(sender);
            case "team"     -> cmdTeam(sender, args);
            case "end"      -> cmdEnd(sender);
            case "give"     -> cmdGive(sender, args);
            case "capzone"  -> cmdCapZone(sender, args);
            case "pvp"      -> cmdPvp(sender);
            default         -> { sendHelp(sender); yield true; }
        };
    }

    // ── Subcommands ───────────────────────────────────────────────────────────

    private boolean cmdAddZone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command must be run by a player."); return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /emp addzone <name>"); return true;
        }
        String name = args[1];
        var sel = plugin.getZoneManager().getWandSelection(player.getUniqueId());
        if (sel == null || sel[0] == null || sel[1] == null) {
            sender.sendMessage("§cSelect two corners first using a §6Blaze Rod §c(right-click)."); return true;
        }
        if (!plugin.getZoneManager().addZone(name, sel[0], sel[1])) {
            sender.sendMessage("§cA zone named §e" + name + " §calready exists."); return true;
        }
        sender.sendMessage("§aZone §e" + name + " §acreated successfully.");
        return true;
    }

    private boolean cmdSetSafe(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command must be run by a player."); return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /emp setsafe <spanish|aztec>"); return true;
        }
        EmpTeam team = EmpTeam.fromString(args[1]);
        if (team == null) {
            sender.sendMessage("§cUnknown team. Use §espanish §cor §eaztec§c."); return true;
        }
        plugin.setSafePoint(team, player.getLocation());
        sender.sendMessage("§aSet §e" + team.name() + "§a spawn to your current location.");
        return true;
    }

    private boolean cmdSetRatio(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /emp setratio <spanish> <aztec>"); return true;
        }
        try {
            int s = Integer.parseInt(args[1]);
            int a = Integer.parseInt(args[2]);
            if (s <= 0 || a <= 0) throw new NumberFormatException();
            plugin.getConfig().set("ratio-spanish", s);
            plugin.getConfig().set("ratio-aztec",   a);
            plugin.saveConfig();
            sender.sendMessage("§aRatio set to §e" + s + "§a:§e" + a + " §a(Spanish:Aztec).");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cBoth values must be positive integers.");
        }
        return true;
    }

    private boolean cmdSetLives(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /emp setlives <amount>"); return true;
        }
        try {
            int amount = Integer.parseInt(args[1]);
            if (amount <= 0) throw new NumberFormatException();
            // For simplicity, set both teams to the same per-player value
            plugin.getConfig().set("spanish-lives", amount);
            plugin.getConfig().set("aztec-lives",   amount);
            plugin.saveConfig();
            sender.sendMessage("§aSet per-player lives to §e" + amount + "§a for both teams.");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cAmount must be a positive integer.");
        }
        return true;
    }

    private boolean cmdStart(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("conquest")) {
            sender.sendMessage("§cUsage: /emp start conquest"); return true;
        }
        if (plugin.getPhase() == Phase.CONQUEST) {
            sender.sendMessage("§cConquest is already active."); return true;
        }
        plugin.startConquest();
        Bukkit.broadcastMessage("§6§l[EmpireConquest] §eThe Conquest phase has begun!");
        return true;
    }

    private boolean cmdPause(CommandSender sender) {
        boolean paused = plugin.togglePause();
        sender.sendMessage(paused
            ? "§e[EmpireConquest] §fTimers and capture progress §cpaused§f."
            : "§e[EmpireConquest] §fTimers and capture progress §aresumed§f.");
        Bukkit.broadcastMessage(paused
            ? "§e[EmpireConquest] §fEvent §cpaused§f by an admin."
            : "§e[EmpireConquest] §fEvent §aresumed§f by an admin.");
        return true;
    }

    private boolean cmdBalance(CommandSender sender) {
        List<UUID> unassigned = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.getTeamManager().isAssigned(p.getUniqueId())) {
                unassigned.add(p.getUniqueId());
            }
        }
        if (unassigned.isEmpty()) {
            sender.sendMessage("§eAll online players are already assigned to a team."); return true;
        }
        plugin.getTeamManager().balance(unassigned);

        int s = plugin.getTeamManager().getTeamPlayers(EmpTeam.SPANISH).size();
        int a = plugin.getTeamManager().getTeamPlayers(EmpTeam.AZTEC).size();
        sender.sendMessage("§aAssigned §e" + unassigned.size() + " §aplayer(s). " +
            "Spanish: §e" + s + "§a, Aztec: §e" + a);

        for (UUID uuid : unassigned) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                EmpTeam t = plugin.getTeamManager().getTeam(uuid);
                p.sendMessage("§e[EmpireConquest] §fYou have been assigned to team §" +
                    (t == EmpTeam.SPANISH ? "c" : "9") + t.name() + "§f!");
            }
        }
        return true;
    }

    private boolean cmdTeam(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /emp team <player> <spanish|aztec>"); return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer §e" + args[1] + " §cnot found or not online."); return true;
        }
        EmpTeam team = EmpTeam.fromString(args[2]);
        if (team == null) {
            sender.sendMessage("§cUnknown team. Use §espanish §cor §eaztec§c."); return true;
        }
        plugin.getTeamManager().assignTeam(target.getUniqueId(), team);
        sender.sendMessage("§aAssigned §e" + target.getName() + " §ato §" +
            (team == EmpTeam.SPANISH ? "c" : "9") + team.name() + "§a.");
        target.sendMessage("§e[EmpireConquest] §fAn admin assigned you to team §" +
            (team == EmpTeam.SPANISH ? "c" : "9") + team.name() + "§f!");
        return true;
    }

    private boolean cmdEnd(CommandSender sender) {
        if (plugin.getPhase() == Phase.PREPARATION) {
            sender.sendMessage("§eNo conquest is currently active."); return true;
        }
        plugin.endEvent();
        Bukkit.broadcastMessage("§6§l[EmpireConquest] §eThe event has been ended by an admin.");
        return true;
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("empireconquest.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return TOP_LEVEL.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "setsafe" -> args.length == 2
                ? filterStartsWith(List.of("spanish", "aztec"), args[1]) : Collections.emptyList();
            case "start" -> args.length == 2
                ? filterStartsWith(List.of("conquest"), args[1]) : Collections.emptyList();
            case "team" -> {
                if (args.length == 2) yield onlinePlayers(args[1]);
                if (args.length == 3) yield filterStartsWith(List.of("spanish", "aztec"), args[2]);
                yield Collections.emptyList();
            }
            case "setratio" -> args.length == 2 || args.length == 3
                ? List.of("<number>") : Collections.emptyList();
            case "setlives" -> args.length == 2
                ? List.of("<amount>") : Collections.emptyList();
            case "addzone" -> args.length == 2
                ? List.of("<name>") : Collections.emptyList();
            case "give" -> {
                if (args.length == 2) yield onlinePlayers(args[1]);
                if (args.length == 3) yield filterStartsWith(GIVE_ITEMS, args[2]);
                if (args.length == 4) yield List.of("<amount>");
                yield Collections.emptyList();
            }
            case "capzone" -> {
                if (args.length == 2) yield plugin.getZoneManager().getZones().stream()
                    .map(z -> z.getName())
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
                if (args.length == 3) yield filterStartsWith(List.of("spanish", "aztec", "neutral"), args[2]);
                yield Collections.emptyList();
            }
            default -> Collections.emptyList();
        };
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        return options.stream()
            .filter(s -> s.startsWith(prefix.toLowerCase()))
            .collect(Collectors.toList());
    }

    private List<String> onlinePlayers(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
            .collect(Collectors.toList());
    }

    private boolean cmdPvp(CommandSender sender) {
        boolean enabled = plugin.togglePvp();
        String state = enabled ? "§aenabled" : "§cdisabled";
        Bukkit.broadcastMessage("§6[EmpireConquest] §fFree PvP " + state + "§f by an admin.");
        return true;
    }

    private boolean cmdCapZone(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /emp capzone <zone> <spanish|aztec|neutral>"); return true;
        }
        var zone = plugin.getZoneManager().getZone(args[1]);
        if (zone == null) {
            sender.sendMessage("§cNo zone named §e" + args[1] + "§c."); return true;
        }
        EmpTeam team = args[2].equalsIgnoreCase("neutral") ? null : EmpTeam.fromString(args[2]);
        if (team == null && !args[2].equalsIgnoreCase("neutral")) {
            sender.sendMessage("§cUnknown team. Use §espanish§c, §eaztec§c, or §eneutral§c."); return true;
        }
        zone.setOwner(team);
        zone.setProgress(team == EmpTeam.SPANISH ? 1.0f : team == EmpTeam.AZTEC ? 0.0f : 0.5f);
        String teamDisplay = team == null ? "§7Neutral" : team == EmpTeam.SPANISH ? "§fSpanish" : "§6Aztec";
        Bukkit.broadcastMessage("§6[EmpireConquest] §e" + zone.getName() + " §fforced to " + teamDisplay + "§f by an admin.");
        sender.sendMessage("§aSet §e" + zone.getName() + " §ato " + teamDisplay + "§a.");
        return true;
    }

    private boolean cmdGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /emp give <player> <item> [amount]"); return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer §e" + args[1] + " §cnot found or not online."); return true;
        }
        String itemName = args[2].toLowerCase();
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1 || amount > 64) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage("§cAmount must be between 1 and 64."); return true;
            }
        }

        ItemStack item = switch (itemName) {
            case "musket"        -> MusketItem.createMusket(plugin);
            case "flintlock"     -> FlintlockItem.createFlintlock(plugin);
            case "iron_ball"     -> { ItemStack i = MusketItem.createIronBall(plugin); i.setAmount(amount); yield i; }
            case "macuahuitl"    -> MacuahuitlItem.createMacuahuitl(plugin);
            case "obsidian_blade"-> ObsidianBladeItem.createObsidianBlade(plugin);
            default -> null;
        };

        if (item == null) {
            sender.sendMessage("§cUnknown item. Choose: " + String.join(", ", GIVE_ITEMS)); return true;
        }

        // iron_ball already has its amount set; all others default to 1
        if (!itemName.equals("iron_ball")) item.setAmount(amount);

        target.getInventory().addItem(item);
        sender.sendMessage("§aGave §e" + amount + "x " + itemName + " §ato §e" + target.getName() + "§a.");
        target.sendMessage("§e[EmpireConquest] §fYou received §e" + amount + "x " + itemName + "§f.");
        return true;
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l═══ EmpireConquest Commands ═══");
        sender.sendMessage("§e/emp addzone <name>       §7- Create zone (wand required)");
        sender.sendMessage("§e/emp setsafe <team>       §7- Set team spawn point");
        sender.sendMessage("§e/emp setratio <s> <a>     §7- Set Spanish:Aztec ratio");
        sender.sendMessage("§e/emp setlives <amount>    §7- Set per-player lives");
        sender.sendMessage("§e/emp start conquest       §7- Begin Phase 2");
        sender.sendMessage("§e/emp pause                §7- Toggle timer/progress freeze");
        sender.sendMessage("§e/emp balance              §7- Assign unassigned players");
        sender.sendMessage("§e/emp team <player> <team> §7- Force-assign a player");
        sender.sendMessage("§e/emp end                  §7- End the event");
        sender.sendMessage("§e/emp give <player> <item> [amt] §7- Give a custom item");
        sender.sendMessage("§e/emp capzone <zone> <team>    §7- Force capture a zone");
        sender.sendMessage("§e/emp pvp                      §7- Toggle PvP during preparation");
    }
}
