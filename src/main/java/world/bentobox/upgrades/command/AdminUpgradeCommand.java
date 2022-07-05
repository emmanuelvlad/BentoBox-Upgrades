package world.bentobox.upgrades.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.upgrades.UpgradesAddon;
import world.bentobox.upgrades.api.Upgrade;
import world.bentobox.upgrades.dataobjects.UpgradesData;

public class AdminUpgradeCommand extends CompositeCommand {

	public AdminUpgradeCommand(UpgradesAddon addon, CompositeCommand cmd) {
		super(addon, cmd, "upgrade");
		
		this.addon = addon;
	}
	
	@Override
	public void setup() {
		this.setPermission("admin.upgrades");
		this.setOnlyPlayer(false);
		this.setParametersHelp("admin.upgrades.parameters");
		this.setDescription("admin.upgrades.description");
	}

	@Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
		switch (args.size()) {
			case 2:
			List<String> options = new ArrayList<>(Util.getOnlinePlayerList(user));
			return Optional.of(Util.tabLimit(options, args.get(1)));
			case 3:
				return Optional.of(List.of("get", "set", "add"));
			case 4:
				return Optional.of(List.of("LimitsUpgrade-", "command-", "RangeUpgrade", "[other]"));
			case 5:
				return Optional.of(List.of("[amount]"));
		}
		return Optional.empty();
    }
	
	@Override
	public boolean canExecute(User user, String label, List<String> args) {
		if (args.size() < 4) {
			this.showHelp(this, user);
			return false;
		}

		UUID requestedUuid = Util.getUUID(args.get(0));
		if (requestedUuid == null) {
			user.sendMessage("general.errors.offline-player");
			return false;
		}

		User reqUser = User.getInstance(requestedUuid);
		Island island = getIslands().getIsland(this.getWorld(), reqUser);

		if (island == null) {
			user.sendMessage("general.errors.no-island");
			return false;
		}

		if (!island.isAllowed(reqUser, UpgradesAddon.UPGRADES_RANK_RIGHT)) {
			user.sendMessage("general.errors.insufficient-rank",
				TextVariables.RANK,
				user.getTranslation(this.addon.getPlugin().getRanksManager().getRank(island.getRank(user))));
			return false;
		}
		
		return true;
	}
	
	@Override
	public boolean execute(User user, String label, List<String> args) {
		UUID requestedUuid = Util.getUUID(args.get(0));
		if (requestedUuid == null) {
			return false;
		}

		User reqUser = User.getInstance(requestedUuid);
		Island island = getIslands().getIsland(this.getWorld(), reqUser);

		if (island == null) {
			return false;
		}

		UpgradesData data = addon.getUpgradesLevels(island.getUniqueId());
		String upgradeName = args.get(2);
		Upgrade upgrade = addon.getUpgrade(upgradeName);
		if (upgrade == null) {
			user.sendRawMessage("unknown upgrade");
			return false;
		}
		switch (args.get(1)) {
			case "get":
				user.sendRawMessage(data.getUpgradeLevel(args.get(2))+"");
				return true;
			case "set", "add":
				try {
					int amount = Integer.parseInt(args.get(3));
					if (args.get(1).equals("add")) {
						amount += data.getUpgradeLevel(upgradeName);
					}
					if (!upgrade.beforeUpgrade(user, island, amount)) {
						return false;
					}
					int oldLevel = data.getUpgradeLevel(upgradeName);
					data.setUpgradeLevel(upgradeName, amount);
					upgrade.afterUpgrade(user, island, oldLevel);
					return true;
				} catch (NumberFormatException e) {
					user.sendRawMessage("amount is not an integer");
					return false;
				}
			default:
				user.sendRawMessage("type not found, supported: set, add");
				return false;
		}
	}
	
	UpgradesAddon addon;
	
}
