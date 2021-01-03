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

package tools.packet;

import client.BuddylistEntry;
import client.IEquip;
import client.IEquip.ScrollResult;
import client.IItem;
import client.MapleBuffStat;
import client.MapleCharacter;
import client.MapleClient;
import client.MapleDisease;
import client.MapleKeyBinding;
import client.MapleQuestStatus;
import client.MapleStat;
import client.SkillMacro;
import client.inventory.Item;
import client.inventory.MapleInventory;
import client.inventory.MapleInventoryType;
import client.inventory.MapleMount;
import client.inventory.MaplePet;
import client.inventory.MapleRing;
import client.status.MonsterStatus;
import config.configuration.Configuration;
import database.DatabaseConnection;
import handling.ByteArrayMaplePacket;
import handling.LongValueHolder;
import handling.MaplePacket;
import handling.SendPacketOpcode;
import handling.channel.handler.SummonDamageHandler.SummonAttackEntry;
import handling.login.LoginServer;
import handling.world.MapleParty;
import handling.world.MaplePartyCharacter;
import handling.world.PartyOperation;
import handling.world.guild.MapleAlliance;
import handling.world.guild.MapleGuild;
import handling.world.guild.MapleGuildCharacter;
import handling.world.guild.MapleGuildSummary;
import java.awt.Point;
import java.awt.Rectangle;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import server.DueyPackages;
import server.MapleItemInformationProvider;
import server.MapleShopItem;
import server.MapleStatEffect;
import server.MapleTrade;
import server.PlayerInteraction.HiredMerchant;
import server.PlayerInteraction.IPlayerInteractionManager;
import server.PlayerInteraction.MapleMiniGame;
import server.PlayerInteraction.MaplePlayerShop;
import server.PlayerInteraction.MaplePlayerShopItem;
import server.life.MapleMonster;
import server.life.MapleNPC;
import server.life.MapleNPCStats;
import server.life.MobSkill;
import server.maps.MapleMap;
import server.maps.MapleReactor;
import server.maps.MapleSummon;
import server.maps.PlayerNPCMerchant;
import server.movement.LifeMovementFragment;
import tools.BitTools;
import tools.HexTool;
import tools.KoreanDateUtil;
import tools.Pair;
import tools.StringUtil;
import tools.data.output.LittleEndianWriter;
import tools.data.output.MaplePacketLittleEndianWriter;

/**
 * Provides all MapleStory packets needed in one place.
 *
 * @author Frz
 * @since Revision 259
 * @version 1.0
 */

public class MaplePacketCreator {

  private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(
    MaplePacketCreator.class
  );

  private static final byte[] CHAR_INFO_MAGIC = new byte[] {
    (byte) 0xff,
    (byte) 0xc9,
    (byte) 0x9a,
    0x3b,
  };
  private static final byte[] ITEM_MAGIC = new byte[] { (byte) 0x80, 5 };
  public static final List<Pair<MapleStat, Integer>> EMPTY_STATUPDATE = Collections.emptyList();

  /**
   * Sends a hello packet.
   *
   * @param mapleVersion The maple client version.
   * @param sendIv the IV used by the server for sending
   * @param recvIv the IV used by the server for receiving
   * @param testServer
   * @return
   */
  public static MaplePacket getHello(
    short mapleVersion,
    byte[] sendIv,
    byte[] recvIv,
    boolean testServer
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);

    mplew.writeShort(0x0d);
    mplew.writeShort(mapleVersion);
    mplew.write(new byte[] { 0, 0 });
    mplew.write(recvIv);
    mplew.write(sendIv);
    mplew.write(testServer ? 5 : 8);

