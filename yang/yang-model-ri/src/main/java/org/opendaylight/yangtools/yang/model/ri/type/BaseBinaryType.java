/*
 * Copyright (c) 2015 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.model.ri.type;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.model.api.type.BinaryTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.TypeDefinitions;

final class BaseBinaryType extends AbstractLengthRestrictedBaseType<BinaryTypeDefinition>
        implements BinaryTypeDefinition {
    static final @NonNull BaseBinaryType INSTANCE = new BaseBinaryType();

    private BaseBinaryType() {
        super(TypeDefinitions.BINARY);
    }

    @Override
    public int hashCode() {
        return BinaryTypeDefinition.hashCode(this);
    }

    @Override
    public boolean equals(final Object obj) {
        return BinaryTypeDefinition.equals(this, obj);
    }

    @Override
    public String toString() {
        return BinaryTypeDefinition.toString(this);
    }
}
