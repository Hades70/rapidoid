package org.rapidoid.setup;

/*
 * #%L
 * rapidoid-http-server
 * %%
 * Copyright (C) 2014 - 2016 Nikolche Mihajlovski and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.rapidoid.RapidoidThing;
import org.rapidoid.annotation.Authors;
import org.rapidoid.annotation.Since;
import org.rapidoid.config.Conf;
import org.rapidoid.data.JSON;
import org.rapidoid.io.Res;
import org.rapidoid.log.Log;
import org.rapidoid.reload.Reload;
import org.rapidoid.render.Templates;
import org.rapidoid.scan.ClasspathUtil;
import org.rapidoid.u.U;
import org.rapidoid.util.Msc;

@Authors("Nikolche Mihajlovski")
@Since("5.1.0")
public class App extends RapidoidThing {

	private static volatile String[] path;

	private static volatile String mainClassName;
	private static volatile String appPkgName;
	private static volatile boolean dirty;
	private static volatile boolean restarted;

	static volatile ClassLoader loader;

	static {
		resetGlobalState();
	}

	public static void args(String... args) {
		Conf.args(args);
	}

	public static void path(String... path) {
		App.path = path;
	}

	public static synchronized String[] path() {
		inferCallers();

		if (U.isEmpty(App.path)) {
			App.path = new String[]{appPkgName};
		}

		return path;
	}

	static void inferCallers() {
		if (!restarted && appPkgName == null && mainClassName == null) {
			String pkg = Msc.getCallingPackage();

			appPkgName = pkg;

			if (mainClassName == null) {
				Class<?> mainClass = Msc.getCallingMainClass();
				mainClassName = mainClass != null ? mainClass.getName() : null;
			}

			if (mainClassName != null || pkg != null) {
				Log.info("Inferring application root", "!main", mainClassName, "!package", pkg);
			}
		}
	}

	private static void restartApp() {
		U.notNull(mainClassName, "Cannot restart, the main class is unknown!");

		Msc.logSection("!Restarting the web application...");

		restarted = true;
		App.path = null;

		Conf.reload();
		Res.reset();
		Templates.reset();
		JSON.reset();

		for (Setup setup : Setup.instances()) {
			setup.reload();
		}

		Setup.initDefaults();

		loader = Reload.createClassLoader();
		ClasspathUtil.setDefaultClassLoader(loader);

		Class<?> entry;
		try {
			entry = loader.loadClass(mainClassName);
		} catch (ClassNotFoundException e) {
			Log.error("Cannot restart the application, the main class (app entry point) is missing!");
			return;
		}

		Msc.invokeMain(entry, Conf.getArgs());

		Log.info("!Successfully restarted the application!");
	}

	public static void resetGlobalState() {
		mainClassName = null;
		appPkgName = null;
		restarted = false;
		loader = Setup.class.getClassLoader();
		dirty = false;
		path = null;
		Setup.initDefaults();
	}

	public static void notifyChanges() {
		if (!dirty) {
			dirty = true;
			Log.info("Detected class or resource changes");
		}
	}

	static void restartIfDirty() {
		if (dirty) {
			synchronized (Setup.class) {
				if (dirty) {
					restartApp();
					dirty = false;
				}
			}
		}
	}

}
