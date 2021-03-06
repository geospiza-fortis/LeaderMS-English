/*
 * @autor Java
 * LeaderMS MapleStory Private Server
 * APQ
 */

importPackage(Packages.server.maps);

var status = 0;

function start() {
  action(1, 0, 0);
}

function action(mode, type, selection) {
  if (status == 0) {
    cm.sendYesNo(
      "                          #e<" +
        cm.getServerName() +
        " AriantPQ>#n\r\n\r\nWould you like to go to the #bAriant Coliseum#k?\r\nYou should be level #e20-30#n to participate."
    );
    status++;
  } else {
    if (
      (status == 1 && type == 1 && selection == -1 && mode == 0) ||
      mode == -1
    ) {
      cm.dispose();
    } else {
      if (status == 1) {
        if (
          (cm.getChar().getLevel() >= 20 && cm.getChar().getLevel() < 31) ||
          cm.getChar().isGM()
        ) {
          cm.getPlayer().saveLocation(SavedLocationType.ARIANT_PQ);
          cm.warp(980010000, 3);
          cm.dispose();
        } else {
          cm.sendOk(
            "You are not between level 20 and 30. Sorry, you cannot participate."
          );
          cm.dispose();
        }
      }
    }
  }
}
