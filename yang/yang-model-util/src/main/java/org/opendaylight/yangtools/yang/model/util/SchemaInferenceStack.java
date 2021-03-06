/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.model.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Mutable;
import org.opendaylight.yangtools.yang.common.AbstractQName;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.UnqualifiedQName;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextProvider;
import org.opendaylight.yangtools.yang.model.api.EffectiveStatementInference;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.PathExpression;
import org.opendaylight.yangtools.yang.model.api.PathExpression.DerefSteps;
import org.opendaylight.yangtools.yang.model.api.PathExpression.LocationPathSteps;
import org.opendaylight.yangtools.yang.model.api.PathExpression.Steps;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.SchemaTreeInference;
import org.opendaylight.yangtools.yang.model.api.TypeAware;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.TypedDataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.CaseEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ChoiceEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.DataTreeAwareEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.DataTreeEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.GroupingEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaTreeAwareEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaTreeEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.type.InstanceIdentifierTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.spi.AbstractEffectiveStatementInference;
import org.opendaylight.yangtools.yang.model.spi.DefaultSchemaTreeInference;
import org.opendaylight.yangtools.yang.xpath.api.YangLocationPath;
import org.opendaylight.yangtools.yang.xpath.api.YangLocationPath.AxisStep;
import org.opendaylight.yangtools.yang.xpath.api.YangLocationPath.QNameStep;
import org.opendaylight.yangtools.yang.xpath.api.YangLocationPath.Step;
import org.opendaylight.yangtools.yang.xpath.api.YangXPathAxis;

/**
 * A state tracking utility for walking {@link EffectiveModelContext}'s contents along schema/grouping namespaces. This
 * is conceptually a stack, tracking {@link EffectiveStatement}s encountered along traversal.
 *
 * <p>
 * This is meant to be a replacement concept for the use of {@link SchemaPath} in various places, notably
 * in {@link SchemaContextUtil} methods.
 *
 * <p>
 * This class is designed for single-threaded uses and does not make any guarantees around concurrent access.
 */
@Beta
public final class SchemaInferenceStack implements Mutable, EffectiveModelContextProvider, LeafrefResolver {
    /**
     * Semantic binding of {@link EffectiveStatementInference} produced by {@link SchemaInferenceStack}. Sequence of
     * {@link #statementPath()} is implementation-specific.
     */
    @Beta
    public static final class Inference extends AbstractEffectiveStatementInference<EffectiveStatement<?, ?>> {
        private final ArrayDeque<EffectiveStatement<?, ?>> deque;
        private final ModuleEffectiveStatement currentModule;
        private final int groupingDepth;
        private final boolean clean;

        Inference(final @NonNull EffectiveModelContext modelContext, final ArrayDeque<EffectiveStatement<?, ?>> deque,
                final ModuleEffectiveStatement currentModule, final int groupingDepth, final boolean clean) {
            super(modelContext);
            this.deque = requireNonNull(deque);
            this.currentModule = currentModule;
            this.groupingDepth = groupingDepth;
            this.clean = clean;
        }

        /**
         * Create a new stack backed by an effective model and set up to point and specified data tree node.
         *
         * @param effectiveModel EffectiveModelContext to which this stack is attached
         * @param qnames Data tree path qnames
         * @return A new stack
         * @throws NullPointerException if any argument is null or path contains a null element
         * @throws IllegalArgumentException if a path element cannot be found
         */
        public static @NonNull Inference ofDataTreePath(final EffectiveModelContext effectiveModel,
                final QName... qnames) {
            return SchemaInferenceStack.ofDataTreePath(effectiveModel, qnames).toInference();
        }

        @Override
        public List<EffectiveStatement<?, ?>> statementPath() {
            return ImmutableList.copyOf(deque.descendingIterator());
        }

        /**
         * Convert this inference into a {@link SchemaInferenceStack}.
         *
         * @return A new stack
         */
        public @NonNull SchemaInferenceStack toSchemaInferenceStack() {
            return new SchemaInferenceStack(getEffectiveModelContext(), deque, currentModule, groupingDepth, clean);
        }
    }

    private final @NonNull EffectiveModelContext effectiveModel;
    private final ArrayDeque<EffectiveStatement<?, ?>> deque;

    private @Nullable ModuleEffectiveStatement currentModule;
    private int groupingDepth;

