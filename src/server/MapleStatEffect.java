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

/*
 * ItemEndEffect.java
 *
 * Created on 29. November 2007, 01:34
 */

package server;

import client.IItem;
import client.ISkill;
import client.MapleBuffStat;
import client.MapleCharacter;
import client.MapleDisease;
import client.MapleJob;
import client.MapleStat;
import client.SkillFactory;
import client.inventory.MapleInventory;
import client.inventory.MapleInventoryType;
import client.inventory.MapleMount;
import client.status.MonsterStatus;
import client.status.MonsterStatusEffect;
import config.configuration.Configuration;
import config.skills.SuperGM;
import handling.MaplePacket;
import handling.channel.ChannelServer;
import handling.world.PlayerCoolDownValueHolder;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import provider.MapleData;
import provider.MapleDataTool;
import server.life.MapleMonster;
import server.maps.MapleDoor;
import server.maps.MapleMap;
import server.maps.MapleMapObject;
import server.maps.MapleMapObjectType;
import server.maps.MapleMist;
import server.maps.MapleSummon;
import server.maps.SummonMovementType;
import tools.ArrayMap;
import tools.Pair;
import tools.packet.MaplePacketCreator;

/**
 * @author Matze
 * @author Frz
 */

public class MapleStatEffect implements Serializable {

  static final long serialVersionUID = 9179541993413738569L;
  private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(
    MapleStatEffect.class
  );
  private short watk, matk, wdef, mdef, acc, avoid, hands, speed, jump;
  private short hp, mp;
  private double hpR, mpR;
  private short mpCon, hpCon;
  private int duration;
  private boolean overTime;
  private int sourceid;
  private int moveTo;
  private boolean skill;
  private List<Pair<MapleBuffStat, Integer>> statups;
  private Map<MonsterStatus, Integer> monsterStatus;
  private int x, y, z;
  private double prop;
  private int itemCon, itemConNo;
  private int damage, attackCount, bulletCount, bulletConsume;
  private Point lt, rb;
  private int mobCount;
  private int moneyCon;
  private int cooldown;
  private boolean isMorph = false;
  private int morphId = 0;
  private List<MapleDisease> cureDebuffs;
  private int mastery, range, fixDamage;

  public MapleStatEffect() {}

  public static MapleStatEffect loadSkillEffectFromData(
    MapleData source,
    int skillid,
    boolean overtime
  ) {
    return loadFromData(source, skillid, true, overtime);
  }

  public static MapleStatEffect loadItemEffectFromData(
    MapleData source,
    int itemid
  ) {
    return loadFromData(source, itemid, false, false);
  }

  private static void addBuffStatPairToListIfNotZero(
    List<Pair<MapleBuffStat, Integer>> list,
    MapleBuffStat buffstat,
    Integer val
  ) {
    if (val.intValue() != 0) {
      list.add(new Pair<MapleBuffStat, Integer>(buffstat, val));
    }
  }

