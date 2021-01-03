/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc> 
                       Matthias Butz <matze@odinms.de>
                       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation. You may not use, modify
    or distribute this program under any other version of the
    GNU Affero General Public License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package handling.channel.handler;

import client.MapleCharacter;
import client.MapleClient;
import handling.AbstractMaplePacketHandler;
import handling.channel.ChannelServer;
import handling.world.MapleParty;
import handling.world.MaplePartyCharacter;
import handling.world.PartyOperation;
import handling.world.remote.WorldChannelInterface;
import java.rmi.RemoteException;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.MaplePacketCreator;

public class PartyOperationHandler extends AbstractMaplePacketHandler {

  public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
    int operation = slea.readByte();
    MapleCharacter player = c.getPlayer();
    WorldChannelInterface wci = ChannelServer
      .getInstance(c.getChannel())
      .getWorldInterface();
    MapleParty party = player.getParty();
    MaplePartyCharacter partyplayer = new MaplePartyCharacter(player);

    switch (operation) {
      case 1:
        { // Create.
          if (c.getPlayer().getGMLevel() == 3) {
            c
              .getSession()
              .write(
                MaplePacketCreator.serverNotice(
                  5,
                  "Jr. GameMasters cannot create parties."
                )
              );
            return;
          }
          if (c.getPlayer().getParty() == null) {
            try {
              party = wci.createParty(partyplayer);
              player.setParty(party);
            } catch (Exception e) {
              c.getChannelServer().reconnectWorld();
            }
            c.getSession().write(MaplePacketCreator.partyCreated());
          } else {
            c
              .getSession()
              .write(
                MaplePacketCreator.serverNotice(
                  5,
                  "You cannot create a party because you are already in one."
                )
              );
          }
          break;
        }
      case 2:
        { // Leave.
          if (party != null) {
            try {
              if (partyplayer.equals(party.getLeader())) {
                wci.updateParty(
                  party.getId(),
                  PartyOperation.DISBAND,
                  partyplayer
                );
                if (player.getEventInstance() != null) {
                  player.getEventInstance().disbandParty();
                }
              } else {
                wci.updateParty(
                  party.getId(),
                  PartyOperation.LEAVE,
                  partyplayer
                );
                if (player.getEventInstance() != null) {
                  player.getEventInstance().leftParty(player);
                }
              }
            } catch (Exception e) {
              c.getChannelServer().reconnectWorld();
            }
            player.setParty(null);
          }
          break;
        }
      case 3:
        { // Accept invitation.
          int partyid = slea.readInt();
          if (c.getPlayer().getGMLevel() == 3) {
            c
              .getSession()
              .write(
                MaplePacketCreator.serverNotice(
                  5,
                  "Jr. GameMasters cannot join parties."
                )
              );
            return;
          }
          if (c.getPlayer().getParty() == null) {
            try {
              party = wci.getParty(partyid);
              if (party != null) {
                if (party.getMembers().size() < 6) {
                  wci.updateParty(
                    party.getId(),
                    PartyOperation.JOIN,
                    partyplayer
                  );
                  player.receivePartyMemberHP();
                  player.updatePartyMemberHP();
                } else {
                  c
                    .getSession()
                    .write(MaplePacketCreator.partyStatusMessage(17));
                }
              } else {
                c
                  .getSession()
                  .write(
                    MaplePacketCreator.serverNotice(
                      5,
                      "The party that you are trying to enter does not exist."
                    )
                  );
              }
            } catch (Exception e) {
              c.getChannelServer().reconnectWorld();
            }
          } else {
            c
              .getSession()
              .write(
                MaplePacketCreator.serverNotice(
                  5,
                  "You can not join this party because you are already in one."
                )
              );
          }
          break;
        }
      case 4:
        { // Invite.
          String name = slea.readMapleAsciiString();
          MapleCharacter invited = c
            .getChannelServer()
            .getPlayerStorage()
            .getCharacterByName(name);
          if (invited != null) {
            if (invited.getParty() == null) {
              if (party.getMembers().size() < 6) {
                invited
                  .getClient()
                  .getSession()
                  .write(MaplePacketCreator.partyInvite(player));
              } else {
                c.getSession().write(MaplePacketCreator.partyStatusMessage(16));
              }
            } else {
              c.getSession().write(MaplePacketCreator.partyStatusMessage(17));
            }
          } else {
            c.getSession().write(MaplePacketCreator.partyStatusMessage(19));
          }
          break;
        }
      case 5:
        { // Expel.
          int cid = slea.readInt();
          if (partyplayer.equals(party.getLeader())) {
            MaplePartyCharacter expelled = party.getMemberById(cid);
            if (expelled != null) {
              try {
                wci.updateParty(party.getId(), PartyOperation.EXPEL, expelled);
                if (player.getEventInstance() != null) {
                  if (expelled.isOnline()) {
                    player.getEventInstance().disbandParty();
                  }
                }
              } catch (Exception e) {
                c.getChannelServer().reconnectWorld();
              }
            }
          }
          break;
        }
      case 6:
        {
          int newLeader = slea.readInt();
          MaplePartyCharacter newLeadr = party.getMemberById(newLeader);
          try {
            party.setLeader(newLeadr);
            wci.updateParty(
              party.getId(),
              PartyOperation.CHANGE_LEADER,
              newLeadr
            );
          } catch (Exception e) {
            c.getChannelServer().reconnectWorld();
          }
          break;
        }
    }
  }
}
