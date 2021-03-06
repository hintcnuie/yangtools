/*
 * Copyright (c) 2017 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.rfc7950.stmt.meta;

import static org.opendaylight.yangtools.yang.parser.spi.meta.StmtContextUtils.findFirstDeclaredSubstatement;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.opendaylight.yangtools.yang.model.api.YangStmtMapping;
import org.opendaylight.yangtools.yang.model.api.meta.DeclaredStatement;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.BelongsToEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.BelongsToStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.PrefixStatement;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.ri.stmt.DeclaredStatements;
import org.opendaylight.yangtools.yang.model.ri.stmt.EffectiveStatements;
import org.opendaylight.yangtools.yang.parser.spi.meta.AbstractStringStatementSupport;
import org.opendaylight.yangtools.yang.parser.spi.meta.EffectiveStmtCtx.Current;
import org.opendaylight.yangtools.yang.parser.spi.meta.InferenceException;
import org.opendaylight.yangtools.yang.parser.spi.meta.ModelActionBuilder;
import org.opendaylight.yangtools.yang.parser.spi.meta.ModelActionBuilder.InferenceAction;
import org.opendaylight.yangtools.yang.parser.spi.meta.ModelActionBuilder.InferenceContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ModelActionBuilder.Prerequisite;
import org.opendaylight.yangtools.yang.parser.spi.meta.ModelProcessingPhase;
import org.opendaylight.yangtools.yang.parser.spi.meta.StmtContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.StmtContext.Mutable;
import org.opendaylight.yangtools.yang.parser.spi.meta.SubstatementValidator;
import org.opendaylight.yangtools.yang.parser.spi.source.BelongsToModuleContext;
import org.opendaylight.yangtools.yang.parser.spi.source.BelongsToPrefixToModuleCtx;
import org.opendaylight.yangtools.yang.parser.spi.source.ModuleNamespaceForBelongsTo;

public final class BelongsToStatementSupport
        extends AbstractStringStatementSupport<BelongsToStatement, BelongsToEffectiveStatement> {
    private static final SubstatementValidator SUBSTATEMENT_VALIDATOR =
            SubstatementValidator.builder(YangStmtMapping.BELONGS_TO).addMandatory(YangStmtMapping.PREFIX).build();
    private static final BelongsToStatementSupport INSTANCE = new BelongsToStatementSupport();

    private BelongsToStatementSupport() {
        super(YangStmtMapping.BELONGS_TO, StatementPolicy.reject());
    }

    public static BelongsToStatementSupport getInstance() {
        return INSTANCE;
    }

    @Override
    public void onPreLinkageDeclared(final Mutable<String, BelongsToStatement, BelongsToEffectiveStatement> ctx) {
        ctx.addRequiredSource(getSourceIdentifier(ctx));
    }

    @Override
    public void onLinkageDeclared(final Mutable<String, BelongsToStatement, BelongsToEffectiveStatement> belongsToCtx) {
        ModelActionBuilder belongsToAction = belongsToCtx.newInferenceAction(ModelProcessingPhase.SOURCE_LINKAGE);

        final SourceIdentifier belongsToSourceIdentifier = getSourceIdentifier(belongsToCtx);
        final Prerequisite<StmtContext<?, ?, ?>> belongsToPrereq = belongsToAction.requiresCtx(belongsToCtx,
            ModuleNamespaceForBelongsTo.class, belongsToCtx.getArgument(), ModelProcessingPhase.SOURCE_LINKAGE);

        belongsToAction.apply(new InferenceAction() {
            @Override
            public void apply(final InferenceContext ctx) {
                StmtContext<?, ?, ?> belongsToModuleCtx = belongsToPrereq.resolve(ctx);

                belongsToCtx.addToNs(BelongsToModuleContext.class, belongsToSourceIdentifier, belongsToModuleCtx);
                belongsToCtx.addToNs(BelongsToPrefixToModuleCtx.class,
                    findFirstDeclaredSubstatement(belongsToCtx, PrefixStatement.class).getArgument(),
                    belongsToModuleCtx);
            }

            @Override
            public void prerequisiteFailed(final Collection<? extends Prerequisite<?>> failed) {
                if (failed.contains(belongsToPrereq)) {
                    throw new InferenceException(belongsToCtx, "Module '%s' from belongs-to was not found",
                        belongsToCtx.argument());
                }
            }
        });
    }

    @Override
    protected BelongsToStatement createDeclared(final StmtContext<String, BelongsToStatement, ?> ctx,
            final ImmutableList<? extends DeclaredStatement<?>> substatements) {
        return DeclaredStatements.createBelongsTo(ctx.getRawArgument(), substatements);
    }

    @Override
    protected BelongsToEffectiveStatement createEffective(final Current<String, BelongsToStatement> stmt,
            final ImmutableList<? extends EffectiveStatement<?, ?>> substatements) {
        return EffectiveStatements.createBelongsTo(stmt.declared(), substatements);
    }

    private static SourceIdentifier getSourceIdentifier(final StmtContext<String, BelongsToStatement,
            BelongsToEffectiveStatement> belongsToCtx) {
        return RevisionSourceIdentifier.create(belongsToCtx.getArgument());
    }

    @Override
    protected SubstatementValidator getSubstatementValidator() {
        return SUBSTATEMENT_VALIDATOR;
    }
}