  private static MapleStatEffect loadFromData(
    MapleData source,
    int sourceid,
    boolean skill,
    boolean overTime
  ) {
    MapleStatEffect ret = new MapleStatEffect();
    ret.duration = MapleDataTool.getIntConvert("time", source, -1);
    ret.hp = (short) MapleDataTool.getInt("hp", source, 0);
    ret.hpR = MapleDataTool.getInt("hpR", source, 0) / 100.0;
    ret.mp = (short) MapleDataTool.getInt("mp", source, 0);
    ret.mpR = MapleDataTool.getInt("mpR", source, 0) / 100.0;
    ret.mpCon = (short) MapleDataTool.getInt("mpCon", source, 0);
    ret.hpCon = (short) MapleDataTool.getInt("hpCon", source, 0);
    int iprop = MapleDataTool.getInt("prop", source, 100);
    ret.prop = iprop / 100.0;
    ret.mobCount = MapleDataTool.getInt("mobCount", source, 1);
    ret.cooldown = MapleDataTool.getInt("cooltime", source, 0);
    ret.morphId = MapleDataTool.getInt("morph", source, 0);
    ret.fixDamage = MapleDataTool.getInt("fixdamage", source, -1);
    ret.isMorph = ret.morphId > 0 ? true : false;

    ret.sourceid = sourceid;
    ret.skill = skill;

    if (!ret.skill && ret.duration > -1) {
      ret.overTime = true;
    } else {
      ret.duration *= 1000; // items have their times stored in ms, of course
      ret.overTime = overTime;
    }
    ArrayList<Pair<MapleBuffStat, Integer>> statups = new ArrayList<Pair<MapleBuffStat, Integer>>();

    ret.watk = (short) MapleDataTool.getInt("pad", source, 0);
    ret.wdef = (short) MapleDataTool.getInt("pdd", source, 0);
    ret.matk = (short) MapleDataTool.getInt("mad", source, 0);
    ret.mdef = (short) MapleDataTool.getInt("mdd", source, 0);
    ret.acc = (short) MapleDataTool.getIntConvert("acc", source, 0);
    ret.avoid = (short) MapleDataTool.getInt("eva", source, 0);
    ret.speed = (short) MapleDataTool.getInt("speed", source, 0);
    ret.jump = (short) MapleDataTool.getInt("jump", source, 0);
    if (ret.overTime && ret.getSummonMovementType() == null) {
      addBuffStatPairToListIfNotZero(
        statups,
        MapleBuffStat.WATK,
        Integer.valueOf(ret.watk)
      );
      addBuffStatPairToListIfNotZero(
        statups,
        MapleBuffStat.WDEF,
        Integer.valueOf(ret.wdef)
      );
      addBuffStatPairToListIfNotZero(
        statups,
        MapleBuffStat.MATK,
        Integer.valueOf(ret.matk)
      );
      addBuffStatPairToListIfNotZero(
        statups,
        MapleBuffStat.MDEF,
        Integer.valueOf(ret.mdef)
      );
      addBuffStatPairToListIfNotZero(
        statups,
        MapleBuffStat.ACC,
        Integer.valueOf(ret.acc)
      );
      addBuffStatPairToListIfNotZero(
        statups,
        MapleBuffStat.AVOID,
        Integer.valueOf(ret.avoid)
      );
      addBuffStatPairToListIfNotZero(
        statups,
        MapleBuffStat.SPEED,
        Integer.valueOf(ret.speed)
      );
      addBuffStatPairToListIfNotZero(
        statups,
        MapleBuffStat.JUMP,
        Integer.valueOf(ret.jump)
      );
    }

    MapleData ltd = source.getChildByPath("lt");
    if (ltd != null) {
      ret.lt = (Point) ltd.getData();
      ret.rb = (Point) source.getChildByPath("rb").getData();
    }

    int x = MapleDataTool.getInt("x", source, 0);
    ret.x = x;
    ret.y = MapleDataTool.getInt("y", source, 0);
    ret.z = MapleDataTool.getInt("z", source, 0);
    ret.damage = MapleDataTool.getIntConvert("damage", source, 100);
    ret.attackCount = MapleDataTool.getIntConvert("attackCount", source, 1);
    ret.bulletCount = MapleDataTool.getIntConvert("bulletCount", source, 1);
    ret.bulletConsume = MapleDataTool.getIntConvert("bulletConsume", source, 0);
    ret.moneyCon = MapleDataTool.getIntConvert("moneyCon", source, 0);
    ret.mastery = MapleDataTool.getIntConvert("mastery", source, 0);
    ret.range = MapleDataTool.getIntConvert("range", source, 0);

    ret.itemCon = MapleDataTool.getInt("itemCon", source, 0);
    ret.itemConNo = MapleDataTool.getInt("itemConNo", source, 0);
    ret.moveTo = MapleDataTool.getInt("moveTo", source, -1);

    List<MapleDisease> localCureDebuffs = new ArrayList<MapleDisease>();
    if (MapleDataTool.getInt("poison", source, 0) > 0) {
      localCureDebuffs.add(MapleDisease.POISON);
    }
    if (MapleDataTool.getInt("seal", source, 0) > 0) {
      localCureDebuffs.add(MapleDisease.SEAL);
    }
    if (MapleDataTool.getInt("darkness", source, 0) > 0) {
      localCureDebuffs.add(MapleDisease.DARKNESS);
    }
    if (MapleDataTool.getInt("weakness", source, 0) > 0) {
      localCureDebuffs.add(MapleDisease.WEAKEN);
    }
    if (MapleDataTool.getInt("curse", source, 0) > 0) {
      localCureDebuffs.add(MapleDisease.CURSE);
    }
    ret.cureDebuffs = localCureDebuffs;

    Map<MonsterStatus, Integer> monsterStatus = new ArrayMap<MonsterStatus, Integer>();

    if (skill) { // hack because we can't get from the datafile...
      switch (sourceid) {
        case 2001002: // magic guard
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.MAGIC_GUARD,
              Integer.valueOf(x)
            )
          );
          break;
        case 2301003: // invincible
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.INVINCIBLE,
              Integer.valueOf(x)
            )
          );
          break;
        case 9101004: // hide
          ret.duration = 2100000000;
          ret.overTime = true;
        case 4001003: // darksight
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.DARKSIGHT,
              Integer.valueOf(x)
            )
          );
          break;
        case 4211003: // pickpocket
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.PICKPOCKET,
              Integer.valueOf(x)
            )
          );
          break;
        case 4211005: // mesoguard
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.MESOGUARD,
              Integer.valueOf(x)
            )
          );
          break;
        case 4111001: // mesoup
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.MESOUP,
              Integer.valueOf(x)
            )
          );
          break;
        case 4111002: // shadowpartner
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.SHADOWPARTNER,
              Integer.valueOf(x)
            )
          );
          break;
        case 3101004: // soul arrow
        case 3201004:
        case 2311002: // mystic door - hacked buff icon
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.SOULARROW,
              Integer.valueOf(x)
            )
          );
          break;
        case 1211003:
        case 1211004:
        case 1211005:
        case 1211006: // wk charges
        case 1211007:
        case 1211008:
        case 1221003:
        case 1221004:
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.WK_CHARGE,
              Integer.valueOf(x)
            )
          );
          break;
        case 1101004:
        case 1101005: // booster
        case 1201004:
        case 1201005:
        case 1301004:
        case 1301005:
        case 2111005: // spell booster, do these work the same?
        case 2211005:
        case 3101002:
        case 3201002:
        case 4101003:
        case 4201002:
        case 5101006:
        case 5201003:
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.BOOSTER,
              Integer.valueOf(x)
            )
          );
          break;
        //case 5121009:
        //	statups.add(new Pair<MapleBuffStat, Integer>(MapleBuffStat.SPEED_INFUSION, ret.x));
        //	break;
        case 5121009:
        case 5221010:
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.SPEED_INFUSION,
              Integer.valueOf(-4)
            )
          );
          break;
        case 1101006: // rage
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.WDEF,
              Integer.valueOf(ret.wdef)
            )
          );
        case 1121010: // enrage
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.WATK,
              Integer.valueOf(ret.watk)
            )
          );
          break;
        case 1301006: // iron will
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.MDEF,
              Integer.valueOf(ret.mdef)
            )
          );
        case 1001003: // iron body
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.WDEF,
              Integer.valueOf(ret.wdef)
            )
          );
          break;
        case 2001003: // magic armor
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.WDEF,
              Integer.valueOf(ret.wdef)
            )
          );
          break;
        case 2101001: // meditation
        case 2201001: // meditation
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.MATK,
              Integer.valueOf(ret.matk)
            )
          );
          break;
        case 4101004: // haste
        case 4201003: // haste
        case 9101001: // gm haste
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.SPEED,
              Integer.valueOf(ret.speed)
            )
          );
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.JUMP,
              Integer.valueOf(ret.jump)
            )
          );
          break;
        case 2301004: // bless
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.WDEF,
              Integer.valueOf(ret.wdef)
            )
          );
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.MDEF,
              Integer.valueOf(ret.mdef)
            )
          );
        case 3001003: // focus
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.ACC,
              Integer.valueOf(ret.acc)
            )
          );
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.AVOID,
              Integer.valueOf(ret.avoid)
            )
          );
          break;
        case 9101003: // gm bless
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.MATK,
              Integer.valueOf(ret.matk)
            )
          );
        case 3121008: // concentrate
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.WATK,
              Integer.valueOf(ret.watk)
            )
          );
          break;
        case 5001005: // Dash
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.DASH,
              Integer.valueOf(1)
            )
          );
          break;
        case 1101007: // pguard
        case 1201007:
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.POWERGUARD,
              Integer.valueOf(x)
            )
          );
          break;
        case 1301007:
        case 9101008:
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.HYPERBODYHP,
              Integer.valueOf(x)
            )
          );
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.HYPERBODYMP,
              Integer.valueOf(ret.y)
            )
          );
          break;
        case 1001: // recovery
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.RECOVERY,
              Integer.valueOf(x)
            )
          );
          break;
        case 1111002: // combo
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.COMBO,
              Integer.valueOf(1)
            )
          );
          break;
        case 1004: // monster riding
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.MONSTER_RIDING,
              Integer.valueOf(1)
            )
          );
          break;
        case 5221006: // 4th Job - Pirate riding
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.MONSTER_RIDING,
              1932000
            )
          );
          break;
        case 1311006: //dragon roar
          ret.hpR = -x / 100.0;
          break;
        case 1311008: // dragon blood
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.DRAGONBLOOD,
              Integer.valueOf(ret.x)
            )
          );
          break;
        case 1121000: // maple warrior, all classes
        case 1221000:
        case 1321000:
        case 2121000:
        case 2221000:
        case 2321000:
        case 3121000:
        case 3221000:
        case 4121000:
        case 4221000:
        case 5121000:
        case 5221000:
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.MAPLE_WARRIOR,
              Integer.valueOf(ret.x)
            )
          );
          break;
        case 3121002: // sharp eyes bow master
        case 3221002: // sharp eyes marksmen
          // hack much (TODO is the order correct?)
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.SHARP_EYES,
              Integer.valueOf(ret.x << 8 | ret.y)
            )
          );
          break;
        case 1321007: // Beholder
        case 2221005: // ifrit
        case 2311006: // summon dragon
        case 2321003: // bahamut
        case 3121006: // phoenix
        case 5211001: // Pirate octopus summon
        case 5211002: // Pirate bird summon
        case 5220002: // wrath of the octopi
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.SUMMON,
              Integer.valueOf(1)
            )
          );
          break;
        case 2311003: // hs
        case 9101002: // GM hs
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.HOLY_SYMBOL,
              Integer.valueOf(x)
            )
          );
          break;
        case 4121006: // spirit claw
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.SHADOW_CLAW,
              Integer.valueOf(0)
            )
          );
          break;
        case 2121004:
        case 2221004:
        case 2321004: // Infinity
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.INFINITY,
              Integer.valueOf(x)
            )
          );
          break;
        case 1121002:
        case 1221002:
        case 1321002: // Stance
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.STANCE,
              Integer.valueOf(iprop)
            )
          );
          break;
        case 1005: // Echo of Hero
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.ECHO_OF_HERO,
              Integer.valueOf(ret.x)
            )
          );
          break;
        case 2121002: // mana reflection
        case 2221002:
        case 2321002:
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.MANA_REFLECTION,
              Integer.valueOf(1)
            )
          );
          break;
        case 2321005: // holy shield
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.HOLY_SHIELD,
              Integer.valueOf(x)
            )
          );
          break;
        case 3111002: // puppet ranger
        case 3211002: // puppet sniper
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.PUPPET,
              Integer.valueOf(1)
            )
          );
          break;
        // ----------------------------- MONSTER STATUS PUT! ----------------------------- //
        case 4001002: // disorder
          monsterStatus.put(MonsterStatus.WATK, Integer.valueOf(ret.x));
          monsterStatus.put(MonsterStatus.WDEF, Integer.valueOf(ret.y));
          break;
        case 1201006: // threaten
          monsterStatus.put(MonsterStatus.WATK, Integer.valueOf(ret.x));
          monsterStatus.put(MonsterStatus.WDEF, Integer.valueOf(ret.y));
          break;
        case 1111005: // coma: sword
        case 1111006: // coma: axe
        case 1111008: // shout
        case 1211002: // charged blow
        case 3101005: // arrow bomb
        case 4211002: // assaulter
        case 4221007: // boomerang step
        case 5101002: // Backspin Blow
        case 5101003: // Double Uppercut
        case 5121004: // pirate 8 hit punches
        case 5121005: // pirate pull mob skill? O.o
        case 5121007: // pirate 6 hit shyt...
        case 5201004: // pirate blank shot
          monsterStatus.put(MonsterStatus.STUN, Integer.valueOf(1));
          break;
        //case 5201004: // pirate blank shot
        case 4121003:
        case 4221003:
          monsterStatus.put(MonsterStatus.SHOWDOWN, Integer.valueOf(1));
          break;
        case 2201004: // cold beam
        case 2211002: // ice strike
        case 2211006: // il elemental compo
        case 2221007: // Blizzard
        case 3211003: // blizzard
        case 5211005:
          monsterStatus.put(MonsterStatus.FREEZE, Integer.valueOf(1));
          ret.duration *= 2; // freezing skills are a little strange
          break;
        case 2121006: //Paralyze
        case 2101003: // fp slow
        case 2201003: // il slow
          monsterStatus.put(MonsterStatus.SPEED, Integer.valueOf(ret.x));
          break;
        case 2101005: // poison breath
        case 2111006: // fp elemental compo
          monsterStatus.put(MonsterStatus.POISON, Integer.valueOf(1));
          break;
        case 2311005:
          monsterStatus.put(MonsterStatus.DOOM, Integer.valueOf(1));
          break;
        case 3111005: // golden hawk
        case 3211005: // golden eagle
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.SUMMON,
              Integer.valueOf(1)
            )
          );
          monsterStatus.put(MonsterStatus.STUN, Integer.valueOf(1));
          break;
        case 2121005: // elquines
        case 3221005: // frostprey
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.SUMMON,
              Integer.valueOf(1)
            )
          );
          monsterStatus.put(MonsterStatus.FREEZE, Integer.valueOf(1));
          break;
        case 2111004: // fp seal
        case 2211004: // il seal
          monsterStatus.put(MonsterStatus.SEAL, 1);
          break;
        case 4111003: // shadow web
          monsterStatus.put(MonsterStatus.SHADOW_WEB, 1);
          break;
        case 3121007: // Hamstring
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.HAMSTRING,
              Integer.valueOf(x)
            )
          );
          monsterStatus.put(MonsterStatus.SPEED, x);
          break;
        case 3221006: // Blind
          statups.add(
            new Pair<MapleBuffStat, Integer>(
              MapleBuffStat.BLIND,
              Integer.valueOf(x)
            )
          );
          monsterStatus.put(MonsterStatus.ACC, x);
          break;
        default:
        // nothing needs to be added, that's ok
      }
    }

    if (ret.isMorph()) {
      statups.add(
        new Pair<MapleBuffStat, Integer>(
          MapleBuffStat.MORPH,
          Integer.valueOf(ret.getMorph())
        )
      );
    }

    ret.monsterStatus = monsterStatus;
    // TODO: fixDamage, coolTime

    statups.trimToSize();
    ret.statups = statups;

    return ret;
  }

  /**
   * @param applyto
   * @param obj
   * @param attack damage done by the skill
   */
  public void applyPassive(
    MapleCharacter applyto,
    MapleMapObject obj,
    int attack
  ) {
    if (makeChanceResult()) {
      switch (sourceid) {
        // MP eater
        case 2100000:
        case 2200000:
        case 2300000:
          if (
            obj == null || obj.getType() != MapleMapObjectType.MONSTER
          ) return;
          MapleMonster mob = (MapleMonster) obj;
          // x is absorb percentage
          if (!mob.isBoss()) {
            int absorbMp = Math.min(
              (int) (mob.getMaxMp() * (getX() / 100.0)),
              mob.getMp()
            );
            if (absorbMp > 0) {
              mob.setMp(mob.getMp() - absorbMp);
              applyto.addMP(absorbMp);
              applyto
                .getClient()
                .getSession()
                .write(MaplePacketCreator.showOwnBuffEffect(sourceid, 1));
              applyto
                .getMap()
                .broadcastMessage(
                  applyto,
                  MaplePacketCreator.showBuffeffect(
                    applyto.getId(),
                    sourceid,
                    1,
                    (byte) 3
                  ),
                  false
                );
            }
          }
          break;
      }
    }
  }

  public boolean applyTo(MapleCharacter chr) {
    return applyTo(chr, chr, true, null);
  }

  public boolean applyTo(MapleCharacter chr, boolean hide, boolean login) {
    if (isHide()) {
      chr.Hide(hide, login);
    }
    return applyTo(chr, chr, true, null);
  }

  public boolean applyTo(MapleCharacter chr, Point pos) {
    return applyTo(chr, chr, true, pos);
  }

  private boolean applyTo(
    MapleCharacter applyfrom,
    MapleCharacter applyto,
    boolean primary,
    Point pos
  ) {
    if (
      skill &&
      (this.sourceid == 4001003 || this.sourceid == 5101007) &&
      applyfrom.getMap().getDisableInvincibilitySkills() &&
      !applyfrom.isGM()
    ) {
      applyfrom
        .getClient()
        .getSession()
        .write(MaplePacketCreator.enableActions());
      applyfrom
        .getClient()
        .getSession()
        .write(
          MaplePacketCreator.serverNotice(
            5,
            "Invincibility skills are disabled in this map."
          )
        );
      return false;
    }

    int hpchange = calcHPChange(applyfrom, primary);
    int mpchange = calcMPChange(applyfrom, primary);

    if (primary) {
      if (itemConNo != 0) {
        MapleInventoryType type = MapleItemInformationProvider
          .getInstance()
          .getInventoryType(itemCon);
        MapleInventoryManipulator.removeById(
          applyto.getClient(),
          type,
          itemCon,
          itemConNo,
          false,
          true
        );
      }
    }
    if (cureDebuffs.size() > 0) {
      for (MapleDisease debuff : cureDebuffs) {
        applyfrom.dispelDebuff(debuff);
      }
    }
    List<Pair<MapleStat, Integer>> hpmpupdate = new ArrayList<>(2);
    if (!primary && isResurrection()) {
      hpchange = applyto.getMaxHp();
      applyto.setStance(0);
      applyto
        .getMap()
        .broadcastMessage(
          applyto,
          MaplePacketCreator.removePlayerFromMap(applyto.getId()),
          false
        );
      applyto
        .getMap()
        .broadcastMessage(
          applyto,
          MaplePacketCreator.spawnPlayerMapobject(applyto),
          false
        );
    }
    if (isDispel() && makeChanceResult()) {
      applyto.dispelDebuffs();
    } else if (isHeroWill()) {
      applyto.dispelSeduce();
    }
    if (hpchange != 0) {
      if (hpchange < 0 && (-hpchange) > applyto.getHp()) {
        return false;
      }
      int newHp = applyto.getHp() + hpchange;
      if (newHp < 1) {
        newHp = 1;
      }
      applyto.setHp(newHp);
      hpmpupdate.add(
        new Pair<MapleStat, Integer>(
          MapleStat.HP,
          Integer.valueOf(applyto.getHp())
        )
      );
    }
    if (mpchange != 0) {
      if (mpchange < 0 && (-mpchange) > applyto.getMp()) {
        return false;
      }
      applyto.setMp(applyto.getMp() + mpchange);
      hpmpupdate.add(
        new Pair<MapleStat, Integer>(
          MapleStat.MP,
          Integer.valueOf(applyto.getMp())
        )
      );
    }
    applyto
      .getClient()
      .getSession()
      .write(MaplePacketCreator.updatePlayerStats(hpmpupdate, true));
    if (moveTo != -1) {
      if (applyto.getMap().getReturnMapId() != applyto.getMapId()) {
        MapleMap target;
        if (moveTo == 999999999) {
          target = applyto.getMap().getReturnMap();
        } else {
          target =
            ChannelServer
              .getInstance(applyto.getClient().getChannel())
              .getMapFactory()
              .getMap(moveTo);
          if (
            target.getId() / 10000000 != 60 &&
            applyto.getMapId() / 10000000 != 61
          ) {
            if (
              target.getId() / 10000000 != 21 &&
              applyto.getMapId() / 10000000 != 20
            ) {
              if (target.getId() / 10000000 != applyto.getMapId() / 10000000) {
                log.info(
                  "Player {} is trying to use a return scroll to an illegal location ({}->{})",
                  new Object[] {
                    applyto.getName(),
                    applyto.getMapId(),
                    target.getId(),
                  }
                );
                applyto.getClient().disconnect();
                return false;
              }
            }
          }
        }
        applyto.changeMap(target, target.getPortal(0));
      } else {
        return false;
      }
    }
    if (isShadowClaw()) {
      int projectile = 0;
      MapleInventory use = applyto.getInventory(MapleInventoryType.USE);
      MapleItemInformationProvider mii = MapleItemInformationProvider.getInstance();
      for (int i = 0; i < 255; i++) { // impose order...
        IItem item = use.getItem((byte) i);
        if (item != null) {
          boolean isStar = mii.isThrowingStar(item.getItemId());
          if (isStar && item.getQuantity() >= 200) {
            projectile = item.getItemId();
            break;
          }
        }
      }
      if (projectile == 0) {
        return false;
      } else {
        MapleInventoryManipulator.removeById(
          applyto.getClient(),
          MapleInventoryType.USE,
          projectile,
          200,
          false,
          true
        );
      }
    }
    if (overTime) {
      applyBuffEffect(applyfrom, applyto, primary);
    }
    if (primary && (overTime || isHeal())) {
      applyBuff(applyfrom);
    }
    if (primary && isMonsterBuff()) {
      applyMonsterBuff(applyfrom);
    }

    final SummonMovementType summonMovementType = getSummonMovementType();
    if (summonMovementType != null && pos != null) {
      final MapleSummon tosummon = new MapleSummon(
        applyfrom,
        sourceid,
        pos,
        summonMovementType
      );
      if (!tosummon.isPuppet()) {
        applyfrom.getCheatTracker().resetSummonAttack();
      }
      applyfrom.getMap().spawnSummon(tosummon);
      applyfrom.getSummons().put(sourceid, tosummon);
      tosummon.addHP((short) x);
      if (isBeholder()) {
        tosummon.addHP((short) 1);
      }
    } else if (isMagicDoor()) { // Magic Door
      MapleDoor door = new MapleDoor(applyto, new Point(applyto.getPosition())); // Current Map door
      applyto.getMap().spawnDoor(door);
      applyto.addDoor(door);

      MapleDoor townDoor = new MapleDoor(door); // Town door
      applyto.addDoor(townDoor);
      door.getTown().spawnDoor(townDoor);

      if (applyto.getParty() != null) { // update town doors
        applyto.silentPartyUpdate();
      }
      applyto.disableDoor();
    } else if (isMist()) {
      final Rectangle bounds = calculateBoundingBox(
        pos != null ? pos : applyfrom.getPosition(),
        applyfrom.isFacingLeft()
      );
      final MapleMist mist = new MapleMist(bounds, applyfrom, this);
      applyfrom
        .getMap()
        .spawnMist(mist, getDuration(), sourceid == 2111003, false);
    } else if (isTimeLeap()) { // Time Leap
      for (PlayerCoolDownValueHolder i : applyto.getAllCooldowns()) {
        if (i.skillId != 5121010) {
          applyto.removeCooldown(i.skillId);
          applyto
            .getClient()
            .getSession()
            .write(MaplePacketCreator.skillCooldown(i.skillId, 0));
        }
      }
    }
    return true;
  }

  private void applyBuff(MapleCharacter applyfrom) {
    if (isPartyBuff() && (applyfrom.getParty() != null || isGmBuff())) {
      Rectangle bounds = calculateBoundingBox(
        applyfrom.getPosition(),
        applyfrom.isFacingLeft()
      );
      List<MapleMapObject> affecteds = applyfrom
        .getMap()
        .getMapObjectsInRect(bounds, Arrays.asList(MapleMapObjectType.PLAYER));
      List<MapleCharacter> affectedp = new ArrayList<>(affecteds.size());
      for (MapleMapObject affectedmo : affecteds) {
        MapleCharacter affected = (MapleCharacter) affectedmo;
        if (
          affected != applyfrom &&
          (isGmBuff() || applyfrom.getParty().equals(affected.getParty()))
        ) {
          if (
            (isResurrection() && !affected.isAlive()) ||
            (!isResurrection() && affected.isAlive())
          ) {
            affectedp.add(affected);
          }
          if (isTimeLeap()) {
            for (PlayerCoolDownValueHolder i : affected.getAllCooldowns()) {
              affected.removeCooldown(i.skillId);
            }
          }
        }
      }
      for (MapleCharacter affected : affectedp) {
        applyTo(applyfrom, affected, false, null);
        affected
          .getClient()
          .getSession()
          .write(MaplePacketCreator.showOwnBuffEffect(sourceid, 2));
        affected
          .getMap()
          .broadcastMessage(
            affected,
            MaplePacketCreator.showBuffeffect(
              affected.getId(),
              sourceid,
              2,
              (byte) 3
            ),
            false
          );
      }
    }
  }

  private void applyMonsterBuff(MapleCharacter applyfrom) {
    Rectangle bounds = calculateBoundingBox(
      applyfrom.getPosition(),
      applyfrom.isFacingLeft()
    );
    List<MapleMapObject> affected = applyfrom
      .getMap()
      .getMapObjectsInRect(bounds, Arrays.asList(MapleMapObjectType.MONSTER));
    ISkill skill_ = SkillFactory.getSkill(sourceid);
    int i = 0;
    for (MapleMapObject mo : affected) {
      MapleMonster monster = (MapleMonster) mo;
      if (makeChanceResult()) {
        monster.applyStatus(
          applyfrom,
          new MonsterStatusEffect(getMonsterStati(), skill_, false),
          isPoison(),
          getDuration()
        );
      }
      i++;
      if (i >= mobCount) {
        break;
      }
    }
  }

  private Rectangle calculateBoundingBox(Point posFrom, boolean facingLeft) {
    Point mylt;
    Point myrb;
    if (facingLeft) {
      mylt = new Point(lt.x + posFrom.x, lt.y + posFrom.y);
      myrb = new Point(rb.x + posFrom.x, rb.y + posFrom.y);
    } else {
      myrb = new Point(lt.x * -1 + posFrom.x, rb.y + posFrom.y);
      mylt = new Point(rb.x * -1 + posFrom.x, lt.y + posFrom.y);
    }
    Rectangle bounds = new Rectangle(
      mylt.x,
      mylt.y,
      myrb.x - mylt.x,
      myrb.y - mylt.y
    );
    return bounds;
  }

  public void silentApplyBuff(MapleCharacter chr, long starttime) {
    int localDuration = duration;
    localDuration = alchemistModifyVal(chr, localDuration, false);
    CancelEffectAction cancelAction = new CancelEffectAction(
      chr,
      this,
      starttime
    );
    ScheduledFuture<?> schedule = TimerManager
      .getInstance()
      .schedule(
        cancelAction,
        ((starttime + localDuration) - System.currentTimeMillis())
      );
    chr.registerEffect(this, starttime, schedule);
    SummonMovementType summonMovementType = getSummonMovementType();
    if (summonMovementType != null) {
      final MapleSummon tosummon = new MapleSummon(
        chr,
        sourceid,
        chr.getPosition(),
        summonMovementType
      );
      if (!tosummon.isPuppet()) {
        chr.getMap().spawnSummon(tosummon);
        chr.getCheatTracker().resetSummonAttack();
        chr.getSummons().put(sourceid, tosummon);
        tosummon.addHP(x);
      }
    }
  }

  private void applyBuffEffect(
    MapleCharacter applyfrom,
    MapleCharacter applyto,
    boolean primary
  ) {
    if (
      skill &&
      (this.sourceid == 4001003 || this.sourceid == 5101007) &&
      applyfrom.getMap().getDisableInvincibilitySkills() &&
      !applyfrom.isGM()
    ) {
      applyfrom
        .getClient()
        .getSession()
        .write(MaplePacketCreator.enableActions());
      applyfrom
        .getClient()
        .getSession()
        .write(
          MaplePacketCreator.serverNotice(
            5,
            "Invincibility skills are disabled in this map."
          )
        );
      return;
    }
    if (sourceid != 5221006) {
      if (!this.isMonsterRiding()) {
        applyto.cancelEffect(this, true, -1);
      }
    } else {
      applyto.cancelEffect(this, true, -1);
    }
    List<Pair<MapleBuffStat, Integer>> localstatups = statups;
    int localDuration = duration;
    int localsourceid = sourceid;
    int localX = x;
    int localY = y;
    int seconds = localDuration / 1000;
    MapleMount givemount = null;
    if (isMonsterRiding()) {
      int ridingLevel = 0; // mount id
      IItem mount = applyfrom
        .getInventory(MapleInventoryType.EQUIPPED)
        .getItem((byte) -18);
      if (mount != null) {
        ridingLevel = mount.getItemId();
      }
      if (sourceid == 5221006) {
        ridingLevel = 1932000;
        givemount = new MapleMount(applyto, ridingLevel, 5221006);
        givemount.setActive(false);
      } else {
        if (applyto.getMount() == null) {
          applyto.Mount(ridingLevel, sourceid);
        }
        givemount = applyto.getMount();
        givemount.startSchedule();
        givemount.setActive(true);
      }
      localDuration = sourceid;
      localsourceid = ridingLevel;
      localstatups =
        Collections.singletonList(
          new Pair<MapleBuffStat, Integer>(MapleBuffStat.MONSTER_RIDING, 0)
        );
    }
    if (isPirateMorph()) {
      localstatups = new ArrayList<Pair<MapleBuffStat, Integer>>();
      localstatups.add(
        new Pair<MapleBuffStat, Integer>(
          MapleBuffStat.SPEED,
          Integer.valueOf(40)
        )
      );
      localstatups.add(
        new Pair<MapleBuffStat, Integer>(
          MapleBuffStat.JUMP,
          Integer.valueOf(20)
        )
      );
      localstatups.add(
        new Pair<MapleBuffStat, Integer>(MapleBuffStat.MORPH, getMorph(applyto))
      );
    }
    if (primary) {
      localDuration = alchemistModifyVal(applyfrom, localDuration, false);
    }
    if (localstatups.size() > 0) {
      if (isDash()) {
        localstatups =
          Collections.singletonList(
            new Pair<MapleBuffStat, Integer>(MapleBuffStat.DASH, 1)
          );
        applyto
          .getClient()
          .getSession()
          .write(
            MaplePacketCreator.showDashP(localstatups, localX, localY, seconds)
          );
      } else if (isInfusion()) {
        applyto
          .getClient()
          .getSession()
          .write(MaplePacketCreator.giveInfusion(seconds, x));
      } else {
        applyto
          .getClient()
          .getSession()
          .write(
            MaplePacketCreator.giveBuff(
              (skill ? localsourceid : -localsourceid),
              localDuration,
              localstatups,
              isMorph(),
              isMonsterRiding(),
              givemount
            )
          );
      }
    } // else if (!this.isResurrection()) {
    //log.warn(applyto.getName() + " is applying an empty statup.");
    //}
    if (isDs()) {
      List<Pair<MapleBuffStat, Integer>> dsstat = Collections.singletonList(
        new Pair<MapleBuffStat, Integer>(MapleBuffStat.DARKSIGHT, 0)
      );
      applyto
        .getMap()
        .broadcastMessage(
          applyto,
          MaplePacketCreator.giveForeignBuff(applyto.getId(), dsstat, false),
          false
        );
    }
    if (isCombo()) {
      List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(
        new Pair<MapleBuffStat, Integer>(MapleBuffStat.COMBO, 1)
      );
      applyto
        .getMap()
        .broadcastMessage(
          applyto,
          MaplePacketCreator.giveForeignBuff(applyto.getId(), stat, false),
          false
        );
    } else if (isMonsterRiding()) {
      if (givemount.getItemId() != 0) {
        applyto
          .getMap()
          .broadcastMessage(
            applyto,
            MaplePacketCreator.showMonsterRiding(
              applyto.getId(),
              Collections.singletonList(
                new Pair<MapleBuffStat, Integer>(
                  MapleBuffStat.MONSTER_RIDING,
                  1
                )
              ),
              givemount
            ),
            false
          );
      }
      localDuration = duration;
    }
    if (isShadowPartner()) {
      List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(
        new Pair<MapleBuffStat, Integer>(MapleBuffStat.SHADOWPARTNER, 0)
      );
      applyto
        .getMap()
        .broadcastMessage(
          applyto,
          MaplePacketCreator.giveForeignBuff(applyto.getId(), stat, false),
          false
        );
    }
    if (isSoulArrow()) {
      List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(
        new Pair<MapleBuffStat, Integer>(MapleBuffStat.SOULARROW, 0)
      );
      applyto
        .getMap()
        .broadcastMessage(
          applyto,
          MaplePacketCreator.giveForeignBuff(applyto.getId(), stat, false),
          false
        );
    }

    if (isEnrage()) {
      applyto.handleOrbconsume();
    }
    if (isMorph() || isOakBarrel() || isPirateMorph()) { //Not sure o.o
      List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(
        new Pair<MapleBuffStat, Integer>(
          MapleBuffStat.MORPH,
          Integer.valueOf(getMorph(applyto))
        )
      );
      applyto
        .getMap()
        .broadcastMessage(
          applyto,
          MaplePacketCreator.giveForeignBuff(applyto.getId(), stat, true),
          false
        );
    }
    if (isTimeLeap()) {
      for (PlayerCoolDownValueHolder i : applyto.getAllCooldowns()) {
        if (i.skillId != 5121010) {
          applyto.removeCooldown(i.skillId);
        }
      }
    }
    if (localstatups.size() > 0) {
      long starttime = System.currentTimeMillis();
      CancelEffectAction cancelAction = new CancelEffectAction(
        applyto,
        this,
        starttime
      );
      ScheduledFuture<?> schedule = TimerManager
        .getInstance()
        .schedule(cancelAction, localDuration);
      applyto.registerEffect(this, starttime, schedule);
    }
    if (primary) {
      if (isDash()) {
        applyto
          .getMap()
          .broadcastMessage(
            applyto,
            MaplePacketCreator.showDashEffecttoOthers(
              applyto.getId(),
              localX,
              localY,
              seconds
            ),
            false
          );
      } else if (isInfusion()) {
        applyto
          .getMap()
          .broadcastMessage(
            applyto,
            MaplePacketCreator.giveForeignInfusion(applyto.getId(), x, seconds),
            false
          );
      } else {
        applyto
          .getMap()
          .broadcastMessage(
            applyto,
            MaplePacketCreator.showBuffeffect(
              applyto.getId(),
              sourceid,
              1,
              (byte) 3
            ),
            false
          );
      }
    }
    if (isOakBarrel() || isPirateMorph()) { //Kinda a hacky fix, but who cares..
      applyto
        .getMap()
        .broadcastMessage(
          applyto,
          MaplePacketCreator.removePlayerFromMap(applyto.getObjectId()),
          false
        );
      applyto
        .getMap()
        .broadcastMessage(
          applyto,
          MaplePacketCreator.spawnPlayerMapobject(applyto),
          false
        );
    }
  }

  private int calcHPChange(MapleCharacter applyfrom, boolean primary) {
    if (isGMHeal()) return 30000;
    int hpchange = 0;
    if (hp != 0) {
      if (!skill) {
        if (primary) {
          hpchange += alchemistModifyVal(applyfrom, hp, true);
        } else {
          hpchange += hp;
        }
      } else { // assumption: this is heal
        hpchange += makeHealHP(hp / 100.0, applyfrom.getTotalMagic(), 3, 5);
      }
    }
    if (hpR != 0) {
      hpchange += (int) (applyfrom.getCurrentMaxHp() * hpR);
      applyfrom.checkBerserk();
    }
    // actually receivers probably never get any hp when it's not heal but whatever
    if (primary) {
      if (hpCon != 0) {
        hpchange -= hpCon;
      }
    }
    if (isChakra()) {
      hpchange += makeHealHP(getY() / 100.0, applyfrom.getTotalLuk(), 2.3, 3.5);
    }
    return hpchange;
  }

  private int makeHealHP(
    double rate,
    double stat,
    double lowerfactor,
    double upperfactor
  ) {
    int maxHeal = (int) (stat * upperfactor * rate);
    int minHeal = (int) (stat * lowerfactor * rate);
    return (int) ((Math.random() * (maxHeal - minHeal + 1)) + minHeal);
  }

  private int calcMPChange(MapleCharacter applyfrom, boolean primary) {
    if (isGMHeal()) return 30000;
    int mpchange = 0;
    if (mp != 0) {
      if (primary) {
        mpchange += alchemistModifyVal(applyfrom, mp, true);
      } else {
        mpchange += mp;
      }
    }
    if (mpR != 0) {
      mpchange += (int) (applyfrom.getCurrentMaxMp() * mpR);
    }
    if (primary) {
      if (mpCon != 0) {
        double mod = 1.0;
        boolean isAFpMage = applyfrom.getJob().isA(MapleJob.FP_MAGE);
        if (isAFpMage || applyfrom.getJob().isA(MapleJob.IL_MAGE)) {
          ISkill amp;
          if (isAFpMage) {
            amp = SkillFactory.getSkill(2110001);
          } else {
            amp = SkillFactory.getSkill(2210001);
          }
          int ampLevel = applyfrom.getSkillLevel(amp);
          if (ampLevel > 0) {
            MapleStatEffect ampStat = amp.getEffect(ampLevel);
            mod = ampStat.getX() / 100.0;
          }
        }
        mpchange -= mpCon * mod;
        if (applyfrom.getBuffedValue(MapleBuffStat.INFINITY) != null) {
          mpchange = 0;
        }
      }
    }
    return mpchange;
  }

  private int alchemistModifyVal(MapleCharacter chr, int val, boolean withX) {
    if (
      !skill &&
      (
        chr.getJob().isA(MapleJob.HERMIT) ||
        chr.getJob().isA(MapleJob.NIGHTLORD)
      )
    ) {
      MapleStatEffect alchemistEffect = getAlchemistEffect(chr);
      if (alchemistEffect != null) {
        return (int) (
          val *
          ((withX ? alchemistEffect.getX() : alchemistEffect.getY()) / 100.0)
        );
      }
    }
    return val;
  }

  private MapleStatEffect getAlchemistEffect(MapleCharacter chr) {
    ISkill alchemist = SkillFactory.getSkill(4110000);
    int alchemistLevel = chr.getSkillLevel(alchemist);
    if (alchemistLevel == 0) {
      return null;
    }
    return alchemist.getEffect(alchemistLevel);
  }

  public void setSourceId(int newid) {
    sourceid = newid;
  }

  public short getHpCon() {
    return hpCon;
  }

  public short getMpCon() {
    return mpCon;
  }

  public final boolean isGmBuff() {
    switch (sourceid) {
      case 10001075: //Empress Prayer
      case 9001000: // GM dispel
      case 9001001: // GM haste
      case 9001002: // GM Holy Symbol
      case 9001003: // GM Bless
      case 9001005: // GM resurrection
      case 9001008: // GM Hyper body
      case 9101000:
      case 9101001:
      case 9101002:
      case 9101003:
      case 9101005:
      case 9101008:
        return true;
      default:
        return (
          Configuration.isBeginnerJob(sourceid / 10000) &&
          sourceid % 10000 == 1005
        );
    }
  }

  private boolean isMonsterBuff() {
    if (!skill) {
      return false;
    }
    switch (sourceid) {
      case 1201006: // threaten
      case 2101003: // fp slow
      case 2201003: // il slow
      case 2211004: // il seal
      case 2111004: // fp seal
      case 2311005: // doom
      case 4111003: // shadow web
        return true;
    }
    return false;
  }

  private boolean isPartyBuff() {
    if (lt == null || rb == null) {
      return false;
    }
    if (
      (sourceid >= 1211003 && sourceid <= 1211008) ||
      sourceid == 1221003 ||
      sourceid == 1221004
    ) { // wk charges have lt and rb set but are neither player nor monster buffs
      return false;
    }
    return true;
  }

  public boolean isHeal() {
    return sourceid == 2301002 || sourceid == 9101000;
  }

  public boolean isResurrection() {
    return (
      sourceid == 9101005 ||
      sourceid == 2321006 ||
      sourceid == SuperGM.RESURRECTION
    );
  }

  public boolean isTimeLeap() {
    return sourceid == 5121010;
  }

  public boolean isInfusion() {
    return skill && sourceid == 0000000;
  }

  public final boolean isMonsterRiding_() {
    return (
      skill &&
      (
        sourceid == 1004 ||
        sourceid == 10001004 ||
        sourceid == 20001004 ||
        sourceid == 20011004 ||
        sourceid == 11004 ||
        sourceid == 20021004 ||
        sourceid == 80001000
      )
    );
  }

  public short getHp() {
    return hp;
  }

  public short getMp() {
    return mp;
  }

  public short getWatk() {
    return watk;
  }

  public short getMatk() {
    return matk;
  }

  public short getWdef() {
    return wdef;
  }

  public short getMdef() {
    return mdef;
  }

  public short getAcc() {
    return acc;
  }

  public short getAvoid() {
    return avoid;
  }

  public short getHands() {
    return hands;
  }

  public short getSpeed() {
    return speed;
  }

  public short getJump() {
    return jump;
  }

  public int getDuration() {
    return duration;
  }

  public boolean isOverTime() {
    return overTime;
  }

  public List<Pair<MapleBuffStat, Integer>> getStatups() {
    return statups;
  }

  public boolean sameSource(MapleStatEffect effect) {
    return this.sourceid == effect.sourceid && this.skill == effect.skill;
  }

  public int getX() {
    return x;
  }

  public int getY() {
    return y;
  }

  public int getZ() {
    return z;
  }

  public int getDamage() {
    return damage;
  }

  public int getAttackCount() {
    return attackCount;
  }

  public int getBulletCount() {
    return bulletCount;
  }

  public int getBulletConsume() {
    return bulletConsume;
  }

  public int getMoneyCon() {
    return moneyCon;
  }

  public int getCooldown() {
    return cooldown;
  }

  public Map<MonsterStatus, Integer> getMonsterStati() {
    return monsterStatus;
  }

  public boolean isHide() {
    return skill && sourceid == 9101004;
  }

  public boolean isDragonBlood() {
    return skill && sourceid == 1311008;
  }

  public boolean isBerserk() {
    return skill && sourceid == 1320006;
  }

  private boolean isDs() {
    return skill && sourceid == 4001003;
  }

  private boolean isOakBarrel() {
    return skill && sourceid == 5101007;
  }

  private boolean isCombo() {
    return skill && sourceid == 1111002;
  }

  private boolean isEnrage() {
    return skill && sourceid == 1121010;
  }

  public boolean isMPRecovery() {
    return skill && sourceid == 5101005;
  }

  public boolean isBeholder() {
    return skill && sourceid == 1321007;
  }

  public boolean isPhoenix() {
    return skill && sourceid == 3121006;
  }

  public boolean isMarksMan() {
    return skill && sourceid == 3221005;
  }

  public boolean isMageSummon() {
    return skill && sourceid == 2121005;
  }

  public boolean isMageSummon2() {
    return skill && sourceid == 2221005;
  }

  private boolean isShadowPartner() {
    return skill && sourceid == 4111002;
  }

  private boolean isChakra() {
    return skill && sourceid == 4211001;
  }

  public boolean isMonsterRiding() {
    return skill && (sourceid == 1004 || sourceid == 5221006);
  }

  public boolean isBattleShip() {
    return skill && sourceid == 5221006;
  }

  public boolean isMagicDoor() {
    return skill && sourceid == 2311002;
  }

  public boolean isMesoGuard() {
    return skill && sourceid == 4211005;
  }

  public boolean isCharge() {
    return skill && sourceid >= 1211003 && sourceid <= 1211008;
  }

  public boolean isPoison() {
    return (
      skill &&
      (sourceid == 2111003 || sourceid == 2101005 || sourceid == 2111006)
    );
  }

  private boolean isMist() {
    return skill && (sourceid == 2111003 || sourceid == 4221006); // poison mist and smokescreen
  }

  private boolean isSoulArrow() {
    return skill && (sourceid == 3101004 || sourceid == 3201004); // bow and crossbow
  }

  private boolean isShadowClaw() {
    return skill && sourceid == 4121006;
  }

  private boolean isDispel() {
    return skill && (sourceid == 2311001 || sourceid == 9101000);
  }

  private boolean isHeroWill() {
    return (
      skill &&
      (
        sourceid == 1121011 ||
        sourceid == 1221012 ||
        sourceid == 1321010 ||
        sourceid == 2121008 ||
        sourceid == 2221008 ||
        sourceid == 2321009 ||
        sourceid == 3121009 ||
        sourceid == 3221008 ||
        sourceid == 4121009 ||
        sourceid == 4221008 ||
        sourceid == 5121008 ||
        sourceid == 5221010
      )
    );
  }

  private boolean isDash() {
    return skill && sourceid == 5001005;
  }

  public boolean isPirateMorph() {
    return skill && (sourceid == 5111005 || sourceid == 5121003);
  }

  public boolean isMorph() {
    return morphId > 0;
  }

  public int getMorph() {
    return morphId;
  }

  public boolean isGMHeal() {
    return skill && sourceid == 9101000;
  }

  public int getMorph(MapleCharacter chr) {
    if (isOakBarrel()) {
      return 1002;
    }
    if (isPirateMorph()) {
      if (this.sourceid == 5111005) { // transform
        if (chr.getGender() == 0) return 1000; else return 1100;
      } else if (sourceid == 5121003) { //super transform
        if (chr.getGender() == 0) return 1001; else return 1101;
      }
    }
    return morphId;
  }

  public SummonMovementType getSummonMovementType() {
    if (!skill) {
      return null;
    }
    switch (sourceid) {
      case 3211002: // puppet sniper
      case 3111002: // puppet ranger
      case 5211001: // octopus - pirate
      case 5220002: // advanced octopus - pirate
        return SummonMovementType.STATIONARY;
      case 3211005: // golden eagle
      case 3111005: // golden hawk
      case 2311006: // summon dragon
      case 3221005: // frostprey
      case 3121006: // phoenix
      case 5211002: // bird - pirate
        return SummonMovementType.CIRCLE_FOLLOW;
      case 1321007: // beholder
      case 2121005: // elquines
      case 2221005: // ifrit
      case 2321003: // bahamut
        return SummonMovementType.FOLLOW;
    }
    return null;
  }

  public boolean isSkill() {
    return skill;
  }

  public int getSourceId() {
    return sourceid;
  }

  public int getMastery() {
    return mastery;
  }

  public int getRange() {
    return range;
  }

  public int getMobCount() {
    return mobCount;
  }

  public int getFixDamage() {
    return fixDamage;
  }

  /**
   *
   * @return true if the effect should happen based on it's probablity, false otherwise
   */
  public boolean makeChanceResult() {
    return prop == 1.0 || Math.random() < prop;
  }

  private static class CancelEffectAction implements Runnable {

    private MapleStatEffect effect;
    private WeakReference<MapleCharacter> target;
    private long startTime;

    public CancelEffectAction(
      MapleCharacter target,
      MapleStatEffect effect,
      long startTime
    ) {
      this.effect = effect;
      this.target = new WeakReference<>(target);
      this.startTime = startTime;
    }

    @Override
    public void run() {
      MapleCharacter realTarget = target.get();
      if (realTarget != null) {
        realTarget.cancelEffect(effect, false, startTime);
      }
    }
  }
}
