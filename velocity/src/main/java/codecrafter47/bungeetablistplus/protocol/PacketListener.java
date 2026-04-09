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

import codecrafter47.bungeetablistplus.BungeeTabListPlus;
import codecrafter47.bungeetablistplus.managers.TabViewManager;
import codecrafter47.bungeetablistplus.util.GeyserCompat;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerListHeaderAndFooter;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;

public class PacketListener extends PacketListenerAbstract {

    private final BungeeTabListPlus btlp;
    private final TabViewManager tabViewManager;

    public PacketListener(BungeeTabListPlus btlp, TabViewManager tabViewManager) {
        this.btlp = btlp;
        this.tabViewManager = tabViewManager;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        try {
            if (!(event.getPlayer() instanceof Player player)) {
                return;
            }

            if (GeyserCompat.isBedrockPlayer(player.getUniqueId())) {
                return;
            }

            PacketHandler handler = tabViewManager.getPacketHandler(player);
            if (handler == null) {
                return;
            }

            PacketListenerResult result;
            MinecraftPacket packet;
            var packetType = event.getPacketType();

            if (packetType == PacketType.Play.Server.TEAMS) {
                packet = PacketConversionUtil.toTeam(new WrapperPlayServerTeams(event), player.getProtocolVersion());
                result = handler.onTeamPacket((Team) packet);
            } else if (packetType == PacketType.Play.Server.PLAYER_INFO) {
                packet = PacketConversionUtil.toLegacyPlayerInfo(new WrapperPlayServerPlayerInfo(event), player.getProtocolVersion());
                result = handler.onPlayerListPacket((com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket) packet);
            } else if (packetType == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
                packet = PacketConversionUtil.toPlayerInfoUpdate(new WrapperPlayServerPlayerInfoUpdate(event), player.getProtocolVersion());
                result = handler.onPlayerListUpdatePacket((com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket) packet);
            } else if (packetType == PacketType.Play.Server.PLAYER_INFO_REMOVE) {
                packet = PacketConversionUtil.toPlayerInfoRemove(new WrapperPlayServerPlayerInfoRemove(event));
                result = handler.onPlayerListRemovePacket((com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket) packet);
            } else if (packetType == PacketType.Play.Server.PLAYER_LIST_HEADER_AND_FOOTER) {
                packet = PacketConversionUtil.toHeaderAndFooter(new WrapperPlayServerPlayerListHeaderAndFooter(event), player.getProtocolVersion());
                result = handler.onPlayerListHeaderFooterPacket((com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket) packet);
            } else {
                return;
            }

            if (result != PacketListenerResult.PASS) {
                event.setCancelled(true);
                if (result == PacketListenerResult.MODIFIED) {
                    sendPacket(player, packet);
                }
            }
        } catch (Throwable th) {
            event.setCancelled(true);
            btlp.reportError(th);
        }
    }

    public static void sendPacket(Player player, MinecraftPacket packet) {
        PacketWrapper<?> wrapper = PacketConversionUtil.toWrapper(packet);
        if (wrapper != null) {
            PacketEvents.getAPI().getPlayerManager().sendPacketSilently(player, wrapper);
            return;
        }
        ((ConnectedPlayer) player).getConnection().write(packet);
    }
}
