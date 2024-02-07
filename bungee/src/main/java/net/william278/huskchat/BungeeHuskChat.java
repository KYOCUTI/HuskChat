/*
 * This file is part of HuskChat, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.huskchat;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.william278.desertwell.util.Version;
import net.william278.huskchat.api.BungeeHuskChatAPI;
import net.william278.huskchat.command.BungeeCommand;
import net.william278.huskchat.command.ShortcutCommand;
import net.william278.huskchat.config.Locales;
import net.william278.huskchat.config.Settings;
import net.william278.huskchat.discord.DiscordHook;
import net.william278.huskchat.event.BungeeEventDispatcher;
import net.william278.huskchat.getter.BungeePermsDataGetter;
import net.william278.huskchat.getter.DataGetter;
import net.william278.huskchat.getter.DefaultDataGetter;
import net.william278.huskchat.getter.LuckPermsDataGetter;
import net.william278.huskchat.listener.BungeeListener;
import net.william278.huskchat.placeholders.DefaultReplacer;
import net.william278.huskchat.placeholders.PAPIProxyBridgeReplacer;
import net.william278.huskchat.placeholders.PlaceholderReplacer;
import net.william278.huskchat.user.BungeeUser;
import net.william278.huskchat.user.OnlineUser;
import net.william278.huskchat.user.UserCache;
import org.bstats.bungeecord.Metrics;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.*;
import java.util.logging.Level;

public final class BungeeHuskChat extends Plugin implements HuskChat {

    // bStats ID
    private static final int METRICS_ID = 11882;

    private BungeeAudiences audiences;
    private Settings settings;
    private BungeeEventDispatcher eventDispatcher;
    private DiscordHook discordHook;
    private Locales locales;
    private DataGetter playerDataGetter;
    private UserCache userCache;
    private List<PlaceholderReplacer> placeholders;

    @Override
    public void onEnable() {
        // Setup audiences
        audiences = BungeeAudiences.create(this);

        // Load config and locale files
        this.loadConfig();

        // Load discord hook
        this.loadDiscordHook();

        // Load saved social spy state
        this.userCache = new UserCache(this);

        // Setup event dispatcher
        eventDispatcher = new BungeeEventDispatcher(getProxy());
        BungeeHuskChatAPI.register(this);

        // Setup player data getter
        if (isPluginPresent("LuckPerms")) {
            this.playerDataGetter = new LuckPermsDataGetter();
        } else {
            if (isPluginPresent("BungeePerms")) {
                this.playerDataGetter = new BungeePermsDataGetter();
            } else {
                this.playerDataGetter = new DefaultDataGetter();
            }
        }

        // Setup placeholder parser
        this.placeholders = new ArrayList<>();
        this.placeholders.add(new DefaultReplacer(this));
        if (getSettings().doPlaceholderAPI() && isPluginPresent("PAPIProxyBridge")) {
            this.placeholders.add(new PAPIProxyBridgeReplacer(this));
        }

        // Register events
        getProxy().getPluginManager().registerListener(this, new BungeeListener(this));

        // Register commands & channel shortcuts
        BungeeCommand.Type.registerAll(this);
        getSettings().getChannels().values().forEach(channel -> channel.getShortcutCommands()
                .forEach(command -> new BungeeCommand(
                        new ShortcutCommand(command, channel.getId(), this), this
                )));

        // Initialise metrics and log
        new Metrics(this, METRICS_ID);
        this.checkForUpdates();
        log(Level.INFO, "Enabled HuskChat version " + this.getVersion());
    }

    @Override
    @NotNull
    public Settings getSettings() {
        return settings;
    }

    @Override
    public void setSettings(@NotNull Settings settings) {
        this.settings = settings;
    }

    @NotNull
    @Override
    public Locales getLocales() {
        return locales;
    }

    @Override
    public void setLocales(@NotNull Locales locales) {
        this.locales = locales;
    }

    @NotNull
    @Override
    public Version getVersion() {
        return Version.fromString(getDescription().getVersion());
    }

    @NotNull
    @Override
    public String getPluginDescription() {
        return getDescription().getDescription();
    }

    @NotNull
    @Override
    public String getPlatform() {
        return ProxyServer.getInstance().getName();
    }

    @NotNull
    @Override
    public List<PlaceholderReplacer> getPlaceholderReplacers() {
        return placeholders;
    }

    @Override
    @NotNull
    public DataGetter getDataGetter() {
        return playerDataGetter;
    }

    @Override
    public Optional<DiscordHook> getDiscordHook() {
        return Optional.ofNullable(discordHook);
    }

    @Override
    public void setDiscordHook(@NotNull DiscordHook discordHook) {
        this.discordHook = discordHook;
    }

    @NotNull
    public BungeeEventDispatcher getEventDispatcher() {
        return eventDispatcher;
    }

    @Override
    @NotNull
    public UserCache getPlayerCache() {
        return userCache;
    }

    @Override
    public Optional<OnlineUser> getPlayer(@NotNull UUID uuid) {
        final ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if (player != null) {
            return Optional.of(BungeeUser.adapt(player, this));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Collection<OnlineUser> getOnlinePlayers() {
        ArrayList<OnlineUser> crossPlatform = new ArrayList<>();
        for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
            crossPlatform.add(BungeeUser.adapt(player, this));
        }
        return crossPlatform;
    }

    @Override
    public Collection<OnlineUser> getOnlinePlayersOnServer(@NotNull OnlineUser user) {
        return ((BungeeUser) user).getPlayer().getServer().getInfo().getPlayers().stream()
                .map(player -> (OnlineUser) BungeeUser.adapt(player, this)).toList();
    }

    @Override
    public InputStream getResource(@NotNull String path) {
        return getResourceAsStream(path);
    }

    @Override
    public boolean isPluginPresent(@NotNull String dependency) {
        return ProxyServer.getInstance().getPluginManager().getPlugin(dependency) != null;
    }

    @Override
    public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable... exceptions) {
        if (exceptions.length > 0) {
            getLogger().log(level, message, exceptions[0]);
            return;
        }
        getLogger().log(level, message);
    }

    @Override
    public Optional<OnlineUser> findPlayer(@NotNull String username) {
        if (username.isEmpty()) {
            return Optional.empty();
        }

        final Optional<OnlineUser> optionalPlayer;
        if (ProxyServer.getInstance().getPlayer(username) != null) {
            final ProxiedPlayer player = ProxyServer.getInstance().getPlayer(username);
            optionalPlayer = Optional.of(BungeeUser.adapt(player, this));
        } else {
            final List<ProxiedPlayer> matchedPlayers = ProxyServer.getInstance().matchPlayer(username)
                    .stream().filter(val -> val.getName().startsWith(username)).sorted().toList();
            if (!matchedPlayers.isEmpty()) {
                optionalPlayer = Optional.of(BungeeUser.adapt(matchedPlayers.get(0), this));
            } else {
                optionalPlayer = Optional.empty();
            }
        }
        return optionalPlayer;
    }

    @NotNull
    @Override
    public Audience getAudience(@NotNull UUID user) {
        return audiences.player(user);
    }

    @NotNull
    @Override
    public Audience getConsole() {
        return audiences.console();
    }

    @NotNull
    @Override
    public HuskChat getPlugin() {
        return this;
    }
}