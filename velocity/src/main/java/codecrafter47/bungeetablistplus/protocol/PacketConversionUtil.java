/*
 *     Copyright (C) 2025 proferabg
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package codecrafter47.bungeetablistplus.protocol;

import com.github.retrooper.packetevents.protocol.chat.RemoteChatSession;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.PublicProfileKey;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.crypto.SignatureData;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerListHeaderAndFooter;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.crypto.IdentifiedKey;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.crypto.IdentifiedKeyImpl;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

final class PacketConversionUtil {

    private PacketConversionUtil() {
    }

    static Team toTeam(WrapperPlayServerTeams wrapper, ProtocolVersion protocolVersion) {
        Team packet = new Team();
        packet.setName(wrapper.getTeamName());
        packet.setMode(toVelocityTeamMode(wrapper.getTeamMode()));

        Optional<WrapperPlayServerTeams.ScoreBoardTeamInfo> optionalInfo = wrapper.getTeamInfo();
        if (optionalInfo.isPresent()) {
            WrapperPlayServerTeams.ScoreBoardTeamInfo info = optionalInfo.get();
            packet.setDisplayName(toComponentHolder(protocolVersion, info.getDisplayName()));
            packet.setPrefix(info.getPrefix() != null ? toComponentHolder(protocolVersion, info.getPrefix()) : null);
            packet.setSuffix(info.getSuffix() != null ? toComponentHolder(protocolVersion, info.getSuffix()) : null);
            packet.setNameTagVisibility(toVelocityNameTagVisibility(info.getTagVisibility()));
            packet.setCollisionRule(toVelocityCollisionRule(info.getCollisionRule()));
            packet.setColor(toVelocityColor(info.getColor()));
            packet.setFriendlyFire(toVelocityFriendlyFire(info.getOptionData()));
        } else {
            packet.setNameTagVisibility(Team.NameTagVisibility.ALWAYS);
            packet.setCollisionRule(Team.CollisionRule.ALWAYS);
            packet.setFriendlyFire((byte) 0);
        }

        Collection<String> players = wrapper.getPlayers();
        packet.setPlayers(players.toArray(new String[0]));
        return packet;
    }

    static LegacyPlayerListItemPacket toLegacyPlayerInfo(WrapperPlayServerPlayerInfo wrapper, ProtocolVersion protocolVersion) {
        int action = toVelocityLegacyAction(wrapper.getAction());
        List<LegacyPlayerListItemPacket.Item> items = new ArrayList<>();

        for (WrapperPlayServerPlayerInfo.PlayerData playerData : wrapper.getPlayerDataList()) {
            UserProfile profile = playerData.getUserProfile();
            if (profile == null || profile.getUUID() == null) {
                continue;
            }

            LegacyPlayerListItemPacket.Item item = new LegacyPlayerListItemPacket.Item(profile.getUUID());
            item.setName(profile.getName() != null ? profile.getName() : "");
            item.setProperties(toVelocityProperties(profile.getTextureProperties()));
            item.setGameMode(toVelocityGameMode(playerData.getGameMode()));
            item.setLatency(playerData.getPing());
            item.setDisplayName(playerData.getDisplayName());

            SignatureData signatureData = playerData.getSignatureData();
            if (signatureData != null) {
                item.setPlayerKey(toVelocityPlayerKey(signatureData, protocolVersion));
            }
            items.add(item);
        }

        return new LegacyPlayerListItemPacket(action, items);
    }

    static UpsertPlayerInfoPacket toPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate wrapper, ProtocolVersion protocolVersion) {
        UpsertPlayerInfoPacket packet = new UpsertPlayerInfoPacket();
        for (WrapperPlayServerPlayerInfoUpdate.Action action : wrapper.getActions()) {
            UpsertPlayerInfoPacket.Action mapped = toVelocityUpsertAction(action);
            if (mapped != null) {
                packet.addAction(mapped);
            }
        }

        for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo info : wrapper.getEntries()) {
            UUID profileId = info.getProfileId();
            UpsertPlayerInfoPacket.Entry entry = new UpsertPlayerInfoPacket.Entry(profileId);
            entry.setProfile(toVelocityProfile(info.getGameProfile(), profileId));
            entry.setListed(info.isListed());
            entry.setLatency(info.getLatency());
            entry.setGameMode(toVelocityGameMode(info.getGameMode()));
            entry.setDisplayName(info.getDisplayName() != null ? toComponentHolder(protocolVersion, info.getDisplayName()) : null);
            entry.setChatSession(toVelocityRemoteChatSession(info.getChatSession(), protocolVersion));
            entry.setListOrder(info.getListOrder());
            entry.setShowHat(info.isShowHat());
            packet.addEntry(entry);
        }

        return packet;
    }

    static RemovePlayerInfoPacket toPlayerInfoRemove(WrapperPlayServerPlayerInfoRemove wrapper) {
        return new RemovePlayerInfoPacket(new ArrayList<>(wrapper.getProfileIds()));
    }

    static HeaderAndFooterPacket toHeaderAndFooter(WrapperPlayServerPlayerListHeaderAndFooter wrapper, ProtocolVersion protocolVersion) {
        return new HeaderAndFooterPacket(
                toComponentHolder(protocolVersion, wrapper.getHeader()),
                toComponentHolder(protocolVersion, wrapper.getFooter())
        );
    }

    @Nullable
    static PacketWrapper<?> toWrapper(MinecraftPacket packet) {
        if (packet instanceof Team team) {
            return fromTeam(team);
        }
        if (packet instanceof LegacyPlayerListItemPacket legacy) {
            return fromLegacyPlayerInfo(legacy);
        }
        if (packet instanceof UpsertPlayerInfoPacket upsert) {
            return fromPlayerInfoUpdate(upsert);
        }
        if (packet instanceof RemovePlayerInfoPacket remove) {
            return fromPlayerInfoRemove(remove);
        }
        if (packet instanceof HeaderAndFooterPacket headerAndFooter) {
            return fromHeaderAndFooter(headerAndFooter);
        }
        return null;
    }

    private static WrapperPlayServerTeams fromTeam(Team packet) {
        WrapperPlayServerTeams.ScoreBoardTeamInfo info = null;
        if (packet.getMode() == Team.Mode.CREATE || packet.getMode() == Team.Mode.UPDATE_INFO) {
            info = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    componentOrEmpty(packet.getDisplayName()),
                    packet.getPrefix() != null ? packet.getPrefix().getComponent() : null,
                    packet.getSuffix() != null ? packet.getSuffix().getComponent() : null,
                    toWrapperNameTagVisibility(packet.getNameTagVisibility()),
                    toWrapperCollisionRule(packet.getCollisionRule()),
                    toWrapperColor(packet.getColor()),
                    toWrapperOptionData(packet.getFriendlyFire())
            );
        }

        String[] players = packet.getPlayers() == null ? new String[0] : packet.getPlayers();
        return new WrapperPlayServerTeams(
                packet.getName(),
                toWrapperTeamMode(packet.getMode()),
                info,
                players
        );
    }

    private static WrapperPlayServerPlayerInfo fromLegacyPlayerInfo(LegacyPlayerListItemPacket packet) {
        WrapperPlayServerPlayerInfo.Action action = toWrapperLegacyAction(packet.getAction());
        List<WrapperPlayServerPlayerInfo.PlayerData> playerData = new ArrayList<>(packet.getItems().size());

        for (LegacyPlayerListItemPacket.Item item : packet.getItems()) {
            UUID uuid = item.getUuid();
            if (uuid == null) {
                continue;
            }

            UserProfile profile = new UserProfile(
                    uuid,
                    item.getName(),
                    toPacketEventsTextureProperties(item.getProperties())
            );
            GameMode gameMode = toPacketEventsGameMode(item.getGameMode());
            SignatureData signatureData = toPacketEventsSignatureData(item.getPlayerKey());

            WrapperPlayServerPlayerInfo.PlayerData data = signatureData != null
                    ? new WrapperPlayServerPlayerInfo.PlayerData(item.getDisplayName(), profile, gameMode, signatureData, item.getLatency())
                    : new WrapperPlayServerPlayerInfo.PlayerData(item.getDisplayName(), profile, gameMode, item.getLatency());
            playerData.add(data);
        }

        return new WrapperPlayServerPlayerInfo(action, playerData);
    }

    private static WrapperPlayServerPlayerInfoUpdate fromPlayerInfoUpdate(UpsertPlayerInfoPacket packet) {
        EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> actions = EnumSet.noneOf(WrapperPlayServerPlayerInfoUpdate.Action.class);
        for (UpsertPlayerInfoPacket.Action action : packet.getActions()) {
            WrapperPlayServerPlayerInfoUpdate.Action mapped = toWrapperUpsertAction(action);
            if (mapped != null) {
                actions.add(mapped);
            }
        }

        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = new ArrayList<>(packet.getEntries().size());
        for (UpsertPlayerInfoPacket.Entry entry : packet.getEntries()) {
            UserProfile profile = toPacketEventsProfile(entry.getProfile(), entry.getProfileId());
            RemoteChatSession chatSession = toPacketEventsRemoteChatSession(entry.getChatSession());
            Component displayName = entry.getDisplayName() != null ? entry.getDisplayName().getComponent() : null;
            entries.add(new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    profile,
                    entry.isListed(),
                    entry.getLatency(),
                    toPacketEventsGameMode(entry.getGameMode()),
                    displayName,
                    chatSession,
                    entry.getListOrder(),
                    entry.isShowHat()
            ));
        }

        return new WrapperPlayServerPlayerInfoUpdate(actions, entries);
    }

    private static WrapperPlayServerPlayerInfoRemove fromPlayerInfoRemove(RemovePlayerInfoPacket packet) {
        return new WrapperPlayServerPlayerInfoRemove(new ArrayList<>(packet.getProfilesToRemove()));
    }

    private static WrapperPlayServerPlayerListHeaderAndFooter fromHeaderAndFooter(HeaderAndFooterPacket packet) {
        return new WrapperPlayServerPlayerListHeaderAndFooter(
                componentOrEmpty(packet.getHeader()),
                componentOrEmpty(packet.getFooter())
        );
    }

    private static Team.Mode toVelocityTeamMode(WrapperPlayServerTeams.TeamMode mode) {
        return switch (mode) {
            case CREATE -> Team.Mode.CREATE;
            case REMOVE -> Team.Mode.REMOVE;
            case UPDATE -> Team.Mode.UPDATE_INFO;
            case ADD_ENTITIES -> Team.Mode.ADD_PLAYER;
            case REMOVE_ENTITIES -> Team.Mode.REMOVE_PLAYER;
        };
    }

    private static WrapperPlayServerTeams.TeamMode toWrapperTeamMode(Team.Mode mode) {
        return switch (mode) {
            case CREATE -> WrapperPlayServerTeams.TeamMode.CREATE;
            case REMOVE -> WrapperPlayServerTeams.TeamMode.REMOVE;
            case UPDATE_INFO -> WrapperPlayServerTeams.TeamMode.UPDATE;
            case ADD_PLAYER -> WrapperPlayServerTeams.TeamMode.ADD_ENTITIES;
            case REMOVE_PLAYER -> WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES;
        };
    }

    private static Team.NameTagVisibility toVelocityNameTagVisibility(WrapperPlayServerTeams.NameTagVisibility visibility) {
        if (visibility == null) {
            return Team.NameTagVisibility.ALWAYS;
        }
        return switch (visibility) {
            case ALWAYS -> Team.NameTagVisibility.ALWAYS;
            case NEVER -> Team.NameTagVisibility.NEVER;
            case HIDE_FOR_OTHER_TEAMS -> Team.NameTagVisibility.HIDE_FOR_OTHER_TEAMS;
            case HIDE_FOR_OWN_TEAM -> Team.NameTagVisibility.HIDE_FOR_OWN_TEAM;
        };
    }

    private static WrapperPlayServerTeams.NameTagVisibility toWrapperNameTagVisibility(Team.NameTagVisibility visibility) {
        if (visibility == null || visibility == Team.NameTagVisibility.UNKNOWN) {
            return WrapperPlayServerTeams.NameTagVisibility.ALWAYS;
        }
        return switch (visibility) {
            case ALWAYS -> WrapperPlayServerTeams.NameTagVisibility.ALWAYS;
            case NEVER -> WrapperPlayServerTeams.NameTagVisibility.NEVER;
            case HIDE_FOR_OTHER_TEAMS -> WrapperPlayServerTeams.NameTagVisibility.HIDE_FOR_OTHER_TEAMS;
            case HIDE_FOR_OWN_TEAM -> WrapperPlayServerTeams.NameTagVisibility.HIDE_FOR_OWN_TEAM;
            case UNKNOWN -> WrapperPlayServerTeams.NameTagVisibility.ALWAYS;
        };
    }

    private static Team.CollisionRule toVelocityCollisionRule(WrapperPlayServerTeams.CollisionRule collisionRule) {
        if (collisionRule == null) {
            return Team.CollisionRule.ALWAYS;
        }
        return switch (collisionRule) {
            case ALWAYS -> Team.CollisionRule.ALWAYS;
            case NEVER -> Team.CollisionRule.NEVER;
            case PUSH_OTHER_TEAMS -> Team.CollisionRule.PUSH_OTHER_TEAMS;
            case PUSH_OWN_TEAM -> Team.CollisionRule.PUSH_OWN_TEAM;
        };
    }

    private static WrapperPlayServerTeams.CollisionRule toWrapperCollisionRule(Team.CollisionRule collisionRule) {
        if (collisionRule == null) {
            return WrapperPlayServerTeams.CollisionRule.ALWAYS;
        }
        return switch (collisionRule) {
            case ALWAYS -> WrapperPlayServerTeams.CollisionRule.ALWAYS;
            case NEVER -> WrapperPlayServerTeams.CollisionRule.NEVER;
            case PUSH_OTHER_TEAMS -> WrapperPlayServerTeams.CollisionRule.PUSH_OTHER_TEAMS;
            case PUSH_OWN_TEAM -> WrapperPlayServerTeams.CollisionRule.PUSH_OWN_TEAM;
        };
    }

    private static int toVelocityColor(@Nullable NamedTextColor color) {
        if (color == null) {
            return 21;
        }
        if (color == NamedTextColor.BLACK) {
            return 0;
        }
        if (color == NamedTextColor.DARK_BLUE) {
            return 1;
        }
        if (color == NamedTextColor.DARK_GREEN) {
            return 2;
        }
        if (color == NamedTextColor.DARK_AQUA) {
            return 3;
        }
        if (color == NamedTextColor.DARK_RED) {
            return 4;
        }
        if (color == NamedTextColor.DARK_PURPLE) {
            return 5;
        }
        if (color == NamedTextColor.GOLD) {
            return 6;
        }
        if (color == NamedTextColor.GRAY) {
            return 7;
        }
        if (color == NamedTextColor.DARK_GRAY) {
            return 8;
        }
        if (color == NamedTextColor.BLUE) {
            return 9;
        }
        if (color == NamedTextColor.GREEN) {
            return 10;
        }
        if (color == NamedTextColor.AQUA) {
            return 11;
        }
        if (color == NamedTextColor.RED) {
            return 12;
        }
        if (color == NamedTextColor.LIGHT_PURPLE) {
            return 13;
        }
        if (color == NamedTextColor.YELLOW) {
            return 14;
        }
        if (color == NamedTextColor.WHITE) {
            return 15;
        }
        return 21;
    }

    private static NamedTextColor toWrapperColor(int color) {
        return switch (color) {
            case 0 -> NamedTextColor.BLACK;
            case 1 -> NamedTextColor.DARK_BLUE;
            case 2 -> NamedTextColor.DARK_GREEN;
            case 3 -> NamedTextColor.DARK_AQUA;
            case 4 -> NamedTextColor.DARK_RED;
            case 5 -> NamedTextColor.DARK_PURPLE;
            case 6 -> NamedTextColor.GOLD;
            case 7 -> NamedTextColor.GRAY;
            case 8 -> NamedTextColor.DARK_GRAY;
            case 9 -> NamedTextColor.BLUE;
            case 10 -> NamedTextColor.GREEN;
            case 11 -> NamedTextColor.AQUA;
            case 12 -> NamedTextColor.RED;
            case 13 -> NamedTextColor.LIGHT_PURPLE;
            case 14 -> NamedTextColor.YELLOW;
            case 15 -> NamedTextColor.WHITE;
            default -> NamedTextColor.WHITE;
        };
    }

    private static byte toVelocityFriendlyFire(@Nullable WrapperPlayServerTeams.OptionData optionData) {
        return optionData != null ? optionData.getByteValue() : 0;
    }

    private static WrapperPlayServerTeams.OptionData toWrapperOptionData(byte friendlyFire) {
        WrapperPlayServerTeams.OptionData optionData = WrapperPlayServerTeams.OptionData.fromValue((byte) (friendlyFire & 0x03));
        return optionData != null ? optionData : WrapperPlayServerTeams.OptionData.NONE;
    }

    private static int toVelocityLegacyAction(@Nullable WrapperPlayServerPlayerInfo.Action action) {
        if (action == null) {
            return LegacyPlayerListItemPacket.UPDATE_LATENCY;
        }
        return switch (action) {
            case ADD_PLAYER -> LegacyPlayerListItemPacket.ADD_PLAYER;
            case UPDATE_GAME_MODE -> LegacyPlayerListItemPacket.UPDATE_GAMEMODE;
            case UPDATE_LATENCY -> LegacyPlayerListItemPacket.UPDATE_LATENCY;
            case UPDATE_DISPLAY_NAME -> LegacyPlayerListItemPacket.UPDATE_DISPLAY_NAME;
            case REMOVE_PLAYER -> LegacyPlayerListItemPacket.REMOVE_PLAYER;
        };
    }

    private static WrapperPlayServerPlayerInfo.Action toWrapperLegacyAction(int action) {
        return switch (action) {
            case LegacyPlayerListItemPacket.ADD_PLAYER -> WrapperPlayServerPlayerInfo.Action.ADD_PLAYER;
            case LegacyPlayerListItemPacket.UPDATE_GAMEMODE -> WrapperPlayServerPlayerInfo.Action.UPDATE_GAME_MODE;
            case LegacyPlayerListItemPacket.UPDATE_DISPLAY_NAME -> WrapperPlayServerPlayerInfo.Action.UPDATE_DISPLAY_NAME;
            case LegacyPlayerListItemPacket.REMOVE_PLAYER -> WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER;
            case LegacyPlayerListItemPacket.UPDATE_LATENCY -> WrapperPlayServerPlayerInfo.Action.UPDATE_LATENCY;
            default -> WrapperPlayServerPlayerInfo.Action.UPDATE_LATENCY;
        };
    }

    @Nullable
    private static UpsertPlayerInfoPacket.Action toVelocityUpsertAction(WrapperPlayServerPlayerInfoUpdate.Action action) {
        try {
            return UpsertPlayerInfoPacket.Action.valueOf(action.name());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Nullable
    private static WrapperPlayServerPlayerInfoUpdate.Action toWrapperUpsertAction(UpsertPlayerInfoPacket.Action action) {
        try {
            return WrapperPlayServerPlayerInfoUpdate.Action.valueOf(action.name());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int toVelocityGameMode(@Nullable GameMode gameMode) {
        return gameMode != null ? gameMode.getId() : GameMode.SURVIVAL.getId();
    }

    private static GameMode toPacketEventsGameMode(int gameMode) {
        GameMode mapped = GameMode.getById(gameMode);
        return mapped != null ? mapped : GameMode.SURVIVAL;
    }

    private static ComponentHolder toComponentHolder(ProtocolVersion protocolVersion, Component component) {
        return new ComponentHolder(protocolVersion, component != null ? component : Component.empty());
    }

    private static Component componentOrEmpty(@Nullable ComponentHolder componentHolder) {
        return componentHolder != null ? componentHolder.getComponent() : Component.empty();
    }

    private static List<GameProfile.Property> toVelocityProperties(@Nullable List<TextureProperty> properties) {
        if (properties == null || properties.isEmpty()) {
            return List.of();
        }
        List<GameProfile.Property> converted = new ArrayList<>(properties.size());
        for (TextureProperty property : properties) {
            converted.add(new GameProfile.Property(property.getName(), property.getValue(), property.getSignature()));
        }
        return converted;
    }

    private static List<TextureProperty> toPacketEventsTextureProperties(@Nullable List<GameProfile.Property> properties) {
        if (properties == null || properties.isEmpty()) {
            return List.of();
        }
        List<TextureProperty> converted = new ArrayList<>(properties.size());
        for (GameProfile.Property property : properties) {
            converted.add(new TextureProperty(property.getName(), property.getValue(), property.getSignature()));
        }
        return converted;
    }

    private static GameProfile toVelocityProfile(@Nullable UserProfile profile, UUID fallbackUuid) {
        if (profile == null) {
            return new GameProfile(fallbackUuid, "", List.of());
        }
        UUID uuid = profile.getUUID() != null ? profile.getUUID() : fallbackUuid;
        String name = profile.getName() != null ? profile.getName() : "";
        return new GameProfile(uuid, name, toVelocityProperties(profile.getTextureProperties()));
    }

    private static UserProfile toPacketEventsProfile(@Nullable GameProfile profile, UUID fallbackUuid) {
        if (profile == null) {
            return new UserProfile(fallbackUuid, "", List.of());
        }
        UUID uuid = profile.getId() != null ? profile.getId() : fallbackUuid;
        String name = profile.getName() != null ? profile.getName() : "";
        return new UserProfile(uuid, name, toPacketEventsTextureProperties(profile.getProperties()));
    }

    @Nullable
    private static IdentifiedKey toVelocityPlayerKey(SignatureData signatureData, ProtocolVersion protocolVersion) {
        if (signatureData == null) {
            return null;
        }
        IdentifiedKey.Revision revision = protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_19) == 0
                ? IdentifiedKey.Revision.GENERIC_V1
                : IdentifiedKey.Revision.LINKED_V2;
        return new IdentifiedKeyImpl(revision, signatureData.getPublicKey(), signatureData.getTimestamp(), signatureData.getSignature());
    }

    @Nullable
    private static SignatureData toPacketEventsSignatureData(@Nullable IdentifiedKey identifiedKey) {
        if (identifiedKey == null) {
            return null;
        }
        return new SignatureData(identifiedKey.getExpiryTemporal(), identifiedKey.getSignedPublicKey(), identifiedKey.getSignature());
    }

    @Nullable
    private static com.velocitypowered.proxy.protocol.packet.chat.RemoteChatSession toVelocityRemoteChatSession(@Nullable RemoteChatSession chatSession, ProtocolVersion protocolVersion) {
        if (chatSession == null) {
            return null;
        }

        PublicProfileKey profileKey = chatSession.getPublicProfileKey();
        if (profileKey == null) {
            return null;
        }

        IdentifiedKey.Revision revision = protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_19) == 0
                ? IdentifiedKey.Revision.GENERIC_V1
                : IdentifiedKey.Revision.LINKED_V2;
        IdentifiedKey identifiedKey = new IdentifiedKeyImpl(
                revision,
                profileKey.getKey(),
                profileKey.getExpiresAt(),
                profileKey.getKeySignature()
        );
        return new com.velocitypowered.proxy.protocol.packet.chat.RemoteChatSession(chatSession.getSessionId(), identifiedKey);
    }

    @Nullable
    private static RemoteChatSession toPacketEventsRemoteChatSession(@Nullable com.velocitypowered.proxy.protocol.packet.chat.RemoteChatSession chatSession) {
        if (chatSession == null || chatSession.getSessionId() == null) {
            return null;
        }
        IdentifiedKey identifiedKey = chatSession.getIdentifiedKey();
        PublicProfileKey publicProfileKey = new PublicProfileKey(
                identifiedKey.getExpiryTemporal(),
                identifiedKey.getSignedPublicKey(),
                identifiedKey.getSignature()
        );
        return new RemoteChatSession(chatSession.getSessionId(), publicProfileKey);
    }
}
