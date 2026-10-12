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
package ghidra.features.codecompare.decompile;

import static org.junit.Assert.*;

import java.awt.Dialog;
import java.awt.Window;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import docking.ActionContext;
import docking.action.DockingActionIf;
import ghidra.app.decompiler.ClangFuncNameToken;
import ghidra.app.decompiler.ClangToken;
import ghidra.app.decompiler.DecompileProcessFactory;
import ghidra.framework.Application;
import ghidra.framework.Platform;
import ghidra.app.decompiler.component.ClangTextField;
import ghidra.features.codecompare.plugin.FunctionComparisonProvider;
import ghidra.program.database.ProgramBuilder;
import ghidra.program.model.data.IntegerDataType;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
import ghidra.program.model.listing.ReturnParameterImpl;
import ghidra.program.model.symbol.SourceType;
import ghidra.util.datastruct.Duo.Side;

/** Exercises callee naming and neighboring actions through actual native decompiler tokens. */
public class ExternalCalleeFunctionNameTest extends AbstractDualDecompilerTest {
	@Test
	public void testExternalCalleeNameCanBeTransferred() throws Exception {
		check(true, true, null, false);
	}

	@Test
	public void testInternalCalleeNameControl() throws Exception {
		check(false, false, null, false);
	}

	@Test
	public void testExternalSourceInternalTargetNameCanBeTransferred() throws Exception {
		check(true, false, null, false);
	}

	@Test
	public void testInternalSourceExternalTargetNameCanBeTransferred() throws Exception {
		check(false, true, null, false);
	}

	@Test
	public void testCompareExternalCalleesRemainRejected() throws Exception {
		check(true, true, "Compare Matching Callees", false);
	}

	@Test
	public void testExternalLibraryNamespaceCanBeTransferred() throws Exception {
		check(true, true, null, true);
	}

	@Test
	public void testInternalNamespaceCanBeTransferred() throws Exception {
		check(false, false, null, true);
	}

	@Test
	public void testEmptySignatureExternalCalleesRemainRejected() throws Exception {
		check(true, true, ApplyCalleeEmptySignatureFromMatchedTokensAction.ACTION_NAME, false);
	}

	@Test
	public void testTypedSignatureExternalCalleesRemainRejected() throws Exception {
		check(true, true, ApplyCalleeSignatureWithDatatypesFromMatchedTokensAction.ACTION_NAME, false);
	}

