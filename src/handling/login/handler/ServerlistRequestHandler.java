package handling.login.handler;

import client.MapleClient;
import handling.AbstractMaplePacketHandler;
import handling.login.LoginServer;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.MaplePacketCreator;

public class ServerlistRequestHandler extends AbstractMaplePacketHandler {

  @Override
  public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
    c
      .getSession()
      .write(
        MaplePacketCreator.getServerList(
          0,
          LoginServer.getInstance().getServerName(),
          LoginServer.getInstance().getLoad()
        )
      );
    if (LoginServer.getInstance().twoWorldsActive()) c
      .getSession()
      .write(
        MaplePacketCreator.getServerList(
          1,
          LoginServer.getInstance().getServerName(),
          LoginServer.getInstance().getLoad()
        )
      );
    c.getSession().write(MaplePacketCreator.getEndOfServerList());
  }
}