    // True if there were only steps along grouping and schema tree, hence it is consistent with SchemaNodeIdentifier
    // False if we have evidence of a data tree lookup succeeding
    private boolean clean;

    private SchemaInferenceStack(final EffectiveModelContext effectiveModel, final int expectedSize) {
        this.deque = new ArrayDeque<>(expectedSize);
        this.effectiveModel = requireNonNull(effectiveModel);
        this.clean = true;
    }

    private SchemaInferenceStack(final SchemaInferenceStack source) {
        this.deque = source.deque.clone();
        this.effectiveModel = source.effectiveModel;
        this.currentModule = source.currentModule;
        this.groupingDepth = source.groupingDepth;
        this.clean = source.clean;
    }

    private SchemaInferenceStack(final EffectiveModelContext effectiveModel,
            final ArrayDeque<EffectiveStatement<?, ?>> deque, final ModuleEffectiveStatement currentModule,
            final int groupingDepth, final boolean clean) {
        this.effectiveModel = requireNonNull(effectiveModel);
        this.deque = deque.clone();
        this.currentModule = currentModule;
        this.groupingDepth = groupingDepth;
        this.clean = clean;
    }

    private SchemaInferenceStack(final EffectiveModelContext effectiveModel) {
        this.effectiveModel = requireNonNull(effectiveModel);
        this.deque = new ArrayDeque<>();
        this.clean = true;
    }

    /**
     * Create a new empty stack backed by an effective model.
     *
     * @param effectiveModel EffectiveModelContext to which this stack is attached
     * @return A new stack
     * @throws NullPointerException if {@code effectiveModel} is null
     */
    public static @NonNull SchemaInferenceStack of(final EffectiveModelContext effectiveModel) {
        return new SchemaInferenceStack(effectiveModel);
    }

    /**
     * Create a new stack backed by an effective model, pointing to specified schema node identified by
     * {@link Absolute}.
     *
     * @param effectiveModel EffectiveModelContext to which this stack is attached
     * @return A new stack
     * @throws NullPointerException if {@code effectiveModel} is null
     * @throws IllegalArgumentException if {@code path} cannot be resolved in the effective model
     */
    public static @NonNull SchemaInferenceStack of(final EffectiveModelContext effectiveModel, final Absolute path) {
        final SchemaInferenceStack ret = new SchemaInferenceStack(effectiveModel);
        path.getNodeIdentifiers().forEach(ret::enterSchemaTree);
        return ret;
    }

    /**
     * Create a new stack from an {@link EffectiveStatementInference}.
     *
     * @param inference Inference to use for initialization
     * @return A new stack
     * @throws NullPointerException if {@code inference} is null
     * @throws IllegalArgumentException if {@code inference} implementation is not supported
     */
    public static @NonNull SchemaInferenceStack ofInference(final EffectiveStatementInference inference) {
        if (inference.statementPath().isEmpty()) {
            return new SchemaInferenceStack(inference.getEffectiveModelContext());
        } else if (inference instanceof SchemaTreeInference) {
            return ofInference((SchemaTreeInference) inference);
        } else if (inference instanceof Inference) {
            return ((Inference) inference).toSchemaInferenceStack();
        } else {
            throw new IllegalArgumentException("Unsupported Inference " + inference);
        }
    }

    /**
     * Create a new stack from an {@link SchemaTreeInference}.
     *
     * @param inference SchemaTreeInference to use for initialization
     * @return A new stack
     * @throws NullPointerException if {@code inference} is null
     * @throws IllegalArgumentException if {@code inference} cannot be resolved to a valid stack
     */
    public static @NonNull SchemaInferenceStack ofInference(final SchemaTreeInference inference) {
        return of(inference.getEffectiveModelContext(), inference.toSchemaNodeIdentifier());
    }

    /**
     * Create a new stack backed by an effective model and set up to point and specified data tree node.
     *
     * @param effectiveModel EffectiveModelContext to which this stack is attached
     * @return A new stack
     * @throws NullPointerException if any argument is null or path contains a null element
     * @throws IllegalArgumentException if a path element cannot be found
     */
    public static @NonNull SchemaInferenceStack ofDataTreePath(final EffectiveModelContext effectiveModel,
            final QName... path) {
        final SchemaInferenceStack ret = new SchemaInferenceStack(effectiveModel);
        for (QName qname : path) {
            ret.enterDataTree(qname);
        }
        return ret;
    }

