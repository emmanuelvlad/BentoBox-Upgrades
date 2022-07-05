package world.bentobox.upgrades;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.World;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.flags.Flag;
import world.bentobox.bentobox.api.flags.clicklisteners.CycleClick;
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.level.Level;
import world.bentobox.limits.Limits;
import world.bentobox.upgrades.api.Upgrade;
import world.bentobox.upgrades.command.PlayerUpgradeCommand;
import world.bentobox.upgrades.command.AdminUpgradeCommand;
import world.bentobox.upgrades.config.Settings;
import world.bentobox.upgrades.dataobjects.UpgradesData;
import world.bentobox.upgrades.listeners.IslandChangeListener;
import world.bentobox.upgrades.upgrades.BlockLimitsUpgrade;
import world.bentobox.upgrades.upgrades.CommandUpgrade;
import world.bentobox.upgrades.upgrades.EntityGroupLimitsUpgrade;
import world.bentobox.upgrades.upgrades.EntityLimitsUpgrade;
import world.bentobox.upgrades.upgrades.RangeUpgrade;

public class UpgradesAddon extends Addon {

    @Override
    public void onLoad() {
        super.onLoad();
        this.saveDefaultConfig();
        this.settings = new Settings(this);
    }

    @Override
    public void onEnable() {
        if (this.getState().equals(State.DISABLED)) {
            this.logWarning("Upgrades Addon is not available or disabled!");
            return;
        }

        List<String> hookedGameModes = new ArrayList<>();

        getPlugin().getAddonsManager().getGameModeAddons().stream()
        .filter(g -> !settings.getDisabledGameModes().contains(g.getDescription().getName()))
        .forEach(g -> {
            if (g.getPlayerCommand().isPresent()) {

                if (this.getSettings().getEnableCommand()) {
                    new PlayerUpgradeCommand(this, g.getPlayerCommand().get());
                }

                new AdminUpgradeCommand(this, g.getAdminCommand().get());
                UpgradesAddon.UPGRADES_RANK_RIGHT.addGameModeAddon(g);

                this.hooked = true;
                hookedGameModes.add(g.getDescription().getName());
            }
        });

        if (this.hooked) {
            this.upgradesManager = new UpgradesManager(this);
            this.upgradesManager.addGameModes(hookedGameModes);

            this.upgrade = new HashSet<>();

            this.database = new Database<>(this, UpgradesData.class);
            this.upgradesCache = new HashMap<>();

            Optional<Addon> level = this.getAddonByName("Level");

            if (!level.isPresent()) {
                this.logWarning("Level addon not found so Upgrades won't look for Island Level");
                this.levelAddon = null;
            } else
                this.levelAddon = (Level) level.get();

            Optional<Addon> limits = this.getAddonByName("Limits");

            if (!limits.isPresent()) {
                this.logWarning("Limits addon not found so Island Upgrade won't look for IslandLevel");
                this.limitsAddon = null;
            } else
                this.limitsAddon = (Limits) limits.get();

            Optional<VaultHook> vault = this.getPlugin().getVault();
            if (!vault.isPresent()) {
                this.logWarning("Vault plugin not found so Upgrades won't look for money");
                this.vault = null;
            } else
                this.vault = vault.get();

            if (this.isLimitsProvided()) {
                this.getSettings().getEntityLimitsUpgrade().forEach(ent -> this.registerUpgrade(new EntityLimitsUpgrade(this, ent)));
                this.getSettings().getEntityGroupLimitsUpgrade().forEach(group -> this.registerUpgrade(new EntityGroupLimitsUpgrade(this, group)));
                this.getSettings().getMaterialsLimitsUpgrade().forEach(mat -> this.registerUpgrade(new BlockLimitsUpgrade(this, mat)));
            }

            this.getSettings().getCommandUpgrade().forEach(cmd -> this.registerUpgrade(new CommandUpgrade(this, cmd, this.getSettings().getCommandIcon(cmd))));

            if (this.getSettings().getHasRangeUpgrade())
                this.registerUpgrade(new RangeUpgrade(this));

            this.registerListener(new IslandChangeListener(this));

            //if (this.isLimitsProvided())
            //this.registerListener(new JoinPermCheckListener(this));

            getPlugin().getFlagsManager().registerFlag(UpgradesAddon.UPGRADES_RANK_RIGHT);
            hookedGameModes.forEach(gm -> {
                this.getPlugin().getAddonsManager().getGameModeAddons().stream().filter(g -> {
                    return g.getDescription().getName().equals(gm);
                }).forEach(rgm -> {
                    registerPlaceholders(rgm);
                });;
            });

            this.log("Upgrades addon enabled");
        } else {
            this.logError("Upgrades addon could not hook into any GameMode and therefore will not do anything");
            this.setState(State.DISABLED);
        }
    }

    @Override
    public void onDisable() {
        if (this.upgradesCache != null)
            this.upgradesCache.values().forEach(this.database::saveObjectAsync);
    }

    @Override
    public void onReload() {
        super.onReload();

        if (this.hooked)
            this.settings = new Settings(this);
        this.log("Island upgrade addon reloaded");
    }

