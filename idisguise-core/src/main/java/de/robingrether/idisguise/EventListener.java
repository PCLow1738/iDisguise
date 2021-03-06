package de.robingrether.idisguise;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import de.robingrether.idisguise.disguise.DisguiseType;
import de.robingrether.idisguise.disguise.MobDisguise;
import de.robingrether.idisguise.disguise.PlayerDisguise;
import de.robingrether.idisguise.io.UpdateCheck;
import de.robingrether.idisguise.management.DisguiseManager;
import de.robingrether.idisguise.management.ProfileHelper;
import de.robingrether.idisguise.management.channel.ChannelInjector;
import de.robingrether.idisguise.management.util.EntityIdList;
import de.robingrether.util.StringUtil;

public class EventListener implements Listener {
	
	private iDisguise plugin;
	
	public EventListener(iDisguise plugin) {
		this.plugin = plugin;
	}
	
	@EventHandler
	public void onPlayerLogin(PlayerLoginEvent event) {
		if(!plugin.enabled()) {
			event.disallow(Result.KICK_OTHER, "Server start/reload has not finished yet");
		}
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		ChannelInjector.inject(player);
		EntityIdList.addPlayer(player);
		ProfileHelper.getInstance().registerGameProfile(player);
		if(DisguiseManager.isDisguised(player)) {
			player.sendMessage(plugin.getLanguage().JOIN_DISGUISED);
		}
		if(plugin.getConfiguration().MODIFY_MESSAGE_JOIN) {
			if(event.getJoinMessage() != null && DisguiseManager.isDisguised(player)) {
				if(DisguiseManager.getDisguise(player) instanceof PlayerDisguise) {
					event.setJoinMessage(event.getJoinMessage().replace(player.getName(), ((PlayerDisguise)DisguiseManager.getDisguise(player)).getDisplayName()));
				} else {
					event.setJoinMessage(null);
				}
			}
		}
		if(player.hasPermission("iDisguise.update") && plugin.getConfiguration().UPDATE_CHECK) {
			plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, new UpdateCheck(plugin, player, plugin.getConfiguration().UPDATE_DOWNLOAD), 20L);
		}
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		if(plugin.getConfiguration().MODIFY_MESSAGE_LEAVE) {
			if(event.getQuitMessage() != null && DisguiseManager.isDisguised(player)) {
				if(DisguiseManager.getDisguise(player) instanceof PlayerDisguise) {
					event.setQuitMessage(event.getQuitMessage().replace(player.getName(), ((PlayerDisguise)DisguiseManager.getDisguise(player)).getDisplayName()));
				} else {
					event.setQuitMessage(null);
				}
			}
		}
		if(!plugin.getConfiguration().KEEP_DISGUISE_LEAVE) {
			if(DisguiseManager.isDisguised(player)) {
				DisguiseManager.undisguise(player);
			}
		}
		ChannelInjector.remove(player);
		EntityIdList.removePlayer(player);
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDeath(PlayerDeathEvent event) {
		if(event.getDeathMessage() != null) {
			Player player = event.getEntity();
			if(plugin.getConfiguration().MODIFY_MESSAGE_DEATH) {
				if(DisguiseManager.isDisguised(player)) {
					if(DisguiseManager.getDisguise(player) instanceof PlayerDisguise) {
						event.setDeathMessage(event.getDeathMessage().replaceAll("(" + player.getDisplayName() + "|" + player.getName() + ")", ((PlayerDisguise)DisguiseManager.getDisguise(player)).getDisplayName()));
					} else {
						event.setDeathMessage(null);
						return;
					}
				}
			}
			if(plugin.getConfiguration().MODIFY_MESSAGE_KILL) {
				if(player.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
					Entity damager = ((EntityDamageByEntityEvent)player.getLastDamageCause()).getDamager();
					if(damager instanceof Player) {
						Player killer = (Player)damager;
						if(DisguiseManager.isDisguised(killer)) {
							if(DisguiseManager.getDisguise(killer) instanceof PlayerDisguise) {
								event.setDeathMessage(event.getDeathMessage().replaceAll("(" + killer.getDisplayName() + "|" + killer.getName() + ")", ((PlayerDisguise)DisguiseManager.getDisguise(killer)).getDisplayName()));
							} else if(DisguiseManager.getDisguise(killer) instanceof MobDisguise) {
								event.setDeathMessage(event.getDeathMessage().replaceAll("(" + killer.getDisplayName() + "|" + killer.getName() + ")", StringUtil.capitalizeFully(DisguiseManager.getDisguise(killer).getType().name().replace('_', ' '))));
							} else {
								event.setDeathMessage(null);
							}
						}
					} else if(damager instanceof Projectile && ((Projectile)damager).getShooter() instanceof Player) {
						Player killer = (Player)((Projectile)damager).getShooter();
						if(DisguiseManager.isDisguised(killer)) {
							if(DisguiseManager.getDisguise(killer) instanceof PlayerDisguise) {
								event.setDeathMessage(event.getDeathMessage().replaceAll("(" + killer.getDisplayName() + "|" + killer.getName() + ")", ((PlayerDisguise)DisguiseManager.getDisguise(killer)).getDisplayName()));
							} else if(DisguiseManager.getDisguise(killer) instanceof MobDisguise) {
								event.setDeathMessage(event.getDeathMessage().replaceAll("(" + killer.getDisplayName() + "|" + killer.getName() + ")", StringUtil.capitalizeFully(DisguiseManager.getDisguise(killer).getType().name().replace('_', ' '))));
							} else {
								event.setDeathMessage(null);
							}
						}
					}
				}
			}
		}
	}
	
	private Map<UUID, Long> mapLastMessageSent = new ConcurrentHashMap<UUID, Long>();
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		if(DisguiseManager.isDisguised(player) && DisguiseManager.getDisguise(player).getType().equals(DisguiseType.SHULKER)) {
			event.setCancelled(true);
			long lastSent = mapLastMessageSent.containsKey(player.getUniqueId()) ? mapLastMessageSent.get(player.getUniqueId()) : 0L;
			if(lastSent + 3000L < System.currentTimeMillis()) {
				player.sendMessage(plugin.getLanguage().MOVE_AS_SHULKER);
				mapLastMessageSent.put(player.getUniqueId(), System.currentTimeMillis());
			}
		}
	}
	
}