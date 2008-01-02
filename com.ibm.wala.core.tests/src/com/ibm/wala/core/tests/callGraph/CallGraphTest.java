/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.core.tests.callGraph;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.util.TestConstants;
import com.ibm.wala.core.tests.util.WalaTestCase;
import com.ibm.wala.eclipse.util.CancelException;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphStats;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.Atom;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.debug.Trace;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;
import com.ibm.wala.util.warnings.Warnings;

/**
 * Tests for Call Graph construction
 * 
 * @author sfink
 */

public class CallGraphTest extends WalaTestCase {


  public static void main(String[] args) {
    justThisTest(CallGraphTest.class);
  }

  public CallGraphTest(String arg0) {
    super(arg0);
  }
  

  public void testJava_cup() throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope(TestConstants.JAVA_CUP, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchy.make(scope);
    Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha,
        TestConstants.JAVA_CUP_MAIN);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    doCallGraphs(options, new AnalysisCache(), cha, scope,  useShortProfile(), false, true);
  }

  public void testBcelVerifier() throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope(TestConstants.BCEL, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchy.make(scope);
    Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha,
        TestConstants.BCEL_VERIFIER_MAIN);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    doCallGraphs(options, new AnalysisCache(), cha, scope, useShortProfile(), false);
  }


  public void testJLex() throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope(TestConstants.JLEX, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchy.make(scope);
    Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util
        .makeMainEntrypoints(scope, cha, TestConstants.JLEX_MAIN);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    doCallGraphs(options,new AnalysisCache(), cha, scope,  useShortProfile(), false);
  }

  public void testCornerCases() throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope(TestConstants.WALA_TESTDATA, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchy.make(scope);
    Iterable<Entrypoint> entrypoints = new AllApplicationEntrypoints(scope, cha);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    doCallGraphs(options,new AnalysisCache(), cha, scope, useShortProfile(), false);

    // we expect a warning or two about class Abstract1, which has no concrete
    // subclasses
    String ws = Warnings.asString();
    assertTrue("failed to report a warning about Abstract1", ws.indexOf("cornerCases/Abstract1") > -1);

    // we do not expect a warning about class Abstract2, which has a concrete
    // subclasses
    assertTrue("reported a warning about Abstract2", ws.indexOf("cornerCases/Abstract2") == -1);
  }

  //
  // public void testSPECjvm98() {
  // AnalysisScope scope = CGTUtils.makeJ2SEAnalysisScope(Config.SPECJVM);
  //
  // // temporary hack because 1.5 libraries still cause grief
  // if (scope.isJava15Libraries()) {
  // scope = CGTUtils.makeJ2EEAnalysisScope(Config.SPECJVM);
  // }
  //
  // WarningSet warnings = new WarningSet();
  // ClassHierarchy cha = ClassHierarchy.buildClassHierarchy(scope, warnings);
  // Entrypoints entrypoints =
  // com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha,
  // Config.SPECJVM_MAIN);
  // AnalysisOptions options = CGTUtils.makeAnalysisOptions(scope, entrypoints);
  //
  // Trace.println("SPECjvm98 set up warnings:\n");
  // Trace.print(warnings.toString());
  //
  // doCallGraphs(options, cha, scope, Config.SPECJVM_DCG, false, false);
  // }
  //
  public void testHello() throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope(TestConstants.HELLO, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchy.make(scope);
    Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha,
        TestConstants.HELLO_MAIN);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    doCallGraphs(options, new AnalysisCache(), cha, scope, false, false);
  }

  public void testRecursion() throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope(TestConstants.WALA_TESTDATA, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchy.make(scope);
    Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha,
        TestConstants.RECURSE_MAIN);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    doCallGraphs(options, new AnalysisCache(),cha, scope, useShortProfile(), false);
  }

  public void testHelloAllEntrypoints() throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope(TestConstants.HELLO, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchy.make(scope);
    Iterable<Entrypoint> entrypoints = new AllApplicationEntrypoints(scope, cha);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    doCallGraphs(options, new AnalysisCache(),cha, scope, useShortProfile(), false);
  }

  public void testIO() throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope("primordial.txt", CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchy.make(scope);
    Iterable<Entrypoint> entrypoints = makePrimordialPublicEntrypoints(scope, cha, "java/io");
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    CallGraphTestUtil.buildZeroCFA(options, new AnalysisCache(),cha, scope, false);
  }

  public static Iterable<Entrypoint> makePrimordialPublicEntrypoints(AnalysisScope scope, ClassHierarchy cha, String pkg) {
    final HashSet<Entrypoint> result = HashSetFactory.make();
    for (IClass clazz : cha) {

      if (clazz.getName().toString().indexOf(pkg) != -1 && !clazz.isInterface() && !clazz.isAbstract()) {
        for (IMethod method : clazz.getDeclaredMethods()) {
          if (method.isPublic() && !method.isAbstract()) {
            System.out.println("Entry:" + method.getReference());
            result.add(new DefaultEntrypoint(method, cha));
          }
        }
      }
    }
    return new Iterable<Entrypoint>() {
      public Iterator<Entrypoint> iterator() {
        return result.iterator();
      }
    };
  }

  public void testPrimordial() throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    if (useShortProfile()) {
      return;
    }

    AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope("primordial.txt", "GUIExclusions.txt");
    ClassHierarchy cha = ClassHierarchy.make(scope);
    Iterable<Entrypoint> entrypoints = makePrimordialMainEntrypoints(scope, cha);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    CallGraphTestUtil.buildZeroCFA(options,new AnalysisCache(), cha, scope, false);
  }

  /**
   * make main entrypoints, even in the primordial loader.
   */
  public static Iterable<Entrypoint> makePrimordialMainEntrypoints(AnalysisScope scope, ClassHierarchy cha) {
    final Atom mainMethod = Atom.findOrCreateAsciiAtom("main");
    final HashSet<Entrypoint> result = HashSetFactory.make();
    for (IClass klass : cha) {
      MethodReference mainRef = MethodReference.findOrCreate(klass.getReference(), mainMethod, Descriptor
          .findOrCreateUTF8("([Ljava/lang/String;)V"));
      IMethod m = klass.getMethod(mainRef.getSelector());
      if (m != null) {
        result.add(new DefaultEntrypoint(m, cha));
      }
    }
    return new Iterable<Entrypoint>() {
      public Iterator<Entrypoint> iterator() {
        return result.iterator();
      }
    };
  }


  public static void doCallGraphs(AnalysisOptions options, AnalysisCache cache, ClassHierarchy cha, AnalysisScope scope, 
      boolean stopAfterZeroCFA, boolean stopAfterZeroContainerCFA) throws IllegalArgumentException, CancelException {
    doCallGraphs(options, cache, cha, scope, stopAfterZeroCFA, stopAfterZeroContainerCFA, false);
  }
  
  /**
   * TODO: refactor this to avoid excessive code bloat.
   * @throws CancelException 
   * @throws IllegalArgumentException 
   */
  public static void doCallGraphs(AnalysisOptions options, AnalysisCache cache, ClassHierarchy cha, AnalysisScope scope, 
      boolean stopAfterZeroCFA, boolean stopAfterZeroContainerCFA, boolean testPAToString) throws IllegalArgumentException, CancelException {

    // ///////////////
    // // RTA /////
    // ///////////////
    CallGraph cg = CallGraphTestUtil.buildRTA(options,cache, cha, scope);
    try {
      GraphIntegrity.check(cg);
    } catch (UnsoundGraphException e1) {
      e1.printStackTrace();
      assertTrue(e1.getMessage(), false);
    }
    
    Set<MethodReference> rtaMethods = CallGraphStats.collectMethods(cg);
    Trace.println("RTA methods reached: " + rtaMethods.size());
    Trace.println(CallGraphStats.getStats(cg));
    Trace.println("RTA warnings:\n");

    // ///////////////
    // // 0-CFA /////
    // ///////////////
    cg = CallGraphTestUtil.buildZeroCFA(options, cache,cha, scope, testPAToString);

    // FIXME: annoying special cases caused by clone2assign mean using
    // the rta graph for proper graph subset checking does not work.
    // (note that all the other such checks do use proper graph subset)
    Graph<MethodReference> squashZero = checkCallGraph(cg, null, rtaMethods, "0-CFA");

    // test Pretransitive 0-CFA
    // not currently supported
    // warnings = new WarningSet();
    // options.setUsePreTransitiveSolver(true);
    // CallGraph cgP = CallGraphTestUtil.buildZeroCFA(options, cha, scope,
    // warnings);
    // options.setUsePreTransitiveSolver(false);
    // Graph squashPT = checkCallGraph(warnings, cgP, squashZero, null, "Pre-T
    // 1");
    // checkCallGraph(warnings, cg, squashPT, null, "Pre-T 2");

    if (stopAfterZeroCFA) {
      return;
    }
    // ///////////////
    // // 0-1-CFA ///
    // ///////////////
    cg = CallGraphTestUtil.buildZeroOneCFA(options, cache,cha, scope, testPAToString);
    Graph<MethodReference> squashZeroOne = checkCallGraph(cg, squashZero, null, "0-1-CFA");

    // ///////////////////////////////////////////////////
    // // 0-CFA augmented to disambiguate containers ///
    // ///////////////////////////////////////////////////
    cg = CallGraphTestUtil.buildZeroContainerCFA(options,cache, cha, scope);
    Graph<MethodReference> squashZeroContainer = checkCallGraph(cg, squashZero, null, "0-Container-CFA");

    if (stopAfterZeroContainerCFA)
      return;

    // ///////////////////////////////////////////////////
    // // 0-1-CFA augmented to disambiguate containers ///
    // ///////////////////////////////////////////////////
    cg = CallGraphTestUtil.buildZeroOneContainerCFA(options, cache,cha, scope);
    checkCallGraph(cg, squashZeroContainer, null, "0-1-Container-CFA");
    checkCallGraph(cg, squashZeroOne, null, "0-1-Container-CFA");

    // test ICFG
    checkICFG(cg);
    return;
    // /////////////
    // // 1-CFA ///
    // /////////////
    // warnings = new WarningSet();
    // cg = buildOneCFA();

  }

  /**
   * Check properties of the InterproceduralCFG
   * 
   * @param cg
   */
  private static void checkICFG(CallGraph cg) {
    InterproceduralCFG icfg = new InterproceduralCFG(cg);

    try {
      GraphIntegrity.check(icfg);
    } catch (UnsoundGraphException e) {
      e.printStackTrace();
      assertTrue(false);
    }

    // perform a little icfg exercise
    int count = 0;
    for (Iterator<BasicBlockInContext<ISSABasicBlock>> it = icfg.iterator(); it.hasNext();) {
      BasicBlockInContext<ISSABasicBlock> bb = it.next();
      if (icfg.hasCall((BasicBlockInContext<ISSABasicBlock>) bb)) {
        count++;
      }
    }
  }

  /**
   * Check consistency of a callgraph, and check that this call graph is a
   * subset of a super-graph
   * 
   * @param warnings
   *            object to track warnings
   * @param cg
   * @param superCG
   * @param superMethods
   * @param thisAlgorithm
   * @return a squashed version of cg
   */
  private static Graph<MethodReference> checkCallGraph(CallGraph cg, Graph<MethodReference> superCG,
      Set<MethodReference> superMethods, String thisAlgorithm) {
    try {
      GraphIntegrity.check(cg);
    } catch (UnsoundGraphException e1) {
      assertTrue(e1.getMessage(), false);
    }
    Set<MethodReference> callGraphMethods = CallGraphStats.collectMethods(cg);
    Trace.println(thisAlgorithm + " methods reached: " + callGraphMethods.size());
    Trace.println(CallGraphStats.getStats(cg));

    Graph<MethodReference> thisCG = com.ibm.wala.ipa.callgraph.impl.Util.squashCallGraph(thisAlgorithm, cg);

    if (superCG != null) {
      com.ibm.wala.ipa.callgraph.impl.Util.checkGraphSubset(superCG, thisCG);
    } else {
      if (!superMethods.containsAll(callGraphMethods)) {
        Set<MethodReference> temp = HashSetFactory.make();
        temp.addAll(callGraphMethods);
        temp.removeAll(superMethods);
        System.err.println("Violations");
        for (MethodReference m : temp) {
          System.err.println(m);
        }
        Assertions.UNREACHABLE();
      }
    }

    return thisCG;
  }

}
