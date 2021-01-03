package handling.channel.handler;

import client.MapleClient;
import handling.AbstractMaplePacketHandler;
import java.util.Arrays;
import server.maps.MapleMapObjectType;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.MaplePacketCreator;

public class HiredMerchantRequest extends AbstractMaplePacketHandler {

  @Override
  public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
    if (!c.isGuest()) {
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
          c.getPlayer().dropMessage(1, "You already have an open shop!");
        }
      } else {
        c.getPlayer().dropMessage(1, "You can not establish a store here.");
      }
    } else {
      c
        .getPlayer()
        .dropMessage(1, "Guest users are not allowed to open hired merchants.");
    }
  }
}