	private void check(boolean sourceExternal, boolean targetExternal, String rejectedAction,
			boolean differentNamespaces) throws Exception {
		ProgramBuilder left = new ProgramBuilder("source", ProgramBuilder._X86);
		ProgramBuilder right = null;
		try {
			right = new ProgramBuilder("target", ProgramBuilder._X86);
			Function leftCaller = populate(left, "imported_name", sourceExternal,
				differentNamespaces ? "source_scope" : null);
			Function rightCaller = populate(right, "unknown_name", targetExternal,
				differentNamespaces ? "target_scope" : null);
			Program sourceProgram = left.getProgram();
			Program targetProgram = right.getProgram();
			showTool(fcPlugin.getTool());
			env.open(sourceProgram);
			env.open(targetProgram);
			FunctionComparisonProvider provider = compareFunctions(Set.of(leftCaller, rightCaller));
			DecompilerCodeComparisonView view = findDecompilerPanel(provider);
			assertNotNull(view);
			setActivePanel(provider, view);
			waitForDecompile(view);
			assertCurrentNative(sourceExternal, targetExternal);
			assertSame(sourceProgram, view.getProgram(Side.LEFT));
			assertSame(targetProgram, view.getProgram(Side.RIGHT));
			selectCall(view, "unknown_name");
			ActionContext context = runSwing(() -> provider.getActionContext(null));
			assertTrue(context instanceof DualDecompilerActionContext);
			DualDecompilerActionContext dual = (DualDecompilerActionContext) context;
			dual.setOverrideReadOnly(true); // Existing comparison-test context contract.
			assertNotNull("Actual native matched call tokens are required", dual.getTokenPair());
			assertTrue(dual.getTokenPair().leftToken() instanceof ClangFuncNameToken);
			assertTrue(dual.getTokenPair().rightToken() instanceof ClangFuncNameToken);
			Function nativeSource = resolveCall(dual.getTokenPair().leftToken(), sourceProgram);
			Function nativeTarget = resolveCall(dual.getTokenPair().rightToken(), targetProgram);
			assertEquals(sourceExternal, nativeSource.isExternal());
			assertEquals(targetExternal, nativeTarget.isExternal());
			assertEquals("imported_name", nativeSource.getName());
			Function target = targetProgram.getFunctionManager().getFunctionAt(right.addr("0x1100"));
			if (targetExternal) {
				target = target.getThunkedFunction(true);
				assertTrue(target.isExternal());
			}
			assertSame(target, nativeTarget);
			final Function actualTarget = target;
			var originalNamespace = actualTarget.getParentNamespace();
			String originalSignature = actualTarget.getPrototypeString(false, true);
			var originalReturnType = actualTarget.getReturnType();
			var originalReturnSource = actualTarget.getReturn().getSource();
			var originalSignatureSource = actualTarget.getSignatureSource();
			int originalParameterCount = actualTarget.getParameterCount();
			DockingActionIf action = getLocalAction(provider,
				rejectedAction != null ? rejectedAction :
				ApplyCalleeFunctionNameFromMatchedTokensAction.ACTION_NAME);
			assertNotNull(action);
			assertTrue(runSwing(() -> action.isEnabledForContext(context)));
			AtomicBoolean completed = new AtomicBoolean();
			AtomicBoolean warningObserved = new AtomicBoolean();
			runSwingLater(() -> {
				try {
					action.actionPerformed(context);
				}
				finally {
					completed.set(true);
				}
			});
			waitForCondition(() -> {
				Dialog warning = runSwing(() -> warningDialog());
				if (warning != null) {
					warningObserved.set(true);
					runSwing(() -> warning.dispose());
				}
				return completed.get();
			});
			waitForSwing();
			System.out.println("ACTUAL_CALLEE_NAME sourceExternal=" + sourceExternal +
				" targetExternal=" + targetExternal + " rejectedAction=" + rejectedAction +
				" matched=true warning=" + warningObserved.get() + " result=" + actualTarget.getName() +
				" namespace=" + actualTarget.getParentNamespace().getName(true));
			if (rejectedAction != null) {
				assertTrue("Body and signature actions must continue to reject external callees",
					warningObserved.get());
				assertEquals("unknown_name", actualTarget.getName());
				assertEquals(originalSignature, actualTarget.getPrototypeString(false, true));
				assertEquals(originalReturnType, actualTarget.getReturnType());
				assertEquals(originalReturnSource, actualTarget.getReturn().getSource());
				assertEquals(originalSignatureSource, actualTarget.getSignatureSource());
				assertEquals(originalParameterCount, actualTarget.getParameterCount());
			}
			else {
				assertEquals("imported_name", actualTarget.getName());
				assertFalse("Name transfer must complete without a warning", warningObserved.get());
				assertEquals(targetExternal, actualTarget.isExternal());
				assertEquals(nativeSource.getSymbol().getSource(), actualTarget.getSymbol().getSource());
				if (sourceExternal != targetExternal) {
					assertSame("Mixed transfers must retain the target namespace", originalNamespace,
						actualTarget.getParentNamespace());
				}
				else {
					assertEquals(nativeSource.getParentNamespace().getName(true),
						actualTarget.getParentNamespace().getName(true));
				}
			}
		}
		finally {
			left.dispose();
			if (right != null) {
				right.dispose();
			}
		}
	}

