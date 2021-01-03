package handling.channel.handler;

import client.MapleCharacter;
import client.MapleClient;
import client.messages.CommandProcessor;
import config.configuration.Configuration;
import handling.AbstractMaplePacketHandler;
import handling.channel.ChannelServer;
import java.rmi.RemoteException;
import server.AutobanManager;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.*;

public class WhisperHandler extends AbstractMaplePacketHandler {

  public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
    byte mode = slea.readByte();
    String recipient = slea.readMapleAsciiString();
    int channel;
    try {
      channel = c.getChannelServer().getWorldInterface().find(recipient);
    } catch (RemoteException re) {
      c
        .getSession()
        .write(MaplePacketCreator.getWhisperReply(recipient, (byte) 0));
      c.getChannelServer().reconnectWorld();
      return;
    }
    if (channel == -1) {
      c
        .getSession()
        .write(MaplePacketCreator.getWhisperReply(recipient, (byte) 0));
    } else {
      if (mode == 6) { // Whisper
        String text = slea.readMapleAsciiString();
        if (!(c.getPlayer().isGM()) && text.length() > 100) { // 70 = max text for client. remove this if u have edit the client
          AutobanManager
            .getInstance()
            .autoban(
              c.getPlayer().getClient(),
              Configuration.Server_Name +
              " | " +
              c.getPlayer().getName() +
              " exceeded text length limit with a text length of " +
              text.length() +
              "."
            );
        } else {
          if (!CommandProcessor.getInstance().processCommand(c, text)) {
            ChannelServer pserv = ChannelServer.getInstance(channel);
            MapleCharacter victim = pserv
              .getPlayerStorage()
              .getCharacterByName(recipient);
            victim
              .getClient()
              .getSession()
              .write(
                MaplePacketCreator.getWhisper(
                  c.getPlayer().getName(),
                  c.getChannel(),
                  text
                )
              );
            c
              .getSession()
              .write(MaplePacketCreator.getWhisperReply(recipient, (byte) 1));
          }
        }
      } else if (mode == 5) { // Find
        ChannelServer pserv = ChannelServer.getInstance(channel);
        MapleCharacter victim = pserv
          .getPlayerStorage()
          .getCharacterByName(recipient);
        if (!victim.isGM() || (c.getPlayer().isGM() && victim.isGM())) {
          if (victim.inCS()) {
            c
              .getSession()
              .write(
                MTSCSPacket.getFindReplyWithCSorMTS(victim.getName(), false)
              );
          } else if (victim.inMTS()) {
            c
              .getSession()
              .write(
                MTSCSPacket.getFindReplyWithCSorMTS(victim.getName(), true)
              );
          } else if (c.getChannel() == victim.getClient().getChannel()) {
            c
              .getSession()
              .write(
                MaplePacketCreator.getFindReplyWithMap(
                  victim.getName(),
                  victim.getMapId()
                )
              );
          } else {
            c
              .getSession()
              .write(
                MaplePacketCreator.getFindReply(
                  victim.getName(),
                  (byte) victim.getClient().getChannel()
                )
              );
          }
        } else {
          c
            .getSession()
            .write(MaplePacketCreator.getWhisperReply(recipient, (byte) 0));
        }
      }
    }
  }
}
