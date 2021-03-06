/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.checks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.sonar.check.BelongsToProfile;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.java.checks.methods.MethodInvocationMatcher;
import org.sonar.java.model.AbstractTypedTree;
import org.sonar.java.resolve.Symbol;
import org.sonar.java.resolve.Type;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.InstanceOfTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.Tree.Kind;

import java.util.List;

@Rule(
  key = "S2118",
  priority = Priority.CRITICAL,
  tags = {"pitfall"})
@BelongsToProfile(title = "Sonar way", priority = Priority.CRITICAL)
public class NonSerializableWriteCheck extends SubscriptionBaseVisitor {

  private static final MethodInvocationMatcher WRITE_OBJECT_MATCHER = MethodInvocationMatcher.create()
    .typeDefinition("java.io.ObjectOutputStream")
    .name("writeObject")
    .addParameter("java.lang.Object");

  private final List<Symbol> testedSymbols = Lists.newArrayList();

  @Override
  public List<Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.METHOD_INVOCATION, Tree.Kind.INSTANCE_OF);
  }

  @Override
  public void scanFile(JavaFileScannerContext context) {
    testedSymbols.clear();
    super.scanFile(context);
  }

  @Override
  public void visitNode(Tree tree) {
    if (hasSemantic()) {
      if (tree.is(Tree.Kind.METHOD_INVOCATION)) {
        visitMethodInvocation((MethodInvocationTree) tree);
      } else {
        visitInstanceOf((InstanceOfTree) tree);
      }
    }
  }

  private void visitInstanceOf(InstanceOfTree instanceOfTree) {
    ExpressionTree expression = instanceOfTree.expression();
    AbstractTypedTree testedType = (AbstractTypedTree) instanceOfTree.type();
    if (expression.is(Tree.Kind.IDENTIFIER) && testedType.getSymbolType().is("java.io.Serializable")) {
      testedSymbols.add(getSemanticModel().getReference((IdentifierTree) expression));
    }
  }

  // If we met a test such as "x instanceof Serializable", we suppose that symbol x is Serializable
  private boolean isTestedSymbol(AbstractTypedTree tree) {
    if (tree.is(Tree.Kind.IDENTIFIER)) {
      Symbol symbol = getSemanticModel().getReference((IdentifierTree) tree);
      return testedSymbols.contains(symbol);
    }
    return false;
  }

  private void visitMethodInvocation(MethodInvocationTree methodInvocation) {
    if (WRITE_OBJECT_MATCHER.matches(methodInvocation, getSemanticModel())) {
      AbstractTypedTree argument = (AbstractTypedTree) methodInvocation.arguments().get(0);
      if (!isAcceptableType(argument.getSymbolType()) && !isTestedSymbol(argument)) {
        addIssue(methodInvocation, "Make the \"" + argument.getSymbolType() + "\" class \"Serializable\" or don't write it.");
      }
    }
  }

  private boolean isAcceptableType(Type argType) {
    return argType.isSubtypeOf("java.io.Serializable")
      || argType.is("java.lang.Object")
      || argType.isPrimitive();
  }

}
