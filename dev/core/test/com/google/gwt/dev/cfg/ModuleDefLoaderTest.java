/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.dev.cfg;

import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.dev.CompilerContext;
import com.google.gwt.dev.cfg.Libraries.IncompatibleLibraryVersionException;
import com.google.gwt.dev.javac.testing.impl.MockResource;
import com.google.gwt.dev.resource.Resource;
import com.google.gwt.dev.util.UnitTestTreeLogger;
import com.google.gwt.dev.util.log.PrintWriterTreeLogger;
import com.google.gwt.thirdparty.guava.common.collect.ImmutableSet;
import com.google.gwt.thirdparty.guava.common.collect.Lists;
import com.google.gwt.thirdparty.guava.common.collect.Sets;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * Test for the module def loading
 */
public class ModuleDefLoaderTest extends TestCase {

  private CompilerContext compilerContext;
  private CompilerContext.Builder compilerContextBuilder;
  private MockLibraryWriter mockLibraryWriter = new MockLibraryWriter();

  public void assertHonorsStrictResources(boolean strictResources)
      throws UnableToCompleteException {
    TreeLogger logger = TreeLogger.NULL;
    compilerContext.getOptions().setEnforceStrictSourceResources(strictResources);
    compilerContext.getOptions().setEnforceStrictPublicResources(strictResources);
    ModuleDef emptyModule = ModuleDefLoader.loadFromClassPath(
        logger, compilerContext, "com.google.gwt.dev.cfg.testdata.merging.Empty");
    Resource sourceFile =
        emptyModule.findSourceFile("com/google/gwt/dev/cfg/testdata/merging/client/InOne.java");
    Resource publicFile = emptyModule.findPublicFile("Public.java");
    if (strictResources) {
      // Empty.gwt.xml did not register any source or public paths and the strictResource setting is
      // blocking the implicit addition of any default entries. So these resource searches should
      // fail.
      assertNull(sourceFile);
      assertNull(publicFile);
    } else {
      assertNotNull(sourceFile);
      assertNotNull(publicFile);
    }
  }

  public void testAllowsImpreciseResources() throws Exception {
    assertHonorsStrictResources(false);
  }

  public void testErrorReporting_badXml() throws UnableToCompleteException, IOException,
      IncompatibleLibraryVersionException {
    assertErrorsWhenLoading("com.google.gwt.dev.cfg.testdata.errors.BadModule",
        "Line 3, column 1 : Element type \"inherits\" must be followed by either "
            + "attribute specifications, \">\" or \"/>\".");
  }

  public void testErrorReporting_badLinker() throws UnableToCompleteException, IOException,
      IncompatibleLibraryVersionException {
    assertErrorsWhenLoading("com.google.gwt.dev.cfg.testdata.errors.BadLinker",
        "Line 2: Invalid linker name 'X'");
  }

  public void testErrorReporting_badProperty() throws UnableToCompleteException, IOException,
      IncompatibleLibraryVersionException {
    assertErrorsWhenLoading("com.google.gwt.dev.cfg.testdata.errors.BadProperty",
        "Line 2: Property 'X' not found");
  }

  public void testErrorReporting_badPropertyValue() throws UnableToCompleteException, IOException,
      IncompatibleLibraryVersionException {
    assertErrorsWhenLoading("com.google.gwt.dev.cfg.testdata.errors.BadPropertyValue",
        "Line 3: Value 'z' in not a valid value for property 'X'");
  }

  public void testErrorReporting_deepError() throws UnableToCompleteException, IOException,
      IncompatibleLibraryVersionException {
    UnitTestTreeLogger.Builder builder = new UnitTestTreeLogger.Builder();
    builder.setLowestLogLevel(TreeLogger.DEBUG);
    builder.expectDebug(
        "Loading inherited module 'com.google.gwt.dev.cfg.testdata.errors.DeepInheritsError0'",
        null);
    builder.expectDebug(
        "Loading inherited module 'com.google.gwt.dev.cfg.testdata.errors.DeepInheritsError1'",
        null);
    builder.expectDebug(
        "Loading inherited module 'com.google.gwt.dev.cfg.testdata.errors.BadModule'", null);
    builder.expectError("Line 3, column 1 : Element type \"inherits\" must be followed by either "
        + "attribute specifications, \">\" or \"/>\".", null);

    UnitTestTreeLogger logger = builder.createLogger();

    try {
      ModuleDefLoader.loadFromClassPath(
          logger, compilerContext, "com.google.gwt.dev.cfg.testdata.errors.DeepInheritsError0");
      fail("Should have failed to load module.");
    } catch (UnableToCompleteException e) {
      // failure is expected.
    }
    logger.assertLogEntriesContainExpected();
  }

