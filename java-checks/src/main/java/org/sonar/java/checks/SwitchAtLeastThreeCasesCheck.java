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

import org.sonar.api.rule.RuleKey;
import org.sonar.check.BelongsToProfile;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.CaseGroupTree;
import org.sonar.plugins.java.api.tree.SwitchStatementTree;

@Rule(
  key = SwitchAtLeastThreeCasesCheck.RULE_KEY,
  priority = Priority.MINOR,
  tags = {"misra"})
@BelongsToProfile(title = "Sonar way", priority = Priority.MINOR)
public class SwitchAtLeastThreeCasesCheck extends BaseTreeVisitor implements JavaFileScanner {

  public static final String RULE_KEY = "S1301";
  private final RuleKey ruleKey = RuleKey.of(CheckList.REPOSITORY_KEY, RULE_KEY);

  private JavaFileScannerContext context;

  @Override
  public void scanFile(JavaFileScannerContext context) {
    this.context = context;
    scan(context.getTree());
  }

  @Override
  public void visitSwitchStatement(SwitchStatementTree tree) {
    int count = 0;
    for (CaseGroupTree caseGroup : tree.cases()) {
      count += caseGroup.labels().size();
    }
    if (count < 3) {
      context.addIssue(tree, ruleKey, "Replace this \"switch\" statement by \"if\" statements to increase readability.");
    }

    super.visitSwitchStatement(tree);
  }

}
