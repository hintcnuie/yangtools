/*
 * Copyright (c) 2015 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.model.spi.type;

import java.util.Collection;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.Status;
import org.opendaylight.yangtools.yang.model.api.UnknownSchemaNode;
import org.opendaylight.yangtools.yang.model.api.type.EmptyTypeDefinition;

final class DerivedEmptyType extends AbstractDerivedType<EmptyTypeDefinition> implements EmptyTypeDefinition {
    DerivedEmptyType(final EmptyTypeDefinition baseType, final QName qname, final Object defaultValue,
            final String description, final String reference, final Status status, final String units,
            final Collection<? extends UnknownSchemaNode> unknownSchemNodes) {
        super(baseType, qname, defaultValue, description, reference, status, units, unknownSchemNodes);
    }

    private DerivedEmptyType(final DerivedEmptyType original, final QName qname) {
        super(original, qname);
    }

    @Override
    DerivedEmptyType bindTo(final QName newQName) {
        return new DerivedEmptyType(this, newQName);
    }

    @Override
    public int hashCode() {
        return EmptyTypeDefinition.hashCode(this);
    }

    @Override
    public boolean equals(final Object obj) {
        return EmptyTypeDefinition.equals(this, obj);
    }
}