	private Function populate(ProgramBuilder builder, String name, boolean external,
			String namespace) throws Exception {
		builder.createMemory("code", "0x1000", 0x200);
		builder.setBytes("0x1000", "e8 fb 00 00 00 c3");
		builder.setBytes("0x1100", "b8 01 00 00 00 c3");
		builder.disassemble("0x1100", 6);
		builder.disassemble("0x1000", 6);
		Function callee = getOrCreateFunction(builder, "0x1100");
		Function caller = getOrCreateFunction(builder, "0x1000");
		Program ownedProgram = builder.getProgram();
		int signatureTransaction = ownedProgram.startTransaction("Set real caller and callee signatures");
		try {
			callee.setName(name, SourceType.USER_DEFINED);
			caller.setName("caller", SourceType.USER_DEFINED);
			for (Function function : new Function[] { callee, caller }) {
				function.updateFunction(null,
					new ReturnParameterImpl(IntegerDataType.dataType, ownedProgram),
					Function.FunctionUpdateType.DYNAMIC_STORAGE_FORMAL_PARAMS, false,
					SourceType.USER_DEFINED);
			}
		}
		finally {
			ownedProgram.endTransaction(signatureTransaction, true);
		}
		if (external) {
			Program program = builder.getProgram();
			int transaction = program.startTransaction("Create actual external callee thunk");
			try {
				Function imported = program.getExternalManager()
					.addExtFunction(namespace == null ? "fixture_library" : namespace, name, null,
						SourceType.IMPORTED).getFunction();
				imported.setReturnType(IntegerDataType.dataType, SourceType.IMPORTED);
				callee.setThunkedFunction(imported);
			}
			finally {
				program.endTransaction(transaction, true);
			}
		}
		if (!external && namespace != null) {
			Program program = builder.getProgram();
			int transaction = program.startTransaction("Set actual internal callee namespace");
			try {
				callee.setParentNamespace(program.getSymbolTable().createNameSpace(
					program.getGlobalNamespace(), namespace, SourceType.USER_DEFINED));
			}
			finally {
				program.endTransaction(transaction, true);
			}
		}
		return caller;
	}

	private void selectCall(DecompilerCodeComparisonView view, String name) {
		var fields = getDecompSide(view, Side.RIGHT).getFields();
		for (int line = 0; line < fields.size(); line++) {
			ClangTextField field = (ClangTextField) fields.get(line);
			int column = field.getText().indexOf(name);
			if (column >= 0) {
				ClangToken token = setDecompLocation(view, Side.RIGHT, line + 1, column);
				if (token instanceof ClangFuncNameToken && token.getPcodeOp() != null) {
					return;
				}
			}
		}
		fail("Actual native decompiler call token was not displayed");
	}

	private Function getOrCreateFunction(ProgramBuilder builder, String address) {
		Function function = builder.getProgram().getFunctionManager()
			.getFunctionAt(builder.addr(address));
		if (function == null) {
			function = builder.createFunction(address);
		}
		assertNotNull("Actual disassembled function must be available", function);
		assertEquals(builder.addr(address), function.getEntryPoint());
		assertEquals(6L, function.getBody().getNumAddresses());
		assertEquals(builder.addr(address).add(5), function.getBody().getMaxAddress());
		return function;
	}

	private Function resolveCall(ClangToken token, Program program) {
		Function function = program.getFunctionManager()
			.getFunctionAt(token.getPcodeOp().getInput(0).getAddress());
		assertNotNull("Actual call target must resolve to a function", function);
		return function.isThunk() ? function.getThunkedFunction(true) : function;
	}

	private void assertCurrentNative(boolean sourceExternal, boolean targetExternal) throws Exception {
		Field executablePath = DecompileProcessFactory.class.getDeclaredField("exepath");
		executablePath.setAccessible(true); // Read-only inspection of the actual process factory path.
		File actual = new File((String) executablePath.get(null));
		File built = new File(Application.getModuleRootDir("Decompiler").getFile(false),
			"build/os/" + Platform.CURRENT_PLATFORM.getDirectoryName() + "/decompile");
		assertEquals(built.getCanonicalFile(), actual.getCanonicalFile());
		assertTrue(actual.isFile());
		System.out.println("CALLEE_CURRENT_NATIVE sourceExternal=" + sourceExternal +
			" targetExternal=" + targetExternal + " path=" + actual);
	}

	private Dialog warningDialog() {
		for (Window window : Window.getWindows()) {
			if (window instanceof Dialog dialog && dialog.isShowing() &&
				(dialog.getTitle().startsWith("Unable to Compare") ||
				 dialog.getTitle().equals("Failed to Apply Callee Function Name"))) {
				return dialog;
			}
		}
		return null;
	}
}
