/******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *****************************************************************************/
package com.ibm.wala.cast.ir.ssa;

import java.util.Collection;
import java.util.Collections;

import com.ibm.wala.ssa.SSAAbstractUnaryInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.TypeReference;

/**
 * This instruction represents iterating through the properties of its receiver object. The use represents an object,
 * and the l-value represents one of a sequence of property names, suitable for yse with trhe appropriate
 * AbstractReflectiveGet sub-class.
 * 
 * Iterating across the fields or properties of a given object is a common idiom in scripting languages, which is why
 * the IR has first-class support for it.
 * 
 * @author Julian Dolby (dolby@us.ibm.com)
 */
public class EachElementGetInstruction extends SSAAbstractUnaryInstruction {

  public EachElementGetInstruction(int lValue, int objectRef) {
    super(lValue, objectRef);
  }

  public SSAInstruction copyForSSA(int[] defs, int[] uses) {
    return new EachElementGetInstruction((defs == null) ? getDef(0) : defs[0], (uses == null) ? getUse(0) : uses[0]);
  }

  public String toString(SymbolTable symbolTable) {
    return getValueString(symbolTable, getDef(0)) + " = a property name of " + getValueString(symbolTable, getUse(0));
  }

  public void visit(IVisitor v) {
    ((AstInstructionVisitor) v).visitEachElementGet(this);
  }

  public Collection<TypeReference> getExceptionTypes() {
    return Collections.emptySet();
  }
}
