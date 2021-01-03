package handling.channel.handler;

import client.MapleClient;
import handling.AbstractMaplePacketHandler;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.MaplePacketCreator;

/**
 *
 * @author Xterminator
 */

public class CloseChalkboardHandler extends AbstractMaplePacketHandler {

  @Override
  public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
    c.getPlayer().setChalkboard(null);
    c
      .getPlayer()
      .getMap()
      .broadcastMessage(MaplePacketCreator.useChalkboard(c.getPlayer(), true));
  }
}
