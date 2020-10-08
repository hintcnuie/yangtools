/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.data.impl.codec;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.odlext.model.api.YangModeledAnyxmlSchemaNode;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.SchemaUtils;
import org.opendaylight.yangtools.yang.model.api.AnydataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AnyxmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationTarget;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DocumentedNode.WithStatus;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.util.EffectiveAugmentationSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for tracking the underlying state of the underlying
 * schema node.
 */
@Beta
public final class SchemaTracker {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaTracker.class);
    private final Deque<WithStatus> schemaStack = new ArrayDeque<>();
    private final DataNodeContainer root;

    private SchemaTracker(final DataNodeContainer root) {
        this.root = requireNonNull(root);
    }

    /**
     * Create a new writer with the specified node as its root.
     *
     * @param root Root node
     * @return A new {@link NormalizedNodeStreamWriter}
     */
    public static @NonNull SchemaTracker create(final DataNodeContainer root) {
        return new SchemaTracker(root);
    }

    /**
     * Create a new writer with the specified context and rooted in the specified schema path.
     *
     * @param context Associated {@link EffectiveModelContext}
     * @param path schema path
     * @return A new {@link NormalizedNodeStreamWriter}
     */
    public static @NonNull SchemaTracker create(final EffectiveModelContext context, final Absolute path) {
        return create(context, path.getNodeIdentifiers());
    }

    /**
     * Create a new writer with the specified context and rooted in the specified schema path.
     *
     * @param context Associated {@link EffectiveModelContext}
     * @param path schema path
     * @return A new {@link NormalizedNodeStreamWriter}
     */
    public static @NonNull SchemaTracker create(final EffectiveModelContext context, final SchemaPath path) {
        return create(context, path.getPathFromRoot());
    }

    private static @NonNull SchemaTracker create(final EffectiveModelContext context, final Iterable<QName> path) {
        final Collection<SchemaNode> schemaNodes = SchemaUtils.findParentSchemaNodesOnPath(context, path);
        checkArgument(!schemaNodes.isEmpty(), "Unable to find schema node for supplied schema path: %s", path);
        if (schemaNodes.size() > 1) {
            LOG.warn("More possible schema nodes {} for supplied schema path {}", schemaNodes, path);
        }
        final Optional<DataNodeContainer> current = schemaNodes.stream()
                .filter(node -> node instanceof DataNodeContainer).map(DataNodeContainer.class::cast)
                .findFirst();
        checkArgument(current.isPresent(),
                "Schema path must point to container or list or an rpc input/output. Supplied path %s pointed to: %s",
                path, current);
        return new SchemaTracker(current.get());
    }

    /**
     * Create a new writer with the specified context and rooted in the specified schema path.
     *
     * @param context Associated {@link EffectiveModelContext}
     * @param operation Operation schema path
     * @param qname Input/Output container QName
     * @return A new {@link NormalizedNodeStreamWriter}
     */
    public static @NonNull SchemaTracker forOperation(final EffectiveModelContext context, final Absolute operation,
            final QName qname) {
        return create(context, Iterables.concat(operation.getNodeIdentifiers(), List.of(qname)));
    }

    public Object getParent() {
        if (schemaStack.isEmpty()) {
            return root;
        }
        return schemaStack.peek();
    }

    private SchemaNode getSchema(final PathArgument name) {
        final Object parent = getParent();
        SchemaNode schema = null;
        final QName qname = name.getNodeType();
        if (parent instanceof DataNodeContainer) {
            schema = ((DataNodeContainer)parent).getDataChildByName(qname);
            if (schema == null) {
                if (parent instanceof GroupingDefinition) {
                    schema = (GroupingDefinition) parent;
                } else if (parent instanceof NotificationDefinition) {
                    schema = (NotificationDefinition) parent;
                }
            }
        } else if (parent instanceof ChoiceSchemaNode) {
            schema = findChildInCases((ChoiceSchemaNode) parent, qname);
        } else {
            throw new IllegalStateException("Unsupported schema type " + parent.getClass() + " on stack.");
        }

        checkArgument(schema != null, "Could not find schema for node %s in %s", qname, parent);
        return schema;
    }

    private static SchemaNode findChildInCases(final ChoiceSchemaNode parent, final QName qname) {
        for (final CaseSchemaNode caze : parent.getCases()) {
            final Optional<DataSchemaNode> potential = caze.findDataChildByName(qname);
            if (potential.isPresent()) {
                return potential.get();
            }
        }
        return null;
    }

    private static SchemaNode findCaseByChild(final ChoiceSchemaNode parent, final QName qname) {
        for (final CaseSchemaNode caze : parent.getCases()) {
            final Optional<DataSchemaNode> potential = caze.findDataChildByName(qname);
            if (potential.isPresent()) {
                return caze;
            }
        }
        return null;
    }

    public void startList(final PathArgument name) {
        final SchemaNode schema = getSchema(name);
        checkArgument(schema instanceof ListSchemaNode, "Node %s is not a list", schema);
        schemaStack.push(schema);
    }

    public void startListItem(final PathArgument name) throws IOException {
        final Object schema = getParent();
        checkArgument(schema instanceof ListSchemaNode, "List item is not appropriate");
        schemaStack.push((ListSchemaNode) schema);
    }

    public LeafSchemaNode leafNode(final NodeIdentifier name) throws IOException {
        final SchemaNode schema = getSchema(name);
        checkArgument(schema instanceof LeafSchemaNode, "Node %s is not a leaf", schema);
        return (LeafSchemaNode) schema;
    }

    public void startLeafNode(final NodeIdentifier name) throws IOException {
        schemaStack.push(leafNode(name));
    }

    public LeafListSchemaNode startLeafSet(final NodeIdentifier name) {
        final SchemaNode schema = getSchema(name);
        checkArgument(schema instanceof LeafListSchemaNode, "Node %s is not a leaf-list", schema);
        schemaStack.push(schema);
        return (LeafListSchemaNode) schema;
    }

    public LeafListSchemaNode leafSetEntryNode(final QName qname) {
        final Object parent = getParent();
        if (parent instanceof LeafListSchemaNode) {
            return (LeafListSchemaNode) parent;
        }

        final SchemaNode child = SchemaUtils.findDataChildSchemaByQName((SchemaNode) parent, qname);
        checkArgument(child instanceof LeafListSchemaNode,
            "Node %s is neither a leaf-list nor currently in a leaf-list", child);
        return (LeafListSchemaNode) child;
    }

    public void startLeafSetEntryNode(final NodeWithValue<?> name) {
        schemaStack.push(leafSetEntryNode(name.getNodeType()));
    }

    public ChoiceSchemaNode startChoiceNode(final NodeIdentifier name) {
        LOG.debug("Enter choice {}", name);
        final SchemaNode schema = getSchema(name);

        checkArgument(schema instanceof ChoiceSchemaNode, "Node %s is not a choice", schema);
        schemaStack.push(schema);
        return (ChoiceSchemaNode)schema;
    }

    public SchemaNode startContainerNode(final NodeIdentifier name) {
        LOG.debug("Enter container {}", name);
        final SchemaNode schema = getSchema(name);
        final boolean isAllowed = schema instanceof ContainerLike | schema instanceof NotificationDefinition;

        checkArgument(isAllowed, "Node %s is not a container nor a notification", schema);
        schemaStack.push(schema);
        return schema;
    }

    public SchemaNode startYangModeledAnyXmlNode(final NodeIdentifier name) {
        LOG.debug("Enter yang modeled anyXml {}", name);
        final SchemaNode schema = getSchema(name);

        checkArgument(schema instanceof YangModeledAnyxmlSchemaNode, "Node %s is not an yang modeled anyXml.", schema);
        schemaStack.push(((YangModeledAnyxmlSchemaNode) schema).getSchemaOfAnyXmlData());
        return schema;
    }

    public AugmentationSchemaNode startAugmentationNode(final AugmentationIdentifier identifier) {
        LOG.debug("Enter augmentation {}", identifier);
        Object parent = getParent();

        checkArgument(parent instanceof AugmentationTarget, "Augmentation not allowed under %s", parent);
        if (parent instanceof ChoiceSchemaNode) {
            final QName name = Iterables.get(identifier.getPossibleChildNames(), 0);
            parent = findCaseByChild((ChoiceSchemaNode) parent, name);
        }
        checkArgument(parent instanceof DataNodeContainer, "Augmentation allowed only in DataNodeContainer", parent);
        final AugmentationSchemaNode schema = SchemaUtils.findSchemaForAugment((AugmentationTarget) parent,
            identifier.getPossibleChildNames());
        final AugmentationSchemaNode resolvedSchema = EffectiveAugmentationSchema.create(schema,
            (DataNodeContainer) parent);
        schemaStack.push(resolvedSchema);
        return resolvedSchema;
    }

    public AnyxmlSchemaNode anyxmlNode(final NodeIdentifier name) {
        final SchemaNode schema = getSchema(name);
        checkArgument(schema instanceof AnyxmlSchemaNode, "Node %s is not anyxml", schema);
        return (AnyxmlSchemaNode)schema;
    }

    public void startAnyxmlNode(final NodeIdentifier name) {
        schemaStack.push(anyxmlNode(name));
    }

    public AnydataSchemaNode anydataNode(final NodeIdentifier name) {
        final SchemaNode schema = getSchema(name);
        checkArgument(schema instanceof AnydataSchemaNode, "Node %s is not anydata", schema);
        return (AnydataSchemaNode)schema;
    }

    public void startAnydataNode(final NodeIdentifier name) {
        schemaStack.push(anydataNode(name));
    }

    public Object endNode() {
        return schemaStack.pop();
    }
}
