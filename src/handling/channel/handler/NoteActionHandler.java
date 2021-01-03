package handling.channel.handler;

import client.MapleClient;
import database.DatabaseConnection;
import handling.AbstractMaplePacketHandler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import tools.data.input.SeekableLittleEndianAccessor;

public class NoteActionHandler extends AbstractMaplePacketHandler {

  @Override
  public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
    int action = slea.readByte();

    if (action == 1) { // Delete
      int num = slea.readByte();
      slea.readShort();
      for (int i = 0; i < num; i++) {
        deleteNote(slea.readInt());
        slea.readByte();
      }
    }
  }

  private void deleteNote(int id) {
    Connection con = DatabaseConnection.getConnection();
    try {
      PreparedStatement ps = con.prepareStatement(
        "DELETE FROM notes WHERE `id` = ? "
      );
      ps.setInt(1, id);
      ps.executeUpdate();
      ps.close();
    } catch (SQLException e) {}
  }
}
