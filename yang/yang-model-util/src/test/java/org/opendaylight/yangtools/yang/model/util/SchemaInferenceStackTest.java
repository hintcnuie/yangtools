/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.model.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.PathExpression;
import org.opendaylight.yangtools.yang.model.api.PathExpression.LocationPathSteps;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.opendaylight.yangtools.yang.xpath.api.YangLocationPath;
import org.opendaylight.yangtools.yang.xpath.api.YangXPathAxis;

public class SchemaInferenceStackTest {
    private static EffectiveModelContext context;
    private static Module myModule;

    @BeforeClass
    public static void beforeClass() {
        context = YangParserTestUtils.parseYangResourceDirectory("/schema-context-util");
        myModule = context.findModule(XMLNamespace.of("uri:my-module"), Revision.of("2014-10-07")).get();
    }

    @Test
    public void findDataSchemaNodeTest() {
        final Module importedModule = context.findModule(XMLNamespace.of("uri:imported-module"),
            Revision.of("2014-10-07")).get();

        final QName myImportedContainer = QName.create(importedModule.getQNameModule(), "my-imported-container");
        final QName myImportedLeaf = QName.create(importedModule.getQNameModule(), "my-imported-leaf");

        final SchemaNode testNode = ((ContainerSchemaNode) importedModule.getDataChildByName(myImportedContainer))
            .getDataChildByName(myImportedLeaf);

        final PathExpression expr = mock(PathExpression.class);
        doReturn(true).when(expr).isAbsolute();
        doReturn(new LocationPathSteps(YangLocationPath.absolute(
            YangXPathAxis.CHILD.asStep(myImportedContainer), YangXPathAxis.CHILD.asStep(myImportedLeaf))))
                .when(expr).getSteps();

        assertEquals(testNode, SchemaInferenceStack.of(context).resolvePathExpression(expr));
    }

    @Test
    public void findDataSchemaNodeTest2() {
        final QName myLeafInGrouping2 = QName.create(myModule.getQNameModule(), "my-leaf-in-gouping2");
        final PathExpression expr = mock(PathExpression.class);
        doReturn(true).when(expr).isAbsolute();
        doReturn(new LocationPathSteps(YangLocationPath.relative(YangXPathAxis.CHILD.asStep(myLeafInGrouping2))))
            .when(expr).getSteps();

        final GroupingDefinition grouping = getGroupingByName(myModule, "my-grouping");
        final SchemaInferenceStack stack = SchemaInferenceStack.of(context);
        assertSame(grouping, stack.enterGrouping(grouping.getQName()));
        assertEquals(grouping.getDataChildByName(myLeafInGrouping2), stack.resolvePathExpression(expr));
    }

    private static GroupingDefinition getGroupingByName(final DataNodeContainer dataNodeContainer, final String name) {
        for (final GroupingDefinition grouping : dataNodeContainer.getGroupings()) {
            if (grouping.getQName().getLocalName().equals(name)) {
                return grouping;
            }
        }
        return null;
    }
}