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
package ghidra.features.bsim.gui.search.dialog;

import static org.junit.Assert.*;

import javax.swing.AbstractButton;
import javax.swing.JButton;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import docking.widgets.textfield.GFormattedTextField;
import ghidra.features.bsim.query.BSimServerInfo;
import ghidra.features.bsim.query.BSimServerInfo.DBType;
import ghidra.framework.plugintool.PluginTool;
import ghidra.test.AbstractGhidraHeadedIntegrationTest;
import ghidra.test.TestEnv;

public class CreateBsimServerInfoDialogTest extends AbstractGhidraHeadedIntegrationTest {

	private TestEnv env;
	private CreateBsimServerInfoDialog dialog;

	@Before
	public void setUp() throws Exception {
		env = new TestEnv();
		PluginTool tool = env.launchDefaultTool();
		runSwing(() -> dialog = new CreateBsimServerInfoDialog());
		showDialogWithoutBlocking(tool, dialog);
		waitForDialogComponent(dialog.getTitle());
	}

	@After
	public void tearDown() {
		close(dialog);
		env.dispose();
	}

	@Test
	public void testPostgresHyphenatedNameAcceptedLikeCli() {
		checkValidName(DBType.postgres, "repo-libc");
	}

	@Test
	public void testElasticHyphenatedNameAcceptedLikeCli() {
		checkValidName(DBType.elastic, "repo-libc");
	}

	@Test
	public void testOrdinaryNamesRemainAccepted() {
		checkValidName(DBType.postgres, "repo_libc1");
		checkValidName(DBType.elastic, "repo_libc1");
	}

	@Test
	public void testEmptyAndPathNamesRemainRejected() {
		for (DBType type : new DBType[] { DBType.postgres, DBType.elastic }) {
			for (String name : new String[] { "", "repo/libc", "repo\\libc" }) {
				Object panel = enterDatabaseName(type, name);
				assertNull(getServerInfo(panel));
				JButton okButton = (JButton) getInstanceField("okButton", dialog);
				assertFalse(okButton.isEnabled());
			}
		}
	}

	private void checkValidName(DBType type, String name) {
		Object panel = enterDatabaseName(type, name);
		BSimServerInfo actual = getServerInfo(panel);
		assertNotNull("Dialog must accept CLI-compatible name " + name, actual);
		int port = type == DBType.postgres ? BSimServerInfo.DEFAULT_POSTGRES_PORT
				: BSimServerInfo.DEFAULT_ELASTIC_PORT;
		BSimServerInfo cliModel = new BSimServerInfo(type, null, "localhost", port, name);
		assertEquals(cliModel, actual);
		assertEquals(cliModel.toURLString(), actual.toURLString());
		assertEquals(name, actual.getDBName());
		JButton okButton = (JButton) getInstanceField("okButton", dialog);
		assertTrue(okButton.isEnabled());
	}

	private Object enterDatabaseName(DBType type, String name) {
		AbstractButton radio = (AbstractButton) getInstanceField(
			type == DBType.postgres ? "postgresButton" : "elasticButton", dialog);
		runSwing(() -> radio.doClick());
		Object panel = getInstanceField("activePanel", dialog);
		GFormattedTextField hostField = (GFormattedTextField) getInstanceField("hostField", panel);
		GFormattedTextField nameField = (GFormattedTextField) getInstanceField("nameField", panel);
		runSwing(() -> {
			hostField.setText("localhost");
			nameField.setText(name);
		});
		waitForSwing();
		return panel;
	}

	private BSimServerInfo getServerInfo(Object panel) {
		BSimServerInfo[] result = new BSimServerInfo[1];
		runSwing(() -> result[0] = (BSimServerInfo) invokeInstanceMethod("getServerInfo", panel));
		return result[0];
	}
}
