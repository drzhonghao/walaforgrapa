/*
 *  Copyright (c) 2013,
 *      Tobias Blaschke <code@tobiasblaschke.de>
 *  All rights reserved.

 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  3. The names of the contributors may not be used to endorse or promote
 *     products derived from this software without specific prior written
 *     permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 */
package com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa;

import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentStarters;
import com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentStarters.StartInfo;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.MicroModel;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.stubs.ExternalModel;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.stubs.UnknownTargetModel;

import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.dalvik.util.AndroidComponent;
import com.ibm.wala.dalvik.util.AndroidTypes;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.classLoader.CallSiteReference;

import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.ClassLoaderReference;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.summaries.SummarizedMethod;

import com.ibm.wala.util.collections.EmptyIterator;
import com.ibm.wala.util.strings.Atom;

import com.ibm.wala.dalvik.util.AndroidEntryPointManager;

import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.FieldReference;

import java.util.Iterator;
import com.ibm.wala.util.collections.EmptyIterator;
import java.util.Set;

import com.ibm.wala.util.CancelException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

/**
 *  An {@link SSAContextInterpreter} that redirects functions that start Android-Components.
 *
 *  The Starter-Functions (listed in IntentStarters) are replaced by a Model that emulates Android Lifecycle
 *  based on their Target (Internal, External, ...): A wrapper around the single models is generated dynamically
 *  (by the models themselves) to resemble the signature of the replaced function.
 *
 *  Methods are replacement by generating a adapted Intermediate Representation of this function on every 
 *  occurrence of a call to it.
 * 
 *  @see    com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentContextSelector
 *  @see    com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentStarters
 *  @see    com.ibm.wala.dalvik.ipa.callgraph.androidModel.MicroModel
 *  @see    com.ibm.wala.dalvik.ipa.callgraph.androidModel.stubs.ExternalModel
 *
 *  @author Tobias Blaschke <code@tobiasblaschke.de>
 *  @since  2013-10-14
 */
public class IntentContextInterpreter implements SSAContextInterpreter {
    private static final Logger logger = LoggerFactory.getLogger(IntentContextInterpreter.class);
 
    private final IntentStarters intentStarters;
    private final IClassHierarchy cha;
    private final AnalysisOptions options;
    private final AnalysisCache cache;

    public IntentContextInterpreter(IClassHierarchy cha, final AnalysisOptions options, final AnalysisCache cache) {
        this.cha = cha;
        this.options = options;
        this.cache = cache;
        this.intentStarters = new IntentStarters(cha);
    }

