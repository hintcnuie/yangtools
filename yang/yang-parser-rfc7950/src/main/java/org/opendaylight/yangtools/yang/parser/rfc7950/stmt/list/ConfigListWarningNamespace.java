/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.rfc7950.stmt.list;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.parser.spi.meta.NamespaceBehaviour;
import org.opendaylight.yangtools.yang.parser.spi.meta.ParserNamespace;
import org.opendaylight.yangtools.yang.parser.spi.source.StatementSourceReference;

@Beta
public interface ConfigListWarningNamespace extends ParserNamespace<StatementSourceReference, Boolean> {
    NamespaceBehaviour<StatementSourceReference, Boolean, @NonNull ConfigListWarningNamespace> BEHAVIOUR =
            NamespaceBehaviour.global(ConfigListWarningNamespace.class);
}
