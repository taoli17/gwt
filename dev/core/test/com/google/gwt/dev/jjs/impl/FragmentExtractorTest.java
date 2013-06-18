/*
 * Copyright 2013 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.dev.jjs.impl;

import com.google.gwt.dev.jjs.Correlation;
import com.google.gwt.dev.jjs.Correlation.Axis;
import com.google.gwt.dev.jjs.CorrelationFactory;
import com.google.gwt.dev.jjs.SourceInfo;
import com.google.gwt.dev.jjs.SourceOrigin;
import com.google.gwt.dev.jjs.ast.JClassType;
import com.google.gwt.dev.jjs.ast.JConstructor;
import com.google.gwt.dev.jjs.ast.JDeclaredType;
import com.google.gwt.dev.jjs.ast.JField;
import com.google.gwt.dev.jjs.ast.JMethod;
import com.google.gwt.dev.jjs.impl.FragmentExtractor.LivenessPredicate;
import com.google.gwt.dev.jjs.impl.FragmentExtractor.NothingAlivePredicate;
import com.google.gwt.dev.js.ast.JsExprStmt;
import com.google.gwt.dev.js.ast.JsFunction;
import com.google.gwt.dev.js.ast.JsInvocation;
import com.google.gwt.dev.js.ast.JsName;
import com.google.gwt.dev.js.ast.JsNameRef;
import com.google.gwt.dev.js.ast.JsProgram;
import com.google.gwt.dev.js.ast.JsRootScope;
import com.google.gwt.dev.js.ast.JsStatement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link FragmentExtractor}.
 */
public class FragmentExtractorTest extends JJSTestBase {

  private static final JsName DEFINE_SEED_NAME = new JsName(null, "defineSeed", "defineSeed");

  private static class MockSourceInfo implements SourceInfo {

    @Override
    public void addCorrelation(Correlation c) {}

    @Override
    public Correlation getCorrelation(Axis axis) {
      return null;
    }

    @Override
    public Correlation[] getCorrelations() {
      return null;
    }

    @Override
    public CorrelationFactory getCorrelator() {
      return null;
    }

    @Override
    public int getEndPos() {
      return 0;
    }

    @Override
    public String getFileName() {
      return null;
    }

    @Override
    public SourceOrigin getOrigin() {
      return null;
    }

    @Override
    public int getStartLine() {
      return 0;
    }

    @Override
    public int getStartPos() {
      return 0;
    }

    @Override
    public SourceInfo makeChild() {
      return null;
    }

    @Override
    public SourceInfo makeChild(SourceOrigin origin) {
      return null;
    }
  }

  private static class MockJavaToJavaScriptMap implements JavaToJavaScriptMap {

    @Override
    public JsName nameForMethod(JMethod method) {
      return null;
    }

    @Override
    public JsName nameForType(JClassType type) {
      return null;
    }

    @Override
    public JField nameToField(JsName name) {
      return null;
    }

    @Override
    public JMethod nameToMethod(JsName name) {
      return null;
    }

    @Override
    public JClassType nameToType(JsName name) {
      return null;
    }

    @Override
    public JClassType typeForStatement(JsStatement stat) {
      return null;
    }

    @Override
    public JMethod vtableInitToMethod(JsStatement stat) {
      return null;
    }
  }

  private static class MockLivenessPredicate implements LivenessPredicate {

    @Override
    public boolean isLive(JDeclaredType type) {
      return false;
    }

    @Override
    public boolean isLive(JField field) {
      return false;
    }

    @Override
    public boolean isLive(JMethod method) {
      return false;
    }

    @Override
    public boolean isLive(String literal) {
      return false;
    }

    @Override
    public boolean miscellaneousStatementsAreLive() {
      return false;
    }
  }

  /**
   * Invokes FragmentExtractor with a fragment description claiming that Bar was not made live by
   * the current fragment, but that it has a constructor which *was* made live. Verifies that the
   * defineSeed invocation from the global JS block *is* included in the extracted statement output.
   */
  public void testDefineSeed_DeadTypeLiveConstructor() {
    FragmentExtractor fragmentExtractor;
    LivenessPredicate constructorLivePredicate;

    // Environment setup.
    {
      SourceInfo nullSourceInfo = new MockSourceInfo();
      final JClassType barType = new JClassType(nullSourceInfo, "Bar", false, false);
      final JsName barConstructorName = new JsName(null, "Bar", "Bar");
      final JConstructor barConstructor = new JConstructor(nullSourceInfo, barType);
      Map<String, JsFunction> functionsByName = new HashMap<String, JsFunction>();
      functionsByName.put("SeedUtil.defineSeed",
          new JsFunction(nullSourceInfo, new JsRootScope(), DEFINE_SEED_NAME));

      final JsExprStmt defineSeedStatement = createDefineSeedStatement(barConstructorName);

      JsProgram jsProgram = new JsProgram();
      jsProgram.setIndexedFunctions(functionsByName);
      // Defines the entirety of the JS program being split, to be the one defineSeed statement.
      jsProgram.getGlobalBlock().getStatements().add(defineSeedStatement);

      JavaToJavaScriptMap map = new MockJavaToJavaScriptMap() {
          @Override
        public JMethod nameToMethod(JsName name) {
          if (name == barConstructorName) {
            // Finds the Bar constructor by name.
            return barConstructor;
          }
          return null;
        }

          @Override
        public JClassType typeForStatement(JsStatement statement) {
          if (statement == defineSeedStatement) {
            // Indicates that Bar is the type associated with the defineSeed statement.
            return barType;
          }
          return null;
        }
      };
      fragmentExtractor = new FragmentExtractor(null, jsProgram, map);
      constructorLivePredicate = new MockLivenessPredicate() {
          @Override
        public boolean isLive(JDeclaredType type) {
          // Claims that Bar is not made live by the current fragment.
          return false;
        }

          @Override
        public boolean isLive(JMethod method) {
          // Claims that the bar Constructor *is* made live by the current fragment.
          return method == barConstructor;
        }
      };
    }

    List<JsStatement> extractedStatements =
        fragmentExtractor.extractStatements(constructorLivePredicate, new NothingAlivePredicate());

    // Asserts that the single defineSeed statement was included in the extraction output.
    assertEquals(1, extractedStatements.size());
    JsStatement defineSeedStatement = extractedStatements.get(0);
    assertTrue(defineSeedStatement.toString().contains("defineSeed"));
  }

  private JsExprStmt createDefineSeedStatement(final JsName barConstructorName) {
    SourceInfo nullSourceInfo = new MockSourceInfo();
    JsInvocation defineSeedInvocation = new JsInvocation(nullSourceInfo);
    defineSeedInvocation.getArguments().add(new JsNameRef(nullSourceInfo, barConstructorName));
    defineSeedInvocation.setQualifier(new JsNameRef(nullSourceInfo, DEFINE_SEED_NAME));
    final JsExprStmt defineSeedStatement = new JsExprStmt(nullSourceInfo, defineSeedInvocation);
    return defineSeedStatement;
  }
}