    /**
     * Create a new stack backed by an effective model, pointing to specified schema node identified by an absolute
     * {@link SchemaPath} and its {@link SchemaPath#getPathFromRoot()}.
     *
     * @param effectiveModel EffectiveModelContext to which this stack is attached
     * @return A new stack
     * @throws NullPointerException {@code effectiveModel} is null
     * @throws IllegalArgumentException if {@code path} cannot be resolved in the effective model or if it is not an
     *                                  absolute path.
     */
    @Deprecated
    public static @NonNull SchemaInferenceStack ofInstantiatedPath(final EffectiveModelContext effectiveModel,
            final SchemaPath path) {
        checkArgument(path.isAbsolute(), "Cannot operate on relative path %s", path);
        final SchemaInferenceStack ret = new SchemaInferenceStack(effectiveModel);
        path.getPathFromRoot().forEach(ret::enterSchemaTree);
        return ret;
    }

    @Override
    public EffectiveModelContext getEffectiveModelContext() {
        return effectiveModel;
    }

    /**
     * Create a deep copy of this object.
     *
     * @return An isolated copy of this object
     */
    public @NonNull SchemaInferenceStack copy() {
        return new SchemaInferenceStack(this);
    }

    /**
     * Check if this stack is empty.
     *
     * @return True if this stack has not entered any node.
     */
    public boolean isEmpty() {
        return deque.isEmpty();
    }

    /**
     * Return the statement at the top of the stack.
     *
     * @return Top statement
     * @throws IllegalStateException if the stack is empty
     */
    public @NonNull EffectiveStatement<?, ?> currentStatement() {
        return checkNonNullState(deque.peekFirst());
    }

    /**
     * Return current module the stack has entered.
     *
     * @return Current module
     * @throws IllegalStateException if the stack is empty
     */
    public @NonNull ModuleEffectiveStatement currentModule() {
        return checkNonNullState(currentModule);
    }

    /**
     * Check if the stack is in instantiated context. This indicates the stack is non-empty and there is no grouping
     * (or similar construct) present in the stack.
     *
     * @return False if the stack is empty or contains a grouping, true otherwise.
     */
    public boolean inInstantiatedContext() {
        return groupingDepth == 0 && !deque.isEmpty();
    }

    /**
     * Reset this stack to empty state.
     */
    public void clear() {
        deque.clear();
        currentModule = null;
        groupingDepth = 0;
        clean = true;
    }

    /**
     * Lookup a {@code choice} by its node identifier and push it to the stack. This step is very similar to
     * {@link #enterSchemaTree(QName)}, except it handles the use case where traversal ignores actual {@code case}
     * intermediate schema tree children.
     *
     * @param nodeIdentifier Node identifier of the grouping to enter
     * @return Resolved choice
     * @throws NullPointerException if {@code nodeIdentifier} is null
     * @throws IllegalArgumentException if the corresponding choice cannot be found
     */
    public @NonNull ChoiceEffectiveStatement enterChoice(final QName nodeIdentifier) {
        final EffectiveStatement<?, ?> parent = deque.peek();
        if (parent instanceof ChoiceEffectiveStatement) {
            return enterChoice((ChoiceEffectiveStatement) parent, nodeIdentifier);
        }

        // Fall back to schema tree lookup. Note if it results in non-choice, we rewind before reporting an error
        final SchemaTreeEffectiveStatement<?> result = enterSchemaTree(nodeIdentifier);
        if (result instanceof ChoiceEffectiveStatement) {
            return (ChoiceEffectiveStatement) result;
        }
        exit();
        throw new IllegalArgumentException("Choice " + nodeIdentifier + " not present");
    }

    // choice -> choice transition, we have to deal with intermediate case nodes
    private @NonNull ChoiceEffectiveStatement enterChoice(final ChoiceEffectiveStatement parent,
            final QName nodeIdentifier) {
        for (EffectiveStatement<?, ?> stmt : parent.effectiveSubstatements()) {
            if (stmt instanceof CaseEffectiveStatement) {
                final Optional<ChoiceEffectiveStatement> optMatch = ((CaseEffectiveStatement) stmt)
                    .findSchemaTreeNode(nodeIdentifier)
                    .filter(ChoiceEffectiveStatement.class::isInstance)
                    .map(ChoiceEffectiveStatement.class::cast);
                if (optMatch.isPresent()) {
                    final SchemaTreeEffectiveStatement<?> match = optMatch.orElseThrow();
                    deque.push(match);
                    clean = false;
                    return (ChoiceEffectiveStatement) match;
                }
            }
        }
        throw new IllegalArgumentException("Choice " + nodeIdentifier + " not present");
    }

