package handling.channel.handler;

import client.IItem;
import client.MapleClient;
import client.anticheat.CheatingOffense;
import client.inventory.MapleInventoryType;
import handling.AbstractMaplePacketHandler;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.MaplePacketCreator;

public class UseItemEffectHandler extends AbstractMaplePacketHandler {

  private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(
    UseItemHandler.class
  );

  public UseItemEffectHandler() {}

  public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
    int itemId = slea.readInt();
    boolean mayUse = true;
    if (itemId >= 5000000 && itemId <= 5000053) {
      log.warn(slea.toString());
    }
    if (itemId != 0) {
      IItem toUse = c
        .getPlayer()
        .getInventory(MapleInventoryType.CASH)
        .findById(itemId);

      if (toUse == null) {
        mayUse = false;
        //				log.info("[h4x] Player {} is using an item he does not have: {}", c.getPlayer().getName(), Integer.valueOf(itemId));
        c
          .getPlayer()
          .getCheatTracker()
          .registerOffense(
            CheatingOffense.USING_UNAVAILABLE_ITEM,
            Integer.toString(itemId)
          );
        return;
      }
    }

    if (mayUse) {
      c.getPlayer().setItemEffect(itemId);
      c
        .getPlayer()
        .getMap()
        .broadcastMessage(
          c.getPlayer(),
          MaplePacketCreator.itemEffect(c.getPlayer().getId(), itemId),
          false
        );
    }
  }
}
