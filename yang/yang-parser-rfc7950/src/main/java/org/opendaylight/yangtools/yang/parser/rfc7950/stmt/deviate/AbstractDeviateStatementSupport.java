/*
 * Copyright (c) 2017 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.rfc7950.stmt.deviate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.YangVersion;
import org.opendaylight.yangtools.yang.model.api.DeviateKind;
import org.opendaylight.yangtools.yang.model.api.YangStmtMapping;
import org.opendaylight.yangtools.yang.model.api.meta.DeclaredStatement;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.meta.StatementDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.DeviateEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.DeviateStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;
import org.opendaylight.yangtools.yang.model.ri.stmt.DeclaredStatements;
import org.opendaylight.yangtools.yang.model.ri.stmt.EffectiveStatements;
import org.opendaylight.yangtools.yang.parser.rfc7950.reactor.YangValidationBundles;
import org.opendaylight.yangtools.yang.parser.spi.SchemaTreeNamespace;
import org.opendaylight.yangtools.yang.parser.spi.meta.AbstractStatementSupport;
import org.opendaylight.yangtools.yang.parser.spi.meta.CopyType;
import org.opendaylight.yangtools.yang.parser.spi.meta.EffectiveStmtCtx.Current;
import org.opendaylight.yangtools.yang.parser.spi.meta.InferenceException;
import org.opendaylight.yangtools.yang.parser.spi.meta.ModelActionBuilder;
import org.opendaylight.yangtools.yang.parser.spi.meta.ModelActionBuilder.InferenceAction;
import org.opendaylight.yangtools.yang.parser.spi.meta.ModelActionBuilder.InferenceContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ModelActionBuilder.Prerequisite;
import org.opendaylight.yangtools.yang.parser.spi.meta.ModelProcessingPhase;
import org.opendaylight.yangtools.yang.parser.spi.meta.StmtContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.StmtContext.Mutable;
import org.opendaylight.yangtools.yang.parser.spi.meta.StmtContextUtils;
import org.opendaylight.yangtools.yang.parser.spi.meta.SubstatementValidator;
import org.opendaylight.yangtools.yang.parser.spi.source.ModuleCtxToModuleQName;
import org.opendaylight.yangtools.yang.parser.spi.source.ModulesDeviatedByModules;
import org.opendaylight.yangtools.yang.parser.spi.source.ModulesDeviatedByModules.SupportedModules;
import org.opendaylight.yangtools.yang.parser.spi.source.SourceException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.StatementContextBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractDeviateStatementSupport
        extends AbstractStatementSupport<DeviateKind, DeviateStatement, DeviateEffectiveStatement> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDeviateStatementSupport.class);

    private static final SubstatementValidator DEVIATE_NOT_SUPPORTED_SUBSTATEMENT_VALIDATOR =
            SubstatementValidator.builder(YangStmtMapping.DEVIATE).build();

    private static final SubstatementValidator DEVIATE_ADD_SUBSTATEMENT_VALIDATOR =
            SubstatementValidator.builder(YangStmtMapping.DEVIATE)
                .addOptional(YangStmtMapping.CONFIG)
                .addOptional(YangStmtMapping.DEFAULT)
                .addOptional(YangStmtMapping.MANDATORY)
                .addOptional(YangStmtMapping.MAX_ELEMENTS)
                .addOptional(YangStmtMapping.MIN_ELEMENTS)
                .addAny(YangStmtMapping.MUST)
                .addAny(YangStmtMapping.UNIQUE)
                .addOptional(YangStmtMapping.UNITS)
                .build();

    private static final SubstatementValidator DEVIATE_REPLACE_SUBSTATEMENT_VALIDATOR =
            SubstatementValidator.builder(YangStmtMapping.DEVIATE)
                .addOptional(YangStmtMapping.CONFIG)
                .addOptional(YangStmtMapping.DEFAULT)
                .addOptional(YangStmtMapping.MANDATORY)
                .addOptional(YangStmtMapping.MAX_ELEMENTS)
                .addOptional(YangStmtMapping.MIN_ELEMENTS)
                .addOptional(YangStmtMapping.TYPE)
                .addOptional(YangStmtMapping.UNITS)
                .build();

    private static final SubstatementValidator DEVIATE_DELETE_SUBSTATEMENT_VALIDATOR =
            SubstatementValidator.builder(YangStmtMapping.DEVIATE)
                .addOptional(YangStmtMapping.DEFAULT)
                .addAny(YangStmtMapping.MUST)
                .addAny(YangStmtMapping.UNIQUE)
                .addOptional(YangStmtMapping.UNITS)
                .build();

    private static final ImmutableMap<String, DeviateKind> KEYWORD_TO_DEVIATE_MAP =
            Maps.uniqueIndex(Arrays.asList(DeviateKind.values()), DeviateKind::getKeyword);

    private static final ImmutableSet<YangStmtMapping> SINGLETON_STATEMENTS = ImmutableSet.of(
            YangStmtMapping.UNITS, YangStmtMapping.CONFIG, YangStmtMapping.MANDATORY,
            YangStmtMapping.MIN_ELEMENTS, YangStmtMapping.MAX_ELEMENTS);

    private static final ImmutableSet<YangStmtMapping> IMPLICIT_STATEMENTS = ImmutableSet.of(YangStmtMapping.CONFIG,
            YangStmtMapping.MANDATORY, YangStmtMapping.MAX_ELEMENTS, YangStmtMapping.MIN_ELEMENTS);

    AbstractDeviateStatementSupport() {
        super(YangStmtMapping.DEVIATE, StatementPolicy.contextIndependent());
    }

    @Override
    public final DeviateKind parseArgumentValue(final StmtContext<?, ?, ?> ctx, final String value) {
        return SourceException.throwIfNull(KEYWORD_TO_DEVIATE_MAP.get(value), ctx,
            "String '%s' is not valid deviate argument", value);
    }

    @Override
    public final void onFullDefinitionDeclared(
            final Mutable<DeviateKind, DeviateStatement, DeviateEffectiveStatement> deviateStmtCtx) {
        final DeviateKind deviateKind = deviateStmtCtx.argument();
        getSubstatementValidatorForDeviate(deviateKind).validate(deviateStmtCtx);

        final SchemaNodeIdentifier deviationTarget =
                (SchemaNodeIdentifier) deviateStmtCtx.coerceParentContext().argument();

        if (!isDeviationSupported(deviateStmtCtx, deviationTarget)) {
            return;
        }

        final ModelActionBuilder deviateAction = deviateStmtCtx.newInferenceAction(
                ModelProcessingPhase.EFFECTIVE_MODEL);

        final Prerequisite<StmtContext<DeviateKind, DeviateStatement,
            DeviateEffectiveStatement>> sourceCtxPrerequisite =
                deviateAction.requiresCtx(deviateStmtCtx, ModelProcessingPhase.EFFECTIVE_MODEL);

        final Prerequisite<Mutable<?, ?, EffectiveStatement<?, ?>>> targetCtxPrerequisite =
                deviateAction.mutatesEffectiveCtxPath(deviateStmtCtx.getRoot(),
                    SchemaTreeNamespace.class, deviationTarget.getNodeIdentifiers());

        deviateAction.apply(new InferenceAction() {
            @Override
            public void apply(final InferenceContext ctx) {
                // FIXME once BUG-7760 gets fixed, there will be no need for these dirty casts
                final StatementContextBase<?, ?, ?> sourceNodeStmtCtx =
                        (StatementContextBase<?, ?, ?>) sourceCtxPrerequisite.resolve(ctx);
                final StatementContextBase<?, ?, ?> targetNodeStmtCtx =
                        (StatementContextBase<?, ?, ?>) targetCtxPrerequisite.resolve(ctx);

                switch (deviateKind) {
                    case NOT_SUPPORTED:
                        targetNodeStmtCtx.setIsSupportedToBuildEffective(false);
                        break;
                    case ADD:
                        performDeviateAdd(sourceNodeStmtCtx, targetNodeStmtCtx);
                        break;
                    case REPLACE:
                        performDeviateReplace(sourceNodeStmtCtx, targetNodeStmtCtx);
                        break;
                    case DELETE:
                        performDeviateDelete(sourceNodeStmtCtx, targetNodeStmtCtx);
                        break;
                    default:
                        throw new IllegalStateException("Unsupported deviate " + deviateKind);
                }
            }

            @Override
            public void prerequisiteFailed(final Collection<? extends Prerequisite<?>> failed) {
                throw new InferenceException(deviateStmtCtx.coerceParentContext(), "Deviation target '%s' not found.",
                    deviationTarget);
            }
        });
    }

    @Override
    public String internArgument(final String rawArgument) {
        if ("add".equals(rawArgument)) {
            return "add";
        } else if ("delete".equals(rawArgument)) {
            return "delete";
        } else if ("replace".equals(rawArgument)) {
            return "replace";
        } else if ("not-supported".equals(rawArgument)) {
            return "not-supported";
        } else {
            return rawArgument;
        }
    }

    @Override
    protected final SubstatementValidator getSubstatementValidator() {
        return null;
    }

    @Override
    protected final DeviateStatement createDeclared(final StmtContext<DeviateKind, DeviateStatement, ?> ctx,
            final ImmutableList<? extends DeclaredStatement<?>> substatements) {
        return DeclaredStatements.createDeviate(ctx.getArgument(), substatements);
    }

    @Override
    protected final DeviateEffectiveStatement createEffective(final Current<DeviateKind, DeviateStatement> stmt,
            final ImmutableList<? extends EffectiveStatement<?, ?>> substatements) {
        return EffectiveStatements.createDeviate(stmt.declared(), substatements);
    }

    protected SubstatementValidator getSubstatementValidatorForDeviate(final DeviateKind deviateKind) {
        switch (deviateKind) {
            case NOT_SUPPORTED:
                return DEVIATE_NOT_SUPPORTED_SUBSTATEMENT_VALIDATOR;
            case ADD:
                return DEVIATE_ADD_SUBSTATEMENT_VALIDATOR;
            case REPLACE:
                return DEVIATE_REPLACE_SUBSTATEMENT_VALIDATOR;
            case DELETE:
                return DEVIATE_DELETE_SUBSTATEMENT_VALIDATOR;
            default:
                throw new IllegalStateException(String.format(
                        "Substatement validator for deviate %s has not been defined.", deviateKind));
        }
    }

    private static boolean isDeviationSupported(
            final Mutable<DeviateKind, DeviateStatement, DeviateEffectiveStatement> deviateStmtCtx,
            final SchemaNodeIdentifier deviationTarget) {
        final SetMultimap<QNameModule, QNameModule> modulesDeviatedByModules = deviateStmtCtx.getFromNamespace(
                ModulesDeviatedByModules.class, SupportedModules.SUPPORTED_MODULES);
        if (modulesDeviatedByModules == null) {
            return true;
        }

        final QNameModule currentModule = deviateStmtCtx.getFromNamespace(ModuleCtxToModuleQName.class,
                deviateStmtCtx.getRoot());
        final QNameModule targetModule = Iterables.getLast(deviationTarget.getNodeIdentifiers()).getModule();

        final Set<QNameModule> deviationModulesSupportedByTargetModule = modulesDeviatedByModules.get(targetModule);
        if (deviationModulesSupportedByTargetModule != null) {
            return deviationModulesSupportedByTargetModule.contains(currentModule);
        }

        return false;
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private static void performDeviateAdd(final StatementContextBase<?, ?, ?> deviateStmtCtx,
            final StatementContextBase<?, ?, ?> targetCtx) {
        for (Mutable<?, ?, ?> originalStmtCtx : deviateStmtCtx.mutableDeclaredSubstatements()) {
            validateDeviationTarget(originalStmtCtx, targetCtx);
            addStatement(originalStmtCtx, targetCtx);
        }
    }

    private static void addStatement(final Mutable<?, ?, ?> stmtCtxToBeAdded,
            final StatementContextBase<?, ?, ?> targetCtx) {
        if (!StmtContextUtils.isUnknownStatement(stmtCtxToBeAdded)) {
            final StatementDefinition stmtToBeAdded = stmtCtxToBeAdded.publicDefinition();
            if (SINGLETON_STATEMENTS.contains(stmtToBeAdded) || YangStmtMapping.DEFAULT.equals(stmtToBeAdded)
                    && YangStmtMapping.LEAF.equals(targetCtx.publicDefinition())) {
                for (final StmtContext<?, ?, ?> targetCtxSubstatement : targetCtx.allSubstatements()) {
                    InferenceException.throwIf(stmtToBeAdded.equals(targetCtxSubstatement.publicDefinition()),
                        stmtCtxToBeAdded,
                        "Deviation cannot add substatement %s to target node %s because it is already defined "
                        + "in target and can appear only once.",
                        stmtToBeAdded.getStatementName(), targetCtx.argument());
                }
            }
        }

        copyStatement(stmtCtxToBeAdded, targetCtx);
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private static void performDeviateReplace(final StatementContextBase<?, ?, ?> deviateStmtCtx,
            final StatementContextBase<?, ?, ?> targetCtx) {
        for (Mutable<?, ?, ?> originalStmtCtx : deviateStmtCtx.mutableDeclaredSubstatements()) {
            validateDeviationTarget(originalStmtCtx, targetCtx);
            replaceStatement(originalStmtCtx, targetCtx);
        }
    }

    private static void replaceStatement(final Mutable<?, ?, ?> stmtCtxToBeReplaced,
            final StatementContextBase<?, ?, ?> targetCtx) {
        final StatementDefinition stmtToBeReplaced = stmtCtxToBeReplaced.publicDefinition();

        if (YangStmtMapping.DEFAULT.equals(stmtToBeReplaced)
                && YangStmtMapping.LEAF_LIST.equals(targetCtx.publicDefinition())) {
            LOG.error("Deviation cannot replace substatement {} in target leaf-list {} because a leaf-list can "
                    + "have multiple default statements. At line: {}", stmtToBeReplaced.getStatementName(),
                    targetCtx.argument(), stmtCtxToBeReplaced.sourceReference());
            return;
        }

        for (final StmtContext<?, ?, ?> targetCtxSubstatement : targetCtx.effectiveSubstatements()) {
            if (stmtToBeReplaced.equals(targetCtxSubstatement.publicDefinition())) {
                targetCtx.removeStatementFromEffectiveSubstatements(stmtToBeReplaced);
                copyStatement(stmtCtxToBeReplaced, targetCtx);
                return;
            }
        }

        for (final Mutable<?, ?, ?> targetCtxSubstatement : targetCtx.mutableDeclaredSubstatements()) {
            if (stmtToBeReplaced.equals(targetCtxSubstatement.publicDefinition())) {
                targetCtxSubstatement.setIsSupportedToBuildEffective(false);
                copyStatement(stmtCtxToBeReplaced, targetCtx);
                return;
            }
        }

        // This is a special case when deviate replace of a config/mandatory/max/min-elements substatement targets
        // a node which does not contain an explicitly declared config/mandatory/max/min-elements.
        // However, according to RFC6020/RFC7950, these properties are always implicitly present.
        if (IMPLICIT_STATEMENTS.contains(stmtToBeReplaced)) {
            addStatement(stmtCtxToBeReplaced, targetCtx);
            return;
        }

        throw new InferenceException(stmtCtxToBeReplaced,
            "Deviation cannot replace substatement %s in target node %s because it does not exist in target node.",
            stmtToBeReplaced.getStatementName(), targetCtx.argument());
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private static void performDeviateDelete(final StatementContextBase<?, ?, ?> deviateStmtCtx,
            final StatementContextBase<?, ?, ?> targetCtx) {
        for (Mutable<?, ?, ?> originalStmtCtx : deviateStmtCtx.mutableDeclaredSubstatements()) {
            validateDeviationTarget(originalStmtCtx, targetCtx);
            deleteStatement(originalStmtCtx, targetCtx);
        }
    }

    private static void deleteStatement(final StmtContext<?, ?, ?> stmtCtxToBeDeleted,
            final StatementContextBase<?, ?, ?> targetCtx) {
        final StatementDefinition stmtToBeDeleted = stmtCtxToBeDeleted.publicDefinition();
        final String stmtArgument = stmtCtxToBeDeleted.rawArgument();

        for (final Mutable<?, ?, ?> targetCtxSubstatement : targetCtx.mutableEffectiveSubstatements()) {
            if (statementsAreEqual(stmtToBeDeleted, stmtArgument, targetCtxSubstatement.publicDefinition(),
                    targetCtxSubstatement.rawArgument())) {
                targetCtx.removeStatementFromEffectiveSubstatements(stmtToBeDeleted, stmtArgument);
                return;
            }
        }

        for (final Mutable<?, ?, ?> targetCtxSubstatement : targetCtx.mutableDeclaredSubstatements()) {
            if (statementsAreEqual(stmtToBeDeleted, stmtArgument, targetCtxSubstatement.publicDefinition(),
                    targetCtxSubstatement.rawArgument())) {
                targetCtxSubstatement.setIsSupportedToBuildEffective(false);
                return;
            }
        }

        LOG.error("Deviation cannot delete substatement {} with argument '{}' in target node {} because it does "
                + "not exist in the target node. At line: {}", stmtToBeDeleted.getStatementName(), stmtArgument,
                targetCtx.argument(), stmtCtxToBeDeleted.sourceReference());
    }

    private static void copyStatement(final Mutable<?, ?, ?> stmtCtxToBeCopied,
            final StatementContextBase<?, ?, ?> targetCtx) {
        // we need to make a copy of the statement context only if it is an unknown statement, otherwise
        // we can reuse the original statement context
        if (!StmtContextUtils.isUnknownStatement(stmtCtxToBeCopied)) {
            targetCtx.addEffectiveSubstatement(stmtCtxToBeCopied.replicaAsChildOf(targetCtx));
        } else {
            targetCtx.addEffectiveSubstatement(targetCtx.childCopyOf(stmtCtxToBeCopied, CopyType.ORIGINAL));
        }
    }

    private static boolean statementsAreEqual(final StatementDefinition firstStmtDef, final String firstStmtArg,
            final StatementDefinition secondStmtDef, final String secondStmtArg) {
        return firstStmtDef.equals(secondStmtDef) && Objects.equals(firstStmtArg, secondStmtArg);
    }

    private static void validateDeviationTarget(final StmtContext<?, ?, ?> deviateSubStmtCtx,
            final StmtContext<?, ?, ?> targetCtx) {
        InferenceException.throwIf(!isSupportedDeviationTarget(deviateSubStmtCtx, targetCtx,
            targetCtx.yangVersion()), deviateSubStmtCtx,
            "%s is not a valid deviation target for substatement %s.", targetCtx.argument(),
            deviateSubStmtCtx.publicDefinition().getStatementName());
    }

    private static boolean isSupportedDeviationTarget(final StmtContext<?, ?, ?> deviateSubstatementCtx,
            final StmtContext<?, ?, ?> deviateTargetCtx, final YangVersion yangVersion) {
        Set<StatementDefinition> supportedDeviationTargets =
                YangValidationBundles.SUPPORTED_DEVIATION_TARGETS.get(yangVersion,
                        deviateSubstatementCtx.publicDefinition());

        if (supportedDeviationTargets == null) {
            supportedDeviationTargets = YangValidationBundles.SUPPORTED_DEVIATION_TARGETS.get(YangVersion.VERSION_1,
                    deviateSubstatementCtx.publicDefinition());
        }

        // if supportedDeviationTargets is null, it means that the deviate substatement is an unknown statement
        return supportedDeviationTargets == null || supportedDeviationTargets.contains(
                deviateTargetCtx.publicDefinition());
    }
}