    /**
     *  Generates an adapted IR of the managed functions on each call.
     *
     *  @param  node    The function to create the IR of
     *  @throws IllegalArgumentException on a node of null
     */
    @Override
    public IR getIR(CGNode node) {
        if (node == null) {
            throw new IllegalArgumentException("node is null");
        }
        assert understands(node);   // Should already have been checked before
        {
            // TODO: CACHE!
            final Context ctx = node.getContext();


            if (ctx.get(Intent.INTENT_KEY) != null) {
                try { // Translate CancelException to IllegalStateException
                final Intent inIntent = (Intent) ctx.get(Intent.INTENT_KEY);                // Intent without overrides
                final Intent intent = AndroidEntryPointManager.MANAGER.getIntent(inIntent); // Apply overrides
                final Atom target = intent.action;
                final IMethod method = node.getMethod();
                final TypeReference callingClass = node.getMethod().getReference().getDeclaringClass(); // TODO: This may not necessarily fit!

                final Intent.IntentType type = intent.getType();
                final IR ir;

                logger.info("Generating IR for {} in {} as {}", node.getMethod().getName(), inIntent, intent);
               
                // TODO: Deduplicate
                if (type == Intent.IntentType.INTERNAL_TARGET) {
                    final MicroModel model = new MicroModel(this.cha, this.options, this.cache, target);
                    final SummarizedMethod override = model.getMethodAs(method.getReference(), callingClass, intentStarters.getInfo(method.getReference()), node);
                    ir = override.makeIR(ctx, this.options.getSSAOptions());
                } else if (type == Intent.IntentType.EXTERNAL_TARGET) {
                    final AndroidComponent targetComponent;
                    if (intent.getComponent() != null) {
                        targetComponent = intent.getComponent();
                    } else {
                        assert(intentStarters.getInfo(method.getReference()) != null) : "IntentInfo is null! Understands should" +
                                                    " not have dispatched here - Every Starter should have an StartInfo...";
                        final Set<AndroidComponent> possibleTargets = intentStarters.getInfo(method.getReference()).getComponentsPossible(); 
                        if (possibleTargets.size() == 1) {
                            final Iterator<AndroidComponent> it = possibleTargets.iterator();
                            targetComponent = it.next();
                        } else {
                            // TODO: Go interactive and ask user?
                            final Iterator<AndroidComponent> it = possibleTargets.iterator();
                            targetComponent = it.next();
                            logger.error("Unable to determine the exact type of component of the function {} calls." +
                                    "Possible targets are {} we'll assume {} for now in " + 
                                    "order to not break fatally here.", method, possibleTargets, targetComponent);

                        }
                    }
                    logger.debug("Generating ExternalModel for {} as {}", targetComponent, method);
                    final ExternalModel model = new ExternalModel(this.cha, this.options, this.cache, targetComponent);
                    final SummarizedMethod override = model.getMethodAs(method.getReference(), callingClass, intentStarters.getInfo(method.getReference()), node);
                    ir = override.makeIR(ctx, this.options.getSSAOptions());
                } else if (type == Intent.IntentType.STANDARD_ACTION) { // TODO:        Handle as such!
                    logger.warn("Still handling STANDARD_ACTION as UNKONOWN_TARGET...");
                    final AndroidComponent targetComponent;
                    if (intent.getComponent() != null) {
                        targetComponent = intent.getComponent();
                    } else {
                        assert(intentStarters.getInfo(method.getReference()) != null) : "IntentInfo is null! Understands should" +
                                                    " not have dispatched here - Every Starter should have an StartInfo...";
                        final Set<AndroidComponent> possibleTargets = intentStarters.getInfo(method.getReference()).getComponentsPossible(); 
                        if (possibleTargets.size() == 1) {
                            final Iterator<AndroidComponent> it = possibleTargets.iterator();
                            targetComponent = it.next();
                        } else {
                            // TODO: Go interactive and ask user?
                            final Iterator<AndroidComponent> it = possibleTargets.iterator();
                            targetComponent = it.next();
                            logger.error("Unable to determine the exact type of component of the function {} calls." +
                                    "Possible targets are {} we'll assume {} for now in " + 
                                    "order to not break fatally here.", method, possibleTargets, targetComponent);
                        }
                    }
                    logger.debug("Generating UnknownTargetModel for {} as {}", targetComponent, method);
                    final UnknownTargetModel model = new UnknownTargetModel(this.cha, this.options, this.cache, targetComponent);
                    final SummarizedMethod override = model.getMethodAs(method.getReference(), callingClass, intentStarters.getInfo(method.getReference()), node);
                    ir = override.makeIR(ctx, this.options.getSSAOptions());
                } else if (type == Intent.IntentType.UNKNOWN_TARGET) {
                    logger.warn("Target of Intent still not known when generating IR...");
                    final AndroidComponent targetComponent;
                    if (intent.getComponent() != null) {
                        targetComponent = intent.getComponent();
                    } else {
                        assert(intentStarters.getInfo(method.getReference()) != null) : "IntentInfo is null! Understands should" +
                                                    " not have dispatched here - Every Starter should have an StartInfo...";
                        final Set<AndroidComponent> possibleTargets = intentStarters.getInfo(method.getReference()).getComponentsPossible(); 
                        if (possibleTargets.size() == 1) {
                            final Iterator<AndroidComponent> it = possibleTargets.iterator();
                            targetComponent = it.next();
                        } else {
                            // TODO: Go interactive and ask user?
                            final Iterator<AndroidComponent> it = possibleTargets.iterator();
                            targetComponent = it.next();
                            logger.error("Unable to determine the exact type of component of the function {} calls." +
                                    "Possible targets are {} we'll assume {} for now in " +
                                    "order to not break fatally here.", method, possibleTargets, targetComponent);
                        }
                    }
                    logger.debug("Generating UnknownTargetModel for {} as {}", targetComponent, method);
                    final UnknownTargetModel model = new UnknownTargetModel(this.cha, this.options, this.cache, targetComponent);
                    final SummarizedMethod override = model.getMethodAs(method.getReference(), callingClass, intentStarters.getInfo(method.getReference()), node);
                    ir = override.makeIR(ctx, this.options.getSSAOptions());
                } else {
                    throw new java.lang.UnsupportedOperationException("The Intent-Type " + type + " is not known to IntentContextInterpreter");
                }
                return ir;
                } catch (CancelException e) {
                    throw new IllegalStateException("The operation was canceled.", e);
                }
            } else {
                logger.error("No target: IntentContextSelector didn't add an IntentContext to this call.");
                return null;
            }
        }
    }

    /**
     *  If the function associated with the node is handled by this class.
     *
     *  @throws IllegalArgumentException if the given node is null
     */
    @Override 
    public boolean understands(CGNode node) {
        if (node == null) {
            throw new IllegalArgumentException("node is null");
        }
        final MethodReference target = node.getMethod().getReference();
        return (
                intentStarters.isStarter(target) 
        );
    }

    @Override
    public Iterator<NewSiteReference> iterateNewSites(CGNode node) {
        if (node == null) {
            throw new IllegalArgumentException("node is null");
        }
        assert understands(node);   // Should already have been checked before
        {
            logger.info("My new site for {} in {}", node.getMethod(), node.getContext());
            final IR ir = getIR(node); // Speeeed
            return ir.iterateNewSites();
        }
    }

    @Override
    public Iterator<CallSiteReference> iterateCallSites(CGNode node) {
        if (node == null) {
            throw new IllegalArgumentException("node is null");
        }
        assert understands(node);   // Should already have been checked before
        {
            logger.info("My call sites");
            final IR ir = getIR(node); // Speeeed
            return ir.iterateCallSites();
        }
    }

    //
    // Satisfy the rest of the interface
    //
    @Override
    public ControlFlowGraph<SSAInstruction, ISSABasicBlock> getCFG(CGNode node) {
        assert understands(node);
        return getIR(node).getControlFlowGraph();
    }

    @Override
    public int getNumberOfStatements(CGNode node) {
        assert understands(node);
        return getIR(node).getInstructions().length;
    }

    @Override
    public DefUse getDU(CGNode node) {
        assert understands(node);
        return new DefUse(getIR(node));
    }

    @Override
    public boolean recordFactoryType(CGNode node, IClass klass) {
        //assert understands(node);
        this.logger.error("FATAL: recordFactoryType does not understand Node " + node.toString());
        return false;
    }

    @Override
    public Iterator<FieldReference> iterateFieldsWritten(CGNode node) {
        assert understands(node);
        return EmptyIterator.instance();
    }

    @Override
    public Iterator<FieldReference> iterateFieldsRead(CGNode node) {
        return EmptyIterator.instance();
    }
}
