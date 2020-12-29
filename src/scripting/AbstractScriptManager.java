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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package scripting;

import java.io.File;
import java.io.FileReader;
import java.lang.StringBuilder;
import java.util.Scanner;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import client.MapleClient;
import config.configuration.*;
import config.configuration.FeatureManager.Feature;

/**
 *
 * @author Matze
 */
public abstract class AbstractScriptManager {

    protected ScriptEngine engine;
    private ScriptEngineManager sem;

    protected static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AbstractScriptManager.class);

    protected AbstractScriptManager() {
            sem = new ScriptEngineManager();
    }

    protected Invocable getInvocable(String path, MapleClient c) {
            try {
                    String scriptPath = "scripts/" + path;
                    int prior = 0;  //Used for priority of features
                    engine = null;
                    if (c != null) {
                            engine = c.getScriptEngine(scriptPath);
                    }
                    if (engine == null) {
                            File scriptFile = new File(scriptPath);
                            FeatureManager fm = new FeatureManager();
                            for(int i = 0; i < fm.count(); i++) {
                                Feature feat = fm.featureList[i];
                                if(feat.isEnabled() && feat.getPriority() > prior) {
                                    scriptPath = "scripts/feature/" + feat.toString() + "/" + path;
                                    File tmpScriptFile = new File(scriptPath);
                                    if(tmpScriptFile.exists())
                                        scriptFile = tmpScriptFile;
                                }
                            }
                            if (!scriptFile.exists())
                                    return null;
                            engine = sem.getEngineByName("javascript");
                            if (c != null) {
                                    c.setScriptEngine(scriptPath, engine);
                            }
                            // Add javascript compatibility shims for importPackage statements.
                            // http://forum.ragezone.com/f566/importpackage-defined-1111351/#post8676489
                            // https://stackoverflow.com/a/4716556
                            // https://stackoverflow.com/a/14169690
                            StringBuilder sb = new StringBuilder();
                            sb.append("load('nashorn:mozilla_compat.js');" + System.lineSeparator());
                            Scanner in = new Scanner(new FileReader(scriptFile));
                            in.useDelimiter("\\Z");
                            sb.append(in.next());
                            in.close();
                            engine.eval(sb.toString());
                    }
                    return (Invocable) engine;
            } catch (Exception e) {
                    log.error("Error executing script.", e);
                    return null;
            }
    }

    protected void resetContext(String path, MapleClient c) {
            path = "scripts/" + path;
            c.removeScriptEngine(path);
    }
}