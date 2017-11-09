/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.stmt.rfc6020;

import java.util.Optional;
import javax.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.meta.StatementDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.UnrecognizedStatement;
import org.opendaylight.yangtools.yang.parser.spi.meta.AbstractDeclaredStatement;
import org.opendaylight.yangtools.yang.parser.spi.meta.AbstractStatementSupport;
import org.opendaylight.yangtools.yang.parser.spi.meta.StatementSupport;
import org.opendaylight.yangtools.yang.parser.spi.meta.StmtContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.SubstatementValidator;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.effective.UnrecognizedEffectiveStatementImpl;

public final class UnrecognizedStatementImpl extends AbstractDeclaredStatement<String>
        implements UnrecognizedStatement {

    protected UnrecognizedStatementImpl(final StmtContext<String, ?, ?> context) {
        super(context);
    }

    public static class Definition extends AbstractStatementSupport<String, UnrecognizedStatement,
            EffectiveStatement<String, UnrecognizedStatement>> {

        public Definition(final StatementDefinition publicDefinition) {
            super(publicDefinition);
        }

        @Override
        public String parseArgumentValue(final StmtContext<?, ?, ?> ctx, final String value) {
            return value;
        }

        @Override
        public UnrecognizedStatement createDeclared(final StmtContext<String, UnrecognizedStatement, ?> ctx) {
            return new UnrecognizedStatementImpl(ctx);
        }

        @Override
        public EffectiveStatement<String, UnrecognizedStatement> createEffective(
                final StmtContext<String, UnrecognizedStatement,
                EffectiveStatement<String, UnrecognizedStatement>> ctx) {
            return new UnrecognizedEffectiveStatementImpl(ctx);
        }

        @Override
        protected SubstatementValidator getSubstatementValidator() {
            return null;
        }

        @Override
        public Optional<StatementSupport<?, ?, ?>> getUnknownStatementDefinitionOf(
                final StatementDefinition yangStmtDef) {
            final QName baseQName = getStatementName();
            final QName argumentName = yangStmtDef.getArgumentName();
            return Optional.of(new ModelDefinedStatementSupport(new ModelDefinedStatementDefinition(
                    QName.create(baseQName, yangStmtDef.getStatementName().getLocalName()),
                    argumentName != null ? QName.create(baseQName, argumentName.getLocalName()) : null,
                    yangStmtDef.isArgumentYinElement())));
        }
    }

    @Nullable
    @Override
    public String getArgument() {
        return argument();
    }
}