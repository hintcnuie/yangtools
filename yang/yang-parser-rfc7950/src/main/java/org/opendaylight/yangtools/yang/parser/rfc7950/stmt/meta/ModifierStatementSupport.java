/*
 * Copyright (c) 2017 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.rfc7950.stmt.meta;

import com.google.common.collect.ImmutableList;
import org.opendaylight.yangtools.yang.model.api.YangStmtMapping;
import org.opendaylight.yangtools.yang.model.api.meta.DeclaredStatement;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ModifierEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ModifierStatement;
import org.opendaylight.yangtools.yang.model.api.type.ModifierKind;
import org.opendaylight.yangtools.yang.model.ri.stmt.DeclaredStatements;
import org.opendaylight.yangtools.yang.model.ri.stmt.EffectiveStatements;
import org.opendaylight.yangtools.yang.parser.spi.meta.AbstractStatementSupport;
import org.opendaylight.yangtools.yang.parser.spi.meta.EffectiveStmtCtx.Current;
import org.opendaylight.yangtools.yang.parser.spi.meta.StmtContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.SubstatementValidator;
import org.opendaylight.yangtools.yang.parser.spi.source.SourceException;

public final class ModifierStatementSupport
        extends AbstractStatementSupport<ModifierKind, ModifierStatement, ModifierEffectiveStatement> {
    private static final SubstatementValidator SUBSTATEMENT_VALIDATOR = SubstatementValidator.builder(
        YangStmtMapping.MODIFIER).build();
    private static final ModifierStatementSupport INSTANCE = new ModifierStatementSupport();

    private ModifierStatementSupport() {
        super(YangStmtMapping.MODIFIER, StatementPolicy.contextIndependent());
    }

    public static ModifierStatementSupport getInstance() {
        return INSTANCE;
    }

    @Override
    public ModifierKind parseArgumentValue(final StmtContext<?, ?, ?> ctx, final String value) {
        return SourceException.unwrap(ModifierKind.parse(value), ctx,
            "'%s' is not valid argument of modifier statement", value);
    }

    @Override
    protected SubstatementValidator getSubstatementValidator() {
        return SUBSTATEMENT_VALIDATOR;
    }

    @Override
    public String internArgument(final String rawArgument) {
        return "invert-match".equals(rawArgument) ? "invert-match" : rawArgument;
    }

    @Override
    protected ModifierStatement createDeclared(final StmtContext<ModifierKind, ModifierStatement, ?> ctx,
            final ImmutableList<? extends DeclaredStatement<?>> substatements) {
        return DeclaredStatements.createModifier(ctx.getArgument(), substatements);
    }

    @Override
    protected ModifierEffectiveStatement createEffective(final Current<ModifierKind, ModifierStatement> stmt,
            final ImmutableList<? extends EffectiveStatement<?, ?>> substatements) {
        return EffectiveStatements.createModifier(stmt.declared(), substatements);
    }
}
