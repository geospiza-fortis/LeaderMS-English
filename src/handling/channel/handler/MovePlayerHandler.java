package handling.channel.handler;

import client.MapleClient;
import handling.MaplePacket;
import java.util.List;
import server.movement.LifeMovementFragment;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.MaplePacketCreator;

public class MovePlayerHandler extends AbstractMovementPacketHandler {

  @Override
  public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
    slea.readByte();
    slea.readInt();
    final List<LifeMovementFragment> res = parseMovement(slea);
    if (res != null) {
      //            if (slea.available() != 18) {
      //                log.warn("slea.available != 18 (movement parsing error)"); //dash problem?
      //                return;
      //            }
      if (!c.getPlayer().isHidden()) {
        MaplePacket packet = MaplePacketCreator.movePlayer(
          c.getPlayer().getId(),
          res
        );
        c.getPlayer().getMap().broadcastMessage(c.getPlayer(), packet, false);
      }
      updatePosition(res, c.getPlayer(), 0);
      c
        .getPlayer()
        .getMap()
        .movePlayer(c.getPlayer(), c.getPlayer().getPosition());
    }
  }
}
