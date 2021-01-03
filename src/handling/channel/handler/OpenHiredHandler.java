/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package handling.channel.handler;

import client.MapleClient;
import handling.AbstractMaplePacketHandler;
import java.util.Arrays;
import server.maps.MapleMapObjectType;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.MaplePacketCreator;

/**
 *
 * @author XoticStory
 */
public class OpenHiredHandler extends AbstractMaplePacketHandler {

  @Override
  public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
    if (
      c
        .getPlayer()
        .getMap()
        .getMapObjectsInRange(
          c.getPlayer().getPosition(),
          23000,
          Arrays.asList(
            MapleMapObjectType.HIRED_MERCHANT,
            MapleMapObjectType.SHOP
          )
        )
        .size() ==
      0
    ) {
      if (!c.getPlayer().hasMerchant()) {
        c.getSession().write(MaplePacketCreator.hiredMerchantBox());
      } else {
        c
          .getPlayer()
          .dropMessage(
            1,
            "You already have an open shop. Please close your open shop first!"
          );
      }
    } else {
      c.getPlayer().dropMessage(1, "You can not establish a store here.");
    }
  }
}