  public void testErrorReporting_inheritNotFound() throws UnableToCompleteException, IOException,
      IncompatibleLibraryVersionException {
    assertErrorsWhenLoading("com.google.gwt.dev.cfg.testdata.errors.InheritNotFound",
        "Unable to find 'com/google/gwt/dev/cfg/testdata/NonExistentModule.gwt.xml' on your "
            + "classpath; could be a typo, or maybe you forgot to include a classpath entry "
            + "for source?");
  }

  public void testErrorReporting_invalidName() throws UnableToCompleteException, IOException,
      IncompatibleLibraryVersionException {
    assertErrorsWhenLoading("com.google.gwt.dev.cfg.testdata.errors.InvalidName",
        "Line 2: Invalid property name '123:33'");
  }

  public void testErrorReporting_multipleErrors() throws UnableToCompleteException, IOException,
      IncompatibleLibraryVersionException {
    assertErrorsWhenLoading("com.google.gwt.dev.cfg.testdata.errors.MultipleErrors",
        "Line 1: Unexpected attribute 'blah' in element 'module'");
  }

  public void testErrorReporting_unexpectedAttribute() throws UnableToCompleteException,
      IOException, IncompatibleLibraryVersionException {
    assertErrorsWhenLoading("com.google.gwt.dev.cfg.testdata.errors.UnexpectedAttribute",
        "Line 2: Unexpected attribute 'blah' in element 'inherits'");
  }

  public void testErrorReporting_unexpectedTag() throws UnableToCompleteException, IOException,
      IncompatibleLibraryVersionException {
    assertErrorsWhenLoading("com.google.gwt.dev.cfg.testdata.errors.UnexpectedTag",
        "Line 2: Unexpected element 'inherited'");
  }

  public void testLoadFromLibraryGroup() throws UnableToCompleteException, IOException,
      IncompatibleLibraryVersionException {
    PrintWriterTreeLogger logger = new PrintWriterTreeLogger();
    logger.setMaxDetail(TreeLogger.INFO);

    // Create the library zip file.
    File zipFile = File.createTempFile("FooLib", ".gwtlib");
    zipFile.deleteOnExit();

    // Put data in the library and save it.
    ZipLibraryWriter zipLibraryWriter = new ZipLibraryWriter(zipFile.getPath());
    zipLibraryWriter.setLibraryName("FooLib");
    MockResource userXmlResource = new MockResource("com/google/gwt/user/User.gwt.xml") {
        @Override
      public CharSequence getContent() {
        return "<module></module>";
      }
    };
    zipLibraryWriter.addBuildResource(userXmlResource);
    zipLibraryWriter.write();

    // Read data back from disk.
    ZipLibrary zipLibrary = new ZipLibrary(zipFile.getPath());

    // Prepare the LibraryGroup and ResourceLoader.
    compilerContext = compilerContextBuilder.libraryGroup(
        LibraryGroup.fromLibraries(Lists.<Library> newArrayList(zipLibrary), false)).build();
    ResourceLoader resourceLoader = ResourceLoaders.forPathAndFallback(Lists.newArrayList(zipFile),
        ResourceLoaders.forClassLoader(Thread.currentThread()));

    // Will throw an exception if com.google.gwt.user.User can't be found and parsed.
    ModuleDefLoader.loadFromResources(logger, compilerContext, "com.google.gwt.user.User",
        resourceLoader, false);
  }

