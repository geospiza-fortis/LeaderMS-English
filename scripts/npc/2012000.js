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

/**
-- Odin JavaScript --------------------------------------------------------------------------------
	Agatha - Orbis Ticketing Booth(200000100)
-- By ---------------------------------------------------------------------------------------------
	Information
-- Version Info -----------------------------------------------------------------------------------
	1.2 - Price as GMS [sadiq]
	1.1 - Text fix [Information]
	1.0 - First Version by Information
---------------------------------------------------------------------------------------------------
**/

var ticket = new Array(4031047, 4031074, 4031331, 4031576);
var cost = new Array(5000, 6000, 30000, 6000);
var tmsg = new Array(15, 10, 10, 10);
var mapNames = new Array(
  "Ellinia of Victoria Island",
  "Ludibrium",
  "Leafre",
  "Ariant"
);
var mapName2 = new Array(
  "Ellinia of Victoria Island",
  "Ludibrium",
  "Leafre of Minar Forest",
  "Nihal Desert"
);
var select;

function start() {
  status = -1;
  action(1, 0, 0);
}

function action(mode, type, selection) {
  if (mode == -1) {
    cm.dispose();
  } else {
    if (mode == 0 && status == 0) {
      cm.dispose();
      return;
    }
    if (mode == 0) {
      cm.sendNext("You must have some business to take care of here, right?");
      cm.dispose();
      return;
    }
    if (mode == 1) {
      status++;
    }
    if (status == 0) {
      var where =
        "Hello, I'm in charge of selling tickets for the ship rides at Orbis station. Which ticket would you like to purchase?";
      for (var i = 0; i < ticket.length; i++) {
        where += "\r\n#L" + i + "##b" + mapNames[i] + "#k#l";
      }
      cm.sendSimple(where);
    } else if (status == 1) {
      select = selection;
      cm.sendYesNo(
        "The ride to " +
          mapName2[select] +
          " takes off every " +
          tmsg[select] +
          " minutes, beginning on the hour, and it'll cost you #b" +
          cost[select] +
          " mesos#k. Are you sure you want to purchase #b#t" +
          ticket[select] +
          "##k?"
      );
    } else if (status == 2) {
      if (cm.getMeso() < cost[select] || !cm.canHold(ticket[select])) {
        cm.sendOk(
          "Are you sure you have #b" +
            cost[select] +
            " mesos#k? If so, then I urge you to check you etc. inventory, and see if it's full or not."
        );
        cm.dispose();
      } else {
        cm.gainMeso(-cost[select]);
        cm.gainItem(ticket[select], 1);
        cm.dispose();
      }
    }
  }
}
