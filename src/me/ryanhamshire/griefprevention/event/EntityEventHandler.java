/*
 * This file is part of GriefPrevention, licensed under the MIT License (MIT).
 *
 * Copyright (c) Ryan Hamshire
 * Copyright (c) bloodmc
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package me.ryanhamshire.griefprevention.event;

import me.ryanhamshire.griefprevention.CustomLogEntryTypes;
import me.ryanhamshire.griefprevention.DataStore;
import me.ryanhamshire.griefprevention.GPPermissionHandler;
import me.ryanhamshire.griefprevention.GPPermissions;
import me.ryanhamshire.griefprevention.GPTimings;
import me.ryanhamshire.griefprevention.GriefPrevention;
import me.ryanhamshire.griefprevention.Messages;
import me.ryanhamshire.griefprevention.PlayerData;
import me.ryanhamshire.griefprevention.TextMode;
import me.ryanhamshire.griefprevention.claim.Claim;
import me.ryanhamshire.griefprevention.claim.ClaimWorldManager;
import me.ryanhamshire.griefprevention.claim.ClaimsMode;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.explosive.Explosive;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.entity.living.monster.Monster;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.entity.projectile.Projectile;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.entity.damage.source.DamageSource;
import org.spongepowered.api.event.cause.entity.damage.source.EntityDamageSource;
import org.spongepowered.api.event.cause.entity.damage.source.IndirectEntityDamageSource;
import org.spongepowered.api.event.cause.entity.spawn.SpawnCause;
import org.spongepowered.api.event.cause.entity.teleport.EntityTeleportCause;
import org.spongepowered.api.event.cause.entity.teleport.TeleportCause;
import org.spongepowered.api.event.cause.entity.teleport.TeleportType;
import org.spongepowered.api.event.cause.entity.teleport.TeleportTypes;
import org.spongepowered.api.event.entity.AttackEntityEvent;
import org.spongepowered.api.event.entity.CollideEntityEvent;
import org.spongepowered.api.event.entity.DamageEntityEvent;
import org.spongepowered.api.event.entity.DestructEntityEvent;
import org.spongepowered.api.event.entity.DisplaceEntityEvent;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.item.inventory.DropItemEvent;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.explosion.Explosion;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.data.util.NbtDataUtil;
import org.spongepowered.common.interfaces.entity.IMixinEntity;

import java.time.Instant;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

//handles events related to entities
public class EntityEventHandler {

    // convenience reference for the singleton datastore
    private DataStore dataStore;

    public EntityEventHandler(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Listener(order = Order.FIRST)
    public void onEntityExplosionPre(ExplosionEvent.Pre event) {
        GPTimings.ENTITY_EXPLOSION_PRE_EVENT.startTimingIfSync();
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getTargetWorld().getProperties())) {
            GPTimings.ENTITY_EXPLOSION_PRE_EVENT.stopTimingIfSync();
            return;
        }

        Claim claim =  GriefPrevention.instance.dataStore.getClaimAt(event.getTargetWorld().getLocation(event.getExplosion().getOrigin()), false, null);

        Optional<User> user = event.getCause().first(User.class);
        Object source = event.getCause().root();
        Optional<Explosive> explosive = Optional.empty();
        if (event.getExplosion() instanceof Explosion) {
            explosive = ((Explosion) event.getExplosion()).getSourceExplosive();
        }

        if (explosive.isPresent()) {
            Entity entity = (Entity) explosive.get();

            if (!user.isPresent()) {
                Optional<UUID> uuid = entity.getCreator();
                if (uuid.isPresent()) {
                    user = Sponge.getServiceManager().provide(UserStorageService.class).get().get(uuid.get());
                }
            }
            if(GPPermissionHandler.getClaimPermission(claim, GPPermissions.ENTITY_EXPLOSION, entity, null, user) == Tristate.FALSE) {
                event.setCancelled(true);
                GPTimings.ENTITY_EXPLOSION_PRE_EVENT.stopTimingIfSync();
                return;
            }
        }

        if (GPPermissionHandler.getClaimPermission(claim, GPPermissions.EXPLOSION, source, null, user) != Tristate.TRUE) {
            event.setCancelled(true);
        }
        GPTimings.ENTITY_EXPLOSION_PRE_EVENT.stopTimingIfSync();
    }

    @Listener(order = Order.FIRST)
    public void onEntityExplosionDetonate(ExplosionEvent.Detonate event) {
        GPTimings.ENTITY_EXPLOSION_DETONATE_EVENT.startTimingIfSync();
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getTargetWorld().getProperties())) {
            GPTimings.ENTITY_EXPLOSION_DETONATE_EVENT.stopTimingIfSync();
            return;
        }

        Optional<User> user = event.getCause().first(User.class);
        Iterator<Entity> iterator = event.getEntities().iterator();
        while (iterator.hasNext()) {
            Entity entity = iterator.next();
            Claim claim =  GriefPrevention.instance.dataStore.getClaimAt(entity.getLocation(), false, null);

            if (GPPermissionHandler.getClaimPermission(claim, GPPermissions.ENTITY_DAMAGE, event.getCause().root(), entity, user) == Tristate.FALSE) {
                iterator.remove();
            }
        }
        GPTimings.ENTITY_EXPLOSION_DETONATE_EVENT.stopTimingIfSync();
    }

    // when a creature spawns...
    @Listener(order = Order.FIRST)
    public void onEntitySpawn(SpawnEntityEvent event, @First SpawnCause spawnCause) {
        GPTimings.ENTITY_SPAWN_EVENT.startTimingIfSync();
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getTargetWorld().getProperties())) {
            GPTimings.ENTITY_SPAWN_EVENT.stopTimingIfSync();
            return;
        }

        Optional<User> user = event.getCause().first(User.class);
        event.filterEntities(new Predicate<Entity>() {
            @Override
            public boolean test(Entity entity) {
                if (entity instanceof EntityItem) {
                    return true;
                }

                Claim claim = GriefPrevention.instance.dataStore.getClaimAt(entity.getLocation(), false, null);
                if (claim != null) {
                    if (user.isPresent() && claim.allowAccess(user.get()) == null) {
                        return true;
                    }

                    net.minecraft.entity.Entity nmsEntity = (net.minecraft.entity.Entity) entity;
                    if (SpongeImplHooks.isCreatureOfType(nmsEntity, EnumCreatureType.AMBIENT)
                            && GPPermissionHandler.getClaimPermission(claim, GPPermissions.ENTITY_SPAWN, spawnCause, entity, user) == Tristate.FALSE) {
                        GriefPrevention.addLogEntry("[Event: SpawnEntityEvent][RootCause: " + event.getCause().root() + "][Entity: " + entity + "][FilterReason: Not allowed to spawn ambients within claim.]", CustomLogEntryTypes.Debug);
                        return false;
                    } else if (SpongeImplHooks.isCreatureOfType(nmsEntity, EnumCreatureType.WATER_CREATURE) 
                            && GPPermissionHandler.getClaimPermission(claim, GPPermissions.ENTITY_SPAWN, spawnCause, entity, user) != Tristate.TRUE) {
                        GriefPrevention.addLogEntry("[Event: SpawnEntityEvent][RootCause: " + event.getCause().root() + "][Entity: " + entity + "][FilterReason: Not allowed to spawn aquatics within claim.]", CustomLogEntryTypes.Debug);
                        return false;
                    } else if (SpongeImplHooks.isCreatureOfType(nmsEntity, EnumCreatureType.MONSTER)
                            && GPPermissionHandler.getClaimPermission(claim, GPPermissions.ENTITY_SPAWN, spawnCause, entity, user) == Tristate.FALSE) {
                        GriefPrevention.addLogEntry("[Event: SpawnEntityEvent][RootCause: " + event.getCause().root() + "][Entity: " + entity + "][FilterReason: Not allowed to spawn monsters within claim.]", CustomLogEntryTypes.Debug);
                        return false;
                    } else if (SpongeImplHooks.isCreatureOfType(nmsEntity, EnumCreatureType.CREATURE)
                            && GPPermissionHandler.getClaimPermission(claim, GPPermissions.ENTITY_SPAWN, spawnCause, entity, user) == Tristate.FALSE) {
                        GriefPrevention.addLogEntry("[Event: SpawnEntityEvent][RootCause: " + event.getCause().root() + "][Entity: " + entity + "][FilterReason: Not allowed to spawn animals within claim.]", CustomLogEntryTypes.Debug);
                        return false;
                    }
                }
                return true;
            }
        });

        for (Entity entity : event.getEntities()) {
            final Location<World> location = entity.getLocation();
            // these rules apply only to creative worlds
            if (!GriefPrevention.instance.claimModeIsActive(location.getExtent().getProperties(), ClaimsMode.Creative)) {
                GPTimings.ENTITY_SPAWN_EVENT.stopTimingIfSync();
                return;
            }

            final Cause cause = event.getCause();
            final Player player = cause.first(Player.class).orElse(null);
            final ItemStack stack = cause.first(ItemStack.class).orElse(null);

            if (player != null) {
                if (stack != null && !stack.getItem().equals(ItemTypes.SPAWN_EGG)) {
                    GriefPrevention.addLogEntry("[Event: SpawnEntityEvent][RootCause: " + event.getCause().root() + "][Entity: " + entity + "][FilterReason: Cannot spawn entities in creative worlds.]", CustomLogEntryTypes.Debug);
                    event.setCancelled(true);
                    GPTimings.ENTITY_SPAWN_EVENT.stopTimingIfSync();
                    return;
                }
            }

            // otherwise, just apply the limit on total entities per claim (and no spawning in the wilderness!)
            Claim claim = this.dataStore.getClaimAt(location, false, null);
            String denyReason = claim.allowMoreEntities();
            if (denyReason != null) {
                GriefPrevention.addLogEntry("[Event: SpawnEntityEvent][RootCause: " + event.getCause().root() + "][Entity: " + entity + "][CancelReason: " + denyReason + "]", CustomLogEntryTypes.Debug);
                event.setCancelled(true);
            }
        }
        GPTimings.ENTITY_SPAWN_EVENT.stopTimingIfSync();
    }

    @Listener(order = Order.FIRST)
    public void onEntityAttack(AttackEntityEvent event, @First DamageSource damageSource) {
        GPTimings.ENTITY_ATTACK_EVENT.startTimingIfSync();
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getTargetEntity().getWorld().getProperties())) {
            GPTimings.ENTITY_ATTACK_EVENT.stopTimingIfSync();
            return;
        }

        if (protectEntity(event.getTargetEntity(), event.getCause(), damageSource)) {
            event.setCancelled(true);
        }
        GPTimings.ENTITY_ATTACK_EVENT.stopTimingIfSync();
    }

    @Listener(order = Order.FIRST)
    public void onEntityDamage(DamageEntityEvent event, @First DamageSource damageSource) {
        GPTimings.ENTITY_DAMAGE_EVENT.startTimingIfSync();
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getTargetEntity().getWorld().getProperties())) {
            GPTimings.ENTITY_DAMAGE_EVENT.stopTimingIfSync();
            return;
        }

        if (protectEntity(event.getTargetEntity(), event.getCause(), damageSource)) {
            event.setCancelled(true);
        }
        GPTimings.ENTITY_DAMAGE_EVENT.stopTimingIfSync();
    }

    public boolean protectEntity(Entity targetEntity, Cause cause, DamageSource damageSource) {
        // monsters are never protected
        Optional<Player> player = cause.first(Player.class);
        if (player.isPresent()) {
            PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(targetEntity.getWorld(), player.get().getUniqueId());
            if (playerData.ignoreClaims) {
                return false;
            }
        }
        if (targetEntity instanceof Monster || !GriefPrevention.isEntityProtected(targetEntity)) {
            return false;
        }

        Claim claim = this.dataStore.getClaimAt(targetEntity.getLocation(), false, null);

        // Protect owned entities anywhere in world
        if (damageSource instanceof EntityDamageSource && !(targetEntity instanceof Player) && !(SpongeImplHooks.isCreatureOfType((net.minecraft.entity.Entity) targetEntity, EnumCreatureType.MONSTER))) {
            EntityDamageSource entityDamageSource = (EntityDamageSource) damageSource;
            Entity sourceEntity = entityDamageSource.getSource();
            if (entityDamageSource instanceof IndirectEntityDamageSource) {
                sourceEntity = ((IndirectEntityDamageSource) entityDamageSource).getIndirectSource();
            }

            Tristate perm = Tristate.UNDEFINED;
            if (sourceEntity instanceof User) {
                User sourceUser = (User) sourceEntity;
                if (sourceUser instanceof Player) {
                    PlayerData playerData = GriefPrevention.instance.dataStore.getPlayerData(targetEntity.getWorld(), sourceUser.getUniqueId());
                    if (playerData.ignoreClaims) {
                        return false;
                    }
                }
                perm = GPPermissionHandler.getClaimPermission(claim, GPPermissions.ENTITY_DAMAGE, sourceEntity, targetEntity, Optional.of(sourceUser));
                if (targetEntity instanceof EntityLivingBase && perm == Tristate.TRUE) {
                    return false;
                }
                Optional<UUID> creatorUuid = targetEntity.getCreator();
                if (creatorUuid.isPresent()) {
                    Optional<User> user = Sponge.getGame().getServiceManager().provide(UserStorageService.class).get().get(creatorUuid.get());
                    if (user.isPresent() && !user.get().getUniqueId().equals(sourceUser.getUniqueId())) {
                        return true;
                    }
                } else if (sourceUser.getUniqueId().equals(claim.ownerID)) {
                    return true;
                }

                return false;
            } else {
                if (targetEntity instanceof Player) {
                    if (SpongeImplHooks.isCreatureOfType((net.minecraft.entity.Entity) entityDamageSource.getSource(), EnumCreatureType.MONSTER)) {
                        Optional<User> user = cause.first(User.class);
                        if (!user.isPresent()) {
                            user = ((IMixinEntity) entityDamageSource.getSource()).getTrackedPlayer(NbtDataUtil.SPONGE_ENTITY_CREATOR);
                        }
                        if (GPPermissionHandler.getClaimPermission(claim, GPPermissions.ENTITY_DAMAGE, entityDamageSource.getSource(), targetEntity, user) != Tristate.TRUE) {
                            GriefPrevention.addLogEntry("[Event: DamageEntityEvent][RootCause: " + cause.root() + "][Entity: " + targetEntity + "][CancelReason: Monsters not allowed to attack players within claim.]", CustomLogEntryTypes.Debug);
                            return true;
                        }
                    }
                } else if (targetEntity instanceof EntityLivingBase && !SpongeImplHooks.isCreatureOfType((net.minecraft.entity.Entity) targetEntity, EnumCreatureType.MONSTER)) {
                    User user = cause.first(User.class).orElse(null);
                    if (user != null && !user.getUniqueId().equals(claim.ownerID) && perm != Tristate.TRUE) {
                        GriefPrevention.addLogEntry("[Event: DamageEntityEvent][RootCause: " + cause.root() + "][Entity: " + targetEntity + "][CancelReason: Untrusted player attempting to attack entity in claim.]", CustomLogEntryTypes.Debug);
                        return true;
                    }
                }
            }
        }

        // the rest is only interested in entities damaging entities (ignoring environmental damage)
        if (!(damageSource instanceof EntityDamageSource)) {
            return false;
        }

        EntityDamageSource entityDamageSource = (EntityDamageSource) damageSource;
        // determine which player is attacking, if any
        Player attacker = null;
        Projectile arrow = null;
        Entity sourceEntity = entityDamageSource.getSource();

        if (sourceEntity != null) {
            if (sourceEntity instanceof Player) {
                attacker = (Player) sourceEntity;
            } else if (sourceEntity instanceof Projectile) {
                arrow = (Projectile) sourceEntity;
                if (arrow.getShooter() instanceof Player) {
                    attacker = (Player) arrow.getShooter();
                }
            }
        }

        // if the attacker is a player and defender is a player (pvp combat)
        if (attacker != null && targetEntity instanceof Player && GriefPrevention.instance.pvpRulesApply(attacker.getWorld())) {
            // FEATURE: prevent pvp in the first minute after spawn, and prevent pvp when one or both players have no inventory
            Player defender = (Player) (targetEntity);

            if (attacker != defender) {
                PlayerData defenderData = this.dataStore.getPlayerData(defender.getWorld().getProperties(), defender.getUniqueId());
                PlayerData attackerData = this.dataStore.getPlayerData(attacker.getWorld().getProperties(), attacker.getUniqueId());

                // otherwise if protecting spawning players
                if (defenderData.pvpImmune) {
                    GriefPrevention.addLogEntry("[Event: DamageEntityEvent][RootCause: " + cause.root() + "][Entity: " + targetEntity + "][CancelReason: Defender PVP Immune.]", CustomLogEntryTypes.Debug);
                    GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.ThatPlayerPvPImmune);
                    return true;
                }

                if (attackerData.pvpImmune) {
                    GriefPrevention.addLogEntry("[Event: DamageEntityEvent][RootCause: " + cause.root() + "][Entity: " + targetEntity + "][CancelReason: Attacker PVP Immune.]", CustomLogEntryTypes.Debug);
                    GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.CantFightWhileImmune);
                    return true;
                }

                // FEATURE: prevent players from engaging in PvP combat inside land claims (when it's disabled)
                Claim attackerClaim = this.dataStore.getClaimAt(attacker.getLocation(), false, attackerData.lastClaim);
                if (!attackerData.ignoreClaims) {
                    // ignore claims mode allows for pvp inside land claims
                    if (attackerClaim != null && !attackerData.inPvpCombat(defender.getWorld()) && attackerClaim.protectPlayersInClaim()) {
                        attackerData.lastClaim = attackerClaim;
                        PreventPvPEvent pvpEvent = new PreventPvPEvent(attackerClaim);
                        Sponge.getGame().getEventManager().post(pvpEvent);
                        if (!pvpEvent.isCancelled()) {
                            pvpEvent.setCancelled(true);
                            GriefPrevention.addLogEntry("[Event: DamageEntityEvent][RootCause: " + cause.root() + "][Entity: " + targetEntity + "][CancelReason: Player in PVP Safe Zone.]", CustomLogEntryTypes.Debug);
                            GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.PlayerInPvPSafeZone);
                            return true;
                        }
                    }

                    Claim defenderClaim = this.dataStore.getClaimAt(defender.getLocation(), false, defenderData.lastClaim);
                    if (defenderClaim != null && !defenderData.inPvpCombat(defender.getWorld()) && defenderClaim.protectPlayersInClaim()) {
                        defenderData.lastClaim = defenderClaim;
                        PreventPvPEvent pvpEvent = new PreventPvPEvent(defenderClaim);
                        Sponge.getGame().getEventManager().post(pvpEvent);
                        if (!pvpEvent.isCancelled()) {
                            pvpEvent.setCancelled(true);
                            GriefPrevention.addLogEntry("[Event: DamageEntityEvent][RootCause: " + cause.root() + "][Entity: " + targetEntity + "][CancelReason: Player in PVP Safe Zone.]", CustomLogEntryTypes.Debug);
                            GriefPrevention.sendMessage(attacker, TextMode.Err, Messages.PlayerInPvPSafeZone);
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    @Listener(order = Order.POST)
    public void onEntityDamageMonitor(DamageEntityEvent event) {
        GPTimings.ENTITY_DAMAGE_MONITOR_EVENT.startTimingIfSync();
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getTargetEntity().getWorld().getProperties())) {
            GPTimings.ENTITY_DAMAGE_MONITOR_EVENT.stopTimingIfSync();
            return;
        }

        //FEATURE: prevent players who very recently participated in pvp combat from hiding inventory to protect it from looting
        //FEATURE: prevent players who are in pvp combat from logging out to avoid being defeated

        if (event.getTargetEntity().getType() != EntityTypes.PLAYER || !GriefPrevention.isEntityProtected(event.getTargetEntity())) {
            GPTimings.ENTITY_DAMAGE_MONITOR_EVENT.stopTimingIfSync();
            return;
        }

        Player defender = (Player) event.getTargetEntity();

        //only interested in entities damaging entities (ignoring environmental damage)
        // the rest is only interested in entities damaging entities (ignoring environmental damage)
        if (!(event.getCause().root() instanceof EntityDamageSource)) {
            GPTimings.ENTITY_DAMAGE_MONITOR_EVENT.stopTimingIfSync();
            return;
        }

        EntityDamageSource entityDamageSource = (EntityDamageSource) event.getCause().root();

        //if not in a pvp rules world, do nothing
        if (!GriefPrevention.instance.pvpRulesApply(defender.getWorld())) {
            GPTimings.ENTITY_DAMAGE_MONITOR_EVENT.stopTimingIfSync();
            return;
        }

        //determine which player is attacking, if any
        Player attacker = null;
        Projectile arrow = null;
        Entity damageSource = entityDamageSource.getSource();

        if (damageSource != null) {
            if (damageSource instanceof Player) {
                attacker = (Player) damageSource;
            } else if (damageSource instanceof Projectile) {
                arrow = (Projectile) damageSource;
                if (arrow.getShooter() instanceof Player) {
                    attacker = (Player) arrow.getShooter();
                }
            }
        }

        //if attacker not a player, do nothing
        if (attacker == null) {
            GPTimings.ENTITY_DAMAGE_MONITOR_EVENT.stopTimingIfSync();
            return;
        }

        PlayerData defenderData = this.dataStore.getPlayerData(defender.getWorld().getProperties(), defender.getUniqueId());
        PlayerData attackerData = this.dataStore.getPlayerData(attacker.getWorld().getProperties(), attacker.getUniqueId());
        Claim attackerClaim = this.dataStore.getClaimAtPlayer(attacker, false);
        Claim defenderClaim = this.dataStore.getClaimAtPlayer(defender, false);

        if (attacker != defender) {
            long now = Calendar.getInstance().getTimeInMillis();
            if (defenderClaim != null) {
                if (GriefPrevention.getActiveConfig(defender.getWorld().getProperties()).getConfig().pvp.protectPlayersInClaims) {
                    GPTimings.ENTITY_DAMAGE_MONITOR_EVENT.stopTimingIfSync();
                    return;
                }
            } else if (attackerClaim != null) {
                if (GriefPrevention.getActiveConfig(attacker.getWorld().getProperties()).getConfig().pvp.protectPlayersInClaims) {
                    GPTimings.ENTITY_DAMAGE_MONITOR_EVENT.stopTimingIfSync();
                    return;
                }
            }

            defenderData.lastPvpTimestamp = now;
            defenderData.lastPvpPlayer = attacker.getName();
            attackerData.lastPvpTimestamp = now;
            attackerData.lastPvpPlayer = defender.getName();
        }
        GPTimings.ENTITY_DAMAGE_MONITOR_EVENT.stopTimingIfSync();
    }

    // when an entity drops items on death
    @Listener(order = Order.FIRST)
    public void onEntityDropItemDeath(DropItemEvent.Destruct event, @Root Living livingEntity) {
        GPTimings.ENTITY_DROP_ITEM_DEATH_EVENT.startTimingIfSync();
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getTargetWorld().getProperties())) {
            GPTimings.ENTITY_DROP_ITEM_DEATH_EVENT.stopTimingIfSync();
            return;
        }

        // special rule for creative worlds: killed entities don't drop items or experience orbs
        if (GriefPrevention.instance.claimModeIsActive(livingEntity.getLocation().getExtent().getProperties(), ClaimsMode.Creative)) {
            GriefPrevention.addLogEntry("[Event: DamageEntityEvent][RootCause: " + event.getCause().root() + "][CancelReason: Drops not allowed in creative worlds.]", CustomLogEntryTypes.Debug);
            event.setCancelled(true);
            GPTimings.ENTITY_DROP_ITEM_DEATH_EVENT.stopTimingIfSync();
            return;
        }

        if (livingEntity instanceof Player) {
            Player player = (Player) livingEntity;
            PlayerData playerData = this.dataStore.getPlayerData(player.getWorld().getProperties(), player.getUniqueId());

            // if involved in a siege
            if (playerData.siegeData != null) {
                // end it, with the dieing player being the loser
                this.dataStore.endSiege(playerData.siegeData,
                        event.getCause().first(Player.class).isPresent() ? event.getCause().first(Player.class).get().getName() : null,
                        player.getName(), true);
                // don't drop items as usual, they will be sent to the siege winner
                GriefPrevention.addLogEntry("[Event: DropItemEvent.Destruct][RootCause: " + event.getCause().root() + "][CancelReason: Siege in progress.]", CustomLogEntryTypes.Debug);
                event.setCancelled(true);
            }
        }
        GPTimings.ENTITY_DROP_ITEM_DEATH_EVENT.stopTimingIfSync();
    }

    // when an entity dies...
    @Listener(order = Order.LAST)
    public void onEntityDeath(DestructEntityEvent.Death event) {
        GPTimings.ENTITY_DEATH_EVENT.startTimingIfSync();
        Living entity = event.getTargetEntity();
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getTargetEntity().getWorld().getProperties())) {
            GPTimings.ENTITY_DEATH_EVENT.stopTimingIfSync();
            return;
        }

        if (!(entity instanceof Player) || !event.getCause().first(EntityDamageSource.class).isPresent()) {
            GPTimings.ENTITY_DEATH_EVENT.stopTimingIfSync();
            return;
        }
        // don't do the rest in worlds where claims are not enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(entity.getWorld().getProperties())) {
            GPTimings.ENTITY_DEATH_EVENT.stopTimingIfSync();
            return;
        }

        Player player = (Player) entity;
        PlayerData playerData = this.dataStore.getPlayerData(player.getWorld().getProperties(), player.getUniqueId());
        EntityDamageSource damageSource = event.getCause().first(EntityDamageSource.class).get();

        // if involved in a siege
        if (playerData.siegeData != null) {
            // end it, with the dying player being the loser
            this.dataStore.endSiege(playerData.siegeData,
                    damageSource.getSource() != null ? ((net.minecraft.entity.Entity) damageSource.getSource()).getName() : null, player.getName(),
                    true);
        }
        GPTimings.ENTITY_DEATH_EVENT.stopTimingIfSync();
    }

    @Listener
    public void onEntityMove(DisplaceEntityEvent.Move event){
        GPTimings.ENTITY_MOVE_EVENT.startTimingIfSync();
        Entity entity = event.getTargetEntity();
        World world = event.getTargetEntity().getWorld();
        if (!GriefPrevention.instance.claimsEnabledForWorld(world.getProperties())) {
            GPTimings.ENTITY_MOVE_EVENT.stopTimingIfSync();
            return;
        }

        Player player = null;
        PlayerData playerData = null;
        User owner = null;
        if (entity instanceof Player) {
            player = (Player) entity;
            playerData = this.dataStore.getPlayerData(world, player.getUniqueId());
        } else {
            if (((net.minecraft.entity.Entity) entity).riddenByEntity instanceof Player) {
                player = (Player) ((net.minecraft.entity.Entity) entity).riddenByEntity;
                playerData = this.dataStore.getPlayerData(world, player.getUniqueId());
            }
            owner = ((IMixinEntity) entity).getTrackedPlayer(NbtDataUtil.SPONGE_ENTITY_CREATOR).orElse(null);
        }

        if (player == null && owner == null) {
            GPTimings.ENTITY_MOVE_EVENT.stopTimingIfSync();
            return;
        }

        Claim fromClaim = this.dataStore.getClaimAt(event.getFromTransform().getLocation(), false, null);
        Claim toClaim = this.dataStore.getClaimAt(event.getToTransform().getLocation(), false, null);
        if (toClaim != null && toClaim.isWildernessClaim()) {
            GPTimings.ENTITY_MOVE_EVENT.stopTimingIfSync();
            return;
        }

        Optional<User> user = player != null ? Optional.of(player) : Optional.of(owner);
        // enter
        if (fromClaim != toClaim && toClaim != null) {
            if (GPPermissionHandler.getClaimPermission(toClaim, GPPermissions.ENTER_CLAIM, user.orElse(null), entity, user) == Tristate.FALSE) {
                if (player != null) {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoEnterClaim);
                    GriefPrevention.addLogEntry("[Event: DropItemEvent.Dispense][RootCause: " + event.getCause().root() + "][CancelReason: " + this.dataStore.getMessage(Messages.NoEnterClaim) + "]", CustomLogEntryTypes.Debug);
                }
                event.setCancelled(true);
                GPTimings.ENTITY_MOVE_EVENT.stopTimingIfSync();
                return;
            }

            if (playerData != null) {
                playerData.lastClaim = toClaim;
                Text welcomeMessage = toClaim.getClaimData().getGreetingMessage();
                if (!welcomeMessage.equals(Text.of())) {
                    player.sendMessage(welcomeMessage);
                }
            }
        }

        // exit
        if (fromClaim != null && fromClaim != toClaim) {
            if (GPPermissionHandler.getClaimPermission(toClaim, GPPermissions.EXIT_CLAIM, user.orElse(null), entity, user) == Tristate.FALSE) {
                if (player != null) {
                    GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoExitClaim);
                    GriefPrevention.addLogEntry("[Event: DropItemEvent.Dispense][RootCause: " + event.getCause().root() + "][CancelReason: " + this.dataStore.getMessage(Messages.NoExitClaim) + "]", CustomLogEntryTypes.Debug);
                }
                event.setCancelled(true);
                GPTimings.ENTITY_MOVE_EVENT.stopTimingIfSync();
                return;
            }

            if (playerData != null) {
                playerData.lastClaim = toClaim;
                Text farewellMessage = fromClaim.getClaimData().getFarewellMessage();
                if (!farewellMessage.equals(Text.of())) {
                    player.sendMessage(farewellMessage);
                }
            }
        }
        GPTimings.ENTITY_MOVE_EVENT.stopTimingIfSync();
    }

    // when a player teleports
    @Listener(order = Order.FIRST)
    public void onEntityTeleport(DisplaceEntityEvent.Teleport event) {
        GPTimings.ENTITY_TELEPORT_EVENT.startTimingIfSync();
        Entity entity = event.getTargetEntity();
        Player player = null;
        Optional<User> user = Optional.empty();
        if (entity instanceof Player) {
            player = (Player) entity;
            user = Optional.of(player);
        } else {
            user = ((IMixinEntity) entity).getTrackedPlayer(NbtDataUtil.SPONGE_ENTITY_CREATOR);
        }

        if (!user.isPresent() || !GriefPrevention.instance.claimsEnabledForWorld(event.getFromTransform().getExtent().getProperties())) {
            GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
            return;
        }

        TeleportType type = event.getCause().first(TeleportCause.class).get().getTeleportType();
        EntityTeleportCause entityTeleportCause = null;
        if (type == TeleportTypes.ENTITY_TELEPORT) {
            entityTeleportCause = (EntityTeleportCause) event.getCause().first(EntityTeleportCause.class).get();
        }

        // FEATURE: prevent teleport abuse to win sieges

        Location<World> sourceLocation = event.getFromTransform().getLocation();
        Claim sourceClaim = null;
        if (player != null) {
            sourceClaim = this.dataStore.getClaimAtPlayer(player, false);
        } else {
            sourceClaim = this.dataStore.getClaimAt(sourceLocation, false, null);
        }

        if (sourceClaim != null) {
            if (player != null && GriefPrevention.getActiveConfig(sourceLocation.getExtent().getProperties()).getConfig().siege.siegeEnabled && sourceClaim.siegeData != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.SiegeNoTeleport);
                GriefPrevention.addLogEntry("[Event: DisplaceEntityEvent.Teleport][RootCause: " + event.getCause().root() + "][CancelReason: " + this.dataStore.getMessage(Messages.SiegeNoTeleport) + "]", CustomLogEntryTypes.Debug);
                event.setCancelled(true);
                GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
                return;
            }
            if (entityTeleportCause != null) {
                if (GPPermissionHandler.getClaimPermission(sourceClaim, GPPermissions.ENTITY_TELEPORT_FROM, entityTeleportCause.getTeleportType().getId(), entity, user) == Tristate.TRUE) {
                    GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
                    return;
                } else if (GPPermissionHandler.getClaimPermission(sourceClaim, GPPermissions.ENTITY_TELEPORT_FROM, entityTeleportCause.getTeleportType().getId(), entity, user) == Tristate.FALSE) {
                    if (player != null) {
                        GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoTeleportFromProtectedClaim, sourceClaim.getOwnerName());
                        GriefPrevention.addLogEntry("[Event: DisplaceEntityEvent.Teleport][RootCause: " + event.getCause().root() + "][CancelReason: " + this.dataStore.getMessage(Messages.NoTeleportFromProtectedClaim) + "]", CustomLogEntryTypes.Debug);
                    }
                    event.setCancelled(true);
                    GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
                    return;
                }
            } else if (type.equals(TeleportTypes.PORTAL)) {
                if (GPPermissionHandler.getClaimPermission(sourceClaim, GPPermissions.PORTAL_USE, type.getId(), entity, user) == Tristate.TRUE) {
                    GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
                    return;
                } else if (GPPermissionHandler.getClaimPermission(sourceClaim, GPPermissions.PORTAL_USE, type.getId(), entity, user) == Tristate.FALSE) {
                    if (player != null) {
                        GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoPortalToProtectedClaim, sourceClaim.getOwnerName());
                        GriefPrevention.addLogEntry("[Event: DisplaceEntityEvent.Teleport][RootCause: " + event.getCause().root() + "][CancelReason: " + this.dataStore.getMessage(Messages.NoPortalToProtectedClaim) + "]", CustomLogEntryTypes.Debug);
                    }
                    event.setCancelled(true);
                    GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
                    return;
                } else if (GPPermissionHandler.getClaimPermission(sourceClaim, GPPermissions.BLOCK_PLACE, entity, null, user) == Tristate.FALSE) {
                    if (player != null) {
                        GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoBuildPortalPermission, sourceClaim.getOwnerName());
                        GriefPrevention.addLogEntry("[Event: DisplaceEntityEvent.Portal][RootCause: " + event.getCause().root() + "][CancelReason: " + this.dataStore.getMessage(Messages.NoBuildPortalPermission) + "]", CustomLogEntryTypes.Debug);
                    }
                    event.setCancelled(true);
                    GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
                    return;
                }
            }
        }

        // check if destination world is enabled
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getToTransform().getExtent().getProperties())) {
            GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
            return;
        }

        Location<World> destination = event.getToTransform().getLocation();
        Claim toClaim = this.dataStore.getClaimAt(destination, false, null);
        if (toClaim != null) {
            if (player != null && GriefPrevention.getActiveConfig(destination.getExtent().getProperties()).getConfig().siege.siegeEnabled && toClaim.siegeData != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.BesiegedNoTeleport);
                GriefPrevention.addLogEntry("[Event: DisplaceEntityEvent.Teleport][RootCause: " + event.getCause().root() + "][CancelReason: " + this.dataStore.getMessage(Messages.BesiegedNoTeleport) + "]", CustomLogEntryTypes.Debug);
                event.setCancelled(true);
                GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
                return;
            }

            // FEATURE: prevent players from using entities to gain access to secured claims
            if (entityTeleportCause != null) {
                if (GPPermissionHandler.getClaimPermission(toClaim, GPPermissions.ENTITY_TELEPORT_TO, entityTeleportCause.getTeleportType().getId(), entity, user) == Tristate.TRUE) {
                    GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
                    return;
                } else if (GPPermissionHandler.getClaimPermission(toClaim, GPPermissions.ENTITY_TELEPORT_TO, entityTeleportCause.getTeleportType().getId(), entity, user) == Tristate.FALSE) {
                    if (player != null) {
                        GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoTeleportToProtectedClaim, toClaim.getOwnerName());
                        GriefPrevention.addLogEntry("[Event: DisplaceEntityEvent.Teleport][RootCause: " + event.getCause().root() + "][CancelReason: " + this.dataStore.getMessage(Messages.NoTeleportToProtectedClaim) + "]", CustomLogEntryTypes.Debug);
                    }
                    event.setCancelled(true);
                    GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
                    return;
                }
                String denyReason = toClaim.allowAccess(user.get());
                if (denyReason != null) {
                    if (player != null) {
                        GriefPrevention.sendMessage(player, Text.of(TextMode.Err, denyReason));
                        GriefPrevention.addLogEntry("[Event: DisplaceEntityEvent.Teleport][RootCause: " + event.getCause().root() + "][CancelReason: " + denyReason + "]", CustomLogEntryTypes.Debug);
                    }
                    event.setCancelled(true);
                    if (entityTeleportCause != null && entityTeleportCause.getTeleporter().getType().equals(EntityTypes.ENDER_PEARL)) {
                        ((EntityPlayer) player).inventory.addItemStackToInventory(new net.minecraft.item.ItemStack(Items.ender_pearl));
                    }
                    GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
                    return;
                }
            } else if (type.equals(TeleportTypes.PORTAL)) {
                if (GPPermissionHandler.getClaimPermission(toClaim, GPPermissions.PORTAL_USE, entity, null, user) == Tristate.TRUE) {
                    GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
                    return;
                } else if (GPPermissionHandler.getClaimPermission(toClaim, GPPermissions.PORTAL_USE, entity, null, user) == Tristate.FALSE) {
                    if (player != null) {
                        GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoPortalToProtectedClaim, toClaim.getOwnerName());
                        GriefPrevention.addLogEntry("[Event: DisplaceEntityEvent.Teleport][RootCause: " + event.getCause().root() + "][CancelReason: " + this.dataStore.getMessage(Messages.NoPortalToProtectedClaim) + "]", CustomLogEntryTypes.Debug);
                    }
                    event.setCancelled(true);
                    GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
                    return;
                } else if (GPPermissionHandler.getClaimPermission(toClaim, GPPermissions.BLOCK_PLACE, entity, null, user) == Tristate.FALSE) {
                    if (player != null) {
                        GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoBuildPortalPermission, toClaim.getOwnerName());
                        GriefPrevention.addLogEntry("[Event: DisplaceEntityEvent.Portal][RootCause: " + event.getCause().root() + "][CancelReason: " + this.dataStore.getMessage(Messages.NoBuildPortalPermission) + "]", CustomLogEntryTypes.Debug);
                    }
                    event.setCancelled(true);
                    GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
                    return;
                }
            } else if (type.equals(TeleportTypes.COMMAND)) {
                // TODO
            }
        }

        if (player != null && !sourceLocation.getExtent().getUniqueId().equals(destination.getExtent().getUniqueId())) {
            // new world, check if player has world storage for it
            ClaimWorldManager claimWorldManager = GriefPrevention.instance.dataStore.getClaimWorldManager(destination.getExtent().getProperties());

            // update lastActive timestamps for claims this player owns
            WorldProperties worldProperties = destination.getExtent().getProperties();
            UUID playerUniqueId = player.getUniqueId();
            for (Claim claim : this.dataStore.getClaimWorldManager(worldProperties).getWorldClaims()) {
                if (claim.ownerID.equals(playerUniqueId)) {
                    // update lastActive timestamp for claim
                    claim.getClaimData().setDateLastActive(Instant.now().toString());
                    claimWorldManager.addWorldClaim(claim);
                } else if (claim.parent != null && claim.parent.ownerID.equals(playerUniqueId)) {
                    // update lastActive timestamp for subdivisions if parent owner logs on
                    claim.getClaimData().setDateLastActive(Instant.now().toString());
                    claimWorldManager.addWorldClaim(claim);
                }
            }
        }

        // TODO
        /*if (event.getCause().first(PortalTeleportCause.class).isPresent()) {
            // FEATURE: when players get trapped in a nether portal, send them back through to the other side
            CheckForPortalTrapTask task = new CheckForPortalTrapTask(player, event.getFromTransform().getLocation());
            Sponge.getGame().getScheduler().createTaskBuilder().delayTicks(200).execute(task).submit(GriefPrevention.instance);
        }*/
        GPTimings.ENTITY_TELEPORT_EVENT.stopTimingIfSync();
    }

    @Listener(order = Order.FIRST)
    public void onProjectileImpactEntity(CollideEntityEvent.Impact event, @First User user) {
        GPTimings.PROJECTILE_IMPACT_ENTITY_EVENT.startTimingIfSync();
        if (!GriefPrevention.instance.claimsEnabledForWorld(event.getImpactPoint().getExtent().getProperties())) {
            GPTimings.PROJECTILE_IMPACT_ENTITY_EVENT.stopTimingIfSync();
            return;
        }

        for (Entity entity : event.getEntities()) {
            Claim targetClaim = null;
            if (user instanceof Player) {
                targetClaim = this.dataStore.getClaimAtPlayer((Player) user, event.getImpactPoint(), false);
            } else {
                targetClaim = this.dataStore.getClaimAt(event.getImpactPoint(), false, null);
            }
    
            String denyReason = targetClaim.allowAccess(user);
            if (denyReason != null) {
                if (GPPermissionHandler.getClaimPermission(targetClaim, GPPermissions.PROJECTILE_IMPACT_ENTITY, event.getCause().root(), entity, Optional.of(user)) == Tristate.TRUE) {
                    GPTimings.PROJECTILE_IMPACT_ENTITY_EVENT.stopTimingIfSync();
                    return;
                }

                GriefPrevention.addLogEntry("[Event: CollideEntityEvent.Impact][RootCause: " + event.getCause().root() + "][ImpactPoint: " + event.getImpactPoint() + "][CancelReason: " + denyReason + "]", CustomLogEntryTypes.Debug);
                event.setCancelled(true);
            }
        }
        GPTimings.PROJECTILE_IMPACT_ENTITY_EVENT.stopTimingIfSync();
    }
}