  /**
   * Tests that the tree representation for a module is correct.
   */
  public void testLibraryTree() throws Exception {
    TreeLogger logger = TreeLogger.NULL;
    ModuleDef six = ModuleDefLoader.loadFromClassPath(
        logger, compilerContext, "com.google.gwt.dev.cfg.testdata.dependents.Six");

    Collection<String> sixActualDependencies =
        six.getDirectDependencies("com.google.gwt.dev.cfg.testdata.dependents.Six");
    Collection<String> fiveActualDependencies =
        six.getDirectDependencies("com.google.gwt.dev.cfg.testdata.dependents.Five");

    assertEquals(ImmutableSet.of("com.google.gwt.core.Core",
        "com.google.gwt.dev.cfg.testdata.dependents.Five"), sixActualDependencies);
    assertEquals("Contains " + fiveActualDependencies, ImmutableSet.of(
        "com.google.gwt.core.Core", "com.google.gwt.dev.cfg.testdata.dependents.One",
        "com.google.gwt.dev.cfg.testdata.dependents.Two",
        "com.google.gwt.dev.cfg.testdata.dependents.Four"), fiveActualDependencies);
  }

  /**
   * Test of merging multiple modules in the same package space.
   * This exercises the interaction of include, exclude, and skip attributes.
   */
  public void testModuleMerging() throws Exception {
    TreeLogger logger = TreeLogger.NULL;
    ModuleDef one = ModuleDefLoader.loadFromClassPath(
        logger, compilerContext, "com.google.gwt.dev.cfg.testdata.merging.One");
    assertNotNull(one.findSourceFile("com/google/gwt/dev/cfg/testdata/merging/client/InOne.java"));
    assertNotNull(one.findSourceFile("com/google/gwt/dev/cfg/testdata/merging/client/Shared.java"));
    assertNull(one.findSourceFile("com/google/gwt/dev/cfg/testdata/merging/client/InTwo.java"));
    assertNull(one.findSourceFile("com/google/gwt/dev/cfg/testdata/merging/client/Toxic.java"));

    ModuleDef two = ModuleDefLoader.loadFromClassPath(
        logger, compilerContext, "com.google.gwt.dev.cfg.testdata.merging.Two");
    assertNotNull(two.findSourceFile("com/google/gwt/dev/cfg/testdata/merging/client/InOne.java"));
    assertNotNull(two.findSourceFile("com/google/gwt/dev/cfg/testdata/merging/client/Shared.java"));
    assertNotNull(two.findSourceFile("com/google/gwt/dev/cfg/testdata/merging/client/InTwo.java"));
    assertNull(two.findSourceFile("com/google/gwt/dev/cfg/testdata/merging/client/Toxic.java"));

    ModuleDef three = ModuleDefLoader.loadFromClassPath(
        logger, compilerContext, "com.google.gwt.dev.cfg.testdata.merging.Three");
    assertNotNull(three.findSourceFile("com/google/gwt/dev/cfg/testdata/merging/client/InOne.java"));
    assertNotNull(three.findSourceFile("com/google/gwt/dev/cfg/testdata/merging/client/Shared.java"));
    assertNull(three.findSourceFile("com/google/gwt/dev/cfg/testdata/merging/client/InTwo.java"));
    assertNull(three.findSourceFile("com/google/gwt/dev/cfg/testdata/merging/client/Toxic.java"));
  }

  /**
   * The top level module has an invalid name.
   */
  public void testModuleNamingInvalid() {
    UnitTestTreeLogger.Builder builder = new UnitTestTreeLogger.Builder();
    builder.setLowestLogLevel(TreeLogger.ERROR);
    builder.expectError("Invalid module name: 'com.google.gwt.dev.cfg.testdata.naming.Invalid..Foo'", null);
    UnitTestTreeLogger logger = builder.createLogger();
    try {
      ModuleDefLoader.loadFromClassPath(
          logger, compilerContext, "com.google.gwt.dev.cfg.testdata.naming.Invalid..Foo");
      fail("Expected exception from invalid module name.");
    } catch (UnableToCompleteException expected) {
    }
    logger.assertLogEntriesContainExpected();
  }

  public void testModuleNamingValid() throws Exception {
    TreeLogger logger = TreeLogger.NULL;

    ModuleDef module;
    module = ModuleDefLoader.loadFromClassPath(
        logger, compilerContext, "com.google.gwt.dev.cfg.testdata.naming.Foo-test");
    assertNotNull(module.findSourceFile("com/google/gwt/dev/cfg/testdata/naming/client/Mock.java"));

    module = ModuleDefLoader.loadFromClassPath(
        logger, compilerContext, "com.google.gwt.dev.cfg.testdata.naming.7Foo");
    assertNotNull(module.findSourceFile("com/google/gwt/dev/cfg/testdata/naming/client/Mock.java"));

    module = ModuleDefLoader.loadFromClassPath(
        logger, compilerContext, "com.google.gwt.dev.cfg.testdata.naming.Nested7Foo");
    assertNotNull(module.findSourceFile("com/google/gwt/dev/cfg/testdata/naming/client/Mock.java"));

    module = ModuleDefLoader.loadFromClassPath(
        logger, compilerContext, "com.google.gwt.dev.cfg.testdata.naming.Nested7Foo");
    assertNotNull(module.findSourceFile("com/google/gwt/dev/cfg/testdata/naming/client/Mock.java"));
  }

