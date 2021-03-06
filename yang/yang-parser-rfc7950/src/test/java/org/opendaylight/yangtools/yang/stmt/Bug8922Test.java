/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.stmt;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class Bug8922Test {
    private static final String NS = "foo";

    @Test
    public void testAllFeaturesSupported() throws Exception {
        final SchemaContext context = StmtTestUtils.parseYangSource("/bugs/bug8922/foo.yang");
        assertNotNull(context);
        final SchemaNode findNode = context.findDataTreeChild(qN("target"), qN("my-con")).get();
        assertThat(findNode, instanceOf(ContainerSchemaNode.class));
        assertEquals(Optional.of("New description"), findNode.getDescription());
    }

    @Test
    public void testNoFeatureSupported() throws Exception {
        final SchemaContext context = StmtTestUtils.parseYangSource("/bugs/bug8922/foo.yang", ImmutableSet.of());
        assertNotNull(context);
        assertEquals(Optional.empty(), context.findDataTreeChild(qN("target"), qN("my-con")));
        assertTrue(context.getAvailableAugmentations().isEmpty());
    }

    private static QName qN(final String localName) {
        return QName.create(NS, localName);
    }
}
