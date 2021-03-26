/*
 *
 * Copyright (c) 2013 - 2020 Lijun Liao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xipki.qa;

import org.xipki.util.CollectionUtil;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.xipki.util.Args.notEmpty;

/**
 * Validation result consisting of failed validation issues and successful issues.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class ValidationResult {

  private final List<ValidationIssue> validationIssues;

  private final List<ValidationIssue> failedValidationIssues;

  private final List<ValidationIssue> successfulValidationIssues;

  public ValidationResult(ValidationIssue validationIssues) {
    this(Arrays.asList(validationIssues));
  }

  public ValidationResult(List<ValidationIssue> validationIssues) {
    this.validationIssues = notEmpty(validationIssues, "validationIssues");

    List<ValidationIssue> failedIssues = new LinkedList<>();
    List<ValidationIssue> successfulIssues = new LinkedList<>();
    for (ValidationIssue issue : validationIssues) {
      if (issue.isFailed()) {
        failedIssues.add(issue);
      } else {
        successfulIssues.add(issue);
      }
    }

    this.failedValidationIssues = failedIssues;
    this.successfulValidationIssues = successfulIssues;
  }

  public boolean isAllSuccessful() {
    return CollectionUtil.isEmpty(failedValidationIssues);
  }

  public List<ValidationIssue> getValidationIssues() {
    return validationIssues;
  }

  public List<ValidationIssue> getFailedValidationIssues() {
    return failedValidationIssues;
  }

  public List<ValidationIssue> getSuccessfulValidationIssues() {
    return successfulValidationIssues;
  }

}
