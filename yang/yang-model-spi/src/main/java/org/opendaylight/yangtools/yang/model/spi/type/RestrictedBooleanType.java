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
import org.opendaylight.yangtools.yang.model.api.UnknownSchemaNode;
import org.opendaylight.yangtools.yang.model.api.type.BooleanTypeDefinition;

final class RestrictedBooleanType extends AbstractRestrictedType<BooleanTypeDefinition>
        implements BooleanTypeDefinition {
    RestrictedBooleanType(final BooleanTypeDefinition baseType, final QName qname,
            final Collection<? extends UnknownSchemaNode> unknownSchemaNodes) {
        super(baseType, qname, unknownSchemaNodes);
    }

    private RestrictedBooleanType(final RestrictedBooleanType original, final QName qname) {
        super(original, qname);
    }

    @Override
    RestrictedBooleanType bindTo(final QName newQName) {
        return new RestrictedBooleanType(this, newQName);
    }

    @Override
    public int hashCode() {
        return BooleanTypeDefinition.hashCode(this);
    }

    @Override
    public boolean equals(final Object obj) {
        return BooleanTypeDefinition.equals(this, obj);
    }

    @Override
    public String toString() {
        return BooleanTypeDefinition.toString(this);
    }
}