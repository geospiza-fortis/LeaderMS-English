importPackage(Packages.server.maps);
importPackage(Packages.handling.channel);
importPackage(Packages.tools.packet);

function enter(pi) {
  var eim = pi.getPlayer().getEventInstance();
  var party = pi.getPlayer().getEventInstance().getPlayers();
  var realParty = pi.getParty();
  var playerStatus = pi.isLeader();
  if (playerStatus) {
    //Leader
    if (eim.getProperty("6stageclear") == null) {
      pi.warp(920010500, 0); //Sealed Room
      return true;
    } else {
      pi.getPlayer()
        .getClient()
        .getSession()
        .write(
          MaplePacketCreator.serverNotice(
            5,
            "You may not go back in this room."
          )
        );
      return false;
    }
  } else {
    //Not leader
    if (party.get(0).getMapId() == 920010500) {
      //Check what map the leader is in
      pi.warp(920010500, 0); //Sealed Room
      return true;
    } else {
      pi.getPlayer()
        .getClient()
        .getSession()
        .write(
          MaplePacketCreator.serverNotice(
            5,
            "You may not go in this room if your leader is not in it."
          )
        );
      return false;
    }
  }
}