    return mplew.getPacket();
  }

  /**
   * Sends a ping packet.
   *
   * @return The packet.
   */
  public static MaplePacket getPing() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);

    mplew.writeShort(SendPacketOpcode.PING.getValue());

    return mplew.getPacket();
  }

  /**
   * MapleTV Stuff
   * All credits to Cheetah And MrMysterious for this.
   * @return various.
   */

  public static MaplePacket enableTV() {
    // [0F 01] [00 00 00 00] [00] <-- 0x112 in v63,
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.ENABLE_TV.getValue()); // enableTV = 0x10F
    mplew.writeInt(0);
    mplew.write(0);
    return mplew.getPacket();
  }

  public static MaplePacket removeTV() {
    // 11 01 <-- 0x10E in v62
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.REMOVE_TV.getValue()); // removeTV = 0x111 <-- v63
    return mplew.getPacket();
  }

  public static MaplePacket sendTV(
    MapleCharacter chr,
    List<String> messages,
    int type,
    MapleCharacter partner
  ) {
    // [10 01] [01] [00 00 03 B1 4F 00 00 00 67 75 00 00 01 75 4B 0F 00 0C E3 FA 10 00 FF FF
    // 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00] [0B 00 64 75 73 74 72 65 6D 6F 76
    // 65 72] [00 00] [07 00] [70 61 63 6B 65 74 73] 00 00 00 00 00 00 00 00 0F 00 00 00

    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.SEND_TV.getValue()); // SEND_TV = 0x11D
    mplew.write(partner != null ? 2 : 0);
    mplew.write(type); // type   Heart = 2  Star = 1  Normal = 0
    PacketHelper.addCharLook(mplew, chr, false);
    mplew.writeMapleAsciiString(chr.getName());
    if (partner != null) {
      mplew.writeMapleAsciiString(partner.getName());
    } else {
      mplew.writeShort(0); // could be partner
    }
    for (int i = 0; i < messages.size(); i++) { // for (String message : messages) {
      if (i == 4 && messages.get(4).length() > 15) {
        mplew.writeMapleAsciiString(messages.get(4).substring(0, 15)); // hmm ?
      } else {
        mplew.writeMapleAsciiString(messages.get(i));
      }
    }
    mplew.writeInt(1337); // time limit lol 'Your thing still start in blah blah seconds'
    if (partner != null) {
      PacketHelper.addCharLook(mplew, partner, false);
    }
    return mplew.getPacket();
  }

  /**
   * Gets a login failed packet.
   *
   * Possible values for <code>reason</code>:<br>
   * 3: ID deleted or blocked<br>
   * 4: Incorrect password<br>
   * 5: Not a registered id<br>
   * 6: System error<br>
   * 7: Already logged in<br>
   * 8: System error<br>
   * 9: System error<br>
   * 10: Cannot process so many connections<br>
   * 11: Only users older than 20 can use this channel<br>
   * 13: Unable to log on as master at this ip<br>
   * 14: Wrong gateway or personal info and weird korean button<br>
   * 15: Processing request with that korean button!<br>
   * 16: Please verify your account through email...<br>
   * 17: Wrong gateway or personal info<br>
   * 21: Please verify your account through email...<br>
   * 23: License agreement<br>
   * 25: Maple Europe notice =[<br>
   * 27: Some weird full client notice, probably for trial versions<br>
   *
   * @param reason The reason logging in failed.
   * @return The login failed packet.
   */
  public static MaplePacket getLoginFailed(int reason) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);

    mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
    mplew.writeInt(reason);
    mplew.writeShort(0);

    return mplew.getPacket();
  }

  public static MaplePacket getPermBan(byte reason) {
    // 00 00 02 00 01 01 01 01 01 00
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);

    mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
    mplew.writeShort(0x02); // Account is banned
    mplew.write(0x0);
    mplew.write(reason);
    mplew.write(HexTool.getByteArrayFromHexString("01 01 01 01 00"));

    return mplew.getPacket();
  }

  public static MaplePacket sendGMPolice() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.write(
      HexTool.getByteArrayFromHexString(
        "59 00 40 42 0F 00 04 00 04 00 4C 75 6C 7A"
      )
    );
    return mplew.getPacket();
  }

  public static MaplePacket getTempBan(long timestampTill, byte reason) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(17);

    mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
    mplew.write(0x02);
    mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 00")); // Account is banned
    mplew.write(reason);
    mplew.writeLong(timestampTill); // Tempban date is handled as a 64-bit long, number of 100NS intervals since 1/1/1601. Lulz.

    return mplew.getPacket();
  }

  /**
   * Gets a successful authentication and PIN Request packet.
   *
   * @param account The account name.
   * @return The PIN request packet.
   */
  public static MaplePacket getAuthSuccessRequestPin(String account) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
    mplew.write(
      new byte[] { 0, 0, 0, 0, 0, 0, (byte) 0xFF, 0x6A, 1, 0, 0, 0, 0x4E }
    );
    mplew.writeMapleAsciiString(account);
    mplew.write(
      new byte[] {
        3,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        (byte) 0xDC,
        0x3D,
        0x0B,
        0x28,
        0x64,
        (byte) 0xC5,
        1,
        8,
        0,
        0,
        0,
      }
    );
    return mplew.getPacket();
  }

  /**
   * Gets a packet detailing a PIN operation.
   *
   * Possible values for <code>mode</code>:<br>
   * 0 - PIN was accepted<br>
   * 1 - Register a new PIN<br>
   * 2 - Invalid pin / Reenter<br>
   * 3 - Connection failed due to system error<br>
   * 4 - Enter the pin
   *
   * @param mode The mode.
   * @return
   */
  public static MaplePacket pinOperation(byte mode) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);
    mplew.writeShort(SendPacketOpcode.PIN_OPERATION.getValue());
    mplew.write(mode);
    return mplew.getPacket();
  }

  /**
   * Gets a packet requesting the client enter a PIN.
   *
   * @return The request PIN packet.
   */
  public static MaplePacket requestPin() {
    return pinOperation((byte) 4);
  }

  /**
   * Gets a packet requesting the PIN after a failed attempt.
   *
   * @return The failed PIN packet.
   */
  public static MaplePacket requestPinAfterFailure() {
    return pinOperation((byte) 2);
  }

  /**
   * Gets a packet saying the PIN has been accepted.
   *
   * @return The PIN accepted packet.
   */
  public static MaplePacket pinAccepted() {
    return pinOperation((byte) 0);
  }

  /**
   * Gets a packet detailing a server and its channels.
   *
   * @param serverIndex The index of the server to create information about.
   * @param serverName The name of the server.
   * @param channelLoad Load of the channel - 1200 seems to be max.
   * @return The server info packet.
   */
  public static MaplePacket getServerList(
    int serverId,
    String serverName,
    Map<Integer, Integer> channelLoad
  ) {
    /*
     * 0B 00 00 06 00 53 63 61 6E 69 61 00 00 00 64 00 64 00 00 13 08 00 53 63 61 6E 69 61 2D 31 5E 04 00 00 00 00
     * 00 08 00 53 63 61 6E 69 61 2D 32 25 01 00 00 00 01 00 08 00 53 63 61 6E 69 61 2D 33 F6 00 00 00 00 02 00 08
     * 00 53 63 61 6E 69 61 2D 34 BC 00 00 00 00 03 00 08 00 53 63 61 6E 69 61 2D 35 E7 00 00 00 00 04 00 08 00 53
     * 63 61 6E 69 61 2D 36 BC 00 00 00 00 05 00 08 00 53 63 61 6E 69 61 2D 37 C2 00 00 00 00 06 00 08 00 53 63 61
     * 6E 69 61 2D 38 BB 00 00 00 00 07 00 08 00 53 63 61 6E 69 61 2D 39 C0 00 00 00 00 08 00 09 00 53 63 61 6E 69
     * 61 2D 31 30 C3 00 00 00 00 09 00 09 00 53 63 61 6E 69 61 2D 31 31 BB 00 00 00 00 0A 00 09 00 53 63 61 6E 69
     * 61 2D 31 32 AB 00 00 00 00 0B 00 09 00 53 63 61 6E 69 61 2D 31 33 C7 00 00 00 00 0C 00 09 00 53 63 61 6E 69
     * 61 2D 31 34 B9 00 00 00 00 0D 00 09 00 53 63 61 6E 69 61 2D 31 35 AE 00 00 00 00 0E 00 09 00 53 63 61 6E 69
     * 61 2D 31 36 B6 00 00 00 00 0F 00 09 00 53 63 61 6E 69 61 2D 31 37 DB 00 00 00 00 10 00 09 00 53 63 61 6E 69
     * 61 2D 31 38 C7 00 00 00 00 11 00 09 00 53 63 61 6E 69 61 2D 31 39 EF 00 00 00 00 12 00
     */
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SERVERLIST.getValue());
    mplew.write(serverId);
    mplew.writeMapleAsciiString(serverName);
    mplew.write(LoginServer.getInstance().getFlag());
    mplew.writeMapleAsciiString(LoginServer.getInstance().getEventMessage());
    mplew.write(0x64); // rate modifier, don't ask O.O!

    mplew.write(0x0); // event xp * 2.6 O.O!

    mplew.write(0x64); // rate modifier, don't ask O.O!

    mplew.write(0x0); // drop rate * 2.6

    mplew.write(0x0);
    int lastChannel = 1;
    Set<Integer> channels = channelLoad.keySet();
    for (int i = 30; i > 0; i--) {
      if (channels.contains(i)) {
        lastChannel = i;
        break;
      }
    }
    mplew.write(lastChannel);

    int load;
    for (int i = 1; i <= lastChannel; i++) {
      if (channels.contains(i)) {
        load = channelLoad.get(i);
      } else {
        load = 1200;
      }
      mplew.writeMapleAsciiString(serverName + "-" + i);
      mplew.writeInt(load);
      mplew.write(1);
      mplew.writeShort(i - 1);
    }
    mplew.writeShort(0); // ver 0.56

    return mplew.getPacket();
  }

  /**
   * Gets a packet saying that the server list is over.
   *
   * @return The end of server list packet.
   */
  public static MaplePacket getEndOfServerList() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SERVERLIST.getValue());
    mplew.write(0xFF);

    return mplew.getPacket();
  }

  /**
   * Gets a packet detailing a server status message.
   *
   * Possible values for <code>status</code>:<br>
   * 0 - Normal<br>
   * 1 - Highly populated<br>
   * 2 - Full
   *
   * @param status The server status.
   * @return The server status packet.
   */
  public static MaplePacket getServerStatus(int status) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SERVERSTATUS.getValue());
    mplew.writeShort(status);

    return mplew.getPacket();
  }

  /**
   * Gets a packet telling the client the IP of the channel server.
   *
   * @param inetAddr The InetAddress of the requested channel server.
   * @param port The port the channel is on.
   * @param clientId The ID of the client.
   * @return The server IP packet.
   */
  public static MaplePacket getServerIP(
    InetAddress inetAddr,
    int port,
    int clientId
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SERVER_IP.getValue());
    mplew.writeShort(0);
    byte[] addr = inetAddr.getAddress();
    mplew.write(addr);
    mplew.writeShort(port);
    // 0x13 = numchannels?
    mplew.writeInt(clientId); // this gets repeated to the channel server
    // leos.write(new byte[] { (byte) 0x13, (byte) 0x37, 0x42, 1, 0, 0, 0, 0, 0 });

    mplew.write(new byte[] { 0, 0, 0, 0, 0 });
    // 0D 00 00 00 3F FB D9 0D 8A 21 CB A8 13 00 00 00 00 00 00

    return mplew.getPacket();
  }

  /**
   * Gets a packet telling the client the IP of the new channel.
   *
   * @param inetAddr The InetAddress of the requested channel server.
   * @param port The port the channel is on.
   * @return The server IP packet.
   */
  public static MaplePacket getChannelChange(InetAddress inetAddr, int port) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CHANGE_CHANNEL.getValue());
    mplew.write(1);
    byte[] addr = inetAddr.getAddress();
    mplew.write(addr);
    mplew.writeShort(port);

    return mplew.getPacket();
  }

  /*

  private static void addCharEquips(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        MapleInventory equip = chr.getInventory(MapleInventoryType.EQUIPPED);
        Collection<IItem> ii = equip.list();
        Map<Byte, Integer> myEquip = new LinkedHashMap<Byte, Integer>();
        Map<Byte, Integer> maskedEquip = new LinkedHashMap<Byte, Integer>();
        for (IItem item : ii) {
            byte pos = (byte) (item.getPosition() * -1);
            if (pos < 100 && myEquip.get(pos) == null) {
                myEquip.put(pos, item.getItemId());
            } else if (pos > 100 && pos != 111) { // don't ask. o.o
                pos -= 100;
                if (myEquip.get(pos) != null) {
                    maskedEquip.put(pos, myEquip.get(pos));
                }
                myEquip.put(pos, item.getItemId());
            } else if (myEquip.get(pos) != null) {
                maskedEquip.put(pos, item.getItemId());
            }
        }
        for (Entry<Byte, Integer> entry : myEquip.entrySet()) {
            mplew.write(entry.getKey());
            mplew.writeInt(entry.getValue());
        }

        mplew.write(0xFF);
        for (Entry<Byte, Integer> entry : maskedEquip.entrySet()) {
            mplew.write(entry.getKey());
            mplew.writeInt(entry.getValue());
        }
        mplew.write(0xFF);
        IItem cWeapon = equip.getItem((byte) -111);
        mplew.writeInt(cWeapon != null ? cWeapon.getItemId() : 0);
        for (int i = 0; i < 3; i++) {
            if (chr.getPet(i) != null) {
                mplew.writeInt(chr.getPet(i).getItemId());
            } else {
                mplew.writeInt(0);
            }
        }
    }
*/

  /**
   * Adds a quest info entry for a character to an existing
   * MaplePacketLittleEndianWriter.
   *
   * @param mplew The MaplePacketLittleEndianWrite instance to write the stats to.
   * @param chr The character to add quest info about.
   */
  /**
   * Adds a quest info entry for a character to an existing
   * MaplePacketLittleEndianWriter.
   *
   * @param mplew The MaplePacketLittleEndianWrite instance to write the stats to.
   * @param chr The character to add quest info about.
   */
  private static void addQuestInfo(
    MaplePacketLittleEndianWriter mplew,
    MapleCharacter chr
  ) {
    List<MapleQuestStatus> started = chr.getStartedQuests();
    mplew.writeShort(started.size());
    for (MapleQuestStatus q : started) {
      mplew.writeShort(q.getQuest().getId());
      mplew.writeMapleAsciiString(q.getQuestData());
    }
    List<MapleQuestStatus> completed = chr.getCompletedQuests();
    mplew.writeShort(completed.size());
    for (MapleQuestStatus q : completed) {
      mplew.writeShort(q.getQuest().getId());
      mplew.writeLong(q.getCompletionTime());
    }
  }

  /**
   * Gets character info for a character.
   *
   * @param chr The character to get info about.
   * @return The character info packet.
   */
  public static MaplePacket getCharInfo(MapleCharacter chr) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.WARP_TO_MAP.getValue()); // 0x49
    mplew.writeInt(chr.getClient().getChannel() - 1);
    mplew.write(1);
    mplew.write(1);
    mplew.writeShort(0);
    mplew.writeInt(new Random().nextInt()); // seed the maplestory rng with a random number <3
    mplew.write(HexTool.getByteArrayFromHexString("F8 17 D7 13 CD C5 AD 78"));
    PacketHelper.addCharWarp(mplew, chr);
    addQuestInfo(mplew, chr);
    /*
        00 00
        01 00
        62 63 33 00
        54 61 6B 65 4D 65 57 69 74 68 00 01 BC
        07 6C 31 00
        00 00 00 00
        08 6C 31 00
        00 00
        
        00 00
        01 00
        0F FD 00 00
        4D 75 73 68 53 6F 6E 6E 79 00 36 34 00
        3B 13 32 00
        00 00 00 00
        3C 13 32 00
        00 00 00 00
        E0 FA 10 00
        00 00
         */
    /*
        01 00
        77 6B 2E 00
        67 66 64 67 68 67 68 66 67 00 00 00 BC
        3C 13 32 00
        00 00 00 00
        3B 13 32 00
        00 00 00 00
        E0 FA 10 00
        00 00*/
    MapleInventory iv = chr.getInventory(MapleInventoryType.EQUIPPED);
    Collection<IItem> equippedC = iv.list();
    List<Item> equipped = new ArrayList<Item>(equippedC.size());
    for (IItem item : equippedC) {
      equipped.add((Item) item);
    }
    Collections.sort(equipped);
    List<MapleRing> rings = new ArrayList<MapleRing>();
    for (Item item : equipped) {
      if (((IEquip) item).getRingId() > -1) {
        rings.add(MapleRing.loadFromDb(((IEquip) item).getRingId()));
      }
    }
    iv = chr.getInventory(MapleInventoryType.EQUIP);
    for (IItem item : iv.list()) {
      if (((IEquip) item).getRingId() > -1) {
        rings.add(MapleRing.loadFromDb(((IEquip) item).getRingId()));
      }
    }
    Collections.sort(rings);
    boolean FR_last = false;
    for (MapleRing ring : rings) {
      if (
        (
          ring.getItemId() >= 1112800 &&
          ring.getItemId() <= 1112803 ||
          ring.getItemId() <= 1112806 ||
          ring.getItemId() <= 1112807 ||
          ring.getItemId() <= 1112809
        ) &&
        rings.indexOf(ring) == 0
      ) {
        mplew.writeShort(0);
      }
      mplew.writeShort(0);
      mplew.writeShort(1);
      mplew.writeInt(ring.getPartnerChrId());
      mplew.writeAsciiString(
        StringUtil.getRightPaddedStr(ring.getPartnerName(), '\0', 13)
      );
      mplew.writeInt(ring.getRingId());
      mplew.writeInt(0);
      mplew.writeInt(ring.getPartnerRingId());
      if (
        ring.getItemId() >= 1112800 &&
        ring.getItemId() <= 1112803 ||
        ring.getItemId() <= 1112806 ||
        ring.getItemId() <= 1112807 ||
        ring.getItemId() <= 1112809
      ) {
        FR_last = true;
        mplew.writeInt(0);
        mplew.writeInt(ring.getItemId());
        mplew.writeShort(0);
      } else {
        if (rings.size() > 1) {
          mplew.writeShort(0);
        }
        FR_last = false;
      }
    }
    if (!FR_last) {
      mplew.writeLong(0);
    }
    List<Integer> maps = chr.getVIPRockMaps(0);
    for (int map : maps) {
      mplew.writeInt(map);
    }
    for (int i = maps.size(); i < 5; i++) {
      mplew.write(CHAR_INFO_MAGIC);
    }
    maps = chr.getVIPRockMaps(1);
    for (int map : maps) {
      mplew.writeInt(map);
    }
    for (int i = maps.size(); i < 10; i++) {
      mplew.write(CHAR_INFO_MAGIC);
    }
    maps.clear();
    mplew.writeInt(0);
    mplew.writeLong(PacketHelper.getTime(System.currentTimeMillis()));
    return mplew.getPacket();
  }

  private static void checkRing(
    MaplePacketLittleEndianWriter mplew,
    List<MapleRing> ringlist
  ) {
    if (ringlist.isEmpty()) {
      mplew.write(0);
    } else {
      addRingPacketInfo(mplew, ringlist);
    }
  }

  private static void addRingPacketInfo(
    MaplePacketLittleEndianWriter mplew,
    List<MapleRing> ringlist
  ) {
    for (MapleRing mr : ringlist) {
      mplew.write(1);
      mplew.writeInt(mr.getRingId());
      mplew.writeInt(0);
      mplew.writeInt(mr.getPartnerRingId());
      mplew.writeInt(0);
      mplew.writeInt(mr.getItemId());
    }
  }

  /**
   * Gets an empty stat update.
   *
   * @return The empy stat update packet.
   */
  public static MaplePacket enableActions() {
    return updatePlayerStats(EMPTY_STATUPDATE, true);
  }

  /**
   * Gets an update for specified stats.
   *
   * @param stats The stats to update.
   * @return The stat update packet.
   */
  public static MaplePacket updatePlayerStats(
    List<Pair<MapleStat, Integer>> stats
  ) {
    return updatePlayerStats(stats, false);
  }

  /**
   * Gets an update for specified stats.
   *
   * @param stats The list of stats to update.
   * @param itemReaction Result of an item reaction(?)
   * @return The stat update packet.
   */
  public static MaplePacket updatePlayerStats(
    List<Pair<MapleStat, Integer>> stats,
    boolean itemReaction
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.UPDATE_STATS.getValue());
    if (itemReaction) {
      mplew.write(1);
    } else {
      mplew.write(0);
    }
    int updateMask = 0;
    for (Pair<MapleStat, Integer> statupdate : stats) {
      updateMask |= statupdate.getLeft().getValue();
    }
    List<Pair<MapleStat, Integer>> mystats = stats;
    if (mystats.size() > 1) {
      Collections.sort(
        mystats,
        new Comparator<Pair<MapleStat, Integer>>() {
          @Override
          public int compare(
            Pair<MapleStat, Integer> o1,
            Pair<MapleStat, Integer> o2
          ) {
            int val1 = o1.getLeft().getValue();
            int val2 = o2.getLeft().getValue();
            return (val1 < val2 ? -1 : (val1 == val2 ? 0 : 1));
          }
        }
      );
    }
    mplew.writeInt(updateMask);
    for (Pair<MapleStat, Integer> statupdate : mystats) {
      if (statupdate.getLeft().getValue() >= 1) {
        if (statupdate.getLeft().getValue() == 0x1) {
          mplew.writeShort(statupdate.getRight().shortValue());
        } else if (statupdate.getLeft().getValue() <= 0x4) {
          mplew.writeInt(statupdate.getRight());
        } else if (statupdate.getLeft().getValue() < 0x20) {
          mplew.write(statupdate.getRight().shortValue());
        } else if (statupdate.getLeft().getValue() < 0xFFFF) {
          mplew.writeShort(statupdate.getRight().shortValue());
        } else {
          mplew.writeInt(statupdate.getRight().intValue());
        }
      }
    }

    return mplew.getPacket();
  }

  /**
   * Gets a packet telling the client to change maps.
   *
   * @param to The <code>MapleMap</code> to warp to.
   * @param spawnPoint The spawn portal number to spawn at.
   * @param chr The character warping to <code>to</code>
   * @return The map change packet.
   */
  public static MaplePacket getWarpToMap(
    MapleMap to,
    int spawnPoint,
    MapleCharacter chr
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.WARP_TO_MAP.getValue()); // 0x49
    mplew.writeInt(chr.getClient().getChannel() - 1);
    mplew.writeShort(0x2);
    mplew.writeShort(0);
    mplew.writeInt(to.getId());
    mplew.write(spawnPoint);
    mplew.writeShort(chr.getHp()); // hp (???)
    mplew.write(0);
    long questMask = 0x1ffffffffffffffL;
    mplew.writeLong(questMask);

    return mplew.getPacket();
  }

  /**
   * Gets a packet to spawn a portal.
   *
   * @param townId The ID of the town the portal goes to.
   * @param targetId The ID of the target.
   * @param pos Where to put the portal.
   * @return The portal spawn packet.
   */
  public static MaplePacket spawnPortal(int townId, int targetId, Point pos) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SPAWN_PORTAL.getValue());
    mplew.writeInt(townId);
    mplew.writeInt(targetId);
    if (pos != null) {
      mplew.writeShort(pos.x);
      mplew.writeShort(pos.y);
    }

    return mplew.getPacket();
  }

  /**
   * Gets a packet to spawn a door.
   *
   * @param oid The door's object ID.
   * @param pos The position of the door.
   * @param town
   * @return The remove door packet.
   */
  public static MaplePacket spawnDoor(int oid, Point pos, boolean town) {
    // [D3 00] [01] [93 AC 00 00] [6B 05] [37 03]
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SPAWN_DOOR.getValue());
    mplew.write(town ? 1 : 0);
    mplew.writeInt(oid);
    mplew.writeShort(pos.x);
    mplew.writeShort(pos.y);

    return mplew.getPacket();
  }

  /**
   * Gets a packet to remove a door.
   *
   * @param oid The door's ID.
   * @param town
   * @return The remove door packet.
   */
  public static MaplePacket removeDoor(int oid, boolean town) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    if (town) {
      mplew.writeShort(SendPacketOpcode.SPAWN_PORTAL.getValue());
      mplew.writeInt(999999999);
      mplew.writeInt(999999999);
    } else {
      mplew.writeShort(SendPacketOpcode.REMOVE_DOOR.getValue());
      mplew.write(/*town ? 1 : */0);
      mplew.writeInt(oid);
    }

    return mplew.getPacket();
  }

  /**
   * Gets a packet to spawn a special map object.
   *
   * @param summon
   * @param skillLevel The level of the skill used.
   * @param animated Animated spawn?
   * @return The spawn packet for the map object.
   */
  public static MaplePacket spawnSpecialMapObject(
    MapleSummon summon,
    int skillLevel,
    boolean animated
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.SPAWN_SPECIAL_MAPOBJECT.getValue());
    mplew.writeInt(summon.getOwner().getId());
    mplew.writeInt(summon.getObjectId()); // Supposed to be Object ID, but this works too! <3
    mplew.writeInt(summon.getSkill());
    mplew.write(skillLevel);
    mplew.writeShort(summon.getPosition().x);
    mplew.writeShort(summon.getPosition().y);
    mplew.write(3); // test
    mplew.write(0); // test
    mplew.write(0); // test
    mplew.write(summon.getMovementType().getValue()); // 0 = don't move, 1 = follow (4th mage summons?), 2/4 = only tele follow, 3 = bird follow
    mplew.write(1); // 0 and the summon can't attack - but puppets don't attack with 1 either ^.-
    mplew.write(animated ? 0 : 1);
    return mplew.getPacket();
  }

  public static MaplePacket spawnSummon(
    MapleSummon summon,
    int skillLevel,
    boolean animated
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SPAWN_SPECIAL_MAPOBJECT.getValue());
    mplew.writeInt(summon.getOwner().getId());
    mplew.writeInt(summon.getObjectId());
    mplew.writeInt(summon.getSkill());
    mplew.write(skillLevel);
    mplew.writePos(summon.getPosition());
    mplew.write(0);
    mplew.writeShort(0);
    mplew.write(summon.getMovementType().getValue()); // 0 = don't move, 1 = follow (4th mage summons?), 2/4 = only tele follow, 3 = bird follow
    mplew.write(summon.isPuppet() ? 0 : 1); // 0 = Summon can't attack - but puppets don't attack with 1 either ^.-
    mplew.write(animated ? 0 : 1);

    return mplew.getPacket();
  }

  /**
   * Gets a packet to remove a special map object.
   *
   * @param summon
   * @param animated Animated removal?
   * @return The packet removing the object.
   */
  public static MaplePacket removeSpecialMapObject(
    MapleSummon summon,
    boolean animated
  ) {
    // [86 00] [6A 4D 27 00] 33 1F 00 00 02
    // 92 00 36 1F 00 00 0F 65 85 01 84 02 06 46 28 00 06 81 02 01 D9 00 BD FB D9 00 BD FB 38 04 2F 21 00 00 10 C1 2A 00 06 00 06 01 00 01 BD FB FC 00 BD FB 6A 04 88 1D 00 00 7D 01 AF FB
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.REMOVE_SPECIAL_MAPOBJECT.getValue());
    mplew.writeInt(summon.getOwner().getId());
    mplew.writeInt(summon.getObjectId());
    mplew.write(animated ? 4 : 1); // ?
    return mplew.getPacket();
  }

  /**
   * Gets the response to a relog request.
   *
   * @return The relog response packet.
   */
  public static MaplePacket getRelogResponse() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);

    mplew.writeShort(SendPacketOpcode.RELOG_RESPONSE.getValue());
    mplew.write(1);

    return mplew.getPacket();
  }

  /**
   * Gets a server message packet.
   *
   * @param message The message to convey.
   * @return The server message packet.
   */
  public static MaplePacket serverMessage(String message) {
    return serverMessage(4, 0, message, true, false);
  }

  /**
   * Gets a server notice packet.
   *
   * Possible values for <code>type</code>:<br>
   * 0: [Notice]<br>
   * 1: Popup<br>
   * 2: Megaphone<br>
   * 3: Super Megaphone<br>
   * 4: Scrolling message at top<br>
   * 5: Pink Text<br>
   * 6: Lightblue Text
   *
   * @param type The type of the notice.
   * @param message The message to convey.
   * @return The server notice packet.
   */
  public static MaplePacket serverNotice(int type, String message) {
    return serverMessage(type, 0, message, false, false);
  }

  /**
   * Gets a server notice packet.
   *
   * Possible values for <code>type</code>:<br>
   * 0: [Notice]<br>
   * 1: Popup<br>
   * 2: Megaphone<br>
   * 3: Super Megaphone<br>
   * 4: Scrolling message at top<br>
   * 5: Pink Text<br>
   * 6: Lightblue Text
   *
   * @param type The type of the notice.
   * @param channel The channel this notice was sent on.
   * @param message The message to convey.
   * @return The server notice packet.
   */
  public static MaplePacket serverNotice(
    int type,
    int channel,
    String message
  ) {
    return serverMessage(type, channel, message, false, false);
  }

  public static MaplePacket serverNotice(
    int type,
    int channel,
    String message,
    boolean smegaEar
  ) {
    return serverMessage(type, channel, message, false, smegaEar);
  }

  /**
   * Gets a server message packet.
   *
   * Possible values for <code>type</code>:<br>
   * 0: [Notice]<br>
   * 1: Popup<br>
   * 2: Megaphone<br>
   * 3: Super Megaphone<br>
   * 4: Scrolling message at top<br>
   * 5: Pink Text<br>
   * 6: Lightblue Text
   *
   * @param type The type of the notice.
   * @param channel The channel this notice was sent on.
   * @param message The message to convey.
   * @param servermessage Is this a scrolling ticker?
   * @return The server notice packet.
   */
  private static MaplePacket serverMessage(
    int type,
    int channel,
    String message,
    boolean servermessage,
    boolean megaEar
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SERVERMESSAGE.getValue());
    mplew.write(type);
    if (servermessage) {
      mplew.write(1);
    }
    mplew.writeMapleAsciiString(message);
    if (type == 3) {
      mplew.write(channel - 1); // channel
      mplew.write(megaEar ? 1 : 0);
    }

    return mplew.getPacket();
  }

  /**
   * Gets an avatar megaphone packet.
   *
   * @param chr The character using the avatar megaphone.
   * @param channel The channel the character is on.
   * @param itemId The ID of the avatar-mega.
   * @param message The message that is sent.
   * @param ear
   * @return The avatar mega packet.
   */
  public static MaplePacket getAvatarMega(
    MapleCharacter chr,
    int channel,
    int itemId,
    List<String> message,
    boolean ear
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.AVATAR_MEGA.getValue());
    mplew.writeInt(itemId);
    mplew.writeMapleAsciiString(chr.getName());
    for (String s : message) {
      mplew.writeMapleAsciiString(s);
    }
    mplew.writeInt(channel - 1); // channel
    mplew.write(ear ? 1 : 0);
    PacketHelper.addCharLook(mplew, chr, true);

    return mplew.getPacket();
  }

  /**
   * Gets a NPC spawn packet.
   *
   * @param life The NPC to spawn.
   * @return The NPC spawn packet.
   */
  public static MaplePacket spawnNPC(MapleNPC life) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SPAWN_NPC.getValue());
    mplew.writeInt(life.getObjectId());
    mplew.writeInt(life.getId());
    mplew.writeShort(life.getPosition().x);
    mplew.writeShort(life.getCy());
    if (life.getF() == 1) {
      mplew.write(0);
    } else {
      mplew.write(1);
    }
    mplew.writeShort(life.getFh());
    mplew.writeShort(life.getRx0());
    mplew.writeShort(life.getRx1());
    mplew.write(1);

    return mplew.getPacket();
  }

  public static MaplePacket spawnNPC(MapleNPC life, boolean show) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SPAWN_NPC.getValue());
    mplew.writeInt(life.getObjectId());
    mplew.writeInt(life.getId());
    mplew.writeShort(life.getPosition().x);
    mplew.writeShort(life.getCy());
    mplew.write(life.getF() == 1 ? 0 : 1);
    mplew.writeShort(life.getFh());
    mplew.writeShort(life.getRx0());
    mplew.writeShort(life.getRx1());
    mplew.write(show ? 1 : 0);

    return mplew.getPacket();
  }

  public static MaplePacket spawnNPCRequestController(
    MapleNPC life,
    boolean MiniMap
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SPAWN_NPC_REQUEST_CONTROLLER.getValue());
    mplew.write(1);
    mplew.writeInt(life.getObjectId());
    mplew.writeInt(life.getId());
    mplew.writeShort(life.getPosition().x);
    mplew.writeShort(life.getCy());
    mplew.write(life.getF() == 1 ? 0 : 1);
    mplew.writeShort(life.getFh());
    mplew.writeShort(life.getRx0());
    mplew.writeShort(life.getRx1());
    mplew.write(MiniMap ? 1 : 0);

    return mplew.getPacket();
  }

  /**
   * Gets a spawn monster packet.
   *
   * @param life The monster to spawn.
   * @param newSpawn Is it a new spawn?
   * @return The spawn monster packet.
   */
  public static MaplePacket spawnMonster(MapleMonster life, boolean newSpawn) {
    return spawnMonsterInternal(life, false, newSpawn, false, 0, false);
  }

  /**
   * Gets a spawn monster packet.
   *
   * @param life The monster to spawn.
   * @param newSpawn Is it a new spawn?
   * @param effect The spawn effect.
   * @return The spawn monster packet.
   */
  public static MaplePacket spawnMonster(
    MapleMonster life,
    boolean newSpawn,
    int effect
  ) {
    return spawnMonsterInternal(life, false, newSpawn, false, effect, false);
  }

  /**
   * Gets a control monster packet.
   *
   * @param life The monster to give control to.
   * @param newSpawn Is it a new spawn?
   * @param aggro Aggressive monster?
   * @return The monster control packet.
   */
  public static MaplePacket controlMonster(
    MapleMonster life,
    boolean newSpawn,
    boolean aggro
  ) {
    return spawnMonsterInternal(life, true, newSpawn, aggro, 0, false);
  }

  public static MaplePacket makeMonsterInvisible(MapleMonster life) {
    return spawnMonsterInternal(life, true, false, false, 0, true);
  }

  /**
   * Internal function to handler monster spawning and controlling.
   *
   * @param life The mob to perform operations with.
   * @param requestController Requesting control of mob?
   * @param newSpawn New spawn (fade in?)
   * @param aggro Aggressive mob?
   * @param effect The spawn effect to use.
   * @return The spawn/control packet.
   */
  private static MaplePacket spawnMonsterInternal(
    MapleMonster life,
    boolean requestController,
    boolean newSpawn,
    boolean aggro,
    int effect,
    boolean makeInvis
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    if (makeInvis) {
      mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
      mplew.write(0);
      mplew.writeInt(life.getObjectId());
      return mplew.getPacket();
    }
    if (requestController) {
      mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
      if (aggro) mplew.write(2); else mplew.write(1);
    } else mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER.getValue());
    mplew.writeInt(life.getObjectId());
    mplew.write(5); // ????!? either 5 or 1?
    mplew.writeInt(life.getId());
    mplew.write(0);
    mplew.writeShort(0);
    mplew.write(8);
    mplew.writeInt(0);
    mplew.writeShort(life.getPosition().x);
    mplew.writeShort(life.getPosition().y);
    mplew.write(life.getStance());
    mplew.writeShort(0);
    mplew.writeShort(life.getFh());
    if (effect > 0) {
      mplew.write(effect);
      mplew.write(0);
      mplew.writeShort(0);
    }
    if (newSpawn) mplew.writeShort(-2); else mplew.writeShort(-1);
    mplew.writeInt(0);
    return mplew.getPacket();
  }

  /**
   * Handles monsters not being targettable, such as Zakum's first body.
   * @param life The mob to spawn as non-targettable.
   * @param effect The effect to show when spawning.
   * @return The packet to spawn the mob as non-targettable.
   */
  public static MaplePacket spawnFakeMonster(MapleMonster life, int effect) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
    mplew.write(1);
    mplew.writeInt(life.getObjectId());
    mplew.write(5);
    mplew.writeInt(life.getId());
    mplew.writeInt(0);
    mplew.writeShort(life.getPosition().x);
    mplew.writeShort(life.getPosition().y);
    mplew.write(life.getStance());
    mplew.writeShort(life.getStartFh());
    mplew.writeShort(life.getFh());
    if (effect > 0) {
      mplew.write(effect);
      mplew.write(0);
      mplew.writeShort(0);
    }
    mplew.writeShort(-2);
    mplew.writeInt(0);
    return mplew.getPacket();
  }

  /**
   * Makes a monster previously spawned as non-targettable, targettable.
   * @param life The mob to make targettable.
   * @return The packet to make the mob targettable.
   */
  public static MaplePacket makeMonsterReal(MapleMonster life) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER.getValue());
    mplew.writeInt(life.getObjectId());
    mplew.write(5);
    mplew.writeInt(life.getId());
    mplew.writeInt(0);
    mplew.writeShort(life.getPosition().x);
    mplew.writeShort(life.getPosition().y);
    mplew.write(life.getStance());
    mplew.writeShort(life.getStartFh());
    mplew.writeShort(life.getFh());
    mplew.writeShort(-1);
    mplew.writeInt(0);
    return mplew.getPacket();
  }

  /**
   * Gets a stop control monster packet.
   *
   * @param oid The ObjectID of the monster to stop controlling.
   * @return The stop control monster packet.
   */

  public static MaplePacket stopControllingMonster(int oid) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
    mplew.write(0);
    mplew.writeInt(oid);

    return mplew.getPacket();
  }

  /**
   * Gets a response to a move monster packet.
   *
   * @param objectid The ObjectID of the monster being moved.
   * @param moveid The movement ID.
   * @param currentMp The current MP of the monster.
   * @param useSkills Can the monster use skills?
   * @return The move response packet.
   */
  public static MaplePacket moveMonsterResponse(
    int objectid,
    short moveid,
    int currentMp,
    boolean useSkills
  ) {
    return moveMonsterResponse(objectid, moveid, currentMp, useSkills, 0, 0);
  }

  /**
   * Gets a response to a move monster packet.
   *
   * @param objectid The ObjectID of the monster being moved.
   * @param moveid The movement ID.
   * @param currentMp The current MP of the monster.
   * @param useSkills Can the monster use skills?
   * @param skillId The skill ID for the monster to use.
   * @param skillLevel The level of the skill to use.
   * @return The move response packet.
   */
  public static MaplePacket moveMonsterResponse(
    int objectid,
    short moveid,
    int currentMp,
    boolean useSkills,
    int skillId,
    int skillLevel
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.MOVE_MONSTER_RESPONSE.getValue());
    mplew.writeInt(objectid);
    mplew.writeShort(moveid);
    mplew.write(useSkills ? 1 : 0);
    mplew.writeShort(currentMp);
    mplew.write(skillId);
    mplew.write(skillLevel);
    return mplew.getPacket();
  }

  /**
   * Gets a general chat packet.
   *
   * @param cidfrom The character ID who sent the chat.
   * @param text The text of the chat.
   * @param whiteBG
   * @param show
   * @return The general chat packet.
   */
  public static MaplePacket getChatText(
    int cidfrom,
    String text,
    boolean whiteBG,
    int show
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CHATTEXT.getValue());
    mplew.writeInt(cidfrom);
    mplew.write(whiteBG ? 1 : 0);
    mplew.writeMapleAsciiString(text);
    mplew.write(show);

    return mplew.getPacket();
  }

  /**
   * For testing only! Gets a packet from a hexadecimal string.
   *
   * @param hex The hexadecimal packet to create.
   * @return The MaplePacket representing the hex string.
   */
  public static MaplePacket getPacketFromHexString(String hex) {
    byte[] b = HexTool.getByteArrayFromHexString(hex);
    return new ByteArrayMaplePacket(b);
  }

  /**
   * Gets a packet telling the client to show an EXP increase.
   *
   * @param gain The amount of EXP gained.
   * @param inChat In the chat box?
   * @param white White text or yellow?
   * @return The exp gained packet.
   */
  public static MaplePacket getShowExpGain(
    int gain,
    boolean inChat,
    boolean white
  ) {
    // 20 00 03 01 0A 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
    mplew.write(3); // 3 = exp, 4 = fame, 5 = mesos, 6 = guildpoints
    mplew.write(white ? 1 : 0);
    mplew.writeInt(gain);
    mplew.write(inChat ? 1 : 0);
    mplew.writeInt(0);
    mplew.writeInt(0);
    mplew.writeInt(0);

    return mplew.getPacket();
  }

  /**
   * Gets a packet telling the client to show a fame gain.
   *
   * @param gain How many fame gained.
   * @return The meso gain packet.
   */
  public static MaplePacket getShowFameGain(int gain) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
    mplew.write(4);
    mplew.writeInt(gain);

    return mplew.getPacket();
  }

  /**
   * Gets a packet telling the client to show a meso gain.
   *
   * @param gain How many mesos gained.
   * @return The meso gain packet.
   */
  public static MaplePacket getShowMesoGain(int gain) {
    return getShowMesoGain(gain, false);
  }

  /**
   * Gets a packet telling the client to show a meso gain.
   *
   * @param gain How many mesos gained.
   * @param inChat Show in the chat window?
   * @return The meso gain packet.
   */
  public static MaplePacket getShowMesoGain(int gain, boolean inChat) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
    if (!inChat) {
      mplew.write(0);
      mplew.write(1);
    } else {
      mplew.write(5);
    }
    mplew.writeInt(gain);
    mplew.writeShort(0); // inet cafe meso gain ?.o

    return mplew.getPacket();
  }

  /**
   * Gets a packet telling the client to show a item gain.
   *
   * @param itemId The ID of the item gained.
   * @param quantity How many items gained.
   * @return The item gain packet.
   */
  public static MaplePacket getShowItemGain(int itemId, short quantity) {
    return getShowItemGain(itemId, quantity, false);
  }

  /**
   * Gets a packet telling the client to show an item gain.
   *
   * @param itemId The ID of the item gained.
   * @param quantity The number of items gained.
   * @param inChat Show in the chat window?
   * @return The item gain packet.
   */
  public static MaplePacket getShowItemGain(
    int itemId,
    short quantity,
    boolean inChat
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    if (inChat) {
      mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
      mplew.write(3);
      mplew.write(1);
      mplew.writeInt(itemId);
      mplew.writeInt(quantity);
    } else {
      mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
      mplew.writeShort(0);
      mplew.writeInt(itemId);
      mplew.writeInt(quantity);
      mplew.writeInt(0);
      mplew.writeInt(0);
    }

    return mplew.getPacket();
  }

  public static MaplePacket showInfo(String path) {
    final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
    mplew.write(0x17);
    mplew.writeMapleAsciiString(path);
    mplew.writeInt(1);
    return mplew.getPacket();
  }

  public static MaplePacket killMonster(int oid, boolean animation) {
    return killMonster(oid, animation ? 1 : 0);
  }

  /**
   * Gets a packet telling the client that a monster was killed.
   *
   * @param oid The objectID of the killed monster.
   * @param animation 0 = dissapear, 1 = fade out, 2+ = special
   * @return The kill monster packet.
   */
  public static MaplePacket killMonster(int oid, int animation) {
    final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.KILL_MONSTER.getValue());
    mplew.writeInt(oid);
    mplew.write(animation);
    mplew.write(animation);
    return mplew.getPacket();
  }

  /**
   * Gets a packet telling the client to show mesos coming out of a map
   * object.
   *
   * @param amount The amount of mesos.
   * @param itemoid The ObjectID of the dropped mesos.
   * @param dropperoid The OID of the dropper.
   * @param ownerid The ID of the drop owner.
   * @param dropfrom Where to drop from.
   * @param dropto Where the drop lands.
   * @param mod ?
   * @return The drop mesos packet.
   */
  public static MaplePacket dropMesoFromMapObject(
    int amount,
    int itemoid,
    int dropperoid,
    int ownerid,
    Point dropfrom,
    Point dropto,
    byte mod
  ) {
    return dropItemFromMapObjectInternal(
      amount,
      itemoid,
      dropperoid,
      ownerid,
      dropfrom,
      dropto,
      mod,
      true
    );
  }

  /**
   * Gets a packet telling the client to show an item coming out of a map
   * object.
   *
   * @param itemid The ID of the dropped item.
   * @param itemoid The ObjectID of the dropped item.
   * @param dropperoid The OID of the dropper.
   * @param ownerid The ID of the drop owner.
   * @param dropfrom Where to drop from.
   * @param dropto Where the drop lands.
   * @param mod ?
   * @return The drop mesos packet.
   */
  public static MaplePacket dropItemFromMapObject(
    int itemid,
    int itemoid,
    int dropperoid,
    int ownerid,
    Point dropfrom,
    Point dropto,
    byte mod
  ) {
    return dropItemFromMapObjectInternal(
      itemid,
      itemoid,
      dropperoid,
      ownerid,
      dropfrom,
      dropto,
      mod,
      false
    );
  }

  /**
   * Internal function to get a packet to tell the client to drop an item onto
   * the map.
   *
   * @param itemid The ID of the item to drop.
   * @param itemoid The ObjectID of the dropped item.
   * @param dropperoid The OID of the dropper.
   * @param ownerid The ID of the drop owner.
   * @param dropfrom Where to drop from.
   * @param dropto Where the drop lands.
   * @param mod ?
   * @param mesos Is the drop mesos?
   * @return The item drop packet.
   */
  public static MaplePacket dropItemFromMapObjectInternal(
    int itemid,
    int itemoid,
    int dropperoid,
    int ownerid,
    Point dropfrom,
    Point dropto,
    byte mod,
    boolean mesos
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.DROP_ITEM_FROM_MAPOBJECT.getValue());
    mplew.write(mod);
    mplew.writeInt(itemoid);
    mplew.write(mesos ? 1 : 0); // 1 = mesos, 0 =item
    mplew.writeInt(itemid);
    mplew.writeInt(ownerid); // owner charid
    mplew.write(0);
    mplew.writeShort(dropto.x);
    mplew.writeShort(dropto.y);
    if (mod != 2) {
      mplew.writeInt(ownerid);
      mplew.writeShort(dropfrom.x);
      mplew.writeShort(dropfrom.y);
    } else mplew.writeInt(dropperoid);
    mplew.write(0);
    if (mod != 2) {
      mplew.write(0); //who knows
      mplew.write(1); //PET Meso pickup
    }
    if (!mesos) {
      mplew.write(ITEM_MAGIC);
      PacketHelper.addExpirationTime(mplew, System.currentTimeMillis(), false);
      mplew.write(1); //pet EQP pickup
    }
    return mplew.getPacket();
  }

  /* (non-javadoc)
   * TODO: make MapleCharacter a mapobject, remove the need for passing oid
   * here.
   */
  /**
   * Gets a packet spawning a player as a mapobject to other clients.
   *
   * @param chr The character to spawn to other clients.
   * @return The spawn player packet.
   */
  public static MaplePacket spawnPlayerMapobject(MapleCharacter chr) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.SPAWN_PLAYER.getValue());
    mplew.writeInt(chr.getId());
    mplew.writeMapleAsciiString(chr.getName());
    if (!chr.getMap().isWarMap()) {
      if (chr.getGuildId() <= 0) {
        mplew.writeMapleAsciiString("");
        mplew.write(new byte[6]);
      } else {
        MapleGuildSummary gs = chr
          .getClient()
          .getChannelServer()
          .getGuildSummary(chr.getGuildId());

        if (gs != null) {
          mplew.writeMapleAsciiString(gs.getName());
          mplew.writeShort(gs.getLogoBG());
          mplew.write(gs.getLogoBGColor());
          mplew.writeShort(gs.getLogo());
          mplew.write(gs.getLogoColor());
        } else {
          mplew.writeMapleAsciiString("");
          mplew.write(new byte[6]);
        }
      }
    } else {
      if (true) {
        mplew.writeMapleAsciiString("Team Red");
        mplew.writeShort(1000); //bg
        mplew.write(1); //bg color
        mplew.writeShort(9026); //logo
        mplew.write(6); //logo color
      } else {
        mplew.writeMapleAsciiString("Team Blue");
        mplew.writeShort(1000); //bg
        mplew.write(11); //bg color
        mplew.writeShort(9026); //logo
        mplew.write(6); //logo color
      }
    }
    mplew.writeInt(0);
    mplew.writeInt(1);
    if (chr.getBuffedValue(MapleBuffStat.MORPH) != null) {
      mplew.write(2);
    } else {
      mplew.write(0);
    }
    mplew.writeShort(0);
    mplew.write(0xF8);
    long buffmask = 0;
    Integer buffvalue = null;
    if (chr.getBuffedValue(MapleBuffStat.DARKSIGHT) != null) {
      buffmask |= MapleBuffStat.DARKSIGHT.getValue();
    }
    if (chr.getBuffedValue(MapleBuffStat.COMBO) != null) {
      buffmask |= MapleBuffStat.COMBO.getValue();
      buffvalue =
        Integer.valueOf(chr.getBuffedValue(MapleBuffStat.COMBO).intValue());
    }
    if (chr.getBuffedValue(MapleBuffStat.SHADOWPARTNER) != null) {
      buffmask |= MapleBuffStat.SHADOWPARTNER.getValue();
    }
    if (chr.getBuffedValue(MapleBuffStat.SOULARROW) != null) {
      buffmask |= MapleBuffStat.SOULARROW.getValue();
    }
    if (chr.getBuffedValue(MapleBuffStat.MORPH) != null) {
      buffvalue =
        Integer.valueOf(chr.getBuffedValue(MapleBuffStat.MORPH).intValue());
    }
    mplew.writeInt((int) ((buffmask >> 32) & 0xffffffffL));
    if (buffvalue != null) {
      if (chr.getBuffedValue(MapleBuffStat.MORPH) != null) {
        mplew.writeShort(buffvalue);
      } else {
        mplew.write(buffvalue.byteValue());
      }
    }
    mplew.writeInt((int) (buffmask & 0xffffffffL));
    int CHAR_MAGIC_SPAWN = new Random().nextInt();
    mplew.writeInt(0);
    mplew.writeShort(0);
    mplew.writeInt(CHAR_MAGIC_SPAWN);
    mplew.writeLong(0);
    mplew.writeShort(0);
    mplew.writeInt(CHAR_MAGIC_SPAWN);
    mplew.writeLong(0);
    mplew.writeShort(0);
    mplew.writeInt(CHAR_MAGIC_SPAWN);
    mplew.writeShort(0);
    MapleMount mount = chr.getMount();
    if (chr.getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
      if (chr.hasBattleShip()) {
        mplew.writeInt(1932000);
        mplew.writeInt(5221006);
      } else if (mount != null) {
        if (
          chr.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -19) !=
          null
        ) {
          mplew.writeInt(mount.getItemId());
          mplew.writeInt(mount.getSkillId());
        }
      }
      mplew.writeInt(0x2D4DFC2A);
    } else {
      mplew.writeLong(0);
      mplew.writeInt(CHAR_MAGIC_SPAWN);
    }
    mplew.writeLong(0);
    mplew.writeInt(CHAR_MAGIC_SPAWN);
    mplew.writeLong(0);
    mplew.writeInt(0);
    mplew.writeShort(0);
    mplew.writeInt(CHAR_MAGIC_SPAWN);
    mplew.writeInt(0);
    mplew.write(0x40);
    mplew.write(1);
    PacketHelper.addCharLook(mplew, chr, false);
    mplew.writeInt(0);
    mplew.writeInt(chr.getItemEffect());
    mplew.writeInt(chr.getChair());
    mplew.writeShort(chr.getPosition().x);
    mplew.writeShort(chr.getPosition().y);
    mplew.write(chr.getStance());
    mplew.writeInt(0);
    mplew.writeInt(1);
    mplew.writeLong(0);
    IPlayerInteractionManager ipim = chr.getInteraction();
    if (ipim != null && ipim.isOwner(chr)) {
      if (
        ipim.getShopType() == 2 ||
        ipim.getShopType() == 3 ||
        ipim.getShopType() == 4
      ) {
        addAnnounceBox(mplew, ipim);
      }
    }
    mplew.write(0); // hmmmmm..
    if (chr.getChalkboard() != null) {
      mplew.write(1);
      mplew.writeMapleAsciiString(chr.getChalkboard());
    } else {
      mplew.write(0);
    }
    MapleInventory iv = chr.getInventory(MapleInventoryType.EQUIPPED);
    Collection<IItem> equippedC = iv.list();
    List<Item> equipped = new ArrayList<Item>(equippedC.size());
    for (IItem item : equippedC) {
      equipped.add((Item) item);
    }
    Collections.sort(equipped);
    List<MapleRing> rings = new ArrayList<MapleRing>();
    for (Item item : equipped) {
      if (((IEquip) item).getRingId() > -1) {
        rings.add(MapleRing.loadFromDb(((IEquip) item).getRingId()));
      }
    }
    Collections.sort(rings);
    if (rings.size() > 0) {
      mplew.write(0);
      for (MapleRing ring : rings) {
        if (ring != null) {
          mplew.write(1);
          mplew.writeInt(ring.getRingId());
          mplew.writeInt(0);
          mplew.writeInt(ring.getPartnerRingId());
          mplew.writeInt(0);
          mplew.writeInt(ring.getItemId());
        }
      }
      mplew.writeShort(0);
    } else {
      mplew.writeInt(0);
    }

    return mplew.getPacket();
  }

  /**
   * Adds a announcement box to an existing MaplePacketLittleEndianWriter.
   *
   * @param mplew The MaplePacketLittleEndianWriter to add an announcement box to.
   * @param shop The shop to announce.
   */
  private static void addAnnounceBox(
    MaplePacketLittleEndianWriter mplew,
    IPlayerInteractionManager interaction
  ) {
    mplew.write(4);
    if (interaction.getShopType() == 2) {
      mplew.writeInt(((MaplePlayerShop) interaction).getObjectId());
    } else {
      mplew.writeInt(((MapleMiniGame) interaction).getObjectId());
    }
    mplew.writeMapleAsciiString(interaction.getDescription()); // desc
    mplew.write(0);
    mplew.write(interaction.getItemType());
    mplew.write(1);
    mplew.write(interaction.getFreeSlot() > -1 ? 4 : 1);

    if (interaction.getShopType() == 2) {
      mplew.write(0);
    } else {
      mplew.write(((MapleMiniGame) interaction).getStarted() ? 1 : 0);
    }
  }

  public static MaplePacket facialExpression(
    MapleCharacter from,
    int expression
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.FACIAL_EXPRESSION.getValue());
    mplew.writeInt(from.getId());
    mplew.writeInt(expression);

    return mplew.getPacket();
  }

  public static MaplePacket getHiredMerchant(
    MapleClient c,
    MapleMiniGame minigame,
    String description
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(
      HexTool.getByteArrayFromHexString("05 05 04 00 00 71 C0 4C 00")
    );
    mplew.writeMapleAsciiString(description);
    mplew.write(0xFF);
    mplew.write(0);
    mplew.write(0);
    mplew.writeMapleAsciiString(c.getPlayer().getName());
    mplew.write(
      HexTool.getByteArrayFromHexString(
        "1F 7E 00 00 00 00 00 00 00 00 03 00 31 32 33 10 00 00 00 00 01 01 00 01 00 7B 00 00 00 02 52 8C 1E 00 00 00 80 05 BB 46 E6 17 02 01 00 00 00 00 00"
      )
    );
    return mplew.getPacket();
  }

  public static MaplePacket movePlayer(
    int cid,
    List<LifeMovementFragment> moves
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MOVE_PLAYER.getValue());
    mplew.writeInt(cid);
    mplew.writeInt(0);
    PacketHelper.serializeMovementList(mplew, moves);

    return mplew.getPacket();
  }

  public static MaplePacket moveSummon(
    int cid,
    int oid,
    Point startPos,
    List<LifeMovementFragment> moves
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MOVE_SUMMON.getValue());
    mplew.writeInt(cid);
    mplew.writeInt(oid);
    mplew.writeShort(startPos.x);
    mplew.writeShort(startPos.y);
    PacketHelper.serializeMovementList(mplew, moves);

    return mplew.getPacket();
  }

  public static MaplePacket moveMonster(
    int useskill,
    int skill,
    int skill_1,
    int skill_2,
    int skill_3,
    int oid,
    Point startPos,
    List<LifeMovementFragment> moves
  ) {
    /*
     * A0 00 C8 00 00 00 00 FF 00 00 00 00 48 02 7D FE 02 00 1C 02 7D FE 9C FF 00 00 2A 00 03 BD 01 00 DC 01 7D FE
     * 9C FF 00 00 2B 00 03 7B 02
     */
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MOVE_MONSTER.getValue());
    // mplew.writeShort(0xA2); // 47 a0
    mplew.writeInt(oid);
    mplew.write(useskill);
    mplew.write(skill);
    mplew.write(skill_1);
    mplew.write(skill_2);
    mplew.write(skill_3);
    mplew.write(0);
    mplew.writeShort(startPos.x);
    mplew.writeShort(startPos.y);
    PacketHelper.serializeMovementList(mplew, moves);

    return mplew.getPacket();
  }

  public static MaplePacket summonAttack(
    int cid,
    int summonSkillId,
    int newStance,
    List<SummonAttackEntry> allDamage
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SUMMON_ATTACK.getValue());
    mplew.writeInt(cid);
    mplew.writeInt(summonSkillId);
    mplew.write(newStance);
    mplew.write(allDamage.size());
    for (SummonAttackEntry attackEntry : allDamage) {
      mplew.writeInt(attackEntry.getMonsterOid()); // oid
      mplew.write(6); // who knows
      mplew.writeInt(attackEntry.getDamage()); // damage
    }

    return mplew.getPacket();
  }

  public static MaplePacket closeRangeAttack(
    int cid,
    int skill,
    int stance,
    int numAttackedAndDamage,
    List<Pair<Integer, List<Integer>>> damage,
    int speed
  ) {
    // 7D 00 #30 75 00 00# 12 00 06 02 0A 00 00 00 00 01 00 00 00 00 97 02 00 00 97 02 00 00
    // 7D 00 #30 75 00 00# 11 00 06 02 0A 00 00 00 00 20 00 00 00 49 06 00 00
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CLOSE_RANGE_ATTACK.getValue());
    // mplew.writeShort(0x7F); // 47 7D
    if (skill == 4211006) { // meso explosion
      addMesoExplosion(
        mplew,
        cid,
        skill,
        stance,
        numAttackedAndDamage,
        0,
        damage,
        speed
      );
    } else {
      addAttackBody(
        mplew,
        cid,
        skill,
        stance,
        numAttackedAndDamage,
        0,
        damage,
        speed
      );
    }

    return mplew.getPacket();
  }

  public static MaplePacket rangedAttack(
    int cid,
    int skill,
    int stance,
    int numAttackedAndDamage,
    int projectile,
    List<Pair<Integer, List<Integer>>> damage,
    int speed
  ) {
    // 7E 00 30 75 00 00 01 00 97 04 0A CB 72 1F 00
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.RANGED_ATTACK.getValue());
    // mplew.writeShort(0x80); // 47 7E
    addAttackBody(
      mplew,
      cid,
      skill,
      stance,
      numAttackedAndDamage,
      projectile,
      damage,
      speed
    );

    return mplew.getPacket();
  }

  public static MaplePacket magicAttack(
    int cid,
    int skill,
    int stance,
    int numAttackedAndDamage,
    List<Pair<Integer, List<Integer>>> damage,
    int charge,
    int speed
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MAGIC_ATTACK.getValue());
    // mplew.writeShort(0x81);
    addAttackBody(
      mplew,
      cid,
      skill,
      stance,
      numAttackedAndDamage,
      0,
      damage,
      speed
    );
    if (charge != -1) {
      mplew.writeInt(charge);
    }

    return mplew.getPacket();
  }

  private static void addAttackBody(
    LittleEndianWriter lew,
    int cid,
    int skill,
    int stance,
    int numAttackedAndDamage,
    int projectile,
    List<Pair<Integer, List<Integer>>> damage,
    int speed
  ) {
    lew.writeInt(cid);
    lew.write(numAttackedAndDamage);
    if (skill > 0) {
      lew.write(0xFF); // too low and some skills don't work (?)

      lew.writeInt(skill);
    } else {
      lew.write(0);
    }
    lew.write(0);
    lew.write(stance);
    lew.write(speed);
    lew.write(0x0A);
    //lew.write(0);
    lew.writeInt(projectile);

    for (Pair<Integer, List<Integer>> oned : damage) {
      if (oned.getRight() != null) {
        lew.writeInt(oned.getLeft().intValue());
        lew.write(0xFF);
        for (Integer eachd : oned.getRight()) {
          // highest bit set = crit
          lew.writeInt(eachd.intValue());
        }
      }
    }
  }

  private static void addMesoExplosion(
    LittleEndianWriter lew,
    int cid,
    int skill,
    int stance,
    int numAttackedAndDamage,
    int projectile,
    List<Pair<Integer, List<Integer>>> damage,
    int speed
  ) {
    // 7A 00 6B F4 0C 00 22 1E 3E 41 40 00 38 04 0A 00 00 00 00 44 B0 04 00
    // 06 02 E6 00 00 00 D0 00 00 00 F2 46 0E 00 06 02 D3 00 00 00 3B 01 00
    // 00
    // 7A 00 6B F4 0C 00 00 1E 3E 41 40 00 38 04 0A 00 00 00 00
    lew.writeInt(cid);
    lew.write(numAttackedAndDamage);
    lew.write(0x1E);
    lew.writeInt(skill);
    lew.write(0);
    lew.write(stance);
    lew.write(speed);
    lew.write(0x0A);
    lew.writeInt(projectile);

    for (Pair<Integer, List<Integer>> oned : damage) {
      if (oned.getRight() != null) {
        lew.writeInt(oned.getLeft().intValue());
        lew.write(0xFF);
        lew.write(oned.getRight().size());
        for (Integer eachd : oned.getRight()) {
          lew.writeInt(eachd.intValue());
        }
      }
    }
  }

  public static MaplePacket getNPCShop(
    MapleClient c,
    int sid,
    List<MapleShopItem> items
  ) {
    MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.OPEN_NPC_SHOP.getValue());
    mplew.writeInt(sid);
    mplew.writeShort(items.size()); // item count
    for (MapleShopItem item : items) {
      mplew.writeInt(item.getItemId());
      mplew.writeInt(item.getPrice());
      if (
        !ii.isThrowingStar(item.getItemId()) && !ii.isBullet(item.getItemId())
      ) {
        mplew.writeShort(1); // stacksize o.o

        mplew.writeShort(item.getBuyable());
      } else {
        mplew.writeShort(0);
        mplew.writeInt(0);
        // o.O getPrice sometimes returns the unitPrice not the price
        mplew.writeShort(
          BitTools.doubleToShortBits(ii.getPrice(item.getItemId()))
        );
        mplew.writeShort(ii.getSlotMax(c, item.getItemId()));
      }
    }

    return mplew.getPacket();
  }

  /**
   * code (8 = sell, 0 = buy, 0x20 = due to an error the trade did not happen
   * o.o)
   *
   * @param code
   * @return
   */
  public static MaplePacket confirmShopTransaction(byte code) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CONFIRM_SHOP_TRANSACTION.getValue());
    // mplew.writeShort(0xE6); // 47 E4
    mplew.write(code); // recharge == 8?

    return mplew.getPacket();
  }

  /*
   * 19 reference 00 01 00 = new while adding 01 01 00 = add from drop 00 01 01 = update count 00 01 03 = clear slot
   * 01 01 02 = move to empty slot 01 02 03 = move and merge 01 02 01 = move and merge with rest
   */
  public static MaplePacket addInventorySlot(
    MapleInventoryType type,
    IItem item
  ) {
    return addInventorySlot(type, item, false);
  }

  public static MaplePacket addInventorySlot(
    MapleInventoryType type,
    IItem item,
    boolean fromDrop
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
    if (fromDrop) {
      mplew.write(1);
    } else {
      mplew.write(0);
    }
    mplew.write(HexTool.getByteArrayFromHexString("01 00")); // add mode
    mplew.write(type.getType()); // iv type
    mplew.write(item.getPosition()); // slot id
    PacketHelper.addItemInfo(mplew, item, true, false);

    return mplew.getPacket();
  }

  public static MaplePacket updateInventorySlot(
    MapleInventoryType type,
    IItem item
  ) {
    return updateInventorySlot(type, item, false);
  }

  public static MaplePacket updateInventorySlot(
    MapleInventoryType type,
    IItem item,
    boolean fromDrop
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
    if (fromDrop) {
      mplew.write(1);
    } else {
      mplew.write(0);
    }
    mplew.write(HexTool.getByteArrayFromHexString("01 01")); // update
    // mode
    mplew.write(type.getType()); // iv type
    mplew.write(item.getPosition()); // slot id
    mplew.write(0); // ?
    mplew.writeShort(item.getQuantity());

    return mplew.getPacket();
  }

  public static MaplePacket moveInventoryItem(
    MapleInventoryType type,
    byte src,
    byte dst
  ) {
    return moveInventoryItem(type, src, dst, (byte) -1);
  }

  public static MaplePacket moveInventoryItem(
    MapleInventoryType type,
    byte src,
    byte dst,
    byte equipIndicator
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
    mplew.write(HexTool.getByteArrayFromHexString("01 01 02"));
    mplew.write(type.getType());
    mplew.writeShort(src);
    mplew.writeShort(dst);
    if (equipIndicator != -1) {
      mplew.write(equipIndicator);
    }

    return mplew.getPacket();
  }

  public static MaplePacket moveAndMergeInventoryItem(
    MapleInventoryType type,
    byte src,
    byte dst,
    short total
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
    mplew.write(HexTool.getByteArrayFromHexString("01 02 03"));
    mplew.write(type.getType());
    mplew.writeShort(src);
    mplew.write(1); // merge mode?
    mplew.write(type.getType());
    mplew.writeShort(dst);
    mplew.writeShort(total);

    return mplew.getPacket();
  }

  public static MaplePacket moveAndMergeWithRestInventoryItem(
    MapleInventoryType type,
    byte src,
    byte dst,
    short srcQ,
    short dstQ
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
    mplew.write(HexTool.getByteArrayFromHexString("01 02 01"));
    mplew.write(type.getType());
    mplew.writeShort(src);
    mplew.writeShort(srcQ);
    mplew.write(HexTool.getByteArrayFromHexString("01"));
    mplew.write(type.getType());
    mplew.writeShort(dst);
    mplew.writeShort(dstQ);

    return mplew.getPacket();
  }

  public static MaplePacket clearInventoryItem(
    MapleInventoryType type,
    byte slot,
    boolean fromDrop
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
    mplew.write(fromDrop ? 1 : 0);
    mplew.write(HexTool.getByteArrayFromHexString("01 03"));
    mplew.write(type.getType());
    mplew.writeShort(slot);

    return mplew.getPacket();
  }

  public static MaplePacket scrolledItem(
    IItem scroll,
    IItem item,
    boolean destroyed
  ) {
    // 18 00 01 02 03 02 08 00 03 01 F7 FF 01
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
    mplew.write(1); // fromdrop always true
    mplew.write(destroyed ? 2 : 3);
    mplew.write(scroll.getQuantity() > 0 ? 1 : 3);
    mplew.write(MapleInventoryType.USE.getType());
    mplew.writeShort(scroll.getPosition());
    if (scroll.getQuantity() > 0) {
      mplew.writeShort(scroll.getQuantity());
    }
    mplew.write(3);
    if (!destroyed) {
      mplew.write(MapleInventoryType.EQUIP.getType());
      mplew.writeShort(item.getPosition());
      mplew.write(0);
    }
    mplew.write(MapleInventoryType.EQUIP.getType());
    mplew.writeShort(item.getPosition());
    if (!destroyed) {
      PacketHelper.addItemInfo(mplew, item, true, true);
    }
    mplew.write(1);

    return mplew.getPacket();
  }

  public static MaplePacket getScrollEffect(
    int chr,
    ScrollResult scrollSuccess,
    boolean legendarySpirit
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_SCROLL_EFFECT.getValue());
    mplew.writeInt(chr);
    switch (scrollSuccess) {
      case SUCCESS:
        mplew.writeShort(1);
        mplew.writeShort(legendarySpirit ? 1 : 0);
        break;
      case FAIL:
        mplew.writeShort(0);
        mplew.writeShort(legendarySpirit ? 1 : 0);
        break;
      case CURSE:
        mplew.write(0);
        mplew.write(1);
        mplew.writeShort(legendarySpirit ? 1 : 0);
        break;
      default:
        throw new IllegalArgumentException("effect in illegal range");
    }

    return mplew.getPacket();
  }

  public static MaplePacket removePlayerFromMap(int cid) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.REMOVE_PLAYER_FROM_MAP.getValue());
    // mplew.writeShort(0x65); // 47 63
    mplew.writeInt(cid);

    return mplew.getPacket();
  }

  /**
   * animation: 0 - expire<br/> 1 - without animation<br/> 2 - pickup<br/>
   * 4 - explode<br/> cid is ignored for 0 and 1
   *
   * @param oid
   * @param animation
   * @param cid
   * @return
   */
  public static MaplePacket removeItemFromMap(int oid, int animation, int cid) {
    return removeItemFromMap(oid, animation, cid, false, 0);
  }

  /**
   * animation: 0 - expire<br/> 1 - without animation<br/> 2 - pickup<br/>
   * 4 - explode<br/> cid is ignored for 0 and 1.<br /><br />Flagging pet
   * as true will make a pet pick up the item.
   *
   * @param oid
   * @param animation
   * @param cid
   * @param pet
   * @param slot
   * @return
   */
  public static MaplePacket removeItemFromMap(
    int oid,
    int animation,
    int cid,
    boolean pet,
    int slot
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.REMOVE_ITEM_FROM_MAP.getValue());
    mplew.write(animation); // expire
    mplew.writeInt(oid);
    if (animation >= 2) {
      mplew.writeInt(cid);
      if (pet) {
        mplew.write(slot);
      }
    }

    return mplew.getPacket();
  }

  public static MaplePacket updateCharLook(MapleCharacter chr) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_LOOK.getValue());
    mplew.writeInt(chr.getId());
    mplew.write(1);
    PacketHelper.addCharLook(mplew, chr, false);
    MapleInventory iv = chr.getInventory(MapleInventoryType.EQUIPPED);
    Collection<IItem> equippedC = iv.list();
    List<Item> equipped = new ArrayList<Item>(equippedC.size());
    for (IItem item : equippedC) {
      equipped.add((Item) item);
    }
    Collections.sort(equipped);
    List<MapleRing> rings = new ArrayList<MapleRing>();
    for (Item item : equipped) {
      if (((IEquip) item).getRingId() > -1) {
        rings.add(MapleRing.loadFromDb(((IEquip) item).getRingId()));
      }
    }
    Collections.sort(rings);
    if (rings.size() > 0) {
      mplew.write(0);
      for (MapleRing ring : rings) {
        mplew.write(1);
        mplew.writeInt(ring.getRingId());
        mplew.writeInt(0);
        mplew.writeInt(ring.getPartnerRingId());
        mplew.writeInt(0);
        mplew.writeInt(ring.getItemId());
      }
      mplew.writeShort(0);
    } else {
      mplew.writeInt(0);
    }
    return mplew.getPacket();
  }

  public static MaplePacket dropInventoryItem(
    MapleInventoryType type,
    short src
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
    // mplew.writeShort(0x19);
    mplew.write(HexTool.getByteArrayFromHexString("01 01 03"));
    mplew.write(type.getType());
    mplew.writeShort(src);
    if (src < 0) {
      mplew.write(1);
    }

    return mplew.getPacket();
  }

  public static MaplePacket dropInventoryItemUpdate(
    MapleInventoryType type,
    IItem item
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
    mplew.write(HexTool.getByteArrayFromHexString("01 01 01"));
    mplew.write(type.getType());
    mplew.writeShort(item.getPosition());
    mplew.writeShort(item.getQuantity());

    return mplew.getPacket();
  }

  public static MaplePacket damagePlayer(
    int skill,
    int monsteridfrom,
    int cid,
    int damage
  ) {
    // 82 00 30 C0 23 00 FF 00 00 00 00 B4 34 03 00 01 00 00 00 00 00 00
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.DAMAGE_PLAYER.getValue());
    // mplew.writeShort(0x84); // 47 82
    mplew.writeInt(cid);
    mplew.write(skill);
    mplew.writeInt(0);
    mplew.writeInt(monsteridfrom);
    mplew.write(1);
    mplew.write(0);
    mplew.write(0); // > 0 = heros will effect

    mplew.writeInt(damage);

    return mplew.getPacket();
  }

  public static MaplePacket damagePlayer(
    int skill,
    int monsteridfrom,
    int cid,
    int damage,
    int fake,
    int direction,
    boolean pgmr,
    int pgmr_1,
    boolean is_pg,
    int oid,
    int pos_x,
    int pos_y
  ) {
    // 82 00 30 C0 23 00 FF 00 00 00 00 B4 34 03 00 01 00 00 00 00 00 00
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.DAMAGE_PLAYER.getValue());
    // mplew.writeShort(0x84); // 47 82
    mplew.writeInt(cid);
    mplew.write(skill);
    mplew.writeInt(damage);
    mplew.writeInt(monsteridfrom);
    mplew.write(direction);
    if (pgmr) {
      mplew.write(pgmr_1);
      mplew.write(is_pg ? 1 : 0);
      mplew.writeInt(oid);
      mplew.write(6);
      mplew.writeShort(pos_x);
      mplew.writeShort(pos_y);
      mplew.write(0);
    } else {
      mplew.writeShort(0);
    }
    mplew.writeInt(damage);
    if (fake > 0) {
      mplew.writeInt(fake);
    }
    return mplew.getPacket();
  }

  public static MaplePacket charNameResponse(
    String charname,
    boolean nameUsed
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.CHAR_NAME_RESPONSE.getValue());
    mplew.writeMapleAsciiString(charname);
    mplew.write(nameUsed ? 1 : 0);
    return mplew.getPacket();
  }

  public static MaplePacket addNewCharEntry(
    MapleCharacter chr,
    boolean worked
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.ADD_NEW_CHAR_ENTRY.getValue());
    mplew.write(worked ? 0 : 1);
    PacketHelper.addCharEntry(mplew, chr);

    return mplew.getPacket();
  }

  public static MaplePacket addNewCharEntry(MapleCharacter chr) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.ADD_NEW_CHAR_ENTRY.getValue());
    mplew.write(0);
    PacketHelper.addCharEntry(mplew, chr);
    return mplew.getPacket();
  }

  /**
   *
   * @param c
   * @param quest
   * @return
   */
  public static MaplePacket startQuest(MapleCharacter c, short quest) {
    // [24 00] [01] [69 08] [01 00] [00]
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    // mplew.writeShort(0x21);
    mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
    mplew.write(1);
    mplew.writeShort(quest);
    mplew.writeShort(1);
    mplew.write(0);

    return mplew.getPacket();
  }

  /**
   * state 0 = del ok state 12 = invalid bday
   *
   * @param cid
   * @param state
   * @return
   */
  public static MaplePacket deleteCharResponse(int cid, boolean state) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.DELETE_CHAR_RESPONSE.getValue());
    mplew.writeInt(cid);
    mplew.write(state ? 0 : 0x12);
    return mplew.getPacket();
  }

  public static MaplePacket triggerMoon(int oid) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.REACTOR_HIT.getValue());
    mplew.writeInt(oid);
    mplew.write(6); //state
    mplew.writeShort(-183);
    mplew.writeShort(-433);
    mplew.writeShort(0);
    mplew.write(-1);
    mplew.write(78);
    return mplew.getPacket();
  }

  public static MaplePacket updateMount(
    int charid,
    MapleMount mount,
    boolean levelup
  ) {
    return updateMount(
      charid,
      mount.getLevel(),
      mount.getExp(),
      mount.getTiredness(),
      levelup
    );
  }

  public static MaplePacket updateMount(
    int charid,
    int newlevel,
    int newexp,
    int tiredness,
    boolean levelup
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.UPDATE_MOUNT.getValue());
    mplew.writeInt(charid);
    mplew.writeInt(newlevel);
    mplew.writeInt(newexp);
    mplew.writeInt(tiredness);
    mplew.write(levelup ? (byte) 1 : (byte) 0);
    return mplew.getPacket();
  }

  public static MaplePacket charInfo(MapleCharacter chr) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CHAR_INFO.getValue());
    mplew.writeInt(chr.getId());
    mplew.write(chr.getLevel());
    mplew.writeShort(chr.getJob().getId());
    mplew.writeShort(chr.getFame());
    mplew.write(chr.isMarried()); // heart red or gray

    String guildName = "";
    String allianceName = "";
    MapleGuildSummary gs = chr
      .getClient()
      .getChannelServer()
      .getGuildSummary(chr.getGuildId());
    if (chr.getGuildId() > 0 && gs != null) {
      guildName = gs.getName();
      try {
        MapleAlliance alliance = chr
          .getClient()
          .getChannelServer()
          .getWorldInterface()
          .getAlliance(gs.getAllianceId());
        if (alliance != null) {
          allianceName = alliance.getName();
        }
      } catch (RemoteException re) {
        re.printStackTrace();
        chr.getClient().getChannelServer().reconnectWorld();
      }
    }

    mplew.writeMapleAsciiString(guildName);
    mplew.writeMapleAsciiString(allianceName);

    mplew.write(0);
    MaplePet[] pets = chr.getPets();
    for (int i = 0; i < 3; i++) {
      if (pets[i] != null) {
        mplew.write(pets[i].getUniqueId());
        mplew.writeInt(pets[i].getItemId()); // petid

        mplew.writeMapleAsciiString(pets[i].getName());
        mplew.write(pets[i].getLevel()); // pet level

        mplew.writeShort(pets[i].getCloseness()); // pet closeness

        mplew.write(pets[i].getFullness()); // pet fullness

        mplew.writeShort(0); // ??

        mplew.writeInt(0);
      } else {
        break;
      }
    }
    mplew.write(0); //end of pets

    if (
      chr.getMount() != null &&
      chr.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -18) != null
    ) {
      if (
        chr
          .getInventory(MapleInventoryType.EQUIPPED)
          .getItem((byte) -18)
          .getItemId() ==
        chr.getMount().getItemId()
      ) {
        if (
          chr.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -19) !=
          null
        ) { // saddle
          mplew.write(chr.getMount().getId()); //mount
          mplew.writeInt(chr.getMount().getLevel()); //level
          mplew.writeInt(chr.getMount().getExp()); //exp
          mplew.writeInt(chr.getMount().getTiredness()); //tiredness
        }
      }
    }

    mplew.write(0); //end of mountpublic static MaplePacket getCharInfo(MapleCharacter chr) {s
    try { // wishlists
      Connection con = DatabaseConnection.getConnection();
      PreparedStatement ps = con.prepareStatement(
        "SELECT * FROM wishlist WHERE characterid = ?"
      );
      ps.setInt(1, chr.getId());
      ResultSet rs = ps.executeQuery();
      int i = 0;
      while (rs.next()) i++;
      mplew.write(i); //size (number of items in the wishlist)
      rs.close();
      ps.close();
    } catch (Exception e) {}
    try {
      Connection con = DatabaseConnection.getConnection();
      PreparedStatement ps = con.prepareStatement(
        "SELECT * FROM wishlist WHERE characterid = ? ORDER BY sn DESC"
      );
      ps.setInt(1, chr.getId());
      ResultSet rs = ps.executeQuery();
      while (rs.next()) mplew.writeInt(rs.getInt("sn"));
      rs.close();
      ps.close();
    } catch (Exception e) {}
    mplew.writeLong(1);
    mplew.writeLong(0);
    mplew.writeInt(0);
    return mplew.getPacket();
  }

  /**
   *
   * @param c
   * @param quest
   * @return
   */
  public static MaplePacket forfeitQuest(MapleCharacter c, short quest) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
    mplew.write(1);
    mplew.writeShort(quest);
    mplew.writeShort(0);
    mplew.write(0);
    mplew.writeInt(0);
    mplew.writeInt(0);

    return mplew.getPacket();
  }

  /**
   *
   * @param c
   * @param quest
   * @return
   */
  public static MaplePacket completeQuest(MapleCharacter c, short quest) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
    mplew.write(1);
    mplew.writeShort(quest);
    mplew.write(2);
    mplew.writeLong(PacketHelper.getTime(System.currentTimeMillis()));

    return mplew.getPacket();
  }

  /**
   *
   * @param c
   * @param quest
   * @param npc
   * @param progress
   * @return
   */
  // frz note, 0.52 transition: this is only used when starting a quest and
  // seems to have no effect, is it needed?
  public static MaplePacket updateQuestInfo(
    MapleCharacter c,
    short quest,
    int npc,
    byte progress
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.UPDATE_QUEST_INFO.getValue());
    mplew.write(progress);
    mplew.writeShort(quest);
    mplew.writeInt(npc);
    mplew.writeInt(0);
    return mplew.getPacket();
  }

  private static <E extends LongValueHolder> long getLongMask(
    List<Pair<E, Integer>> statups
  ) {
    long mask = 0;
    for (Pair<E, Integer> statup : statups) {
      mask |= statup.getLeft().getValue();
    }
    return mask;
  }

  private static <E extends LongValueHolder> long getLongMaskFromList(
    List<E> statups
  ) {
    long mask = 0;
    for (E statup : statups) {
      mask |= statup.getValue();
    }
    return mask;
  }

  public static MaplePacket giveBuffTest(
    int buffid,
    int bufflength,
    long mask
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
    mplew.writeLong(0);
    mplew.writeLong(mask);
    mplew.writeShort(1);
    mplew.writeInt(buffid);
    mplew.writeInt(bufflength);
    mplew.writeShort(0); // ??? wk charges have 600 here o.o
    mplew.write(0); // combo 600, too
    mplew.write(0); // new in v0.56
    mplew.write(0);

    return mplew.getPacket();
  }

  /**
   * It is important that statups is in the correct order (see decleration
   * order in MapleBuffStat) since this method doesn't do automagical
   * reordering.
   *
   * @param buffid
   * @param bufflength
   * @param statups
   * @param morph
   * @return
   */
  public static MaplePacket giveBuff(
    int buffid,
    int bufflength,
    List<Pair<MapleBuffStat, Integer>> statups
  ) {
    // darksight
    // 1C 00 80 04 00 00 00 00 00 00 F4 FF EB 0C 3D 00 C8 00 01 00 EB 0C 3D
    // 00 C8 00 00 00 01
    // fire charge
    // 1C 00 04 00 40 00 00 00 00 00 26 00 7B 7A 12 00 90 01 01 00 7B 7A 12
    // 00 90 01 58 02
    // ice charge
    // 1C 00 04 00 40 00 00 00 00 00 07 00 7D 7A 12 00 26 00 01 00 7D 7A 12
    // 00 26 00 58 02
    // thunder charge
    // 1C 00 04 00 40 00 00 00 00 00 0B 00 7F 7A 12 00 18 00 01 00 7F 7A 12
    // 00 18 00 58 02

    // incincible 0.49
    // 1B 00 00 80 00 00 00 00 00 00 0F 00 4B 1C 23 00 F8 24 01 00 00 00
    // mguard 0.49
    // 1B 00 00 02 00 00 00 00 00 00 50 00 6A 88 1E 00 C0 27 09 00 00 00
    // bless 0.49

    // 1B 00 3A 00 00 00 00 00 00 00 14 00 4C 1C 23 00 3F 0D 03 00 14 00 4C
    // 1C 23 00 3F 0D 03 00 14 00 4C 1C 23 00 3F 0D 03 00 14 00 4C 1C 23 00
    // 3F 0D 03 00 00 00

    // combo
    // 1B 00 00 00 20 00 00 00 00 00 01 00 DA F3 10 00 C0 D4 01 00 58 02
    // 1B 00 00 00 20 00 00 00 00 00 02 00 DA F3 10 00 57 B7 01 00 00 00
    // 1B 00 00 00 20 00 00 00 00 00 03 00 DA F3 10 00 51 A7 01 00 00 00

    // 01 00
    // 79 00 - monster skill
    // 01 00
    // B4 78 00 00
    // 00 00
    // 84 03

    /*
		 1D 00
		 * 
		 00 00 00 00 00 00 00 00
		 * 
		 00 00 00 40 00 00 00 00
		 * 
		 00 00
		 B0 05 1D 00
		 EC 03 00 00
		 * 
		 B9 8D 25 2F
		 * 
		 00 00 02
		 */

    // [1D 00] [00 00 00 00 00 00 00 00] [00 00 00 00 00 00 08 00] [01 00] [78 00 0C 00] [38 7C 00 00] [00 00] [08] [07]
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
    long mask = getLongMask(statups);
    mplew.writeLong(0);
    mplew.writeLong(mask);
    for (Pair<MapleBuffStat, Integer> statup : statups) {
      mplew.writeShort(statup.getRight().shortValue());
      mplew.writeInt(buffid);
      mplew.writeInt(bufflength);
    }
    if (buffid == 1004 || buffid == 5221006) {
      mplew.writeInt(0x2F258DB9);
    } else {
      mplew.writeShort(0); // ??? wk charges have 600 here o.o
    }
    mplew.write(0); // combo 600, too
    mplew.write(0); // new in v0.56
    mplew.write(0);

    return mplew.getPacket();
  }

  public static MaplePacket giveBuff(
    int buffid,
    int bufflength,
    List<Pair<MapleBuffStat, Integer>> statups,
    boolean morph,
    boolean ismount,
    MapleMount mount
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
    long mask = getLongMask(statups);
    mplew.writeLong(0);
    mplew.writeLong(mask);
    if (!ismount) {
      for (Pair<MapleBuffStat, Integer> statup : statups) {
        mplew.writeShort(statup.getRight().shortValue());
        mplew.writeInt(buffid);
        mplew.writeInt(bufflength);
      }
      mplew.writeShort(0);
      mplew.write(0);
      mplew.write(0);
      mplew.write(0);
    } else {
      if (ismount) {
        mplew.writeShort(0);
        mplew.writeInt(mount.getItemId());
        mplew.writeInt(mount.getSkillId());
        mplew.writeInt(0);
        mplew.writeShort(0);
        mplew.write(0);
      } else {
        log.error("Something went wrong with making the mount packet.");
        return null;
      }
    }
    return mplew.getPacket();
  }

  public static MaplePacket giveForeignBuff(
    int cid,
    List<Pair<MapleBuffStat, Integer>> statups,
    boolean morph
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
    mplew.writeInt(cid);
    long mask = getLongMask(statups);
    mplew.writeLong(0);
    mplew.writeLong(mask);
    //mplew.writeShort(0);
    for (Pair<MapleBuffStat, Integer> statup : statups) {
      if (morph) {
        mplew.write(statup.getRight().byteValue());
      } else {
        mplew.writeShort(statup.getRight().shortValue());
      }
    }
    mplew.writeShort(0);
    if (morph) {
      mplew.writeShort(0);
    }
    mplew.write(0);
    return mplew.getPacket();
  }

  public static MaplePacket giveDebuff(
    long mask,
    List<Pair<MapleDisease, Integer>> statups,
    MobSkill skill
  ) {
    // [1D 00] [00 00 00 00 00 00 00 00] [00 00 02 00 00 00 00 00] [00 00] [7B 00] [04 00] [B8 0B 00 00] [00 00] [84 03] [01]
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
    mplew.writeLong(0);
    mplew.writeLong(mask);
    for (Pair<MapleDisease, Integer> statup : statups) {
      mplew.writeShort(statup.getRight().shortValue());
      mplew.writeShort(skill.getSkillId());
      mplew.writeShort(skill.getSkillLevel());
      mplew.writeInt((int) skill.getDuration());
    }
    mplew.writeShort(0); // ??? wk charges have 600 here o.o
    mplew.writeShort(900); //Delay
    mplew.write(1);

    return mplew.getPacket();
  }

  public static MaplePacket giveForeignDebuff(
    int cid,
    long mask,
    MobSkill skill
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
    mplew.writeInt(cid);
    mplew.writeLong(0);
    mplew.writeLong(mask);
    mplew.writeShort(skill.getSkillId());
    mplew.writeShort(skill.getSkillLevel());
    mplew.writeShort(0);
    mplew.writeShort(900);
    return mplew.getPacket();
  }

  public static MaplePacket cancelForeignDebuff(int cid, long mask) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.CANCEL_FOREIGN_BUFF.getValue());
    mplew.writeInt(cid);
    mplew.writeLong(0);
    mplew.writeLong(mask);

    return mplew.getPacket();
  }

  public static MaplePacket cancelForeignDebuff(
    int cid,
    List<MapleDisease> statups
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.CANCEL_FOREIGN_BUFF.getValue());
    mplew.writeInt(cid);
    long mask = getLongMaskFromListD(statups);
    mplew.writeLong(0);
    mplew.writeLong(mask);
    return mplew.getPacket();
  }

  public static MaplePacket showMonsterRiding(
    int cid,
    List<Pair<MapleBuffStat, Integer>> statups,
    MapleMount mount
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
    mplew.writeInt(cid);
    long mask = getLongMask(statups);
    mplew.writeLong(0);
    mplew.writeLong(mask);
    mplew.writeShort(0);
    mplew.writeInt(mount.getItemId());
    mplew.writeInt(mount.getSkillId());
    mplew.writeInt(0x2D4DFC2A);
    mplew.writeShort(0); // same as give_buff
    return mplew.getPacket();
  }

  public static MaplePacket giveForeignBuff(
    int cid,
    List<Pair<MapleBuffStat, Integer>> statups,
    MapleStatEffect effect
  ) {
    // [99 00] [F1 26 1E 00] [00 00 00 00 00 00 00 00] [00 00 00 00 80 00 00 00] [28 00] [00 00] [00]
    // [99 00] [97 E6 53 00] [00 00 00 00 00 00 00 00] [00 00 00 00 80 04 00 00] [E6 00] [00 00] [00]
    // [99 00] [D7 5B 06 00] [00 00 00 00 00 00 00 00] [02 00 00 00 00 00 00 00] [07] [00 00] [00 00] [00]
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
    mplew.writeInt(cid);
    long mask = getLongMask(statups);
    mplew.writeLong(0);
    mplew.writeLong(mask);
    //mplew.writeShort(0);
    for (Pair<MapleBuffStat, Integer> statup : statups) {
      if (effect.isMorph() && !effect.isPirateMorph()) {
        mplew.write(statup.getRight().byteValue());
      } else {
        mplew.writeShort(statup.getRight().shortValue());
      }
    }
    mplew.writeShort(0); // same as give_buff
    if (effect.isMorph()) {
      mplew.writeShort(0);
    }
    mplew.write(0);

    return mplew.getPacket();
  }

  public static MaplePacket giveDash(
    List<Pair<MapleBuffStat, Integer>> statups,
    int x,
    int y,
    int duration
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
    mplew.writeLong(0);
    long mask = getLongMask(statups);
    mplew.writeLong(mask);
    mplew.writeShort(0);
    mplew.writeInt(x);
    mplew.writeInt(5001005);
    mplew.write(HexTool.getByteArrayFromHexString("1A 7C 8D 35"));
    mplew.writeShort(duration);
    mplew.writeInt(y);
    mplew.writeInt(5001005);
    mplew.write(HexTool.getByteArrayFromHexString("1A 7C 8D 35"));
    mplew.writeShort(duration);
    mplew.writeShort(0);
    mplew.write(2);

    return mplew.getPacket();
  }

  public static MaplePacket showDashEffecttoOthers(
    int cid,
    int x,
    int y,
    int duration
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
    mplew.writeInt(cid);
    mplew.writeLong(0);
    mplew.write(HexTool.getByteArrayFromHexString("00 00 00 30 00 00 00 00"));
    mplew.writeShort(0);
    mplew.writeInt(x);
    mplew.writeInt(5001005);
    mplew.write(HexTool.getByteArrayFromHexString("1A 7C 8D 35"));
    mplew.writeShort(duration);
    mplew.writeInt(y);
    mplew.writeInt(5001005);
    mplew.write(HexTool.getByteArrayFromHexString("1A 7C 8D 35"));
    mplew.writeShort(duration);
    mplew.writeShort(0);

    return mplew.getPacket();
  }

  public static MaplePacket cancelForeignBuff(
    int cid,
    List<MapleBuffStat> statups
  ) {
    // 8A 00 24 46 32 00 80 04 00 00 00 00 00 00 F4 00 00
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CANCEL_FOREIGN_BUFF.getValue());
    mplew.writeInt(cid);
    long mask = getLongMaskFromList(statups);
    mplew.writeLong(0);
    mplew.writeLong(mask);

    return mplew.getPacket();
  }

  public static MaplePacket cancelBuff(List<MapleBuffStat> statups) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CANCEL_BUFF.getValue());
    long mask = getLongMaskFromList(statups);
    mplew.writeLong(0);
    mplew.writeLong(mask);
    mplew.write(3); // wtf?

    return mplew.getPacket();
  }

  public static MaplePacket cancelDebuff(long mask) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CANCEL_BUFF.getValue());
    mplew.writeLong(0);
    mplew.writeLong(mask);
    mplew.write(0);

    return mplew.getPacket();
  }

  private static <E extends LongValueHolder> long getLongMaskFromListD(
    List<MapleDisease> statups
  ) {
    long mask = 0;
    for (MapleDisease statup : statups) mask |= statup.getValue();
    return mask;
  }

  public static MaplePacket cancelDebuff(List<MapleDisease> statups) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.CANCEL_BUFF.getValue());
    long mask = getLongMaskFromListD(statups);
    mplew.writeLong(0);
    mplew.writeLong(mask);
    mplew.write(0);
    return mplew.getPacket();
  }

  public static MaplePacket getPlayerShopChat(
    MapleCharacter c,
    String chat,
    boolean owner
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(HexTool.getByteArrayFromHexString("06 08"));
    mplew.write(owner ? 0 : 1);
    mplew.writeMapleAsciiString(c.getName() + " : " + chat);

    return mplew.getPacket();
  }

  public static MaplePacket getPlayerShopNewVisitor(
    MapleCharacter c,
    int slot
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(HexTool.getByteArrayFromHexString("04 0" + slot));
    PacketHelper.addCharLook(mplew, c, false);
    mplew.writeMapleAsciiString(c.getName());
    log.info("player shop send packet: \n" + mplew.toString());
    return mplew.getPacket();
  }

  public static MaplePacket getPlayerShopRemoveVisitor(int slot) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(HexTool.getByteArrayFromHexString("0A 0" + slot));
    log.info("player shop send packet: \n" + mplew.toString());
    return mplew.getPacket();
  }

  public static MaplePacket getTradePartnerAdd(MapleCharacter c) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(HexTool.getByteArrayFromHexString("04 01")); // 00 04 88 4E 00"));
    PacketHelper.addCharLook(mplew, c, false);
    mplew.writeMapleAsciiString(c.getName());

    return mplew.getPacket();
  }

  public static MaplePacket getTradeInvite(MapleCharacter c) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(HexTool.getByteArrayFromHexString("02 03"));
    mplew.writeMapleAsciiString(c.getName());
    mplew.write(HexTool.getByteArrayFromHexString("B7 50 00 00"));

    return mplew.getPacket();
  }

  public static MaplePacket getTradeMesoSet(byte number, int meso) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(0xF);
    mplew.write(number);
    mplew.writeInt(meso);

    return mplew.getPacket();
  }

  public static MaplePacket getTradeItemAdd(byte number, IItem item) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(0xE);
    mplew.write(number);
    // mplew.write(1);
    PacketHelper.addItemInfo(mplew, item);

    return mplew.getPacket();
  }

  public static MaplePacket getTradeStart(
    MapleClient c,
    MapleTrade trade,
    byte number
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(HexTool.getByteArrayFromHexString("05 03 02"));
    mplew.write(number);
    if (number == 1) {
      mplew.write(0);
      PacketHelper.addCharLook(mplew, trade.getPartner().getChr(), false);
      mplew.writeMapleAsciiString(trade.getPartner().getChr().getName());
    }
    mplew.write(number);
    /*if (number == 1) {
		mplew.write(0);
		mplew.writeInt(c.getPlayer().getId());
		}*/
    PacketHelper.addCharLook(mplew, c.getPlayer(), false);
    mplew.writeMapleAsciiString(c.getPlayer().getName());
    mplew.write(0xFF);

    return mplew.getPacket();
  }

  public static MaplePacket getTradeConfirmation() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(0x10);

    return mplew.getPacket();
  }

  public static MaplePacket getTradeCompletion(byte number) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(0xA);
    mplew.write(number);
    mplew.write(6);

    return mplew.getPacket();
  }

  public static MaplePacket getTradeCancel(byte number) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(0xA);
    mplew.write(number);
    mplew.write(2);

    return mplew.getPacket();
  }

  public static MaplePacket removeCharBox(MapleCharacter c) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_BOX.getValue());
    mplew.writeInt(c.getId());
    mplew.write(0);
    return mplew.getPacket();
  }

  public static MaplePacket getNPCTalk(
    int npc,
    byte msgType,
    String talk,
    String endBytes
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
    mplew.write(4); // ?
    mplew.writeInt(npc);
    mplew.write(msgType);
    mplew.writeMapleAsciiString(talk);
    mplew.write(HexTool.getByteArrayFromHexString(endBytes));

    return mplew.getPacket();
  }

  public static MaplePacket getNPCTalkStyle(
    int npc,
    String talk,
    int styles[]
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
    mplew.write(4); // ?
    mplew.writeInt(npc);
    mplew.write(7);
    mplew.writeMapleAsciiString(talk);
    mplew.write(styles.length);
    for (int i = 0; i < styles.length; i++) {
      mplew.writeInt(styles[i]);
    }

    return mplew.getPacket();
  }

  public static MaplePacket getNPCTalkNum(
    int npc,
    String talk,
    int def,
    int min,
    int max
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
    mplew.write(4); // ?
    mplew.writeInt(npc);
    mplew.write(3);
    mplew.writeMapleAsciiString(talk);
    mplew.writeInt(def);
    mplew.writeInt(min);
    mplew.writeInt(max);
    mplew.writeInt(0);

    return mplew.getPacket();
  }

  public static MaplePacket getNPCTalkText(int npc, String talk) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
    mplew.write(4); // ?
    mplew.writeInt(npc);
    mplew.write(2);
    mplew.writeMapleAsciiString(talk);
    mplew.writeInt(0);
    mplew.writeInt(0);

    return mplew.getPacket();
  }

  public static MaplePacket showLevelup(int cid) {
    return showForeignEffect(cid, 0);
  }

  public static MaplePacket showJobChange(int cid) {
    return showForeignEffect(cid, 8);
  }

  public static MaplePacket showForeignEffect(int effect) {
    return showForeignEffect(-1, effect);
  }

  public static MaplePacket showForeignEffect(int cid, int effect) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    if (cid != -1) {
      mplew.writeShort(SendPacketOpcode.SHOW_SPECIAL_EFFECT.getValue());
    } else {
      mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
      mplew.writeInt(cid); // ?
    }
    mplew.write(effect);

    return mplew.getPacket();
  }

  public static MaplePacket showBuffeffect(
    int cid,
    int skillid,
    int effectid,
    byte direction
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
    mplew.writeInt(cid); // ?
    mplew.write(effectid);
    mplew.writeInt(skillid);
    mplew.write(1); // probably buff level but we don't know it and it doesn't really matter
    if (direction != (byte) 3) {
      mplew.write(direction);
    }

    return mplew.getPacket();
  }

  public static MaplePacket showOwnBuffEffect(int skillid, int effectid) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
    mplew.write(effectid);
    mplew.writeInt(skillid);
    mplew.write(1); // probably buff level but we don't know it and it doesn't really matter

    return mplew.getPacket();
  }

  public static MaplePacket showOwnBerserk(int skilllevel, boolean Berserk) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
    mplew.write(1);
    mplew.writeInt(1320006);
    mplew.write(skilllevel);
    mplew.write(Berserk ? 1 : 0);

    return mplew.getPacket();
  }

  public static MaplePacket showBerserk(
    int cid,
    int skilllevel,
    boolean Berserk
  ) {
    // [99 00] [5D 94 27 00] [01] [46 24 14 00] [14] [01]
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
    mplew.writeInt(cid);
    mplew.write(1);
    mplew.writeInt(1320006);
    mplew.write(skilllevel);
    mplew.write(Berserk ? 1 : 0);

    return mplew.getPacket();
  }

  public static MaplePacket updateSkill(
    int skillid,
    int level,
    int masterlevel
  ) {
    // 1E 00 01 01 00 E9 03 00 00 01 00 00 00 00 00 00 00 01
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.UPDATE_SKILLS.getValue());
    mplew.write(1);
    mplew.writeShort(1);
    mplew.writeInt(skillid);
    mplew.writeInt(level);
    mplew.writeInt(masterlevel);
    mplew.write(1);

    return mplew.getPacket();
  }

  public static MaplePacket sendSilverBoxOpened(int itemid) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.SILVER_BOX_OPEN.getValue());
    mplew.writeInt(itemid);
    return mplew.getPacket();
  }

  public static MaplePacket updateQuestMobKills(MapleQuestStatus status) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
    mplew.write(1);
    mplew.writeShort(status.getQuest().getId());
    mplew.write(1);
    String killStr = "";
    for (int kills : status.getMobKills().values()) {
      killStr += StringUtil.getLeftPaddedStr(String.valueOf(kills), '0', 3); // possibly wrong
    }
    mplew.writeMapleAsciiString(killStr);
    /*    mplew.writeInt(0);
        mplew.writeInt(0);*/
    return mplew.getPacket();
  }

  public static MaplePacket getShowQuestCompletion(int id) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_QUEST_COMPLETION.getValue());
    mplew.writeShort(id);

    return mplew.getPacket();
  }

  public static MaplePacket getKeymap(
    Map<Integer, MapleKeyBinding> keybindings
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.KEYMAP.getValue());
    mplew.write(0);

    for (int x = 0; x < 90; x++) {
      MapleKeyBinding binding = keybindings.get(Integer.valueOf(x));
      if (binding != null) {
        mplew.write(binding.getType());
        mplew.writeInt(binding.getAction());
      } else {
        mplew.write(0);
        mplew.writeInt(0);
      }
    }

    return mplew.getPacket();
  }

  public static MaplePacket getWhisper(
    String sender,
    int channel,
    String text
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
    mplew.write(0x12);
    mplew.writeMapleAsciiString(sender);
    mplew.writeShort(channel - 1); // I guess this is the channel
    mplew.writeMapleAsciiString(text);

    return mplew.getPacket();
  }

  /**
   *
   * @param target name of the target character
   * @param reply error code: 0x0 = cannot find char, 0x1 = success
   * @return the MaplePacket
   */
  public static MaplePacket getWhisperReply(String target, byte reply) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
    mplew.write(0x0A); // whisper?
    mplew.writeMapleAsciiString(target);
    mplew.write(reply);

    return mplew.getPacket();
  }

  public static MaplePacket getFindReplyWithMap(String target, int mapid) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
    mplew.write(9);
    mplew.writeMapleAsciiString(target);
    mplew.write(1);
    mplew.writeInt(mapid);
    mplew.write(new byte[8]); // ?? official doesn't send zeros here but whatever

    return mplew.getPacket();
  }

  public static MaplePacket getFindReply(String target, int channel) {
    // Received UNKNOWN (1205941596.79689): (25)
    // 54 00 09 07 00 64 61 76 74 73 61 69 01 86 7F 3D 36 D5 02 00 00 22 00
    // 00 00
    // T....davtsai..=6...."...
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
    mplew.write(9);
    mplew.writeMapleAsciiString(target);
    mplew.write(3);
    mplew.writeInt(channel - 1);

    return mplew.getPacket();
  }

  public static MaplePacket getInventoryFull() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
    mplew.write(1);
    mplew.write(0);

    return mplew.getPacket();
  }

  public static MaplePacket getShowInventoryFull() {
    return getShowInventoryStatus(0xff);
  }

  public static MaplePacket showItemUnavailable() {
    return getShowInventoryStatus(0xfe);
  }

  public static MaplePacket getShowInventoryStatus(int mode) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
    mplew.write(0);
    mplew.write(mode);
    mplew.writeInt(0);
    mplew.writeInt(0);

    return mplew.getPacket();
  }

  public static MaplePacket getStorage(
    int npcId,
    byte slots,
    Collection<IItem> items,
    int meso
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
    mplew.write(0x15);
    mplew.writeInt(npcId);
    mplew.write(slots);
    mplew.writeShort(0x7E);
    mplew.writeShort(0);
    mplew.writeInt(0);
    mplew.writeInt(meso);
    mplew.writeShort(0);
    mplew.write((byte) items.size());
    for (IItem item : items) {
      PacketHelper.addItemInfo(mplew, item, true, true);
    }
    mplew.writeShort(0);
    mplew.write(0);

    return mplew.getPacket();
  }

  public static MaplePacket getStorageFull() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
    mplew.write(0x10);

    return mplew.getPacket();
  }

  public static MaplePacket mesoStorage(byte slots, int meso) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
    mplew.write(0x12);
    mplew.write(slots);
    mplew.writeShort(2);
    mplew.writeShort(0);
    mplew.writeInt(0);
    mplew.writeInt(meso);

    return mplew.getPacket();
  }

  public static MaplePacket storeStorage(
    byte slots,
    MapleInventoryType type,
    Collection<IItem> items
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
    mplew.write(0xC);
    mplew.write(slots);
    mplew.writeShort(type.getBitfieldEncoding());
    mplew.writeShort(0);
    mplew.writeInt(0);
    mplew.write(items.size());
    for (IItem item : items) {
      PacketHelper.addItemInfo(mplew, item, true, true);
      // mplew.write(0);
    }

    return mplew.getPacket();
  }

  public static MaplePacket takeOutStorage(
    byte slots,
    MapleInventoryType type,
    Collection<IItem> items
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
    mplew.write(0x9);
    mplew.write(slots);
    mplew.writeShort(type.getBitfieldEncoding());
    mplew.writeShort(0);
    mplew.writeInt(0);
    mplew.write(items.size());
    for (IItem item : items) {
      PacketHelper.addItemInfo(mplew, item, true, true);
      // mplew.write(0);
    }

    return mplew.getPacket();
  }

  /**
   *
   * @param oid
   * @param remhp in %
   * @return
   */
  public static MaplePacket showMonsterHP(int oid, int remhppercentage) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_MONSTER_HP.getValue());
    mplew.writeInt(oid);
    mplew.write(remhppercentage);

    return mplew.getPacket();
  }

  public static MaplePacket showBossHP(
    int oid,
    int currHP,
    int maxHP,
    byte tagColor,
    byte tagBgColor
  ) {
    //53 00 05 21 B3 81 00 46 F2 5E 01 C0 F3 5E 01 04 01
    //00 81 B3 21 = 8500001 = Pap monster ID
    //01 5E F3 C0 = 23,000,000 = Pap max HP
    //04, 01 - boss bar color/background color as provided in WZ
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.BOSS_ENV.getValue());
    mplew.write(5);
    mplew.writeInt(oid);
    mplew.writeInt(currHP);
    mplew.writeInt(maxHP);
    mplew.write(tagColor);
    mplew.write(tagBgColor);

    return mplew.getPacket();
  }

  public static MaplePacket giveFameResponse(
    int mode,
    String charname,
    int newfame
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());
    mplew.write(0);
    mplew.writeMapleAsciiString(charname);
    mplew.write(mode);
    mplew.writeShort(newfame);
    mplew.writeShort(0);

    return mplew.getPacket();
  }

  /**
   * status can be: <br>
   * 0: ok, use giveFameResponse<br>
   * 1: the username is incorrectly entered<br>
   * 2: users under level 15 are unable to toggle with fame.<br>
   * 3: can't raise or drop fame anymore today.<br>
   * 4: can't raise or drop fame for this character for this month anymore.<br>
   * 5: received fame, use receiveFame()<br>
   * 6: level of fame neither has been raised nor dropped due to an unexpected
   * error
   *
   * @param status
   * @return
   */
  public static MaplePacket giveFameErrorResponse(int status) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());
    mplew.write(status);

    return mplew.getPacket();
  }

  public static MaplePacket receiveFame(int mode, String charnameFrom) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());
    mplew.write(5);
    mplew.writeMapleAsciiString(charnameFrom);
    mplew.write(mode);

    return mplew.getPacket();
  }

  public static MaplePacket partyCreated() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
    mplew.write(8);
    mplew.writeShort(0x8b);
    mplew.writeShort(2);
    mplew.write(CHAR_INFO_MAGIC);
    mplew.write(CHAR_INFO_MAGIC);
    mplew.writeInt(0);
    return mplew.getPacket();
  }

  public static MaplePacket partyInvite(MapleCharacter from) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
    mplew.write(4);
    mplew.writeInt(from.getParty().getId());
    mplew.writeMapleAsciiString(from.getName());
    mplew.write(0);

    return mplew.getPacket();
  }

  /**
   * 10: A beginner can't create a party.
   * 1/11/14/19: Your request for a party didn't work due to an unexpected error.
   * 13: You have yet to join a party.
   * 16: Already have joined a party.
   * 17: The party you're trying to join is already in full capacity.
   * 19: Unable to find the requested character in this channel.
   *
   * @param message
   * @return
   */
  public static MaplePacket partyStatusMessage(int message) {
    // 32 00 08 DA 14 00 00 FF C9 9A 3B FF C9 9A 3B 22 03 6E 67
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
    mplew.write(message);

    return mplew.getPacket();
  }

  /**
   * 23: 'Char' have denied request to the party.
   *
   * @param message
   * @param charname
   * @return
   */
  public static MaplePacket partyStatusMessage(int message, String charname) {
    // 32 00 08 DA 14 00 00 FF C9 9A 3B FF C9 9A 3B 22 03 6E 67
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
    mplew.write(message);
    mplew.writeMapleAsciiString(charname);

    return mplew.getPacket();
  }

  private static void addPartyStatus(
    int forchannel,
    MapleParty party,
    LittleEndianWriter lew,
    boolean leaving
  ) {
    List<MaplePartyCharacter> partymembers = new ArrayList<MaplePartyCharacter>(
      party.getMembers()
    );
    while (partymembers.size() < 6) {
      partymembers.add(new MaplePartyCharacter());
    }
    for (MaplePartyCharacter partychar : partymembers) {
      lew.writeInt(partychar.getId());
    }
    for (MaplePartyCharacter partychar : partymembers) {
      lew.writeAsciiString(
        StringUtil.getRightPaddedStr(partychar.getName(), '\0', 13)
      );
    }
    for (MaplePartyCharacter partychar : partymembers) {
      lew.writeInt(partychar.getJobId());
    }
    for (MaplePartyCharacter partychar : partymembers) {
      lew.writeInt(partychar.getLevel());
    }
    for (MaplePartyCharacter partychar : partymembers) {
      if (partychar.isOnline()) {
        lew.writeInt(partychar.getChannel() - 1);
      } else {
        lew.writeInt(-2);
      }
    }
    lew.writeInt(party.getLeader().getId());
    for (MaplePartyCharacter partychar : partymembers) {
      if (partychar.getChannel() == forchannel) {
        lew.writeInt(partychar.getMapid());
      } else {
        lew.writeInt(0);
      }
    }
    for (MaplePartyCharacter partychar : partymembers) {
      if (partychar.getChannel() == forchannel && !leaving) {
        lew.writeInt(partychar.getDoorTown());
        lew.writeInt(partychar.getDoorTarget());
        lew.writeInt(partychar.getDoorPosition().x);
        lew.writeInt(partychar.getDoorPosition().y);
      } else {
        lew.writeInt(0);
        lew.writeInt(0);
        lew.writeInt(0);
        lew.writeInt(0);
      }
    }
  }

  public static MaplePacket updateParty(
    int forChannel,
    MapleParty party,
    PartyOperation op,
    MaplePartyCharacter target
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
    switch (op) {
      case DISBAND:
      case EXPEL:
      case LEAVE:
        mplew.write(0xC);
        mplew.writeInt(40546);
        mplew.writeInt(target.getId());

        if (op == PartyOperation.DISBAND) {
          mplew.write(0);
          mplew.writeInt(party.getId());
        } else {
          mplew.write(1);
          if (op == PartyOperation.EXPEL) {
            mplew.write(1);
          } else {
            mplew.write(0);
          }
          mplew.writeMapleAsciiString(target.getName());
          addPartyStatus(forChannel, party, mplew, false);
          // addLeavePartyTail(mplew);
        }

        break;
      case JOIN:
        mplew.write(0xF);
        mplew.writeInt(40546);
        mplew.writeMapleAsciiString(target.getName());
        addPartyStatus(forChannel, party, mplew, false);
        // addJoinPartyTail(mplew);
        break;
      case SILENT_UPDATE:
      case LOG_ONOFF:
        mplew.write(0x7);
        mplew.writeInt(party.getId());
        addPartyStatus(forChannel, party, mplew, false);
        break;
      case CHANGE_LEADER:
        mplew.write(0x1A);
        mplew.writeInt(target.getId());
        mplew.write(1);
        break;
    }

    return mplew.getPacket();
  }

  public static MaplePacket partyPortal(
    int townId,
    int targetId,
    Point position
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
    mplew.writeShort(0x22);
    mplew.writeInt(townId);
    mplew.writeInt(targetId);
    mplew.writeShort(position.x);
    mplew.writeShort(position.y);

    return mplew.getPacket();
  }

  public static MaplePacket updatePartyMemberHP(int cid, int curhp, int maxhp) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.UPDATE_PARTYMEMBER_HP.getValue());
    mplew.writeInt(cid);
    mplew.writeInt(curhp);
    mplew.writeInt(maxhp);

    return mplew.getPacket();
  }

  /**
   * mode: 0 buddychat; 1 partychat; 2 guildchat
   *
   * @param name
   * @param chattext
   * @param mode
   * @return
   */
  public static MaplePacket multiChat(String name, String chattext, int mode) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MULTICHAT.getValue());
    mplew.write(mode);
    mplew.writeMapleAsciiString(name);
    mplew.writeMapleAsciiString(chattext);

    return mplew.getPacket();
  }

  public static MaplePacket applyMonsterStatus(
    int oid,
    Map<MonsterStatus, Integer> stats,
    int skill,
    boolean monsterSkill,
    int delay
  ) {
    return applyMonsterStatus(oid, stats, skill, monsterSkill, delay, null);
  }

  public static MaplePacket applyMonsterStatusTest(
    int oid,
    int mask,
    int delay,
    MobSkill mobskill,
    int value
  ) {
    // 9B 00 67 40 6F 00 80 00 00 00 01 00 FD FE 30 00 08 00 64 00 01
    // 1D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 10 00 01 00 79 00 01 00 B4 78 00 00 00 00 84 03
    // B4 00 A8 90 03 00 00 00 04 00 01 00 8C 00 03 00 14 00 4C 04 02
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.APPLY_MONSTER_STATUS.getValue());
    mplew.writeInt(oid);
    mplew.writeInt(mask);
    mplew.writeShort(1);
    mplew.writeShort(mobskill.getSkillId());
    mplew.writeShort(mobskill.getSkillLevel());
    mplew.writeShort(0); // as this looks similar to giveBuff this might actually be the buffTime but it's not displayed anywhere
    mplew.writeShort(delay); // delay in ms
    mplew.write(1); // ?

    return mplew.getPacket();
  }

  public static MaplePacket applyMonsterStatusTest2(
    int oid,
    int mask,
    int skill,
    int value
  ) {
    // 9B 00 67 40 6F 00 80 00 00 00 01 00 FD FE 30 00 08 00 64 00 01
    // 1D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 10 00 01 00 79 00 01 00 B4 78 00 00 00 00 84 03
    // B4 00 A8 90 03 00 00 00 04 00 01 00 8C 00 03 00 14 00 4C 04 02
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.APPLY_MONSTER_STATUS.getValue());
    mplew.writeInt(oid);
    mplew.writeInt(mask);
    mplew.writeShort(value);
    mplew.writeInt(skill);
    mplew.writeShort(0); // as this looks similar to giveBuff this might actually be the buffTime but it's not displayed anywhere
    mplew.writeShort(0); // delay in ms
    mplew.write(1); // ?

    return mplew.getPacket();
  }

  public static MaplePacket applyMonsterStatus(
    int oid,
    Map<MonsterStatus, Integer> stats,
    int skill,
    boolean monsterSkill,
    int delay,
    MobSkill mobskill
  ) {
    // 9B 00 67 40 6F 00 80 00 00 00 01 00 FD FE 30 00 08 00 64 00 01
    // 1D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 10 00 01 00 79 00 01 00 B4 78 00 00 00 00 84 03
    // B4 00 A8 90 03 00 00 00 04 00 01 00 8C 00 03 00 14 00 4C 04 02
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.APPLY_MONSTER_STATUS.getValue());
    mplew.writeInt(oid);
    int mask = 0;
    for (MonsterStatus stat : stats.keySet()) {
      mask |= stat.getValue();
    }
    mplew.writeInt(mask);
    for (Integer val : stats.values()) {
      mplew.writeShort(val);
      if (monsterSkill) {
        mplew.writeShort(mobskill.getSkillId());
        mplew.writeShort(mobskill.getSkillLevel());
      } else {
        mplew.writeInt(skill);
      }
      mplew.writeShort(0); // as this looks similar to giveBuff this
      // might actually be the buffTime but it's not displayed anywhere

    }
    mplew.writeShort(delay); // delay in ms
    mplew.write(1); // ?

    return mplew.getPacket();
  }

  public static MaplePacket cancelMonsterStatus(
    int oid,
    Map<MonsterStatus, Integer> stats
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CANCEL_MONSTER_STATUS.getValue());
    mplew.writeInt(oid);
    int mask = 0;
    for (MonsterStatus stat : stats.keySet()) {
      mask |= stat.getValue();
    }
    mplew.writeInt(mask);
    mplew.write(1);

    return mplew.getPacket();
  }

  public static MaplePacket getClock(int time) { // time in seconds
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CLOCK.getValue());
    mplew.write(2); // clock type. if you send 3 here you have to send another byte (which does not matter at all) before the timestamp
    mplew.writeInt(time);

    return mplew.getPacket();
  }

  public static MaplePacket getClockTime(int hour, int min, int sec) { // Current Time
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CLOCK.getValue());
    mplew.write(1); //Clock-Type

    mplew.write(hour);
    mplew.write(min);
    mplew.write(sec);
    return mplew.getPacket();
  }

  public static MaplePacket spawnMist(
    int oid,
    int ownerCid,
    int skillId,
    Rectangle mistPosition,
    int level
  ) {
    /*
     * D1 00
     * 0E 00 00 00 // OID?
     * 01 00 00 00 // Mist ID
     * 6A 4D 27 00 // Char ID?
     * 1B 36 20 00 // Skill ID
     * 1E
     * 08 00
     * 3D FD FF FF
     * 71 FC FF FF
     * CD FE FF FF
     * 9D FD FF FF
     * 00 00 00 00
     */
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SPAWN_MIST.getValue());
    mplew.writeInt(oid);
    mplew.writeInt(oid); // maybe this should actually be the "mistid" -
    // seems to always be 1 with only one mist in the map...
    mplew.writeInt(ownerCid); // probably only intresting for smokescreen
    mplew.writeInt(skillId);
    mplew.write(level); // who knows
    mplew.writeShort(8); // ???
    mplew.writeInt(mistPosition.x); // left position
    mplew.writeInt(mistPosition.y); // bottom position
    mplew.writeInt(mistPosition.x + mistPosition.width); // left position
    mplew.writeInt(mistPosition.y + mistPosition.height); // upper position
    mplew.writeInt(0);

    return mplew.getPacket();
  }

  public static MaplePacket spawnMobMist(
    int oid,
    int ownerCid,
    int skillId,
    Rectangle mistPosition,
    int level
  ) {
    /*
     * D1 00
     * 0E 00 00 00 // OID?
     * 01 00 00 00 // Mist ID
     * 6A 4D 27 00 // Char ID?
     * 1B 36 20 00 // Skill ID
     * 1E
     * 08 00
     * 3D FD FF FF
     * 71 FC FF FF
     * CD FE FF FF
     * 9D FD FF FF
     * 00 00 00 00
     */
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SPAWN_MIST.getValue());
    mplew.writeInt(oid);
    mplew.writeInt(oid); // maybe this should actually be the "mistid" -
    // seems to always be 1 with only one mist in the map...
    mplew.writeInt(ownerCid); // probably only intresting for smokescreen
    mplew.writeInt(skillId);
    mplew.write(level); // who knows
    mplew.writeShort(8); // ???
    mplew.writeInt(mistPosition.x); // left position
    mplew.writeInt(mistPosition.y); // bottom position
    mplew.writeInt(mistPosition.x + mistPosition.width); // left position
    mplew.writeInt(mistPosition.y + mistPosition.height); // upper position
    mplew.writeInt(1);

    return mplew.getPacket();
  }

  public static MaplePacket removeMist(int oid) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.REMOVE_MIST.getValue());
    mplew.writeInt(oid);

    return mplew.getPacket();
  }

  public static MaplePacket damageSummon(
    int cid,
    int summonSkillId,
    int damage,
    int unkByte,
    int monsterIdFrom
  ) {
    // 77 00 29 1D 02 00 FA FE 30 00 00 10 00 00 00 BF 70 8F 00 00
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.DAMAGE_SUMMON.getValue());
    mplew.writeInt(cid);
    mplew.writeInt(summonSkillId);
    mplew.write(unkByte);
    mplew.writeInt(damage);
    mplew.writeInt(monsterIdFrom);
    mplew.write(0);

    return mplew.getPacket();
  }

  public static MaplePacket damageMonster(int oid, int damage) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.DAMAGE_MONSTER.getValue());
    mplew.writeInt(oid);
    mplew.write(0);
    mplew.writeInt(damage);

    return mplew.getPacket();
  }

  public static MaplePacket healMonster(int oid, int heal) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.DAMAGE_MONSTER.getValue());
    mplew.writeInt(oid);
    mplew.write(0);
    mplew.writeInt(-heal);

    return mplew.getPacket();
  }

  public static MaplePacket updateBuddylist(
    Collection<BuddylistEntry> buddylist
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
    mplew.write(7);
    mplew.write(buddylist.size());
    for (BuddylistEntry buddy : buddylist) {
      if (buddy.isVisible()) {
        mplew.writeInt(buddy.getCharacterId()); // cid
        mplew.writeAsciiString(
          StringUtil.getRightPaddedStr(buddy.getName(), '\0', 13)
        );
        mplew.write(0);
        mplew.writeInt(buddy.getChannel() - 1);
      }
    }
    for (int x = 0; x < buddylist.size(); x++) {
      mplew.writeInt(0);
    }

    return mplew.getPacket();
  }

  public static MaplePacket buddylistMessage(byte message) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
    mplew.write(message);

    return mplew.getPacket();
  }

  public static MaplePacket requestBuddylistAdd(int cidFrom, String nameFrom) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
    mplew.write(9);
    mplew.writeInt(cidFrom);
    mplew.writeMapleAsciiString(nameFrom);
    mplew.writeInt(cidFrom);
    mplew.writeAsciiString(StringUtil.getRightPaddedStr(nameFrom, '\0', 13));
    mplew.write(1);
    mplew.write(31);
    mplew.writeInt(0);

    return mplew.getPacket();
  }

  public static MaplePacket updateBuddyChannel(int characterid, int channel) {
    // 2B 00 14 30 C0 23 00 00 11 00 00 00
    // 2B 00 14 30 C0 23 00 00 0D 00 00 00
    // 2B 00 14 30 75 00 00 00 11 00 00 00
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
    mplew.write(0x14);
    mplew.writeInt(characterid);
    mplew.write(0);
    mplew.writeInt(channel);

    return mplew.getPacket();
  }

  public static MaplePacket itemEffect(int characterid, int itemid) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_ITEM_EFFECT.getValue());

    mplew.writeInt(characterid);
    mplew.writeInt(itemid);

    return mplew.getPacket();
  }

  public static MaplePacket updateBuddyCapacity(int capacity) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
    mplew.write(0x15);
    mplew.write(capacity);

    return mplew.getPacket();
  }

  public static MaplePacket showChair(int characterid, int itemid) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_CHAIR.getValue());

    mplew.writeInt(characterid);
    mplew.writeInt(itemid);

    return mplew.getPacket();
  }

  public static MaplePacket cancelChair() {
    return cancelChair(-1);
  }

  public static MaplePacket cancelChair(int id) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CANCEL_CHAIR.getValue());

    if (id == -1) {
      mplew.write(0);
    } else {
      mplew.write(1);
      mplew.writeShort(id);
    }

    return mplew.getPacket();
  }

  public static MaplePacket spawnReactor(MapleReactor reactor) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.REACTOR_SPAWN.getValue());
    mplew.writeInt(reactor.getObjectId());
    mplew.writeInt(reactor.getReactorId());
    mplew.write(reactor.getState());
    mplew.writePos(reactor.getPosition());
    mplew.write(reactor.getFacingDirection()); // stance

    return mplew.getPacket();
  }

  public static MaplePacket triggerReactor(MapleReactor reactor, int stance) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.REACTOR_HIT.getValue());
    mplew.writeInt(reactor.getObjectId());
    mplew.write(reactor.getState());
    mplew.writePos(reactor.getPosition());
    mplew.writeShort(stance);
    mplew.write(0);
    mplew.write(4); // frame delay, set to 5 since there doesn't appear to be a fixed formula for it

    return mplew.getPacket();
  }

  public static MaplePacket destroyReactor(MapleReactor reactor) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.REACTOR_DESTROY.getValue());
    mplew.writeInt(reactor.getObjectId());
    mplew.write(reactor.getState());
    mplew.writePos(reactor.getPosition());

    return mplew.getPacket();
  }

  public static MaplePacket musicChange(String song) {
    return environmentChange(song, 6);
  }

  public static MaplePacket showEffect(String effect) {
    return environmentChange(effect, 3);
  }

  public static MaplePacket playSound(String sound) {
    return environmentChange(sound, 4);
  }

  public static MaplePacket environmentChange(String env, int mode) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.BOSS_ENV.getValue());
    mplew.write(mode);
    mplew.writeMapleAsciiString(env);

    return mplew.getPacket();
  }

  public static MaplePacket startMapEffect(
    String msg,
    int itemid,
    boolean active
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MAP_EFFECT.getValue());
    mplew.write(active ? 0 : 1);

    mplew.writeInt(itemid);
    if (active) mplew.writeMapleAsciiString(msg);

    return mplew.getPacket();
  }

  public static MaplePacket removeMapEffect() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MAP_EFFECT.getValue());
    mplew.write(0);
    mplew.writeInt(0);

    return mplew.getPacket();
  }

  public static MaplePacket showGuildInfo(MapleCharacter c) {
    //whatever functions calling this better make sure
    //that the character actually HAS a guild
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x1A); //signature for showing guild info
    if (c == null) { //show empty guild (used for leaving, expelled)
      mplew.write(0);
      return mplew.getPacket();
    }
    MapleGuildCharacter initiator = c.getMGC();
    MapleGuild g = c.getClient().getChannelServer().getGuild(initiator);
    if (g == null) { //failed to read from DB - don't show a guild
      mplew.write(0);
      log.warn(MapleClient.getLogMessage(c, "Couldn't load a guild"));
      return mplew.getPacket();
    } else {
      //MapleGuild holds the absolute correct value of guild rank
      //after it is initiated
      MapleGuildCharacter mgc = g.getMGC(c.getId());
      c.setGuildRank(mgc.getGuildRank());
    }
    mplew.write(1); //bInGuild
    mplew.writeInt(c.getGuildId()); //not entirely sure about this one
    mplew.writeMapleAsciiString(g.getName());
    for (int i = 1; i <= 5; i++) mplew.writeMapleAsciiString(g.getRankTitle(i));
    Collection<MapleGuildCharacter> members = g.getMembers();
    mplew.write(members.size());
    //then it is the size of all the members
    for (MapleGuildCharacter mgc : members) mplew.writeInt(mgc.getId()); //and each of their character ids o_O
    for (MapleGuildCharacter mgc : members) {
      mplew.writeAsciiString(
        StringUtil.getRightPaddedStr(mgc.getName(), '\0', 13)
      );
      mplew.writeInt(mgc.getJobId());
      mplew.writeInt(mgc.getLevel());
      mplew.writeInt(mgc.getGuildRank());
      mplew.writeInt(mgc.isOnline() ? 1 : 0);
      mplew.writeInt(g.getSignature());
      mplew.writeInt(3);
    }
    mplew.writeInt(g.getCapacity());
    mplew.writeShort(g.getLogoBG());
    mplew.write(g.getLogoBGColor());
    mplew.writeShort(g.getLogo());
    mplew.write(g.getLogoColor());
    mplew.writeMapleAsciiString(g.getNotice());
    mplew.writeInt(g.getGP());
    mplew.writeInt(0);

    // System.out.println("DEBUG: showGuildInfo packet:\n" + mplew.toString());
    return mplew.getPacket();
  }

  public static MaplePacket guildMemberOnline(
    int gid,
    int cid,
    boolean bOnline
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x3d);
    mplew.writeInt(gid);
    mplew.writeInt(cid);
    mplew.write(bOnline ? 1 : 0);

    return mplew.getPacket();
  }

  public static MaplePacket guildInvite(int gid, String charName) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x05);
    mplew.writeInt(gid);
    mplew.writeMapleAsciiString(charName);

    return mplew.getPacket();
  }

  /**
   * 'Char' has denied your guild invitation.
   *
   * @param charname
   * @return
   */
  public static MaplePacket denyGuildInvitation(String charname) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x37);
    mplew.writeMapleAsciiString(charname);

    return mplew.getPacket();
  }

  public static MaplePacket genericGuildMessage(byte code) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(code);

    return mplew.getPacket();
  }

  public static MaplePacket newGuildMember(MapleGuildCharacter mgc) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x27);
    mplew.writeInt(mgc.getGuildId());
    mplew.writeInt(mgc.getId());
    mplew.writeAsciiString(
      StringUtil.getRightPaddedStr(mgc.getName(), '\0', 13)
    );
    mplew.writeInt(mgc.getJobId());
    mplew.writeInt(mgc.getLevel());
    mplew.writeInt(mgc.getGuildRank()); //should be always 5 but whatevs
    mplew.writeInt(mgc.isOnline() ? 1 : 0); //should always be 1 too
    mplew.writeInt(1); //? could be guild signature, but doesn't seem to matter
    mplew.writeInt(3);

    return mplew.getPacket();
  }

  //someone leaving, mode == 0x2c for leaving, 0x2f for expelled
  public static MaplePacket memberLeft(
    MapleGuildCharacter mgc,
    boolean bExpelled
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(bExpelled ? 0x2f : 0x2c);

    mplew.writeInt(mgc.getGuildId());
    mplew.writeInt(mgc.getId());
    mplew.writeMapleAsciiString(mgc.getName());

    return mplew.getPacket();
  }

  //rank change
  public static MaplePacket changeRank(MapleGuildCharacter mgc) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x40);
    mplew.writeInt(mgc.getGuildId());
    mplew.writeInt(mgc.getId());
    mplew.write(mgc.getGuildRank());

    return mplew.getPacket();
  }

  public static MaplePacket guildNotice(int gid, String notice) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x44);

    mplew.writeInt(gid);
    mplew.writeMapleAsciiString(notice);

    return mplew.getPacket();
  }

  public static MaplePacket guildMemberLevelJobUpdate(MapleGuildCharacter mgc) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x3C);

    mplew.writeInt(mgc.getGuildId());
    mplew.writeInt(mgc.getId());
    mplew.writeInt(mgc.getLevel());
    mplew.writeInt(mgc.getJobId());

    return mplew.getPacket();
  }

  public static MaplePacket rankTitleChange(int gid, String[] ranks) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x3e);
    mplew.writeInt(gid);

    for (int i = 0; i < 5; i++) mplew.writeMapleAsciiString(ranks[i]);

    return mplew.getPacket();
  }

  public static MaplePacket guildDisband(int gid) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x32);
    mplew.writeInt(gid);
    mplew.write(1);

    return mplew.getPacket();
  }

  public static MaplePacket guildEmblemChange(
    int gid,
    short bg,
    byte bgcolor,
    short logo,
    byte logocolor
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x42);
    mplew.writeInt(gid);
    mplew.writeShort(bg);
    mplew.write(bgcolor);
    mplew.writeShort(logo);
    mplew.write(logocolor);

    return mplew.getPacket();
  }

  public static MaplePacket guildCapacityChange(int gid, int capacity) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x3a);
    mplew.writeInt(gid);
    mplew.write(capacity);

    return mplew.getPacket();
  }

  public static void addThread(
    MaplePacketLittleEndianWriter mplew,
    ResultSet rs
  ) throws SQLException {
    mplew.writeInt(rs.getInt("localthreadid"));
    mplew.writeInt(rs.getInt("postercid"));
    mplew.writeMapleAsciiString(rs.getString("name"));
    mplew.writeLong(PacketHelper.getKoreanTimestamp(rs.getLong("timestamp")));
    mplew.writeInt(rs.getInt("icon"));
    mplew.writeInt(rs.getInt("replycount"));
  }

  public static MaplePacket BBSThreadList(ResultSet rs, int start)
    throws SQLException {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.BBS_OPERATION.getValue());
    mplew.write(0x06);
    if (!rs.last()) {
      //no result at all
      mplew.write(0);
      mplew.writeInt(0);
      mplew.writeInt(0);
      return mplew.getPacket();
    }
    int threadCount = rs.getRow();
    if (rs.getInt("localthreadid") == 0) { //has a notice
      mplew.write(1);
      addThread(mplew, rs);
      threadCount--; //one thread didn't count (because it's a notice)
    } else mplew.write(0);
    if (!rs.absolute(start + 1)) { //seek to the thread before where we start
      rs.first(); //uh, we're trying to start at a place past possible
      start = 0;
      // System.out.println("Attempting to start past threadCount");
    }
    mplew.writeInt(threadCount);
    mplew.writeInt(Math.min(10, threadCount - start));
    for (int i = 0; i < Math.min(10, threadCount - start); i++) {
      addThread(mplew, rs);
      rs.next();
    }

    return mplew.getPacket();
  }

  public static MaplePacket showThread(
    int localthreadid,
    ResultSet threadRS,
    ResultSet repliesRS
  ) throws SQLException, RuntimeException {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.BBS_OPERATION.getValue());
    mplew.write(0x07);
    mplew.writeInt(localthreadid);
    mplew.writeInt(threadRS.getInt("postercid"));
    mplew.writeLong(
      PacketHelper.getKoreanTimestamp(threadRS.getLong("timestamp"))
    );
    mplew.writeMapleAsciiString(threadRS.getString("name"));
    mplew.writeMapleAsciiString(threadRS.getString("startpost"));
    mplew.writeInt(threadRS.getInt("icon"));
    if (repliesRS != null) {
      int replyCount = threadRS.getInt("replycount");
      mplew.writeInt(replyCount);
      int i;
      for (i = 0; i < replyCount && repliesRS.next(); i++) {
        mplew.writeInt(repliesRS.getInt("replyid"));
        mplew.writeInt(repliesRS.getInt("postercid"));
        mplew.writeLong(
          PacketHelper.getKoreanTimestamp(repliesRS.getLong("timestamp"))
        );
        mplew.writeMapleAsciiString(repliesRS.getString("content"));
      }
      if (i != replyCount || repliesRS.next()) {
        //in the unlikely event that we lost count of replyid
        throw new RuntimeException(String.valueOf(threadRS.getInt("threadid")));
        //we need to fix the database and stop the packet sending
        //or else it'll probably error 38 whoever tries to read it

        //there is ONE case not checked, and that's when the thread
        //has a replycount of 0 and there is one or more replies to the
        //thread in bbs_replies
      }
    } else mplew.writeInt(0); //0 replies

    return mplew.getPacket();
  }

  public static MaplePacket showApple() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(0x5C);
    return mplew.getPacket();
  }

  public static MaplePacket showGuildRanks(int npcid, ResultSet rs)
    throws SQLException {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x49);
    mplew.writeInt(npcid);
    if (!rs.last()) { //no guilds o.o
      mplew.writeInt(0);
      return mplew.getPacket();
    }
    mplew.writeInt(rs.getRow()); //number of entries
    rs.beforeFirst();
    while (rs.next()) {
      mplew.writeMapleAsciiString(rs.getString("name"));
      mplew.writeInt(rs.getInt("GP"));
      mplew.writeInt(rs.getInt("logo"));
      mplew.writeInt(rs.getInt("logoColor"));
      mplew.writeInt(rs.getInt("logoBG"));
      mplew.writeInt(rs.getInt("logoBGColor"));
    }

    return mplew.getPacket();
  }

  public static MaplePacket updateGP(int gid, int GP) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
    mplew.write(0x48);
    mplew.writeInt(gid);
    mplew.writeInt(GP);
    return mplew.getPacket();
  }

  public static MaplePacket skillEffect(
    MapleCharacter from,
    int skillId,
    int level,
    byte flags,
    int speed
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SKILL_EFFECT.getValue());
    mplew.writeInt(from.getId());
    mplew.writeInt(skillId);
    mplew.write(level);
    mplew.write(flags);
    mplew.write(speed);

    return mplew.getPacket();
  }

  public static MaplePacket skillCancel(MapleCharacter from, int skillId) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CANCEL_SKILL_EFFECT.getValue());
    mplew.writeInt(from.getId());
    mplew.writeInt(skillId);

    return mplew.getPacket();
  }

  public static MaplePacket showMagnet(int mobid, byte success) { // Monster Magnet
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_MAGNET.getValue());
    mplew.writeInt(mobid);
    mplew.write(success);

    return mplew.getPacket();
  }

  /**
   * Sends a player hint.
   *
   * @param hint The hint it's going to send.
   * @param width How tall the box is going to be.
   * @param height How long the box is going to be.
   * @return The player hint packet.
   */
  public static MaplePacket sendHint(String hint, int width, int height) {
    if (width < 1) {
      width = hint.length() * 10;
      if (width < 40) {
        width = 40;
      }
    }
    if (height < 5) {
      height = 5;
    }

    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PLAYER_HINT.getValue());
    mplew.writeMapleAsciiString(hint);
    mplew.writeShort(width);
    mplew.writeShort(height);
    mplew.write(1);

    return mplew.getPacket();
  }

  public static MaplePacket sendHint(String hint) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);
    mplew.writeShort(SendPacketOpcode.PLAYER_HINT.getValue());
    mplew.writeMapleAsciiString(hint);
    mplew.write(HexTool.getByteArrayFromHexString("FA 00 05 00 01"));

    return mplew.getPacket();
  }

  public static MaplePacket messengerInvite(String from, int messengerid) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
    mplew.write(0x03);
    mplew.writeMapleAsciiString(from);
    mplew.write(0x00);
    mplew.writeInt(messengerid);
    mplew.write(0x00);

    return mplew.getPacket();
  }

  public static MaplePacket addMessengerPlayer(
    String from,
    MapleCharacter chr,
    int position,
    int channel
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
    mplew.write(0x00);
    mplew.write(position);
    PacketHelper.addCharLook(mplew, chr, true);
    mplew.writeMapleAsciiString(from);
    mplew.write(channel);
    mplew.write(0x00);

    return mplew.getPacket();
  }

  public static MaplePacket removeMessengerPlayer(int position) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
    mplew.write(0x02);
    mplew.write(position);

    return mplew.getPacket();
  }

  public static MaplePacket updateMessengerPlayer(
    String from,
    MapleCharacter chr,
    int position,
    int channel
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
    mplew.write(0x07);
    mplew.write(position);
    PacketHelper.addCharLook(mplew, chr, true);
    mplew.writeMapleAsciiString(from);
    mplew.write(channel);
    mplew.write(0x00);

    return mplew.getPacket();
  }

  public static MaplePacket joinMessenger(int position) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
    mplew.write(0x01);
    mplew.write(position);

    return mplew.getPacket();
  }

  public static MaplePacket messengerChat(String text) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
    mplew.write(0x06);
    mplew.writeMapleAsciiString(text);

    return mplew.getPacket();
  }

  public static MaplePacket messengerNote(String text, int mode, int mode2) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
    mplew.write(mode);
    mplew.writeMapleAsciiString(text);
    mplew.write(mode2);

    return mplew.getPacket();
  }

  public static MaplePacket itemExpired(int itemid) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
    mplew.write(2);
    mplew.writeInt(itemid);
    return mplew.getPacket();
  }

  public static MaplePacket weirdStatUpdate() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.UPDATE_STATS.getValue());
    mplew.write(0);
    mplew.write(8);
    mplew.write(0);
    mplew.write(0x18);
    mplew.writeLong(0);
    mplew.writeLong(0);
    mplew.writeLong(0);
    mplew.write(0);
    mplew.write(1);
    return mplew.getPacket();
  }

  public static MaplePacket showEquipEffect() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_EQUIP_EFFECT.getValue());

    return mplew.getPacket();
  }

  public static MaplePacket summonSkill(
    int cid,
    int summonSkillId,
    int newStance
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SUMMON_SKILL.getValue());
    mplew.writeInt(cid);
    mplew.writeInt(summonSkillId);
    mplew.write(newStance);

    return mplew.getPacket();
  }

  public static MaplePacket skillCooldown(int sid, int time) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.COOLDOWN.getValue());
    mplew.writeInt(sid);
    mplew.writeShort(time);

    return mplew.getPacket();
  }

  public static MaplePacket skillBookSuccess(
    MapleCharacter chr,
    int skillid,
    int maxlevel,
    boolean canuse,
    boolean success
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.USE_SKILL_BOOK.getValue());
    mplew.writeInt(chr.getId()); // character id
    mplew.write(1);
    mplew.writeInt(skillid);
    mplew.writeInt(maxlevel);
    mplew.write(canuse ? 1 : 0);
    mplew.write(success ? 1 : 0);

    return mplew.getPacket();
  }

  public static MaplePacket getMacros(SkillMacro[] macros) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SKILL_MACRO.getValue());
    int count = 0;
    for (int i = 0; i < 5; i++) {
      if (macros[i] != null) {
        count++;
      }
    }
    mplew.write(count); // number of macros
    for (int i = 0; i < 5; i++) {
      SkillMacro macro = macros[i];
      if (macro != null) {
        mplew.writeMapleAsciiString(macro.getName());
        mplew.write(macro.getShout());
        mplew.writeInt(macro.getSkill1());
        mplew.writeInt(macro.getSkill2());
        mplew.writeInt(macro.getSkill3());
      }
    }

    return mplew.getPacket();
  }

  public static MaplePacket getPlayerNPC(int charid, int npcid) {
    /*
     * Dear LoOpEdd,
     *
     * Even though you are a die-hard legit, you have
     * just assisted the private server community by
     * letting me packet sniff your player NPC
     *
     * Sincerely,
     * Acrylic
     */
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PLAYER_NPC.getValue());
    Connection con = DatabaseConnection.getConnection();
    try {
      PreparedStatement ps = con.prepareStatement(
        "SELECT * FROM characters WHERE id = ?"
      );
      ps.setInt(1, charid);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        mplew.write(1); //Direction
        mplew.writeInt(npcid);
        mplew.writeMapleAsciiString(rs.getString("name"));
        mplew.write(0);
        mplew.write(rs.getByte("skin"));
        mplew.writeInt(rs.getInt("face"));
        mplew.write(0);
        mplew.writeInt(rs.getInt("hair"));
      }
      rs.close();
      ps.close();
    } catch (SQLException se) {
      log.warn("We warn thee...", se);
    }
    try {
      PreparedStatement ps = con.prepareStatement(
        "SELECT * FROM inventoryitems WHERE characterid = ? AND inventorytype = -1"
      );
      ps.setInt(1, charid);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        mplew.write(rs.getInt("position"));
        mplew.writeInt(rs.getInt("itemid"));
      }
      rs.close();
      ps.close();
    } catch (SQLException se) {
      log.warn("We warn thee...", se);
    }
    mplew.writeShort(-1);
    int count = 0;
    //		try {
    //			PreparedStatement ps = con.prepareStatement("SELECT * FROM playernpcs_equip WHERE npcid = ? AND type = 1");
    //			ps.setInt(1, id);
    //			ResultSet rs = ps.executeQuery();
    //			while (rs.next()) {
    //				mplew.writeInt(rs.getInt("equipid"));
    //				count += 1;
    //			}
    //			rs.close();
    //			ps.close();
    //		} catch (SQLException se) {
    //			log.warn("We warn thee...", se);
    //		}
    while (count < 4) {
      mplew.writeInt(0);
      count += 1;
    }

    return mplew.getPacket();
  }

  public static MaplePacket getPlayerNPC(PlayerNPCMerchant merchant) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PLAYER_NPC.getValue());
    mplew.write(1); //Direction
    mplew.writeInt(merchant.getNpcId());
    mplew.writeMapleAsciiString(merchant.getName());
    mplew.write(0);
    mplew.write(merchant.getSkin());
    mplew.writeInt(merchant.getFace());
    mplew.write(0);
    mplew.writeInt(merchant.getHair());
    //Loop through equips
    if (merchant.getHat() != -1) {
      mplew.write(1);
      mplew.writeInt(merchant.getHat());
    }
    if (merchant.getMask() != -1) {
      mplew.write(2);
      mplew.writeInt(merchant.getMask());
    }
    if (merchant.getEyes() != -1) {
      mplew.write(3);
      mplew.writeInt(merchant.getEyes());
    }
    if (merchant.getEarring() != -1) {
      mplew.write(4);
      mplew.writeInt(merchant.getEarring());
    }
    if (merchant.getTop() != -1) {
      mplew.write(5);
      mplew.writeInt(merchant.getTop());
    }
    if (merchant.getBottom() != -1) {
      mplew.write(6);
      mplew.writeInt(merchant.getBottom());
    }
    if (merchant.getShoes() != -1) {
      mplew.write(7);
      mplew.writeInt(merchant.getShoes());
    }
    if (merchant.getGlove() != -1) {
      mplew.write(8);
      mplew.writeInt(merchant.getGlove());
    }
    if (merchant.getCape() != -1) {
      mplew.write(9);
      mplew.writeInt(merchant.getCape());
    }
    if (merchant.getShield() != -1) {
      mplew.write(10);
      mplew.writeInt(merchant.getShield());
    }
    if (merchant.getWeapon() != -1) {
      mplew.write(11);
      mplew.writeInt(merchant.getWeapon());
    }

    mplew.writeShort(-1);
    int count = 0;
    // try {
    // PreparedStatement ps = con.prepareStatement("SELECT * FROM playernpcs_equip WHERE npcid = ? AND type = 1");
    // ps.setInt(1, id);
    // ResultSet rs = ps.executeQuery();
    // while (rs.next()) {
    // mplew.writeInt(rs.getInt("equipid"));
    // count += 1;
    // }
    // rs.close();
    // ps.close();
    // } catch (SQLException se) {
    // log.warn("We warn thee...", se);
    // }
    while (count < 4) {
      mplew.writeInt(0);
      count += 1;
    }

    return mplew.getPacket();
  }

  public static MaplePacket getPlayerNPC(int id) {
    /*
     * Dear LoOpEdd,
     *
     * Even though you are a die-hard legit, you have
     * just assisted the private server community by
     * letting me packet sniff your player NPC
     *
     * Sincerely,
     * Acrylic
     */
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PLAYER_NPC.getValue());
    Connection con = DatabaseConnection.getConnection();
    try {
      PreparedStatement ps = con.prepareStatement(
        "SELECT * FROM playernpcs WHERE id = ?"
      );
      ps.setInt(1, id);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        mplew.write(rs.getByte("dir"));
        //mplew.writeShort(rs.getInt("x"));
        //mplew.writeShort(rs.getInt("y"));
        mplew.writeInt(rs.getInt("npcid"));
        mplew.writeMapleAsciiString(rs.getString("name"));
        mplew.write(0);
        mplew.write(rs.getByte("skin"));
        mplew.writeInt(rs.getInt("face"));
        mplew.write(0);
        mplew.writeInt(rs.getInt("hair"));
        /*
         * 01 // hat
         * CA 4A 0F 00 // 1002186 - transparent hat
         * 03 // eye accessory
         * 4F 98 0F 00 // 1022031 - white toy shades
         * 04 // earring
         * 58 BF 0F 00 // 1032024 - transparent earrings
         * 05 // top
         * D1 E6 0F 00 // 1042129 - ? unknown top maybe?
         * 06 // bottom
         * 9F 34 10 00 // 1062047 - brisk (pants)
         * 07 // shoes
         * 82 5C 10 00 // 1072258 - kitty slippers
         * 08 // gloves
         * 01 83 10 00 // 1082113 - hair cutter gloves
         * 09 // cape
         * D7 D0 10 00 // 1102039 - transparent cape
         * 0B // weapon
         * 00 76 16 // ?
         * 00
         * FF FF
         * D3 F8 19 00 // 1702099 - transparent claw
         * 00 00 00 00
         * 00 00 00 00
         * 00 00 00 00
         */
      }
      rs.close();
      ps.close();
    } catch (SQLException se) {
      log.warn("We warn thee...", se);
    }
    try {
      PreparedStatement ps = con.prepareStatement(
        "SELECT * FROM playernpcs_equip WHERE npcid = ? AND type = 0"
      );
      ps.setInt(1, id);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        mplew.write(rs.getByte("equippos"));
        mplew.writeInt(rs.getInt("equipid"));
      }
      rs.close();
      ps.close();
    } catch (SQLException se) {
      log.warn("We warn thee...", se);
    }
    mplew.writeShort(-1);
    int count = 0;
    try {
      PreparedStatement ps = con.prepareStatement(
        "SELECT * FROM playernpcs_equip WHERE npcid = ? AND type = 1"
      );
      ps.setInt(1, id);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        mplew.writeInt(rs.getInt("equipid"));
        count += 1;
      }
      rs.close();
      ps.close();
    } catch (SQLException se) {
      log.warn("We warn thee...", se);
    }
    while (count < 4) {
      mplew.writeInt(0);
      count += 1;
    }

    return mplew.getPacket();
  }

  public static MaplePacket getPlayerNPC(int id, MapleCharacter player) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PLAYER_NPC.getValue());
    Connection con = DatabaseConnection.getConnection();
    try {
      PreparedStatement ps = con.prepareStatement(
        "SELECT * FROM playernpcs WHERE id = ?"
      );
      ps.setInt(1, id);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        mplew.write(rs.getByte("dir"));
        mplew.writeShort(player.getPosition().x);
        mplew.writeShort(player.getPosition().y);
        mplew.writeMapleAsciiString(player.getName());
        mplew.write(0);
        mplew.write(rs.getByte("skin"));
        mplew.writeInt(rs.getInt("face"));
        mplew.writeInt(player.getMap().getId());
        mplew.write(0);
        mplew.writeInt(rs.getInt("hair"));
      }
      rs.close();
      ps.close();
    } catch (SQLException se) {
      log.warn("We warn thee...", se);
    }
    try {
      PreparedStatement ps = con.prepareStatement(
        "SELECT * FROM playernpcs_equip WHERE npcid = ? AND type = 0"
      );
      ps.setInt(1, id);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        mplew.write(rs.getByte("equippos"));
        mplew.writeInt(rs.getInt("equipid"));
      }
      rs.close();
      ps.close();
    } catch (SQLException se) {
      log.warn("We warn thee...", se);
    }
    mplew.writeShort(-1);
    int count = 0;
    try {
      PreparedStatement ps = con.prepareStatement(
        "SELECT * FROM playernpcs_equip WHERE npcid = ? AND type = 1"
      );
      ps.setInt(1, id);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        mplew.writeInt(rs.getInt("equipid"));
        count += 1;
      }
      rs.close();
      ps.close();
    } catch (SQLException se) {
      log.warn("We warn thee...", se);
    }
    while (count < 4) {
      mplew.writeInt(0);
      count += 1;
    }

    return mplew.getPacket();
  }

  /** @author Jvlaple **/
  public static MaplePacket getPlayerNPC(MapleCharacter player) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PLAYER_NPC.getValue());
    mplew.write(1); //direction
    mplew.writeInt(9901000); //NPCID
    mplew.writeMapleAsciiString(player.getName());
    mplew.write(0);
    mplew.write(player.getSkinColor().getId());
    mplew.writeInt(player.getFace());
    mplew.write(0);
    mplew.writeInt(player.getHair());
    Collection<IItem> equips = player
      .getInventory(MapleInventoryType.EQUIPPED)
      .list();
    for (IItem equip : equips) {
      mplew.write(Math.abs(equip.getPosition()));
      mplew.writeInt(equip.getItemId());
    }
    mplew.writeShort(-1);
    int count = 0;
    //		for (IItem equip : equips) {
    //			mplew.writeInt(equip.getItemId());
    //			count += 1;
    //		}
    while (count < 4) {
      mplew.writeInt(0);
      count += 1;
    }

    return mplew.getPacket();
  }

  public static MaplePacket showNotes(ResultSet notes, int count)
    throws SQLException {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.SHOW_NOTES.getValue());
    mplew.write(2);
    mplew.write(count);
    for (int i = 0; i < count; i++) {
      mplew.writeInt(notes.getInt("id"));
      mplew.writeMapleAsciiString(notes.getString("from"));
      mplew.writeMapleAsciiString(notes.getString("message"));
      mplew.writeLong(
        PacketHelper.getKoreanTimestamp(notes.getLong("timestamp"))
      );
      mplew.write(0);
      notes.next();
    }
    return mplew.getPacket();
  }

  public static void sendUnkwnNote(String to, String msg, String from)
    throws SQLException {
    Connection con = DatabaseConnection.getConnection();
    PreparedStatement ps = con.prepareStatement(
      "INSERT INTO notes (`to`, `from`, `message`, `timestamp`) VALUES (?, ?, ?, ?)"
    );
    ps.setString(1, to);
    ps.setString(2, from);
    ps.setString(3, msg);
    ps.setLong(4, System.currentTimeMillis());
    ps.executeUpdate();
    ps.close();
  }

  public static MaplePacket updateAriantPQRanking(
    String name,
    int score,
    boolean empty
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.ARIANT_PQ_START.getValue());
    //E9 00 pid
    //01 unknown
    //09 00 53 69 6E 50 61 74 6A 65 68 maple ascii string name
    //00 00 00 00 score
    mplew.write(empty ? 0 : 1);
    if (!empty) {
      mplew.writeMapleAsciiString(name);
      mplew.writeInt(score);
    }

    return mplew.getPacket();
  }

  public static MaplePacket catchMonster(int mobid, int itemid, byte success) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.CATCH_MONSTER.getValue());
    //BF 00
    //38 37 2B 00 mob id
    //32 A3 22 00 item id
    //00 success??
    mplew.writeInt(mobid);
    mplew.writeInt(itemid);
    mplew.write(success);

    return mplew.getPacket();
  }

  public static MaplePacket showAriantScoreBoard() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.ARIANT_SCOREBOARD.getValue());
    return mplew.getPacket();
  }

  public static MaplePacket showAllCharacter(int chars, int unk) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.ALL_CHARLIST.getValue());
    mplew.write(1);
    mplew.writeInt(chars);
    mplew.writeInt(unk);
    return mplew.getPacket();
  }

  public static MaplePacket showAllCharacterInfo(
    int worldid,
    List<MapleCharacter> chars
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.ALL_CHARLIST.getValue());
    mplew.write(0);
    mplew.write(worldid);
    mplew.write(chars.size());
    for (MapleCharacter chr : chars) {
      PacketHelper.addCharEntry(mplew, chr);
    }
    return mplew.getPacket();
  }

  public static MaplePacket useChalkboard(MapleCharacter chr, boolean close) {
    final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.CHALKBOARD.getValue());
    mplew.writeInt(chr.getId());
    if (close) {
      mplew.write(0);
    } else {
      mplew.write(1);
      mplew.writeMapleAsciiString(chr.getChalkboard());
    }
    return mplew.getPacket();
  }

  public static MaplePacket showZakumShrineTimeLeft(int timeleft) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.ZAKUM_SHRINE.getValue());
    mplew.write(0);
    mplew.writeInt(timeleft);

    return mplew.getPacket();
  }

  public static MaplePacket boatPacket(boolean type) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.BOAT_EFFECT.getValue());
    mplew.writeShort(type ? 1 : 2);

    return mplew.getPacket();
  }

  public static MaplePacket showBoatEffect(int effect) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.BOAT_EFFECT.getValue());
    mplew.writeShort(effect); //1034: balrog boat comes, 1548: boat comes in ellinia station, 520: boat leaves ellinia station
    return mplew.getPacket();
  }

  public static MaplePacket registerPin() {
    return pinOperation((byte) 1);
  }

  public static MaplePacket pinRegistered() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PIN_ASSIGNED.getValue());
    mplew.write(0);
    return mplew.getPacket();
  }

  public static MaplePacket TrockRefreshMapList(int characterid, byte vip) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    Connection con = DatabaseConnection.getConnection();
    int i = 10;
    mplew.writeShort(SendPacketOpcode.TROCK_LOCATIONS.getValue());
    mplew.write(0x03); // unknown
    mplew.write(vip); // vip/no, please dont put the parameter above 1 -.-
    try {
      PreparedStatement ps = con.prepareStatement(
        "SELECT mapid FROM trocklocations WHERE characterid = ? LIMIT 10"
      );
      ps.setInt(1, characterid);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        mplew.writeInt(rs.getInt("mapid"));
      }
      rs.close();
      ps.close();
    } catch (SQLException se) {}
    while (i > 0) {
      mplew.writeInt(999999999); // write empty maps to remaining slots
      i--;
    }
    return mplew.getPacket();
  }

  public static MaplePacket showDashP(
    List<Pair<MapleBuffStat, Integer>> statups,
    int x,
    int y,
    int duration
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
    mplew.writeLong(0);
    long mask = getLongMask(statups);
    mplew.writeLong(mask);
    mplew.writeShort(0);
    mplew.writeInt(x);
    mplew.writeInt(5001005);
    mplew.write(HexTool.getByteArrayFromHexString("1A 7C 8D 35"));
    mplew.writeShort(duration);
    mplew.writeInt(y);
    mplew.writeInt(5001005);
    mplew.write(HexTool.getByteArrayFromHexString("1A 7C 8D 35"));
    mplew.writeShort(duration);
    mplew.writeShort(0);
    mplew.write(2);
    return mplew.getPacket();
  }

  public static MaplePacket showDashM(int cid, int x, int y, int duration) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
    mplew.writeInt(cid);
    mplew.writeLong(0);
    mplew.write(HexTool.getByteArrayFromHexString("00 00 00 30 00 00 00 00"));
    mplew.writeShort(0);
    mplew.writeInt(x);
    mplew.writeInt(5001005);
    mplew.write(HexTool.getByteArrayFromHexString("1A 7C 8D 35"));
    mplew.writeShort(duration);
    mplew.writeInt(y);
    mplew.writeInt(5001005);
    mplew.write(HexTool.getByteArrayFromHexString("1A 7C 8D 35"));
    mplew.writeShort(duration);
    mplew.writeShort(0);
    return mplew.getPacket();
  }

  public static MaplePacket toSpouse(String sender, String text, int type) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.SPOUSE_CHAT.getValue());
    mplew.write(type);
    if (type == 4) {
      mplew.write(1);
    } else {
      mplew.writeMapleAsciiString(sender);
      mplew.write(5);
    }
    mplew.writeMapleAsciiString(text);
    return mplew.getPacket();
  }

  public static MaplePacket reportReply(byte type) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.REPORTREPLY.getValue());
    mplew.write(type);
    return mplew.getPacket();
  }

  public static MaplePacket giveEnergyCharge(int barammount, int test2) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.write(
      HexTool.getByteArrayFromHexString(
        "1D 00 00 00 00 00 00 00 00 00 00 00 00 08 00 00 00 00 00 00"
      )
    );
    mplew.writeShort(barammount); // 0=no bar, 10000=full bar
    mplew.writeShort(0);
    mplew.writeShort(0);
    mplew.writeShort(0);
    mplew.writeInt(test2);
    mplew.writeShort(0);
    mplew.writeShort(0);
    return mplew.getPacket();
  }

  public static MaplePacket mobDamageMob(
    MapleMonster mob,
    int damage,
    int direction
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.DAMAGE_MONSTER.getValue());
    mplew.writeInt(mob.getObjectId());
    mplew.write(direction); // direction
    mplew.writeInt(damage);
    int remainingHp = mob.getHp() - damage;
    if (remainingHp < 0) {
      remainingHp = 0;
    }
    mob.setHp(remainingHp);
    mplew.writeInt(remainingHp);
    mplew.writeInt(mob.getMaxHp());
    return mplew.getPacket();
  }

  public static MaplePacket yellowChat(String msg) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort((int) 0x4A);
    mplew.write(5);
    mplew.writeMapleAsciiString(msg);
    return mplew.getPacket();
  }

  public static MaplePacket giveInfusion(int bufflength, int speed) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
    mplew.writeLong(0);
    mplew.writeLong(MapleBuffStat.MORPH.getValue());
    mplew.writeShort(speed);
    mplew.writeInt(0000000);
    mplew.writeLong(0);
    mplew.writeShort(bufflength);
    mplew.writeShort(0);
    return mplew.getPacket();
  }

  public static MaplePacket giveForeignInfusion(
    int cid,
    int speed,
    int duration
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
    mplew.writeInt(cid);
    mplew.writeLong(0);
    mplew.writeLong(MapleBuffStat.MORPH.getValue());
    mplew.writeShort(0);
    mplew.writeInt(speed);
    mplew.writeInt(0000000);
    mplew.writeLong(0);
    mplew.writeInt(duration);
    mplew.writeShort(0);
    return mplew.getPacket();
  }

  ///*
  //     * @author Rob/Xcheater3161
  //     * */
  //    public static MaplePacket spawnHiredMerchant(HiredMerchant merchant) {
  //        //[CA 00] [31 6B 0F 00] [71 C0 4C 00] 12 03 6E FF 2F 00 [0B 00 64 75 73 74 72 65 6D 6F 76 65 72] [05] [80 03 00 00] [0A 00 46 72 65 65 20 53 74 75 66 66] [01] [01] [04]
  //        //[CF 00] [31 6B 0F 00] 05 EE 33 00 00 1D 00 4F 68 20 68 61 69 20 67 75 69 65 73 2C 20 69 20 72 20 67 65 74 20 70 61 63 6B 65 74 7A 01 03 04
  //        //[CA 00] [78 F7 12 00] [71 C0 4C 00] 2D 02 E6 FF 23 00 [0C 00 4D 79 44 65 78 49 73 42 72 6F 6B 65] [05] [4A 07 00 00] [03 00 31 32 33] [01] [01] [04]
  //		  MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
  //		  mplew.writeShort(0xCA);
  //		  mplew.writeInt(merchant.getMerchantId());
  //		mplew.writeInt(merchant.getItemId());
  //		mplew.writeShort(merchant.getPosition().x);
  //		mplew.writeShort(merchant.getPosition().y));
  //		mplew.writeShort(merchant.getFoothold());
  //		mplew.writeMapleAsciiString(merchant.getOwnerName());
  //		mplew.write(5);
  //		mplew.writeInt(merchant.getObjectId());
  //		mplew.writeMapleAsciiString(merchant.getDescription());
  //		mplew.write((merchant.getItemId() - 5030000));
  //		mplew.write(1);
  //		mplew.write(4);
  //	  return mplew.getPacket();
  //	 }
  //
  //    /*
  //     * @author Rob/Xcheater3161
  //     * */
  //    public static MaplePacket updateHiredMerchant(HiredMerchant merchant, int status) {
  //        //[CD 00] [5D E2 40 00] 71 C0 4C 00 02 01 5E 00 0F 00 0B 00 78 6F 78 78 47 6F 64 78 78 6F 78 05 EE 33 00 00 1D 00 4F 68 20 68 61 69 20 67 75 69 65 73 2C 20 69 20 72 20 67 65 74 20 70 61 63 6B 65 74 7A 01 00 04
  //        //[CF 00] [5D E2 40 00] [05] [EE 33 00 00] [1D 00 4F 68 20 68 61 69 20 67 75 69 65 73 2C 20 69 20 72 20 67 65 74 20 70 61 63 6B 65 74 7A] 01 03 04
  //		  MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
  //		  mplew.writeShort(0xCD);
  //		  mplew.writeInt(merchant.getMerchantId());
  //		mplew.write(5);
  //		mplew.writeInt(merchant.getObjectId());
  //		mplew.writeMapleAsciiString(merchant.getDescription());
  //		mplew.write(1);
  //		mplew.write(status);
  //		mplew.write(4);
  //	  return mplew.getPacket();
  //	 }
  //
  //    /*
  //     * @author Rob/Xcheater3161
  //     * */
  //    public static MaplePacket removeHiredMerchant(HiredMerchant merchant) {
  //        //[CA 00] [31 6B 0F 00] [71 C0 4C 00] 12 03 6E FF 2F 00 [0B 00 64 75 73 74 72 65 6D 6F 76 65 72] [05] [80 03 00 00] [0A 00 46 72 65 65 20 53 74 75 66 66] [01] [01] [04]
  //        //[CA 00] [78 F7 12 00] [71 C0 4C 00] 2D 02 E6 FF 23 00 [0C 00 4D 79 44 65 78 49 73 42 72 6F 6B 65] [05] [4A 07 00 00] [03 00 31 32 33] [01] [01] [04]
  //	  MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
  //	  mplew.write(HexTool.getByteArrayFromHexString("CB 00"));
  //	  mplew.writeInt(merchant.getMerchantId());
  //	  return mplew.getPacket();
  //	 }
  //    /*
  //     * @author Rob/Xcheater3161
  //     * */
  //    public static MaplePacket closeHiredMerchant() {
  //        //[CA 00] [31 6B 0F 00] [71 C0 4C 00] 12 03 6E FF 2F 00 [0B 00 64 75 73 74 72 65 6D 6F 76 65 72] [05] [80 03 00 00] [0A 00 46 72 65 65 20 53 74 75 66 66] [01] [01] [04]
  //        //[CA 00] [78 F7 12 00] [71 C0 4C 00] 2D 02 E6 FF 23 00 [0C 00 4D 79 44 65 78 49 73 42 72 6F 6B 65] [05] [4A 07 00 00] [03 00 31 32 33] [01] [01] [04]
  //	  MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
  //	  mplew.write(HexTool.getByteArrayFromHexString("F5 00 0A 00 10"));
  //	  return mplew.getPacket();
  //	 }
  //
  //    /*
  //     * @author Rob/Xcheater3161
  //     * */
  //    public static MaplePacket getHiredMerchantMaintenance() {
  //	  MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
  //	  mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
  //	  mplew.write(HexTool.getByteArrayFromHexString("0A 01 0D"));
  //	  return mplew.getPacket();
  //	 }
  //    /*
  //     * @author Rob/Xcheater3161
  //     * */
  //    public static MaplePacket getHiredMerchantItemUpdate(HiredMerchant merchant) {
  //	  MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
  //	  mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
  //	  mplew.write(0x17);
  //			mplew.writeInt(0);
  //	  mplew.write(merchant.getItems().size());
  //	  for (MapleHiredMerchantItem item : merchant.getItems()) {
  //	   mplew.writeShort(item.getBundles());
  //	   mplew.writeShort(item.getItem().getQuantity());
  //	   mplew.writeInt(item.getPrice());
  //	   addItemInfo(mplew, item.getItem(), true, true);
  //	  }
  //	  return mplew.getPacket();
  //	}
  //
  //    /*
  //     * @author Rob/Xcheater3161
  //     * */
  //    public static MaplePacket getHiredMerchantMaintenance(MapleHiredMerchant merchant) {
  //  MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
  //  mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
  //  mplew.write(HexTool.getByteArrayFromHexString("0A 02 0D"));
  //  return mplew.getPacket();
  // }
  //    /*
  //     * @author Rob/Xcheater3161
  //     * */
  //    public static MaplePacket getHiredMerchant(MapleClient c, HiredMerchant merchant, boolean owner, int status) {
  //  //F5 00 05 05 04 00 00 71 C0 4C 00 0E 00 48 69 72 65 64 20 4D 65 72 63 68 61 6E 74 FF 00 00 0C 00 4D 79 44 65 78 49 73 42 72 6F 6B 65 32 92 0D 7E 01 00 00 00 00 00 03 00 31 32 33 10 00 00 00 00 00
  //        //F5 00 05 05 04 00 00 71 C0 4C 00 0E 00 48 69 72 65 64 20 4D 65 72 63 68 61 6E 74 FF 00 00 0C 00 4D 79 44 65 78 49 73 42 72 6F 6B 65 10 16 00 00 00 00 00 00 00 00 03 00 31 32 33 10 00 00 00 01 01 01 00 01 00 01 00 00 00 01 00 76 16 00 00 00 80 05 BB 46 E6 17 02 05 01 00 00 00 00 00 00 00 00 00 00 00 00 0C 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 6E 1B 00 00 4C 00 00 00
  //        //F5 00 05 05 04 00 00 71 C0 4C 00 0E 00 48 69 72 65 64 20 4D 65 72 63 68 61 6E 74 FF 00 00 0C 00 4D 79 44 65 78 49 73 42 72 6F 6B 65 E3 13 00 00 00 00 00 00 00 00 03 00 31 32 33 10 00 00 00 00 03 3A 00 01 00 64 00 00 00 02 71 20 3D 00 00 00 80 05 BB 46 E6 17 02 3A 00 00 00 00 00 02 00 01 00 64 00 00 00 02 73 DA 1E 00 00 00 80 05 BB 46 E6 17 02 02 00 00 00 00 00 10 00 01 00 7B 00 00 00 02 10 0A 3D 00 00 00 80
  //        //F5 00 05 05 04 00 00 71 C0 4C 00 0E 00 48 69 72 65 64 20 4D 65 72 63 68 61 6E 74 FF 00 00 0B 00 64 75 73 74 72 65 6D 6F 76 65 72 E3 13 00 00 00 00 00 00 00 00 0A 00 46 72 65 65 20 53 74 75 66 66 10 00 00 00 00 03 3A 00 01 00 64 00 00 00 02 71 20 3D 00 00 00 80 05 BB 46 E6 17 02 3A 00 00 00 00 00 02 00 01 00 64 00 00 00 02 73 DA 1E 00 00 00 80 05 BB 46 E6 17 02 02 00 00 00 00 00 10 00 01 00 7B 00 00 00 02 10 0A 3D 00 00 00 80
  //        //F5 00 05 05 04 01 00 7A C0 4C 00 0E 00 48 69 72 65 64 20 4D 65 72 63 68 61 6E 74 01 00 00 20 4E 00 00 00 93 76 00 00 01 2A 4A 0F 00 04 40 BF 0F 00 05 8D DE 0F 00 06 A5 2C 10 00 07 2B 5C 10 00 0B 00 76 16 00 FF FF 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0C 00 4D 79 44 65 78 49 73 42 72 6F 6B 65 FF 00 00 08 00 54 69 6E 6B 62 61 62 69 04 00 64 66 61 73 10 00 00 00 00 01 0A 00 01 00 FF E0 F5 05 02 12 30 3D 00 00 00 80 05 BB 46 E6 17 02 0A 00 00 00 00 00
  //        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
  //		mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
  //		mplew.write(HexTool.getByteArrayFromHexString("05 05 04"));
  //        mplew.writeShort(owner ? 0 : 1);
  //        mplew.writeInt(merchant.getItemId());
  //        mplew.writeMapleAsciiString("Hired Merchant");
  //        List<MapleHiredMerchantItem> items = merchant.getItems();
  //        if (owner) {
  //            mplew.write(0xFF);
  //            mplew.writeShort(0);
  //            mplew.writeMapleAsciiString(merchant.getOwnerName());
  //            mplew.writeInt(0);
  //            mplew.writeInt(status);
  //            mplew.writeShort(0);
  //            mplew.writeMapleAsciiString(merchant.getDescription());
  //            mplew.write(0x10);
  //            mplew.writeInt((int) merchant.getMesos());
  //            mplew.write(items.size());
  //            for (MapleHiredMerchantItem item : items) {
  //                mplew.writeShort(item.getBundles());
  //                mplew.writeShort(item.getItem().getQuantity());
  //                mplew.writeInt(item.getPrice());
  //                addItemInfo(mplew, item.getItem(), true, true);
  //            }
  //        } else {
  //            mplew.write(1);
  //            addCharLook(mplew, c.getPlayer(), false);
  //            mplew.writeMapleAsciiString(c.getPlayer().getName());
  //            MapleCharacter[] visitors = merchant.getVisitors();
  //            int i2 = 1;
  //            for (int i = 0; i < visitors.length; i++) {
  //                if (visitors[i] != null) {
  //                    if (visitors[i] != c.getPlayer()) {
  //                        mplew.write(i2 + 1);
  //                        addCharLook(mplew, visitors[i], false);
  //                        mplew.writeMapleAsciiString(visitors[i].getName());
  //                        c.getPlayer().setVisitorSlot((i2 + 1), visitors[i]);
  //                        i2++;
  //                    }
  //                }
  //            }
  //            mplew.write(0xFF);
  //            mplew.writeShort(0);
  //            mplew.writeMapleAsciiString(merchant.getOwnerName());
  //            mplew.writeMapleAsciiString(merchant.getDescription());
  //            mplew.write(0x10);
  //            mplew.writeInt(0);
  //            mplew.write(items.size());
  //            for (MapleHiredMerchantItem item : items) {
  //                mplew.writeShort(item.getBundles());
  //                mplew.writeShort(item.getItem().getQuantity());
  //                mplew.writeInt(item.getPrice());
  //                addItemInfo(mplew, item.getItem(), true, true);
  //            }
  //        }
  //		return mplew.getPacket();
  //	 }

  private static void getGuildInfo(
    MaplePacketLittleEndianWriter mplew,
    MapleGuild guild
  ) {
    mplew.writeInt(guild.getId());
    mplew.writeMapleAsciiString(guild.getName());
    for (int i = 1; i <= 5; i++) {
      mplew.writeMapleAsciiString(guild.getRankTitle(i));
    }
    Collection<MapleGuildCharacter> members = guild.getMembers();
    mplew.write(members.size());
    //then it is the size of all the members
    for (MapleGuildCharacter mgc : members) { //and each of their character ids o_O
      mplew.writeInt(mgc.getId());
    }
    for (MapleGuildCharacter mgc : members) {
      mplew.writeAsciiString(
        StringUtil.getRightPaddedStr(mgc.getName(), '\0', 13)
      );
      mplew.writeInt(mgc.getJobId());
      mplew.writeInt(mgc.getLevel());
      mplew.writeInt(mgc.getGuildRank());
      mplew.writeInt(mgc.isOnline() ? 1 : 0);
      mplew.writeInt(guild.getSignature());
      mplew.writeInt(mgc.getAllianceRank());
    }
    mplew.writeInt(guild.getCapacity());
    mplew.writeShort(guild.getLogoBG());
    mplew.write(guild.getLogoBGColor());
    mplew.writeShort(guild.getLogo());
    mplew.write(guild.getLogoColor());
    mplew.writeMapleAsciiString(guild.getNotice());
    mplew.writeInt(guild.getGP());
    mplew.writeInt(guild.getAllianceId());
  }

  public static MaplePacket getAllianceInfo(MapleAlliance alliance) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
    mplew.write(0x0C);
    mplew.write(1);
    mplew.writeInt(alliance.getId());
    mplew.writeMapleAsciiString(alliance.getName());
    for (int i = 1; i <= 5; i++) {
      mplew.writeMapleAsciiString(alliance.getRankTitle(i));
    }
    mplew.write(alliance.getGuilds().size());
    mplew.writeInt(2); // probably capacity
    for (Integer guild : alliance.getGuilds()) {
      mplew.writeInt(guild);
    }
    mplew.writeMapleAsciiString(alliance.getNotice());
    return mplew.getPacket();
  }

  public static MaplePacket makeNewAlliance(
    MapleAlliance alliance,
    MapleClient c
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
    mplew.write(0x0F);
    mplew.writeInt(alliance.getId());
    mplew.writeMapleAsciiString(alliance.getName());
    for (int i = 1; i <= 5; i++) {
      mplew.writeMapleAsciiString(alliance.getRankTitle(i));
    }
    mplew.write(alliance.getGuilds().size());
    for (Integer guild : alliance.getGuilds()) {
      mplew.writeInt(guild);
    }
    mplew.writeInt(2); // probably capacity
    mplew.writeShort(0);
    for (Integer guildd : alliance.getGuilds()) {
      try {
        getGuildInfo(
          mplew,
          c.getChannelServer().getWorldInterface().getGuild(guildd, null)
        );
      } catch (RemoteException re) {
        c.getChannelServer().reconnectWorld();
      }
    }
    return mplew.getPacket();
  }

  public static MaplePacket getGuildAlliances(
    MapleAlliance alliance,
    MapleClient c
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
    mplew.write(0x0D);
    mplew.writeInt(alliance.getGuilds().size());
    for (Integer guild : alliance.getGuilds()) {
      try {
        getGuildInfo(
          mplew,
          c.getChannelServer().getWorldInterface().getGuild(guild, null)
        );
      } catch (RemoteException re) {
        c.getChannelServer().reconnectWorld();
      }
    }
    return mplew.getPacket();
  }

  public static MaplePacket addGuildToAlliance(
    MapleAlliance alliance,
    int newGuild,
    MapleClient c
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
    mplew.write(0x12);
    mplew.writeInt(alliance.getId());
    mplew.writeMapleAsciiString(alliance.getName());
    for (int i = 1; i <= 5; i++) {
      mplew.writeMapleAsciiString(alliance.getRankTitle(i));
    }
    mplew.write(alliance.getGuilds().size());
    for (Integer guild : alliance.getGuilds()) {
      mplew.writeInt(guild);
    }
    mplew.writeInt(2);
    mplew.writeMapleAsciiString(alliance.getNotice());
    mplew.writeInt(newGuild);
    try {
      getGuildInfo(
        mplew,
        c.getChannelServer().getWorldInterface().getGuild(newGuild, null)
      );
    } catch (RemoteException re) {
      c.getChannelServer().reconnectWorld();
    }
    return mplew.getPacket();
  }

  public static MaplePacket allianceMemberOnline(
    MapleCharacter mc,
    boolean online
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
    mplew.write(0x0E);
    mplew.writeInt(mc.getGuild().getAllianceId());
    mplew.writeInt(mc.getGuildId());
    mplew.writeInt(mc.getId());
    mplew.write(online ? 1 : 0);

    return mplew.getPacket();
  }

  public static MaplePacket allianceNotice(int id, String notice) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
    mplew.write(0x1C);
    mplew.writeInt(id);
    mplew.writeMapleAsciiString(notice);
    return mplew.getPacket();
  }

  public static MaplePacket changeAllianceRankTitle(
    int alliance,
    String[] ranks
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
    mplew.write(0x1A);
    mplew.writeInt(alliance);
    for (int i = 0; i < 5; i++) {
      mplew.writeMapleAsciiString(ranks[i]);
    }
    return mplew.getPacket();
  }

  public static MaplePacket updateAllianceJobLevel(MapleCharacter mc) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
    mplew.write(0x18);
    mplew.writeInt(mc.getGuild().getAllianceId());
    mplew.writeInt(mc.getGuildId());
    mplew.writeInt(mc.getId());
    mplew.writeInt(mc.getLevel());
    mplew.writeInt(mc.getJob().getId());

    return mplew.getPacket();
  }

  public static MaplePacket removeGuildFromAlliance(
    MapleAlliance alliance,
    int expelledGuild,
    MapleClient c
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    // PLEASE NOTE THAT WE MUST REMOVE THE GUILD BEFORE SENDING THIS PACKET. <3
    // ALSO ANOTHER NOTE, WE MUST REMOVE ALLIANCEID FROM GUILD BEFORE SENDING ASWELL <3
    mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
    mplew.write(0x10);
    mplew.writeInt(alliance.getId());
    mplew.writeMapleAsciiString(alliance.getName());
    for (int i = 1; i <= 5; i++) {
      mplew.writeMapleAsciiString(alliance.getRankTitle(i));
    }
    mplew.write(alliance.getGuilds().size());
    for (Integer guild : alliance.getGuilds()) {
      mplew.writeInt(guild);
    }
    mplew.write(HexTool.getByteArrayFromHexString("02 00 00 00"));
    mplew.writeMapleAsciiString(alliance.getNotice());
    mplew.writeInt(expelledGuild);
    try {
      getGuildInfo(
        mplew,
        c.getChannelServer().getWorldInterface().getGuild(expelledGuild, null)
      );
    } catch (RemoteException re) {
      c.getChannelServer().reconnectWorld();
    }
    mplew.write(0x01);
    return mplew.getPacket();
  }

  public static MaplePacket disbandAlliance(int alliance) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
    mplew.write(0x1D);
    mplew.writeInt(alliance);

    return mplew.getPacket();
  }

  public static MaplePacket sendInteractionBox(MapleCharacter c) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_BOX.getValue());
    mplew.writeInt(c.getId());
    addAnnounceBox(mplew, c.getInteraction());
    return mplew.getPacket();
  }

  public static MaplePacket hiredMerchantBox() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(0x2F); // header.
    mplew.write(0x07);
    return mplew.getPacket();
  }

  public static MaplePacket getInteraction(
    MapleCharacter chr,
    boolean firstTime
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue()); // header.

    IPlayerInteractionManager ips = chr.getInteraction();
    int type = ips.getShopType();
    if (type == 1) {
      mplew.write(HexTool.getByteArrayFromHexString("05 05 04"));
    } else if (type == 2) {
      mplew.write(HexTool.getByteArrayFromHexString("05 04 04"));
    } else if (type == 3) {
      mplew.write(HexTool.getByteArrayFromHexString("05 02 02"));
    } else if (type == 4) {
      mplew.write(HexTool.getByteArrayFromHexString("05 01 02"));
    }

    mplew.write(ips.isOwner(chr) ? 0 : 1);
    mplew.write(0);
    if (type == 2 || type == 3 || type == 4) {
      PacketHelper.addCharLook(
        mplew,
        ((MaplePlayerShop) ips).getMCOwner(),
        false
      );
      mplew.writeMapleAsciiString(ips.getOwnerName());
    } else {
      mplew.writeInt(((HiredMerchant) ips).getItemId());
      mplew.writeMapleAsciiString(Configuration.Server_Name + " Merchant");
    }
    for (int i = 0; i < 3; i++) {
      if (ips.getVisitors()[i] != null) {
        mplew.write(i + 1);
        PacketHelper.addCharLook(mplew, ips.getVisitors()[i], false);
        mplew.writeMapleAsciiString(ips.getVisitors()[i].getName());
      }
    }
    mplew.write(0xFF);
    if (type == 1) {
      mplew.writeShort(0);
      mplew.writeMapleAsciiString(ips.getOwnerName());
      if (ips.isOwner(chr)) {
        mplew.writeInt(Integer.MAX_VALUE); // contains timing, suck my dick we dont need this
        mplew.write(firstTime ? 1 : 0);
        mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 00"));
      }
    } else if (type == 3 || type == 4) {
      MapleMiniGame minigame = (MapleMiniGame) ips;
      mplew.write(0);
      if (type == 4) {
        mplew.writeInt(1);
      } else {
        mplew.writeInt(2);
      }
      mplew.writeInt(minigame.getOmokPoints("wins", true));
      mplew.writeInt(minigame.getOmokPoints("ties", true));
      mplew.writeInt(minigame.getOmokPoints("losses", true));
      mplew.writeInt(2000);
      if (ips.getVisitors()[0] != null) {
        mplew.write(1);
        if (type == 4) {
          mplew.writeInt(1);
        } else {
          mplew.writeInt(2);
        }
        mplew.writeInt(minigame.getOmokPoints("wins", false));
        mplew.writeInt(minigame.getOmokPoints("ties", false));
        mplew.writeInt(minigame.getOmokPoints("losses", false));
        mplew.writeInt(2000);
      }
      mplew.write(0xFF);
    }
    mplew.writeMapleAsciiString(ips.getDescription());
    if (type == 3) {
      mplew.write(ips.getItemType());
      mplew.write(0);
    } else {
      mplew.write(0x10);
      if (type == 1) {
        mplew.writeInt(0);
      }
      mplew.write(ips.getItems().size());
      if (ips.getItems().size() == 0) {
        if (type == 1) {
          mplew.write(0);
        } else {
          mplew.writeInt(0);
        }
      } else {
        for (MaplePlayerShopItem item : ips.getItems()) {
          mplew.writeShort(item.getBundles());
          mplew.writeShort(item.getItem().getQuantity());
          mplew.writeInt(item.getPrice());
          PacketHelper.addItemInfo(mplew, item.getItem(), true, true);
        }
      }
    }
    return mplew.getPacket();
  }

  public static MaplePacket shopChat(String message, int slot) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(HexTool.getByteArrayFromHexString("06 08"));
    mplew.write(slot);
    mplew.writeMapleAsciiString(message);
    return mplew.getPacket();
  }

  public static MaplePacket shopErrorMessage(int error, int type) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(0x0A);
    mplew.write(type);
    mplew.write(error);
    return mplew.getPacket();
  }

  private static void addRingInfo(
    MaplePacketLittleEndianWriter mplew,
    MapleCharacter chr
  ) {
    mplew.writeShort(0); // start of rings
    addCrushRings(mplew, chr);
    addFriendshipRings(mplew, chr);
    addMarriageRings(mplew, chr);
  }

  private static void addCrushRings(
    MaplePacketLittleEndianWriter mplew,
    MapleCharacter chr
  ) {
    mplew.writeShort(chr.getCrushRings().size());
    for (MapleRing ring : chr.getCrushRings()) {
      mplew.writeInt(ring.getPartnerChrId());
      mplew.writeAsciiString(
        getRightPaddedStr(ring.getPartnerName(), '\0', 13)
      );
      mplew.writeInt(ring.getRingId());
      mplew.writeInt(0);
      mplew.writeInt(ring.getPartnerRingId());
      mplew.writeInt(0);
    }
  }

  private static void addFriendshipRings(
    MaplePacketLittleEndianWriter mplew,
    MapleCharacter chr
  ) {
    mplew.writeShort(chr.getFriendshipRings().size());
    for (MapleRing ring : chr.getFriendshipRings()) {
      mplew.writeInt(ring.getPartnerChrId());
      mplew.writeAsciiString(
        getRightPaddedStr(ring.getPartnerName(), '\0', 13)
      );
      mplew.writeInt(ring.getRingId());
      mplew.writeInt(0);
      mplew.writeInt(ring.getPartnerRingId());
      mplew.writeInt(0);
      mplew.writeInt(ring.getItemId());
    }
  }

  private static void addMarriageRings(
    MaplePacketLittleEndianWriter mplew,
    MapleCharacter chr
  ) {
    mplew.writeShort(chr.getMarriageRings().size());
    int marriageId = 30000;
    for (MapleRing ring : chr.getMarriageRings()) {
      mplew.writeInt(marriageId);
      mplew.writeInt(chr.getId());
      mplew.writeInt(ring.getPartnerChrId());
      mplew.writeShort(3);
      mplew.writeInt(ring.getRingId());
      mplew.writeInt(ring.getPartnerRingId());
      mplew.writeAsciiString(getRightPaddedStr(chr.getName(), '\0', 13));
      mplew.writeAsciiString(
        getRightPaddedStr(ring.getPartnerName(), '\0', 13)
      );
      marriageId++;
    }
  }

  private static String getRightPaddedStr(String in, char padchar, int length) {
    StringBuilder builder = new StringBuilder(in);
    for (int x = in.length(); x < length; x++) {
      builder.append(padchar);
    }
    return builder.toString();
  }

  public static MaplePacket spawnHiredMerchant(HiredMerchant hm) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.SPAWN_HIRED_MERCHANT.getValue());
    mplew.writeInt(hm.getOwnerId());
    mplew.writeInt(hm.getItemId());
    mplew.writeShort((short) hm.getPosition().getX());
    mplew.writeShort((short) hm.getPosition().getY());
    mplew.writeShort(0);
    mplew.writeMapleAsciiString(hm.getOwnerName());
    mplew.write(0x05);
    mplew.writeInt(hm.getObjectId());
    mplew.writeMapleAsciiString(hm.getDescription());
    mplew.write(hm.getItemType());
    mplew.write(HexTool.getByteArrayFromHexString("01 04"));
    return mplew.getPacket();
  }

  public static MaplePacket destroyHiredMerchant(int id) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.DESTROY_HIRED_MERCHANT.getValue());
    mplew.writeInt(id);
    return mplew.getPacket();
  }

  public static MaplePacket updateHiredMerchant(HiredMerchant shop) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.UPDATE_HIRED_MERCHANT.getValue());
    mplew.writeInt(shop.getOwnerId());
    mplew.write(0x05);
    mplew.writeInt(shop.getObjectId());
    mplew.writeMapleAsciiString(shop.getDescription());
    mplew.write(shop.getItemType());
    mplew.write(shop.getFreeSlot() > -1 ? 3 : 2);
    mplew.write(0x04);
    return mplew.getPacket();
  }

  public static MaplePacket shopItemUpdate(IPlayerInteractionManager shop) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(0x17);
    if (shop.getShopType() == 1) {
      mplew.writeInt(0);
    }
    mplew.write(shop.getItems().size());
    for (MaplePlayerShopItem item : shop.getItems()) {
      mplew.writeShort(item.getBundles());
      mplew.writeShort(item.getItem().getQuantity());
      mplew.writeInt(item.getPrice());
      PacketHelper.addItemInfo(mplew, item.getItem(), true, true);
    }
    return mplew.getPacket();
  }

  public static MaplePacket shopVisitorAdd(MapleCharacter chr, int slot) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(0x04);
    mplew.write(slot);
    PacketHelper.addCharLook(mplew, chr, false);
    mplew.writeMapleAsciiString(chr.getName());
    if (
      chr.getInteraction().getShopType() == 4 ||
      chr.getInteraction().getShopType() == 3
    ) {
      MapleMiniGame game = (MapleMiniGame) chr.getInteraction();
      mplew.writeInt(1);
      mplew.writeInt(game.getOmokPoints("wins", false));
      mplew.writeInt(game.getOmokPoints("ties", false));
      mplew.writeInt(game.getOmokPoints("losses", false));
      mplew.writeInt(2000);
    }
    //  System.out.println(mplew);
    return mplew.getPacket();
  }

  public static MaplePacket shopVisitorLeave(int slot) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.write(0x0A);
    mplew.write(slot);
    return mplew.getPacket();
  }

  public static MaplePacket getMiniBoxFull() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
    mplew.writeShort(5);
    mplew.write(2);
    return mplew.getPacket();
  }

  public static MaplePacket refreshVIPRockMapList(
    List<Integer> maps,
    int type
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.TROCK_LOCATIONS.getValue());
    mplew.write(3);
    mplew.write(type);
    for (int map : maps) {
      mplew.writeInt(map);
    }
    for (int i = maps.size(); i <= 10; i++) {
      mplew.write(CHAR_INFO_MAGIC);
    }
    maps.clear();
    return mplew.getPacket();
  }

  public static MaplePacket removeItemFromDuey(boolean remove, int Package) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.DUEY.getValue());
    mplew.write(0x17);
    mplew.writeInt(Package);
    mplew.write(remove ? 3 : 4);
    return mplew.getPacket();
  }

  public static MaplePacket sendDueyMSG(byte operation) {
    return sendDuey(operation, null);
  }

  public static MaplePacket sendDuey(
    byte operation,
    List<DueyPackages> packages
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.DUEY.getValue());
    mplew.write(operation);
    if (operation == 8) {
      mplew.write(0);
      mplew.write(packages.size());
      for (DueyPackages dp : packages) {
        mplew.writeInt(dp.getPackageId());
        mplew.writeAsciiString(dp.getSender());
        for (int i = dp.getSender().length(); i < 13; i++) {
          mplew.write(0);
        }
        mplew.writeInt(dp.getMesos());
        mplew.writeLong(
          KoreanDateUtil.getQuestTimestamp(dp.sentTimeInMilliseconds())
        );
        mplew.writeLong(0); // Contains message o____o.
        for (int i = 0; i < 48; i++) {
          mplew.writeInt(new Random().nextInt(Integer.MAX_VALUE)); // Seed this with random digits to stop sniffers.
        }
        mplew.writeInt(0);
        mplew.write(0);
        if (dp.getItem() != null) {
          mplew.write(1);
          PacketHelper.addItemInfo(mplew, dp.getItem(), true, true);
        } else {
          mplew.write(0);
        }
      }
      mplew.write(0);
    }
    return mplew.getPacket();
  }

  public static MaplePacket testChat(String msg, int h, int a) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort((int) a);
    mplew.write(h);
    mplew.writeMapleAsciiString(msg);
    return mplew.getPacket();
  }

  //Mensagem amarela
  //LeaderMS <javascriptz@leaderms.com.br>
  public static MaplePacket sendYellowTip(String tip) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.YELLOW_TIP.getValue());
    mplew.write(0xFF);
    mplew.writeMapleAsciiString(tip);
    mplew.writeShort(0);
    return mplew.getPacket();
  }

  public static MaplePacket moveMonster(
    int useskill,
    int skill,
    int skill_1,
    int skill_2,
    int skill_3,
    int skill_4,
    int oid,
    Point startPos,
    List<LifeMovementFragment> moves
  ) {
    final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.MOVE_MONSTER.getValue());
    mplew.writeInt(oid);
    mplew.write(0);
    mplew.write(useskill);
    mplew.write(skill);
    mplew.write(skill_1);
    mplew.write(skill_2);
    mplew.write(skill_3);
    mplew.write(skill_4);
    mplew.writePos(startPos);
    PacketHelper.serializeMovementList(mplew, moves);
    return mplew.getPacket();
  }

  public static MaplePacket updateGender(MapleCharacter chr) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.GENDER.getValue());
    mplew.write(chr.getGender());
    return mplew.getPacket();
  }

  public static MaplePacket updateEquipSlot(IItem item) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
    mplew.write(0); // any number,
    mplew.write(HexTool.getByteArrayFromHexString("02 03 01"));
    mplew.writeShort(item.getPosition()); // set item into this slot
    mplew.write(0);
    mplew.write(item.getType()); // 1 show / 0 disapear ? o________o
    mplew.writeShort(item.getPosition()); // update this slot ?
    PacketHelper.addItemInfo(mplew, item, true, true);
    mplew.writeMapleAsciiString(Configuration.Server_Name);
    return mplew.getPacket();
  }

  public static MaplePacket sendGMPolice(
    int reason,
    String sReason,
    int duration
  ) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.GM_POLICE.getValue());
    mplew.writeInt(duration);
    mplew.write(4);
    mplew.write(reason);
    mplew.writeMapleAsciiString(sReason);
    return mplew.getPacket();
  }

  public static MaplePacket boatArrivePacket() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.BOAT_EFFECT.getValue());
    mplew.writeShort(1);
    return mplew.getPacket();
  }

  public static MaplePacket boatLeavePacket() {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.BOAT_EFFECT.getValue());
    mplew.writeShort(2);
    return mplew.getPacket();
  }

  public static MaplePacket trainPacket(boolean type) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.TRAIN_EFFECT.getValue());
    mplew.writeShort(type ? 1 : 2);

    return mplew.getPacket();
  }

  public static MaplePacket boatPacketFree(byte first, byte second) {
    final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
    mplew.writeShort(SendPacketOpcode.BOAT_EFFECT.getValue());
    mplew.write(first);
    mplew.write(second);
    return mplew.getPacket();
  }

  public static MaplePacket showBuffeffect(int cid, int skillid, int effectid) {
    return showBuffeffect(cid, skillid, effectid, (byte) 3);
  }

  public static MaplePacket beholderAnimation(int cid, int skillid) {
    MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

    mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
    mplew.writeInt(cid);
    mplew.writeInt(skillid);
    mplew.writeShort(135);

    return mplew.getPacket();
  }

  public static Object spawnPlayerNPC(MapleNPCStats stats, int id) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }
}
