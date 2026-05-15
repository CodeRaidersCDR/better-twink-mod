package com.minemods.bettertwink.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Конфигурация для конкретного сервера
 */
public class ServerConfiguration {
    private String serverName;
    private String serverAddress;
    private String playerName; // account name (empty for legacy data)
    private Map<String, ChestConfiguration> chests; // BlockPos.toString() -> ChestConfiguration
    private boolean autoSortEnabled;
    private long lastUpdated;

    public ServerConfiguration(String serverName, String serverAddress, String playerName) {
        this.serverName   = serverName;
        this.serverAddress = serverAddress;
        this.playerName   = playerName != null ? playerName : "";
        this.chests       = new HashMap<>();
        this.autoSortEnabled = false;
        this.lastUpdated  = System.currentTimeMillis();
    }

    /** Legacy constructor — no player name. */
    public ServerConfiguration(String serverName, String serverAddress) {
        this(serverName, serverAddress, "");
    }

    public String getServerName()    { return serverName; }
    public String getServerAddress() { return serverAddress; }
    public String getPlayerName()    { return playerName; }

    public Map<String, ChestConfiguration> getChests() {
        return chests;
    }

    public void addChest(ChestConfiguration chest) {
        chests.put(chest.getPosition().toString(), chest);
        lastUpdated = System.currentTimeMillis();
    }

    public void removeChest(String posKey) {
        chests.remove(posKey);
        lastUpdated = System.currentTimeMillis();
    }

    public ChestConfiguration getChest(String posKey) {
        return chests.get(posKey);
    }

    public boolean isAutoSortEnabled() {
        return autoSortEnabled;
    }

    public void setAutoSortEnabled(boolean enabled) {
        autoSortEnabled = enabled;
        lastUpdated = System.currentTimeMillis();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ServerName",    serverName);
        tag.putString("ServerAddress", serverAddress);
        tag.putString("PlayerName",    playerName);
        tag.putBoolean("AutoSortEnabled", autoSortEnabled);
        tag.putLong("LastUpdated", lastUpdated);

        ListTag chestsTag = new ListTag();
        for (ChestConfiguration chest : chests.values()) {
            chestsTag.add(chest.serializeNBT());
        }
        tag.put("Chests", chestsTag);

        return tag;
    }

    public static ServerConfiguration deserializeNBT(CompoundTag tag) {
        String playerName = tag.contains("PlayerName") ? tag.getString("PlayerName") : "";
        ServerConfiguration config = new ServerConfiguration(
                tag.getString("ServerName"),
                tag.getString("ServerAddress"),
                playerName
        );
        config.autoSortEnabled = tag.getBoolean("AutoSortEnabled");
        config.lastUpdated     = tag.getLong("LastUpdated");

        ListTag chestsTag = tag.getList("Chests", Tag.TAG_COMPOUND);
        for (int i = 0; i < chestsTag.size(); i++) {
            ChestConfiguration chest = ChestConfiguration.deserializeNBT(chestsTag.getCompound(i));
            config.addChest(chest);
        }

        return config;
    }
}