    /**
     * Lookup a {@code grouping} by its node identifier and push it to the stack.
     *
     * @param nodeIdentifier Node identifier of the grouping to enter
     * @return Resolved grouping
     * @throws NullPointerException if {@code nodeIdentifier} is null
     * @throws IllegalArgumentException if the corresponding grouping cannot be found
     */
    public @NonNull GroupingEffectiveStatement enterGrouping(final QName nodeIdentifier) {
        return pushGrouping(requireNonNull(nodeIdentifier));
    }

    /**
     * Lookup a {@code schema tree} child by its node identifier and push it to the stack.
     *
     * @param nodeIdentifier Node identifier of the schema tree child to enter
     * @return Resolved schema tree child
     * @throws NullPointerException if {@code nodeIdentifier} is null
     * @throws IllegalArgumentException if the corresponding child cannot be found
     */
    public @NonNull SchemaTreeEffectiveStatement<?> enterSchemaTree(final QName nodeIdentifier) {
        return pushSchema(requireNonNull(nodeIdentifier));
    }

    /**
     * Lookup a {@code schema tree} child by its node identifier and push it to the stack.
     *
     * @param nodeIdentifier Node identifier of the date tree child to enter
     * @return Resolved date tree child
     * @throws NullPointerException if {@code nodeIdentifier} is null
     * @throws IllegalArgumentException if the corresponding child cannot be found
     */
    public @NonNull DataTreeEffectiveStatement<?> enterDataTree(final QName nodeIdentifier) {
        return pushData(requireNonNull(nodeIdentifier));
    }

    /**
     * Pop the current statement from the stack.
     *
     * @return Previous statement
     * @throws NoSuchElementException if this stack is empty
     */
    public @NonNull EffectiveStatement<?, ?> exit() {
        final EffectiveStatement<?, ?> prev = deque.pop();
        if (prev instanceof GroupingEffectiveStatement) {
            --groupingDepth;
        }
        if (deque.isEmpty()) {
            currentModule = null;
            clean = true;
        }
        return prev;
    }

    /**
     * Pop the current statement from the stack, asserting it is a {@link DataTreeEffectiveStatement} and that
     * subsequent {@link #enterDataTree(QName)} will find it again.
     *
     * @return Previous statement
     * @throws NoSuchElementException if this stack is empty
     * @throws IllegalStateException if current statement is not a DataTreeEffectiveStatement or if its parent is not
     *                               a {@link DataTreeAwareEffectiveStatement}
     */
    public @NonNull DataTreeEffectiveStatement<?> exitToDataTree() {
        final EffectiveStatement<?, ?> child = exit();
        checkState(child instanceof DataTreeEffectiveStatement, "Unexpected current %s", child);
        final EffectiveStatement<?, ?> parent = deque.peekFirst();
        checkState(parent == null || parent instanceof DataTreeAwareEffectiveStatement, "Unexpected parent %s", parent);
        return (DataTreeEffectiveStatement<?>) child;
    }


    @Override
    public TypeDefinition<?> resolveLeafref(final LeafrefTypeDefinition type) {
        final SchemaInferenceStack tmp = copy();

        LeafrefTypeDefinition current = type;
        while (true) {
            final EffectiveStatement<?, ?> resolved = tmp.resolvePathExpression(current.getPathStatement());
            checkState(resolved instanceof TypeAware, "Unexpected result %s resultion of %s", resolved, type);
            final TypeDefinition<?> result = ((TypedDataSchemaNode) resolved).getType();
            if (result instanceof LeafrefTypeDefinition) {
                checkArgument(result != type, "Resolution of %s loops back onto itself via %s", type, current);
                current = (LeafrefTypeDefinition) result;
            } else {
                return result;
            }
        }
    }

