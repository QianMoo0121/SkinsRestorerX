/*
 * SkinsRestorer
 *
 * Copyright (C) 2022 SkinsRestorer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */
package net.skinsrestorer.shared.storage;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.skinsrestorer.api.SkinsRestorerAPI;
import net.skinsrestorer.api.exception.NotPremiumException;
import net.skinsrestorer.api.exception.SkinRequestException;
import net.skinsrestorer.api.interfaces.ISkinStorage;
import net.skinsrestorer.api.property.IProperty;
import net.skinsrestorer.api.util.Pair;
import net.skinsrestorer.shared.exception.SkinRequestExceptionShared;
import net.skinsrestorer.shared.storage.adapter.StorageAdapter;
import net.skinsrestorer.shared.utils.C;
import net.skinsrestorer.shared.utils.connections.MineSkinAPI;
import net.skinsrestorer.shared.utils.connections.MojangAPI;
import net.skinsrestorer.shared.utils.log.SRLogger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class SkinStorage implements ISkinStorage {
    private final SRLogger logger;
    private final MojangAPI mojangAPI;
    private final MineSkinAPI mineSkinAPI;
    @Setter
    private StorageAdapter storageAdapter;

    public void preloadDefaultSkins() {
        if (!Config.DEFAULT_SKINS_ENABLED)
            return;

        List<String> toRemove = new ArrayList<>();
        Config.DEFAULT_SKINS.forEach(skin -> {
            // TODO: add try for skinUrl
            try {
                if (!C.validUrl(skin)) {
                    fetchSkinData(skin);
                }
            } catch (SkinRequestException e) {
                // removing skin from list
                toRemove.add(skin);
                logger.warning("[WARNING] DefaultSkin '" + skin + "'(.skin) could not be found or requested! Removing from list..");

                logger.debug("[DEBUG] DefaultSkin '" + skin + "' error: ", e);
            }
        });
        Config.DEFAULT_SKINS.removeAll(toRemove);

        if (Config.DEFAULT_SKINS.isEmpty()) {
            logger.warning("[WARNING] No more working DefaultSkin left... disabling feature");
            Config.DEFAULT_SKINS_ENABLED = false;
        }
    }

    @Override
    public Pair<IProperty, Boolean> getDefaultSkinForPlayer(String playerName) throws SkinRequestException {
        Pair<String, Boolean> result = getDefaultSkinName(playerName, false);
        String skin = result.getLeft();

        if (C.validUrl(skin)) {
            return Pair.of(mineSkinAPI.genSkin(skin, null), result.getRight());
        } else {
            return Pair.of(fetchSkinData(skin), result.getRight());
        }
    }

    @Override
    public IProperty fetchSkinData(String skinName) throws SkinRequestException {
        Optional<IProperty> textures = getSkinData(skinName, true);
        if (!textures.isPresent()) {
            // No cached skin found, get from MojangAPI, save and return
            try {
                textures = mojangAPI.getSkin(skinName);

                if (!textures.isPresent())
                    throw new SkinRequestExceptionShared(Message.ERROR_NO_SKIN);

                setSkinData(skinName, textures.get());

                return textures.get();
            } catch (NotPremiumException e) {
                throw new SkinRequestExceptionShared(Message.NOT_PREMIUM);
            } catch (SkinRequestException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();

                throw new SkinRequestExceptionShared(Message.WAIT_A_MINUTE);
            }
        } else {
            return textures.get();
        }
    }

    @Override
    public Optional<String> getSkinNameOfPlayer(String playerName) {
        playerName = playerName.toLowerCase();

        Optional<String> optional = storageAdapter.getStoredSkinNameOfPlayer(playerName);

        return optional.isPresent() && !optional.get().isEmpty() ? optional : Optional.empty();
    }

    /**
     * Create a platform specific property and also optionally update cached skin if outdated.
     *
     * @param playerName     the players name
     * @param updateOutdated whether the skin data shall be looked up again if the timestamp is too far away
     * @param value          skin data value
     * @param signature      signature to verify skin data
     * @param timestamp      time cached property data was created
     * @return Platform specific property
     * @throws SkinRequestException throws when no API calls were successful
     */
    private IProperty createProperty(String playerName, boolean updateOutdated, String value, String signature, long timestamp) throws SkinRequestException {
        if (updateOutdated && C.validMojangUsername(playerName) && isExpired(timestamp)) {
            Optional<IProperty> skin = mojangAPI.getSkin(playerName);

            if (skin.isPresent()) {
                setSkinData(playerName, skin.get());
                return skin.get();
            }
        }

        return SkinsRestorerAPI.getApi().createPlatformProperty(IProperty.TEXTURES_NAME, value, signature);
    }

    @Override
    public void removeSkinOfPlayer(String playerName) {
        playerName = playerName.toLowerCase();

        storageAdapter.removeStoredSkinNameOfPlayer(playerName);
    }

    @Override
    public void setSkinOfPlayer(String playerName, String skinName) {
        playerName = playerName.toLowerCase();

        storageAdapter.setStoredSkinNameOfPlayer(playerName, skinName);
    }

    // #getSkinData() also create while we have #getSkinForPlayer()
    @Override
    public Optional<IProperty> getSkinData(String skinName, boolean updateOutdated) {
        skinName = skinName.toLowerCase();

        try {
            Optional<StorageAdapter.StoredProperty> property = storageAdapter.getStoredSkinData(skinName);

            if (!property.isPresent()) {
                return Optional.empty();
            }

            return Optional.of(createProperty(skinName, updateOutdated, property.get().getValue(), property.get().getSignature(), property.get().getTimestamp()));
        } catch (SkinRequestException e) {
            logger.debug(String.format("Failed to update skin data for %s", skinName));
            return Optional.empty();
        } catch (Exception e) {
            logger.info(String.format("Unsupported skin format... removing (%s).", skinName));
            removeSkinData(skinName);
            return Optional.empty();
        }
    }

    /**
     * Removes skin data from database
     *
     * @param skinName Skin name
     */
    public void removeSkinData(String skinName) {
        skinName = skinName.toLowerCase();

        storageAdapter.removeStoredSkinData(skinName);
    }

    @Override
    public void setSkinData(String skinName, IProperty textures) {
        setSkinData(skinName, textures, System.currentTimeMillis());
    }

    @Override
    public void setSkinData(String skinName, IProperty textures, long timestamp) {
        skinName = skinName.toLowerCase();
        String value = textures.getValue();
        String signature = textures.getSignature();

        if (value.isEmpty() || signature.isEmpty())
            return;

        storageAdapter.setStoredSkinData(skinName, new StorageAdapter.StoredProperty(value, signature, timestamp));
    }

    @Override
    public boolean isInitialized() {
        return storageAdapter != null;
    }

    // TODO: CUSTOM_GUI
    // seems to be that crs order is ignored...
    public Map<String, String> getSkins(int offset) {
        return storageAdapter.getStoredSkins(offset);
    }

    /**
     * @param skinName Skin name
     * @return true on updated
     * @throws SkinRequestException On updating disabled OR invalid username + api error
     */
    // skin update [include custom skin flag]
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean updateSkinData(String skinName) throws SkinRequestException {
        if (!C.validMojangUsername(skinName))
            throw new SkinRequestExceptionShared(Message.ERROR_UPDATING_CUSTOMSKIN);

        // Check if updating is disabled for skin (by timestamp = 0)
        boolean updateDisabled = storageAdapter.getStoredTimestamp(skinName).map(timestamp -> timestamp == 0).orElse(false);

        if (updateDisabled)
            throw new SkinRequestExceptionShared(Message.ERROR_UPDATING_CUSTOMSKIN);

        // Update Skin
        try {
            Optional<String> mojangUUID = mojangAPI.getUUIDMojang(skinName);

            if (mojangUUID.isPresent()) {
                Optional<IProperty> textures = mojangAPI.getProfileMojang(mojangUUID.get());

                if (textures.isPresent()) {
                    setSkinData(skinName, textures.get());
                    return true;
                }
            }
        } catch (NotPremiumException e) {
            throw new SkinRequestExceptionShared(Message.ERROR_UPDATING_CUSTOMSKIN);
        }

        return false;
    }

    /**
     * Filters player name to exclude non [a-z_]
     * Checks and process default skin.
     * IF no default skin:
     * 1: Return player if clear
     * 2: Return skin if found
     * Else: return player
     *
     * @param playerName Player name
     * @param clear      ignore custom set skin of player
     * @return Custom skin or default skin or player name, right side indicates if it is a custom skin
     */
    public Pair<String, Boolean> getDefaultSkinName(String playerName, boolean clear) {
        // Trim player name
        playerName = playerName.trim();

        if (!clear) {
            Optional<String> playerSkinName = getSkinNameOfPlayer(playerName);

            if (playerSkinName.isPresent()) {
                return Pair.of(playerSkinName.get(), true);
            }
        }

        if (Config.DEFAULT_SKINS_ENABLED) {
            // don't return default skin name for premium players if enabled
            if (!Config.DEFAULT_SKINS_PREMIUM) {
                // check if player is premium
                try {
                    if (mojangAPI.getUUID(playerName) != null) {
                        // player is premium, return his skin name instead of default skin
                        return Pair.of(playerName, false);
                    }
                } catch (SkinRequestException ignored) {
                    // Player is not premium catching exception here to continue returning a default skin name
                }
            }

            // return default skin name if user has no custom skin set, or we want to clear to default
            List<String> skins = Config.DEFAULT_SKINS;

            // return player name if there are no default skins set
            if (skins.isEmpty())
                return Pair.of(playerName, false);

            // makes no sense to select a random skin if there is only one
            if (skins.size() == 1) {
                return Pair.of(skins.get(0), false);
            }

            return Pair.of(skins.get(ThreadLocalRandom.current().nextInt(skins.size())), false);
        }

        // empty if player has no custom skin, we'll return his name then
        return Pair.of(playerName, false);
    }

    /**
     * Checks if updating skins is disabled and if skin expired
     *
     * @param timestamp in milliseconds
     * @return true if skin is outdated
     */
    private boolean isExpired(long timestamp) {
        // Don't update if timestamp is not 0 or update is disabled.
        if (timestamp == 0 || Config.DISALLOW_AUTO_UPDATE_SKIN)
            return false;

        return timestamp + TimeUnit.MINUTES.toMillis(Config.SKIN_EXPIRES_AFTER) <= System.currentTimeMillis();
    }

    public boolean purgeOldSkins(int days) {
        long targetPurgeTimestamp = Instant.now().minus(days, ChronoUnit.DAYS).toEpochMilli();

        try {
            storageAdapter.purgeStoredOldSkins(targetPurgeTimestamp);
            return true; // TODO: Do better than true/false return
        } catch (StorageAdapter.StorageException e) {
            e.printStackTrace();
            return false;
        }
    }
}