  /**
   * The top level module has a valid name, but the inherited one does not.
   */
  public void testModuleNestedNamingInvalid() {
    UnitTestTreeLogger.Builder builder = new UnitTestTreeLogger.Builder();
    builder.setLowestLogLevel(TreeLogger.ERROR);
    builder.expectError("Invalid module name: 'com.google.gwt.dev.cfg.testdata.naming.Invalid..Foo'", null);
    UnitTestTreeLogger logger = builder.createLogger();
    try {
      ModuleDefLoader.loadFromClassPath(logger,
          compilerContext, "com.google.gwt.dev.cfg.testdata.naming.NestedInvalid", false);
      fail("Expected exception from invalid module name.");
    } catch (UnableToCompleteException expected) {
    }
    logger.assertLogEntriesContainExpected();
  }

  public void testRequiresStrictResources() throws Exception {
    assertHonorsStrictResources(true);
  }

  public void testResourcesVisible() throws Exception {
    TreeLogger logger = TreeLogger.NULL;
    ModuleDef one = ModuleDefLoader.loadFromClassPath(logger, compilerContext,
        "com.google.gwt.dev.cfg.testdata.merging.One");

    Set<String> visibleResourcePaths = one.getBuildResourceOracle().getPathNames();
    // Sees the logo.png image.
    assertTrue(visibleResourcePaths.contains(
        "com/google/gwt/dev/cfg/testdata/merging/resources/logo.png"));
    // But not the java source file.
    assertFalse(visibleResourcePaths.contains(
        "com/google/gwt/dev/cfg/testdata/merging/resources/NotAResource.java"));
  }

  public void testSeparateLibraryModuleReferences() throws UnableToCompleteException {
    compilerContext = compilerContextBuilder.compileMonolithic(false).build();
    ModuleDefLoader.loadFromClassPath(TreeLogger.NULL, compilerContext,
        "com.google.gwt.dev.cfg.testdata.separate.libraryone.LibraryOne", false);

    // The library writer was given the module and it's direct fileset module xml files as build
    // resources.
    assertEquals(Sets.newHashSet(
        "com/google/gwt/dev/cfg/testdata/separate/filesetone/FileSetOne.gwt.xml",
        "com/google/gwt/dev/cfg/testdata/separate/libraryone/LibraryOne.gwt.xml"),
        mockLibraryWriter.getBuildResourcePaths());
    // The library writer was given LibraryTwo as a dependency library.
    assertEquals(Sets.newHashSet("com.google.gwt.core.Core",
        "com.google.gwt.dev.cfg.testdata.separate.librarytwo.LibraryTwo"),
        mockLibraryWriter.getDependencyLibraryNames());
  }

  public void testSeparateLibraryName() throws UnableToCompleteException {
    compilerContext = compilerContextBuilder.compileMonolithic(false).build();
    ModuleDefLoader.loadFromClassPath(TreeLogger.NULL, compilerContext,
        "com.google.gwt.dev.cfg.testdata.separate.libraryone.LibraryOne", false);

    assertEquals("com.google.gwt.dev.cfg.testdata.separate.libraryone.LibraryOne",
        mockLibraryWriter.getLibraryName());
  }

  public void testSeparateModuleReferences() throws UnableToCompleteException {
    compilerContext = compilerContextBuilder.compileMonolithic(false).build();
    ModuleDef libraryOneModule = ModuleDefLoader.loadFromClassPath(TreeLogger.NULL, compilerContext,
        "com.google.gwt.dev.cfg.testdata.separate.libraryone.LibraryOne", false);

    // The module sees itself and it's direct fileset module as "target" modules.
    assertEquals(Sets.newHashSet("com.google.gwt.dev.cfg.testdata.separate.libraryone.LibraryOne",
        "com.google.gwt.dev.cfg.testdata.separate.filesetone.FileSetOne"),
        libraryOneModule.getTargetLibraryCanonicalModuleNames());
    // The module sees the referenced library module as a "library" module.
    assertEquals(Sets.newHashSet("com.google.gwt.core.Core",
        "com.google.gwt.dev.cfg.testdata.separate.librarytwo.LibraryTwo"),
        libraryOneModule.getExternalLibraryCanonicalModuleNames());
  }

