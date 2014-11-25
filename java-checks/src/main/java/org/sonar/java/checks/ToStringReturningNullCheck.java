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
import org.sonar.check.BelongsToProfile;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.ReturnStatementTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.Tree.Kind;

import java.util.List;

@Rule(
  key = "S2225",
  priority = Priority.CRITICAL,
  tags = {"bug"})
@BelongsToProfile(title = "Sonar way", priority = Priority.CRITICAL)
public class ToStringReturningNullCheck extends SubscriptionBaseVisitor {

  private boolean insideToString = false;

  @Override
  public List<Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.METHOD, Tree.Kind.RETURN_STATEMENT);
  }
  
  @Override
  public void visitNode(Tree tree) {
    if (tree.is(Tree.Kind.METHOD)) {
      insideToString = isToStringDeclaration((MethodTree) tree);
    }
    if (insideToString && isReturnNull(tree)) {
      addIssue(tree, "Return empty string instead.");
    }
  }

  @Override
  public void leaveNode(Tree tree) {
    if (tree.is(Tree.Kind.METHOD)) {
      insideToString = false;
    }
  }

  private boolean isToStringDeclaration(MethodTree method) {
    return "toString".equals(method.simpleName().name()) && method.parameters().isEmpty();
  }

  private boolean isReturnNull(Tree tree) {
    if (!tree.is(Tree.Kind.RETURN_STATEMENT)) {
      return false;
    }
    ReturnStatementTree returnStatement = (ReturnStatementTree) tree;
    return returnStatement.expression().is(Tree.Kind.NULL_LITERAL);
  }

}