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
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.Modifier;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.VariableTree;

import java.util.List;

@Rule(
    key = "S1165",
    priority = Priority.MAJOR,
    tags = {"error-handling"})
@BelongsToProfile(title = "Sonar way", priority = Priority.MAJOR)
public class ExceptionsShouldBeImmutableCheck extends SubscriptionBaseVisitor {


  @Override
  public void visitNode(Tree tree) {
    ClassTree classTree = (ClassTree) tree;
    if (isException(classTree)) {
      for (Tree member : classTree.members()) {
        if(member.is(Tree.Kind.VARIABLE) && !isFinal((VariableTree) member)){
          addIssue(member, "Make this \"" + ((VariableTree) member).simpleName().name()+ "\" field final.");
        }
      }
    }
  }

  private boolean isFinal(VariableTree member) {
    return member.modifiers().modifiers().contains(Modifier.FINAL);
  }

  private boolean isException(ClassTree classTree) {
    IdentifierTree simpleName = classTree.simpleName();
    return simpleName != null && (simpleName.name().endsWith("Exception") || simpleName.name().endsWith("Error"));
  }

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.CLASS);
  }
}