  public void testSeparateModuleResourcesLibraryOne() throws UnableToCompleteException {
    compilerContext = compilerContextBuilder.compileMonolithic(false).build();
    ModuleDef libraryOneModule = ModuleDefLoader.loadFromClassPath(TreeLogger.NULL, compilerContext,
        "com.google.gwt.dev.cfg.testdata.separate.libraryone.LibraryOne", false);

    // Includes own source.
    assertNotNull(libraryOneModule.findSourceFile(
        "com/google/gwt/dev/cfg/testdata/separate/libraryone/client/LibraryOne.java"));
    // Cascades to include the subtree of fileset sources.
    assertNotNull(libraryOneModule.findSourceFile(
        "com/google/gwt/dev/cfg/testdata/separate/filesetone/client/FileSetOne.java"));
    // Does not include source from referenced libraries.
    assertNull(libraryOneModule.findSourceFile(
        "com/google/gwt/dev/cfg/testdata/separate/librarytwo/client/LibraryTwo.java"));
  }

  public void testSeparateRootFilesetFail() {
    compilerContext = compilerContextBuilder.compileMonolithic(false).build();
    TreeLogger logger = TreeLogger.NULL;
    try {
      ModuleDefLoader.loadFromClassPath(logger,
          compilerContext, "com.google.gwt.dev.cfg.testdata.separate.filesetone.FileSetOne", false);
      fail("Expected a fileset loaded as the root of a module tree to fail, but it didn't.");
    } catch (UnableToCompleteException e) {
      // Expected behavior.
    }
  }

  public void testWritesTargetLibraryProperties() throws UnableToCompleteException {
    compilerContext = compilerContextBuilder.compileMonolithic(false).build();
    ModuleDef libraryOneModule = ModuleDefLoader.loadFromClassPath(TreeLogger.NULL, compilerContext,
        "com.google.gwt.dev.cfg.testdata.separate.libraryone.LibraryOne", false);

    // Library one sees all defined values for the "libraryTwoProperty" binding property and knows
    // which one was defined in this target library.
    for (BindingProperty bindingProperty :
        libraryOneModule.getProperties().getBindingProperties()) {
      if (!bindingProperty.getName().equals("libraryTwoProperty")) {
        continue;
      }
      assertEquals(Sets.newHashSet(bindingProperty.getDefinedValues()),
          Sets.newHashSet("yes", "no", "maybe"));
    }

    // Library one sees all defined values for the "libraryTwoConfigProperty" property and knows
    // which one was defined in this target library.
    for (ConfigurationProperty configurationProperty :
        libraryOneModule.getProperties().getConfigurationProperties()) {
      if (!configurationProperty.getName().equals("libraryTwoConfigProperty")) {
        continue;
      }
      assertEquals(Sets.newHashSet(configurationProperty.getValues()), Sets.newHashSet("false"));
      assertEquals(Sets.newHashSet(configurationProperty.getTargetLibraryValues()),
          Sets.newHashSet("false"));
    }
  }

  private void assertErrorsWhenLoading(String moduleName, String... errorMessages) {
    UnitTestTreeLogger.Builder builder = new UnitTestTreeLogger.Builder();
    builder.setLowestLogLevel(TreeLogger.WARN);
    for (String errorMessage : errorMessages) {
      builder.expectError(errorMessage, null);

      UnitTestTreeLogger logger = builder.createLogger();

      try {
        ModuleDefLoader.loadFromClassPath(
            logger, compilerContext, moduleName);
        fail("Should have failed to load module.");
      } catch (UnableToCompleteException e) {
        // failure is expected.
      }
      logger.assertCorrectLogEntries();
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ModuleDefLoader.getModulesCache().clear();
    compilerContextBuilder = new CompilerContext.Builder();
    compilerContext = compilerContextBuilder.libraryWriter(mockLibraryWriter).build();
  }
}
