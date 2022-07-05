package world.bentobox.upgrades.upgrades;

import java.util.Map;

import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;

import world.bentobox.limits.listeners.BlockLimitsListener;
import world.bentobox.limits.objects.IslandBlockCount;
import world.bentobox.upgrades.UpgradesAddon;
import world.bentobox.upgrades.api.Upgrade;
import world.bentobox.upgrades.dataobjects.UpgradesData;

public class BlockLimitsUpgrade extends Upgrade {

    public BlockLimitsUpgrade(UpgradesAddon addon, Material block) {
        super(addon, "LimitsUpgrade-" + block.toString(), block.toString() + " limits Upgrade", block);
        this.block = block;
    }

    @Override
    public void updateUpgradeValue(User user, Island island) {
        UpgradesAddon upgradeAddon = this.getUpgradesAddon();
        UpgradesData islandData = upgradeAddon.getUpgradesLevels(island.getUniqueId());
        int upgradeLevel = islandData.getUpgradeLevel(getName());
        int numberPeople = island.getMemberSet().size();
        int islandLevel = upgradeAddon.getUpgradesManager().getIslandLevel(island);

        Map<String, Integer> upgradeInfos = upgradeAddon.getUpgradesManager().getBlockLimitsUpgradeInfos(this.block,
                upgradeLevel, islandLevel, numberPeople, island.getWorld());
        UpgradeValues upgrade;

        if (upgradeInfos == null) {
            upgrade = null;
        } else {
            // Get new description
            String description =  user.getTranslation("upgrades.ui.upgradepanel.tiernameandlevel",
                    "[name]", upgradeAddon.getUpgradesManager().getBlockLimitsUpgradeTierName(this.block, upgradeLevel, island.getWorld()),
                    "[current]", Integer.toString(upgradeLevel),
                    "[max]", Integer.toString(upgradeAddon.getUpgradesManager().getBlockLimitsUpgradeMax(this.block, island.getWorld())));

            // Set new description
            this.setOwnDescription(user, description);

            upgrade = new UpgradeValues(upgradeInfos.get("islandMinLevel"), upgradeInfos.get("vaultCost"),
                    upgradeInfos.get("upgrade"));
        }

        this.setUpgradeValues(user, upgrade);

        String newDisplayName;

        if (upgrade == null) {
            newDisplayName = user.getTranslation("upgrades.ui.upgradepanel.nolimitsupgrade", "[block]",
                    this.block.toString());
        } else {
            newDisplayName = user.getTranslation("upgrades.ui.upgradepanel.limitsupgrade", "[block]",
                    this.block.toString(), "[level]", Integer.toString(upgrade.getUpgradeValue()));
        }

        this.setDisplayName(newDisplayName);
    }

    @Override
    public boolean isShowed(User user, Island island) {
        // Get the addon
        UpgradesAddon upgradesAddon = this.getUpgradesAddon();
        // Get the data from upgrades
        UpgradesData islandData = upgradesAddon.getUpgradesLevels(island.getUniqueId());
        // Get level of the upgrade
        int upgradeLevel = islandData.getUpgradeLevel(this.getName());
        // Permission level required
        int permissionLevel = upgradesAddon.getUpgradesManager().getBlockLimitsPermissionLevel(this.block, upgradeLevel,
                island.getWorld());

        // If default permission, then true
        if (permissionLevel == 0)
            return true;

        Player player = user.getPlayer();
        String gamemode = island.getGameMode();
        String permissionStart = gamemode + ".upgrades." + this.getName() + ".";
        permissionStart = permissionStart.toLowerCase();

        // For each permission of the player
        for (PermissionAttachmentInfo perms : player.getEffectivePermissions()) {

            // If permission is the one we search
            if (!perms.getValue() || !perms.getPermission().startsWith(permissionStart))
                continue;

            if (perms.getPermission().contains(permissionStart + "*")) {
                this.logError(player.getName(), perms.getPermission(), "Wildcards are not allowed.");
                return false;
            }

            String[] split = perms.getPermission().split("\\.");
            if (split.length != 4) {
                logError(player.getName(), perms.getPermission(), "format must be '" + permissionStart + "LEVEL'");
                return false;
            }

            if (!NumberUtils.isDigits(split[3])) {
                logError(player.getName(), perms.getPermission(), "The last part must be a number");
                return false;
            }

            if (permissionLevel <= Integer.parseInt(split[3]))
                return true;
        }

        return false;
    }

    private void logError(String name, String perm, String error) {
        this.getUpgradesAddon()
        .logError("Player " + name + " has permission: '" + perm + "' but " + error + " Ignoring...");
    }

    @Override
    public boolean doUpgrade(User user, Island island) {
        if (!beforeUpgrade(user, island, 0))
            return false;

        if (!super.doUpgrade(user, island))
            return false;

        UpgradesAddon islandAddon = this.getUpgradesAddon();
        BlockLimitsListener bLListener = islandAddon.getLimitsAddon().getBlockLimitListener();
        IslandBlockCount isb = bLListener.getIsland(island);
        int oldCount = isb.getBlockLimitsOffset().getOrDefault(block, 0);
        int newCount = oldCount + this.getUpgradeValues(user).getUpgradeValue();

        afterUpgrade(user, island, oldCount);

        return true;
    }

    @Override
    public boolean beforeUpgrade(User user, Island island, int newLevel) {
        UpgradesAddon islandAddon = this.getUpgradesAddon();

        return islandAddon.isLimitsProvided();
    }

    @Override
    public void afterUpgrade(User user, Island island, int oldLevel) {
        UpgradesAddon upgradeAddon = this.getUpgradesAddon();
        BlockLimitsListener bLListener = upgradeAddon.getLimitsAddon().getBlockLimitListener();
        IslandBlockCount isb = bLListener.getIsland(island);
        int newLevel = upgradeAddon.getUpgradesLevels(island.getUniqueId()).getUpgradeLevel(getName());
        Map<String, Integer> upgradeInfos = this.getUpgradesAddon().getUpgradesManager().getUpgradeInfos(
                getName(),
                newLevel,
                (int)this.getUpgradesAddon().getLevelAddon().getIslandLevel(island.getWorld(), user.getUniqueId()),
                island.getMembers().size(),
                island.getWorld());

        isb.setBlockLimitsOffset(block, upgradeInfos.get("total"));
        user.sendMessage("upgrades.ui.upgradepanel.limitsupgradedone", "[block]", this.block.toString(), "[level]",
                Integer.toString(upgradeInfos.get("upgrade")));
    }

    private Material block;

}