    private void registerPlaceholders(GameModeAddon gm) {
        if (getPlugin().getPlaceholdersManager() == null) return;
        if (this.getAvailableUpgrades() == null) return;

        World gmWorld = gm.getOverWorld();
        String[] perlvl = new String[]{"upgrade", "total", "vaultCost", "islandMinLevel"};

        this.getAvailableUpgrades().forEach(upgrade -> {
            int maxLevel = upgrade.getLastTier(gmWorld).getMaxLevel();
            getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_can_do_upgrade", user -> {
                    Island island = this.getIslands().getIsland(gmWorld, user);
                    if (island == null) {
                        return "false";
                    }

                    return island.isAllowed(user, UpgradesAddon.UPGRADES_RANK_RIGHT) + "";
                });

            getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_" + upgrade.getName().toLowerCase() + "_max", user -> {
                    return maxLevel+"";
                });

            getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_" + upgrade.getName().toLowerCase() + "_icon", user -> {
                    return upgrade.getIcon().toString();
                });

            getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_" + upgrade.getName().toLowerCase() + "_display_name", user -> {
                    return upgrade.getDisplayName();
                });

            for (int i = 1; i <= maxLevel; i++) {
                final int index = i;

                for (String tt : perlvl) {
                    getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                        gm.getDescription().getName().toLowerCase() + "_" + i + "_" + upgrade.getName().toLowerCase() + "_" + tt, user -> {
                            Island island = this.getIslands().getIsland(gmWorld, user);
                            if (island == null) {
                                return "";
                            }

                            Map<String, Integer> upgradeInfos = this.getUpgradesManager().getUpgradeInfos(
                                    upgrade.getName(),
                                    index,
                                    (int)this.getLevelAddon().getIslandLevel(gmWorld, user.getUniqueId()),
                                    island.getMembers().size(),
                                    gmWorld);
                            if (upgradeInfos == null) {
                                return "";
                            }
                            return upgradeInfos.get(tt)+"";
                        });
                }
            }

            getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_current_" + upgrade.getName().toLowerCase(), user -> {
                    Island island = this.getIslands().getIsland(gmWorld, user);
                    if (island == null) {
                        return "";
                    }

                    UpgradesData data = this.getUpgradesLevels(island.getUniqueId());

                    return data.getUpgradeLevel(upgrade.getName())+"";
                });

            getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_total_" + upgrade.getName().toLowerCase(), user -> {
                    Island island = this.getIslands().getIsland(gmWorld, user);
                    if (island == null) {
                        return "";
                    }

                    UpgradesData data = this.getUpgradesLevels(island.getUniqueId());
                    int total = 0;
                    for (int i = 1; i <= data.getUpgradeLevel(upgrade.getName()); i++) {
                        Map<String, Integer> upgradeInfos = this.getUpgradesManager().getUpgradeInfos(
                            upgrade.getName(),
                            i,
                            (int)this.getLevelAddon().getIslandLevel(gmWorld, user.getUniqueId()),
                            island.getMembers().size(),
                            gmWorld);
                        if (upgradeInfos != null) {
                            total += upgradeInfos.get("upgrade");
                        }
                    }
                    return total+"";
                });
        });
    }

    /**
     * @return the settings
     */
    public Settings getSettings() {
        return settings;
    }

    /**
     * @return the islandUpgradesManager
     */
    public UpgradesManager getUpgradesManager() {
        return upgradesManager;
    }

    public Database<UpgradesData> getDatabase() {
        return this.database;
    }

    public UpgradesData getUpgradesLevels(@NonNull String targetIsland) {
        UpgradesData upgradesData = this.upgradesCache.get(targetIsland);
        if (upgradesData != null)
            return upgradesData;
        UpgradesData data = this.database.objectExists(targetIsland) ?
                Optional.ofNullable(this.database.loadObject(targetIsland)).orElse(new UpgradesData(targetIsland)) :
                    new UpgradesData(targetIsland);
        this.upgradesCache.put(targetIsland, data);
        return data;
    }

    public void uncacheIsland(@Nullable String targetIsland, boolean save) {
        UpgradesData data = this.upgradesCache.remove(targetIsland);
        if (data == null)
            return;
        if (save)
            this.database.saveObjectAsync(data);
    }

    public Level getLevelAddon() {
        return this.levelAddon;
    }

    public Limits getLimitsAddon() {
        return this.limitsAddon;
    }

    public VaultHook getVaultHook() {
        return this.vault;
    }

    public boolean isLevelProvided() {
        return this.levelAddon != null;
    }

    public boolean isLimitsProvided() {
        return this.limitsAddon != null;
    }

    public boolean isVaultProvided() {
        return this.vault != null;
    }

    public Set<Upgrade> getAvailableUpgrades() {
        return this.upgrade;
    }

    public Upgrade getUpgrade(String name) {
        return this.upgrade.stream().filter(up -> {
            return up.getName().equalsIgnoreCase(name);
        }).findFirst().orElse(null);
    }

    public void registerUpgrade(Upgrade upgrade) {
        this.upgrade.add(upgrade);
    }

    private Settings settings;

    private boolean hooked;

    private UpgradesManager upgradesManager;

    private Set<Upgrade> upgrade;

    private Database<UpgradesData> database;

    private Map<String, UpgradesData> upgradesCache;

    private Level levelAddon;

    private Limits limitsAddon;

    private VaultHook vault;

    public final static Flag UPGRADES_RANK_RIGHT =
            new Flag.Builder("UPGRADES_RANK_RIGHT", Material.GOLD_INGOT)
            .type(Flag.Type.PROTECTION)
            .mode(Flag.Mode.BASIC)
            .clickHandler(new CycleClick("UPGRADES_RANK_RIGHT", RanksManager.MEMBER_RANK, RanksManager.OWNER_RANK))
            .defaultRank(RanksManager.MEMBER_RANK)
            .build();

}