    /**
     * Resolve a {@link PathExpression}.
     *
     * <p>
     * Note if this method throws, this stack may be in an undefined state.
     *
     * @param path Requested path
     * @return Resolved schema tree child
     * @throws NullPointerException if {@code path} is null
     * @throws IllegalArgumentException if the target node cannot be found
     * @throws VerifyException if path expression is invalid
     */
    public @NonNull EffectiveStatement<?, ?> resolvePathExpression(final PathExpression path) {
        final Steps steps = path.getSteps();
        if (steps instanceof LocationPathSteps) {
            return resolveLocationPath(((LocationPathSteps) steps).getLocationPath());
        } else if (steps instanceof DerefSteps) {
            return resolveDeref((DerefSteps) steps);
        } else {
            throw new VerifyException("Unhandled steps " + steps);
        }
    }

    private @NonNull EffectiveStatement<?, ?> resolveDeref(final DerefSteps deref) {
        final EffectiveStatement<?, ?> leafRefSchemaNode = currentStatement();
        final YangLocationPath.Relative derefArg = deref.getDerefArgument();
        final EffectiveStatement<?, ?> derefStmt = resolveLocationPath(derefArg);
        checkArgument(derefStmt != null, "Cannot find deref(%s) target node %s in context of %s",
                derefArg, leafRefSchemaNode);
        checkArgument(derefStmt instanceof TypedDataSchemaNode, "deref(%s) resolved to non-typed %s", derefArg,
                derefStmt);

        // We have a deref() target, decide what to do about it
        final TypeDefinition<?> targetType = ((TypedDataSchemaNode) derefStmt).getType();
        if (targetType instanceof InstanceIdentifierTypeDefinition) {
            // Static inference breaks down, we cannot determine where this points to
            // FIXME: dedicated exception, users can recover from it, derive from IAE
            throw new UnsupportedOperationException("Cannot infer instance-identifier reference " + targetType);
        }

        // deref() is defined only for instance-identifier and leafref types, handle the latter
        checkArgument(targetType instanceof LeafrefTypeDefinition, "Illegal target type %s", targetType);

        final PathExpression dereferencedLeafRefPath = ((LeafrefTypeDefinition) targetType).getPathStatement();
        EffectiveStatement<?, ?> derefNode = resolvePathExpression(dereferencedLeafRefPath);
        checkArgument(derefStmt != null, "Can not find target node of dereferenced node %s", derefStmt);
        checkArgument(derefNode instanceof LeafSchemaNode, "Unexpected %s reference in %s", deref,
                dereferencedLeafRefPath);
        return resolveLocationPath(deref.getRelativePath());
    }

    private @NonNull EffectiveStatement<?, ?> resolveLocationPath(final YangLocationPath path) {
        // get the default namespace before we clear and loose our deque
        final QNameModule defaultNamespace = deque.isEmpty() ? null : ((QName) deque.peek().argument()).getModule();
        if (path.isAbsolute()) {
            clear();
        }

        EffectiveStatement<?, ?> current = null;
        for (Step step : path.getSteps()) {
            final YangXPathAxis axis = step.getAxis();
            switch (axis) {
                case PARENT:
                    verify(step instanceof AxisStep, "Unexpected parent step %s", step);
                    try {
                        current = exitToDataTree();
                    } catch (IllegalStateException | NoSuchElementException e) {
                        throw new IllegalArgumentException("Illegal parent access in " + path, e);
                    }
                    break;
                case CHILD:
                    verify(step instanceof QNameStep, "Unexpected child step %s", step);
                    current = enterChild((QNameStep) step, defaultNamespace);
                    break;
                default:
                    throw new VerifyException("Unexpected step " + step);
            }
        }

        return verifyNotNull(current);
    }

    private @NonNull EffectiveStatement<?, ?> enterChild(final QNameStep step, final QNameModule defaultNamespace) {
        final AbstractQName toResolve = step.getQName();
        final QName qname;
        if (toResolve instanceof QName) {
            qname = (QName) toResolve;
        } else if (toResolve instanceof UnqualifiedQName) {
            checkArgument(defaultNamespace != null, "Can not find target module of step %s", step);
            qname = ((UnqualifiedQName) toResolve).bindTo(defaultNamespace);
        } else {
            throw new VerifyException("Unexpected child step QName " + toResolve);
        }
        return enterDataTree(qname);
    }

    /**
     * Return an {@link Inference} equivalent of current state.
     *
     * @return An {@link Inference}
     */
    public @NonNull Inference toInference() {
        return new Inference(effectiveModel, deque.clone(), currentModule, groupingDepth, clean);
    }

