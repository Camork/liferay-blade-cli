/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liferay.blade.cli.command;

import com.liferay.blade.cli.WorkspaceConstants;
import com.liferay.blade.cli.util.BladeUtil;
import com.liferay.blade.cli.util.ServerUtil;
import com.liferay.blade.cli.util.WorkspaceUtil;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * @author Gregory Amerson
 */
public class LocalServer {

	public LocalServer(File baseDir) {
		if (isWorkspace(baseDir)) {
			Properties properties = getWorkspaceProperties(baseDir);

			String liferayHomePath = properties.getProperty(WorkspaceConstants.DEFAULT_LIFERAY_HOME_DIR_PROPERTY);

			if ((liferayHomePath == null) || liferayHomePath.equals("")) {
				liferayHomePath = WorkspaceConstants.DEFAULT_LIFERAY_HOME_DIR;
			}

			String bundleArtifactName = properties.getProperty(WorkspaceConstants.BUNDLE_ARTIFACT_NAME);

			if (bundleArtifactName != null) {
				if (bundleArtifactName.contains("jboss")) {
					_serverType = "jboss";
				}
				else if (bundleArtifactName.contains("tomcat")) {
					_serverType = "tomcat";
				}
				else if (bundleArtifactName.contains("wildfly")) {
					_serverType = "wildfly";
				}
			}

			if (_serverType == null) {
				String bundleUrl = properties.getProperty(WorkspaceConstants.BUNDLE_URL);

				if (bundleUrl != null) {
					if (bundleUrl.contains("tomcat")) {
						_serverType = "tomcat";
					}
					else if (bundleUrl.contains("jboss")) {
						_serverType = "jboss";
					}
					else if (bundleUrl.contains("wildfly")) {
						_serverType = "wildfly";
					}
				}
			}

			if (_serverType == null) {
				_serverType = "tomcat";
			}

			Path tempLiferayHome = Paths.get(liferayHomePath);

			if (tempLiferayHome.isAbsolute()) {
				_liferayHomePath = tempLiferayHome.normalize();
			}
			else {
				File workspaceRootDir = getWorkspaceDir(baseDir);

				Path workspaceRootDirPath = workspaceRootDir.toPath();

				Path tempFile = workspaceRootDirPath.resolve(liferayHomePath);

				_liferayHomePath = tempFile.normalize();
			}
		}
		else {
			List<Properties> propertiesList = BladeUtil.getAppServerProperties(baseDir);

			String appServerParentDir = "";

			for (Properties properties : propertiesList) {
				if (appServerParentDir.equals("")) {
					String appServerParentDirTemp = properties.getProperty(BladeUtil.APP_SERVER_PARENT_DIR_PROPERTY);

					if ((appServerParentDirTemp != null) && !appServerParentDirTemp.equals("")) {
						Path rootDirRealPath = baseDir.toPath();

						try {
							rootDirRealPath = rootDirRealPath.toRealPath();
						}
						catch (IOException ioe) {
						}

						appServerParentDirTemp = appServerParentDirTemp.replace(
							"${project.dir}", rootDirRealPath.toString());

						appServerParentDir = appServerParentDirTemp;
					}
				}

				if ((_serverType == null) || _serverType.equals("")) {
					String serverTypeTemp = properties.getProperty(BladeUtil.APP_SERVER_TYPE_PROPERTY);

					if ((serverTypeTemp != null) && !serverTypeTemp.equals("")) {
						_serverType = serverTypeTemp;
					}
				}
			}

			if (appServerParentDir.startsWith("/") || appServerParentDir.contains(":")) {
				_liferayHomePath = Paths.get(appServerParentDir);
			}
			else {
				Path rootDirRealPath = baseDir.toPath();

				try {
					rootDirRealPath = rootDirRealPath.toRealPath();
				}
				catch (IOException ioe) {
				}

				_liferayHomePath = rootDirRealPath.resolve(appServerParentDir);
			}
		}

		try {
			_appServerPath = ServerUtil.findAppServerPath(_liferayHomePath, _serverType);
		}
		catch (IOException ioe) {
			_appServerPath = Optional.empty();
		}
	}

	public Optional<Path> getAppServerPath() throws IOException {
		return _appServerPath;
	}

	public Path getLiferayHomePath() {
		return _liferayHomePath;
	}

	public Optional<Path> getLogPath() {
		if (_serverType.equals("tomcat")) {
			return _appServerPath.map(path -> path.resolve("logs/catalina.out"));
		}
		else {
			return Optional.empty();
		}
	}

	public String getServerType() {
		return _serverType;
	}

	public boolean isSupported() throws IOException {
		Optional<Path> serverBinPath = getAppServerPath();

		String serverType = getServerType();

		if (serverBinPath.isPresent()) {
			if (serverType.equals("tomcat") || serverType.equals("jboss") || serverType.equals("wildfly")) {
				return true;
			}
		}

		return false;
	}

	public ProcessBuilder newLocalServerProcess() throws IOException {
		ProcessBuilder processBuilder = new ProcessBuilder();

		if (_serverType.equals("tomcat")) {
			_buildTomcatProcess(processBuilder);
		}
		else if (_serverType.equals("jboss") || _serverType.equalsIgnoreCase("wildfly")) {
			_buildJbossWildflyProcess(processBuilder);
		}

		return processBuilder;
	}

	protected File getWorkspaceDir(File dir) {
		return WorkspaceUtil.getWorkspaceDir(dir);
	}

	protected Properties getWorkspaceProperties(File baseDir) {
		return WorkspaceUtil.getGradleProperties(baseDir);
	}

	protected boolean isWorkspace(File dir) {
		return WorkspaceUtil.isWorkspace(dir);
	}

	private static String _getJBossWildflyExecutable() {
		String executable = "./standalone.sh";

		if (BladeUtil.isWindows()) {
			executable = "standalone.bat";
		}

		return executable;
	}

	private static String _getTomcatExecutable() {
		String executable = "./catalina.sh";

		if (BladeUtil.isWindows()) {
			executable = "catalina.bat";
		}

		return executable;
	}

	private void _buildJbossWildflyProcess(ProcessBuilder processBuilder) {
		processBuilder.command(_getJBossWildflyExecutable());

		Path appServer = _appServerPath.get();

		Path binPath = appServer.resolve("bin");

		processBuilder.directory(binPath.toFile());
	}

	private void _buildTomcatProcess(ProcessBuilder processBuilder) throws IOException {
		Optional<Path> appServerPath = getAppServerPath();

		Path appServer = appServerPath.get();

		Path logsPath = appServer.resolve("logs");

		if (!Files.exists(logsPath)) {
			Files.createDirectory(logsPath);
		}

		Path catalinaOutPath = logsPath.resolve("catalina.out");

		if (!Files.exists(catalinaOutPath)) {
			Files.createFile(catalinaOutPath);
		}

		Path binPath = appServer.resolve("bin");

		processBuilder.directory(binPath.toFile());

		Map<String, String> environment = processBuilder.environment();

		environment.put("CATALINA_PID", "catalina.pid");

		processBuilder.command(_getTomcatExecutable());
	}

	private Optional<Path> _appServerPath;
	private Path _liferayHomePath;
	private String _serverType;

}