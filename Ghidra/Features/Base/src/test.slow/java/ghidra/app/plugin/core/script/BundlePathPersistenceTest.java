/* ###
 * IP: GHIDRA
 *
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
 */
package ghidra.app.plugin.core.script;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.junit.*;

import generic.jar.ResourceFile;
import ghidra.app.plugin.core.osgi.BundleHost;
import ghidra.framework.Application;
import ghidra.framework.options.SaveState;
import ghidra.test.AbstractGhidraHeadlessIntegrationTest;
import utilities.util.FileUtilities;

public class BundlePathPersistenceTest extends AbstractGhidraHeadlessIntegrationTest {
	private final List<Path> directories = new ArrayList<>();
	private String originalUserHome;

	@Before
	public void setUp() {
		originalUserHome = System.getProperty("user.home");
	}

	@After
	public void tearDown() {
		System.setProperty("user.home", originalUserHome);
		Collections.reverse(directories);
		for (Path directory : directories) {
			FileUtilities.deleteDir(directory);
		}
	}

	@Test
	public void testInstallationSiblingBundlePathSurvivesSaveRestore() throws Exception {
		ResourceFile root = Application.getApplicationRootDirectories().iterator().next();
		Path rootPath = new File(root.getAbsolutePath()).toPath();
		Path home = Files.createTempDirectory(rootPath.getParent(), "bundle-home-");
		directories.add(home);
		System.setProperty("user.home", home.toString());
		Path sibling =
			Files.createTempDirectory(rootPath.getParent(), rootPath.getFileName() + "_external-");
		directories.add(sibling);
		assertSavedAndRestoredBundle(new ResourceFile(sibling.toFile()),
			sibling.toFile().getAbsolutePath().replace('\\', '/'));
	}

	@Test
	public void testInstallationRootAndChildKeepPlaceholders() throws Exception {
		ResourceFile root = Application.getApplicationRootDirectories().iterator().next();
		assertEquals(generic.util.Path.GHIDRA_HOME, generic.util.Path.toPathString(root));
		Path child = Files.createTempDirectory(new File(root.getAbsolutePath()).toPath(), "bundle-child-");
		directories.add(child);
		assertSavedAndRestoredBundle(new ResourceFile(child.toFile()),
			generic.util.Path.GHIDRA_HOME + "/" + child.getFileName());
	}

	@Test
	public void testUserHomeRootAndChildKeepPlaceholders() throws Exception {
		ResourceFile root = Application.getApplicationRootDirectories().iterator().next();
		Path parent = new File(root.getAbsolutePath()).toPath().getParent();
		Path home = Files.createTempDirectory(parent, "bundle-home-");
		directories.add(home);
		System.setProperty("user.home", home.toString());
		assertEquals(generic.util.Path.USER_HOME,
			generic.util.Path.toPathString(new ResourceFile(home.toFile())));
		Path child = Files.createDirectory(home.resolve("scripts"));
		assertSavedAndRestoredBundle(new ResourceFile(child.toFile()),
			generic.util.Path.USER_HOME + "/scripts");
	}

	private void assertSavedAndRestoredBundle(ResourceFile directory, String expectedPath) {
		BundleHost source = new BundleHost();
		source.add(directory, false, false);
		SaveState state = new SaveState();
		source.saveManagedBundleState(state);
		BundleHost restored = new BundleHost();
		restored.restoreManagedBundleState(state, null);
		assertEquals(Set.of(directory), new HashSet<>(restored.getBundleFiles()));
		assertArrayEquals(new String[] { expectedPath }, state.getStrings("BundleHost_FILE", null));
		assertEquals(directory.getAbsolutePath(),
			generic.util.Path.fromPathString(generic.util.Path.toPathString(directory)).getAbsolutePath());
	}
}
