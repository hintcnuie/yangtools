/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.model.spi.stmt;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.meta.DeclaredStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.InputStatement;

/**
 * Static entry point to instantiating {@link DeclaredStatements} covered in the {@code RFC7950} metamodel which are
 * not really declared, i.e. inferred to exist.
 */
@Beta
@NonNullByDefault
public final class UndeclaredStatements {
    private UndeclaredStatements() {
        // Hidden on purpose
    }

    public static InputStatement createInput(final QName argument) {
        return new EmptyUndeclaredInputStatement(argument);
    }

    public static InputStatement createInput(final QName argument,
            final ImmutableList<? extends DeclaredStatement<?>> substatements) {
        return substatements.isEmpty() ? createInput(argument)
            : new RegularUndeclaredInputStatement(argument, substatements);
    }
}
