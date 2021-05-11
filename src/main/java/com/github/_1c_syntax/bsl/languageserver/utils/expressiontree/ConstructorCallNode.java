/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

public class ConstructorCallNode extends AbstractCallNode {

  private final BslExpression typeName;

  private ConstructorCallNode(BslExpression typeName){
    this.typeName = typeName;
  }

  public boolean isStaticallyTyped(){
    return typeName instanceof TerminalSymbolNode && typeName.getNodeType() == ExpressionNodeType.LITERAL;
  }

  public BslExpression getTypeName(){
    return typeName;
  }

  public static ConstructorCallNode createStatic(TerminalSymbolNode typeName){
    return new ConstructorCallNode(typeName);
  }

  public static ConstructorCallNode createDynamic(BslExpression typeNameExpression){
    return new ConstructorCallNode(typeNameExpression);
  }

}
