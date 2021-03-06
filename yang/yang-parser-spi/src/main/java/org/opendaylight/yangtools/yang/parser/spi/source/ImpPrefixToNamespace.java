/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.spi.source;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.parser.spi.meta.NamespaceBehaviour;
import org.opendaylight.yangtools.yang.parser.spi.meta.ParserNamespace;

/**
 * Pre-linkage source-specific mapping of prefixes to module namespaces.
 */
public interface ImpPrefixToNamespace extends ParserNamespace<String, XMLNamespace> {
    NamespaceBehaviour<String, XMLNamespace, @NonNull ImpPrefixToNamespace> BEHAVIOUR =
            NamespaceBehaviour.rootStatementLocal(ImpPrefixToNamespace.class);
}
