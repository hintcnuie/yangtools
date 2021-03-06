/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.model.api.stmt;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.model.api.YangStmtMapping;
import org.opendaylight.yangtools.yang.model.api.meta.DeclaredStatement;
import org.opendaylight.yangtools.yang.model.api.meta.StatementDefinition;

/**
 * Represents YANG if-feature statement. The "if-feature" statement makes its parent statement conditional.
 */
public interface IfFeatureStatement extends DeclaredStatement<IfFeatureExpr> {
    @Override
    default StatementDefinition statementDefinition() {
        return YangStmtMapping.IF_FEATURE;
    }

    /**
     * In YANG 1.1 (RFC7950) implementation of IfFeatureStatement, the argument is a boolean expression over feature
     * names defined by "feature" statements. Hence, add implementation to return a predicate on a collection
     * of features against which to evaluate.
     *
     * @return Predicate on a collection of QNames against which to evaluate
     */
    @Beta
    // FIXME: YANGTOOLS-908: remove this method
    default @NonNull IfFeatureExpr getIfFeaturePredicate() {
        return argument();
    }
}