    /**
     * Return an {@link SchemaTreeInference} equivalent of current state.
     *
     * @return An {@link SchemaTreeInference}
     * @throws IllegalStateException if current state cannot be converted to a {@link SchemaTreeInference}
     */
    public @NonNull SchemaTreeInference toSchemaTreeInference() {
        return DefaultSchemaTreeInference.of(getEffectiveModelContext(), toSchemaNodeIdentifier());
    }

    /**
     * Convert current state into an absolute schema node identifier.
     *
     * @return Absolute schema node identifier representing current state
     * @throws IllegalStateException if current state is not instantiated
     */
    public @NonNull Absolute toSchemaNodeIdentifier() {
        checkState(inInstantiatedContext(), "Cannot convert uninstantiated context %s", this);
        return Absolute.of(ImmutableList.<QName>builderWithExpectedSize(deque.size())
            .addAll(simplePathFromRoot())
            .build());
    }

    /**
     * Convert current state into a SchemaPath.
     *
     * @return Absolute SchemaPath representing current state
     * @throws IllegalStateException if current state is not instantiated
     * @deprecated This method is meant only for interoperation with SchemaPath-based APIs.
     */
    @Deprecated
    public @NonNull SchemaPath toSchemaPath() {
        SchemaPath ret = SchemaPath.ROOT;
        final Iterator<QName> it = simplePathFromRoot();
        while (it.hasNext()) {
            ret = ret.createChild(it.next());
        }
        return ret;
    }

    /**
     * Return an iterator along {@link SchemaPath#getPathFromRoot()}. This method is a faster equivalent of
     * {@code toSchemaPath().getPathFromRoot().iterator()}.
     *
     * @return An unmodifiable iterator
     */
    @Deprecated
    public @NonNull Iterator<QName> schemaPathIterator() {
        return Iterators.unmodifiableIterator(simplePathFromRoot());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("stack", deque).toString();
    }

    private @NonNull GroupingEffectiveStatement pushGrouping(final @NonNull QName nodeIdentifier) {
        final EffectiveStatement<?, ?> parent = deque.peekFirst();
        return parent != null ? pushGrouping(parent, nodeIdentifier) : pushFirstGrouping(nodeIdentifier);
    }

