package com.maris7.leaderboard.listener;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import com.maris7.leaderboard.service.SignSearchService;
import org.bukkit.entity.Player;

public final class SignInputPacketListener extends SimplePacketListenerAbstract {

    private final SignSearchService signSearchService;

    public SignInputPacketListener(SignSearchService signSearchService) {
        super(PacketListenerPriority.NORMAL);
        this.signSearchService = signSearchService;
    }

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.UPDATE_SIGN) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player) || !signSearchService.hasSession(player.getUniqueId())) {
            return;
        }
        WrapperPlayClientUpdateSign packet = new WrapperPlayClientUpdateSign(event);
        signSearchService.handleSignResponse(player, packet.getBlockPosition(), packet.getTextLines());
    }
}
