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

import client.MapleClient;
import client.MapleDisease;
import handling.AbstractMaplePacketHandler;
import java.util.ArrayList;
import java.util.List;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.MaplePacketCreator;

public final class CancelDebuffHandler extends AbstractMaplePacketHandler {

  public final void handlePacket(
    SeekableLittleEndianAccessor slea,
    MapleClient c
  ) {
    List<MapleDisease> diseases = c.getPlayer().getDiseases();
    List<MapleDisease> diseases_ = new ArrayList<MapleDisease>();
    for (MapleDisease disease : diseases) {
      List<MapleDisease> disease_ = new ArrayList<MapleDisease>();
      disease_.add(disease);
      diseases_.add(disease);
      c.getSession().write(MaplePacketCreator.cancelDebuff(disease_));
      c
        .getPlayer()
        .getMap()
        .broadcastMessage(
          c.getPlayer(),
          MaplePacketCreator.cancelForeignDebuff(
            c.getPlayer().getId(),
            disease_
          ),
          false
        );
    }
    for (MapleDisease disease : diseases_) {
      c.getPlayer().removeDisease(disease);
    }
  }
}