    private @NonNull GroupingEffectiveStatement pushGrouping(final @NonNull EffectiveStatement<?, ?> parent,
            final @NonNull QName nodeIdentifier) {
        final GroupingEffectiveStatement ret = parent.streamEffectiveSubstatements(GroupingEffectiveStatement.class)
            .filter(stmt -> nodeIdentifier.equals(stmt.argument()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Grouping " + nodeIdentifier + " not present"));
        deque.push(ret);
        ++groupingDepth;
        return ret;
    }

    private @NonNull GroupingEffectiveStatement pushFirstGrouping(final @NonNull QName nodeIdentifier) {
        final ModuleEffectiveStatement module = getModule(nodeIdentifier);
        final GroupingEffectiveStatement ret = pushGrouping(module, nodeIdentifier);
        currentModule = module;
        return ret;
    }

    private @NonNull SchemaTreeEffectiveStatement<?> pushSchema(final @NonNull QName nodeIdentifier) {
        final EffectiveStatement<?, ?> parent = deque.peekFirst();
        return parent != null ? pushSchema(parent, nodeIdentifier) : pushFirstSchema(nodeIdentifier);
    }

    private @NonNull SchemaTreeEffectiveStatement<?> pushSchema(final EffectiveStatement<?, ?> parent,
            final @NonNull QName nodeIdentifier) {
        checkState(parent instanceof SchemaTreeAwareEffectiveStatement, "Cannot descend schema tree at %s", parent);
        return pushSchema((SchemaTreeAwareEffectiveStatement<?, ?>) parent, nodeIdentifier);
    }

    private @NonNull SchemaTreeEffectiveStatement<?> pushSchema(
            final @NonNull SchemaTreeAwareEffectiveStatement<?, ?> parent, final @NonNull QName nodeIdentifier) {
        final SchemaTreeEffectiveStatement<?> ret = parent.findSchemaTreeNode(nodeIdentifier).orElseThrow(
            () -> new IllegalArgumentException("Schema tree child " + nodeIdentifier + " not present"));
        deque.push(ret);
        return ret;
    }

    private @NonNull SchemaTreeEffectiveStatement<?> pushFirstSchema(final @NonNull QName nodeIdentifier) {
        final ModuleEffectiveStatement module = getModule(nodeIdentifier);
        final SchemaTreeEffectiveStatement<?> ret = pushSchema(module, nodeIdentifier);
        currentModule = module;
        return ret;
    }

    private @NonNull DataTreeEffectiveStatement<?> pushData(final @NonNull QName nodeIdentifier) {
        final EffectiveStatement<?, ?> parent = deque.peekFirst();
        return parent != null ? pushData(parent, nodeIdentifier) : pushFirstData(nodeIdentifier);
    }

    private @NonNull DataTreeEffectiveStatement<?> pushData(final EffectiveStatement<?, ?> parent,
            final @NonNull QName nodeIdentifier) {
        checkState(parent instanceof DataTreeAwareEffectiveStatement, "Cannot descend data tree at %s", parent);
        return pushData((DataTreeAwareEffectiveStatement<?, ?>) parent, nodeIdentifier);
    }

    private @NonNull DataTreeEffectiveStatement<?> pushData(final @NonNull DataTreeAwareEffectiveStatement<?, ?> parent,
            final @NonNull QName nodeIdentifier) {
        final DataTreeEffectiveStatement<?> ret = parent.findDataTreeNode(nodeIdentifier).orElseThrow(
            () -> new IllegalArgumentException("Data tree child " + nodeIdentifier + " not present"));
        deque.push(ret);
        clean = false;
        return ret;
    }

    private @NonNull DataTreeEffectiveStatement<?> pushFirstData(final @NonNull QName nodeIdentifier) {
        final ModuleEffectiveStatement module = getModule(nodeIdentifier);
        final DataTreeEffectiveStatement<?> ret = pushData(module, nodeIdentifier);
        currentModule = module;
        return ret;
    }

    private @NonNull ModuleEffectiveStatement getModule(final @NonNull QName nodeIdentifier) {
        final ModuleEffectiveStatement module = effectiveModel.getModuleStatements().get(nodeIdentifier.getModule());
        checkArgument(module != null, "Module for %s not found", nodeIdentifier);
        return module;
    }

    // Unified access to queue iteration for addressing purposes. Since we keep 'logical' steps as executed by user
    // at this point, conversion to SchemaNodeIdentifier may be needed. We dispatch based on 'clean'.
    private Iterator<QName> simplePathFromRoot() {
        return clean ? iterateQNames() : reconstructQNames();
    }

    private Iterator<QName> iterateQNames() {
        return Iterators.transform(deque.descendingIterator(), stmt -> {
            final Object argument = stmt.argument();
            verify(argument instanceof QName, "Unexpected statement %s", stmt);
            return (QName) argument;
        });
    }

    // So there are some data tree steps in the stack... we essentially need to convert a data tree item into a series
    // of schema tree items. This means at least N searches, but after they are done, we get an opportunity to set the
    // clean flag.
    private Iterator<QName> reconstructQNames() {
        // Let's walk all statements and decipher them into a temporary stack
        final SchemaInferenceStack tmp = new SchemaInferenceStack(effectiveModel, deque.size());
        final Iterator<EffectiveStatement<?, ?>> it = deque.descendingIterator();
        while (it.hasNext()) {
            final EffectiveStatement<?, ?> stmt = it.next();
            // Order of checks is significant
            if (stmt instanceof DataTreeEffectiveStatement) {
                tmp.resolveDataTreeSteps(((DataTreeEffectiveStatement<?>) stmt).argument());
            } else if (stmt instanceof ChoiceEffectiveStatement) {
                tmp.resolveChoiceSteps(((ChoiceEffectiveStatement) stmt).argument());
            } else if (stmt instanceof SchemaTreeEffectiveStatement) {
                tmp.enterSchemaTree(((SchemaTreeEffectiveStatement<?> )stmt).argument());
            } else if (stmt instanceof GroupingEffectiveStatement) {
                tmp.enterGrouping(((GroupingEffectiveStatement) stmt).argument());
            } else {
                throw new VerifyException("Unexpected statement " + stmt);
            }
        }

        // if the sizes match, we did not jump through hoops. let's remember that for future.
        clean = deque.size() == tmp.deque.size();
        return tmp.iterateQNames();
    }

    private void resolveChoiceSteps(final @NonNull QName nodeIdentifier) {
        final EffectiveStatement<?, ?> parent = deque.peekFirst();
        if (parent instanceof ChoiceEffectiveStatement) {
            resolveChoiceSteps((ChoiceEffectiveStatement) parent, nodeIdentifier);
        } else {
            enterSchemaTree(nodeIdentifier);
        }
    }

    private void resolveChoiceSteps(final @NonNull ChoiceEffectiveStatement parent,
            final @NonNull QName nodeIdentifier) {
        for (EffectiveStatement<?, ?> stmt : parent.effectiveSubstatements()) {
            if (stmt instanceof CaseEffectiveStatement) {
                final CaseEffectiveStatement caze = (CaseEffectiveStatement) stmt;
                final SchemaTreeEffectiveStatement<?> found = caze.findSchemaTreeNode(nodeIdentifier).orElse(null);
                if (found instanceof ChoiceEffectiveStatement) {
                    deque.push(caze);
                    deque.push(found);
                    return;
                }
            }
        }
        throw new VerifyException("Failed to resolve " + nodeIdentifier + " in " + parent);
    }

    private void resolveDataTreeSteps(final @NonNull QName nodeIdentifier) {
        final EffectiveStatement<?, ?> parent = deque.peekFirst();
        if (parent != null) {
            verify(parent instanceof SchemaTreeAwareEffectiveStatement, "Unexpected parent %s", parent);
            resolveDataTreeSteps((SchemaTreeAwareEffectiveStatement<?, ?>) parent, nodeIdentifier);
            return;
        }

        final ModuleEffectiveStatement module = getModule(nodeIdentifier);
        resolveDataTreeSteps(module, nodeIdentifier);
        currentModule = module;
    }

    private void resolveDataTreeSteps(final @NonNull SchemaTreeAwareEffectiveStatement<?, ?> parent,
            final @NonNull QName nodeIdentifier) {
        // The algebra of identifiers in 'schema tree versus data tree':
        // - data tree parents are always schema tree parents
        // - data tree children are always schema tree children

        // that implies that a data tree parent must satisfy schema tree queries with data tree children,
        // so a successful lookup of 'data tree parent -> child' and 'schema tree parent -> child' has to be the same
        // for a direct lookup.
        final SchemaTreeEffectiveStatement<?> found = parent.findSchemaTreeNode(nodeIdentifier).orElse(null);
        if (found instanceof DataTreeEffectiveStatement) {
            // ... and it did, we are done
            deque.push(found);
            return;
        }

        // Alright, so now it's down to filtering choice/case statements. For that we keep some globally-reused state
        // and employ a recursive match.
        final Deque<EffectiveStatement<QName, ?>> match = new ArrayDeque<>();
        for (EffectiveStatement<?, ?> stmt : parent.effectiveSubstatements()) {
            if (stmt instanceof ChoiceEffectiveStatement
                && searchChoice(match, (ChoiceEffectiveStatement) stmt, nodeIdentifier)) {
                match.descendingIterator().forEachRemaining(deque::push);
                return;
            }
        }

        throw new VerifyException("Failed to resolve " + nodeIdentifier + " in " + parent);
    }

    private static boolean searchCase(final @NonNull Deque<EffectiveStatement<QName, ?>> result,
            final @NonNull CaseEffectiveStatement parent, final @NonNull QName nodeIdentifier) {
        result.push(parent);
        for (EffectiveStatement<?, ?> stmt : parent.effectiveSubstatements()) {
            if (stmt instanceof DataTreeEffectiveStatement && nodeIdentifier.equals(stmt.argument())) {
                result.push((DataTreeEffectiveStatement<?>) stmt);
                return true;
            }
            if (stmt instanceof ChoiceEffectiveStatement
                && searchChoice(result, (ChoiceEffectiveStatement) stmt, nodeIdentifier)) {
                return true;
            }
        }
        result.pop();
        return false;
    }

    private static boolean searchChoice(final @NonNull Deque<EffectiveStatement<QName, ?>> result,
            final @NonNull ChoiceEffectiveStatement parent, final @NonNull QName nodeIdentifier) {
        result.push(parent);
        for (EffectiveStatement<?, ?> stmt : parent.effectiveSubstatements()) {
            if (stmt instanceof CaseEffectiveStatement
                && searchCase(result, (CaseEffectiveStatement) stmt, nodeIdentifier)) {
                return true;
            }
        }
        result.pop();
        return false;
    }

    private static <T> @NonNull T checkNonNullState(final @Nullable T obj) {
        if (obj == null) {
            throw new IllegalStateException("Cannot execute on empty stack");
        }
        return obj;
    }
}
