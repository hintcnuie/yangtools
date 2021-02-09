/*
 * Copyright (c) 2015 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.model.spi.type;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UnknownSchemaNode;
import org.opendaylight.yangtools.yang.model.api.type.UnionTypeDefinition;

final class BaseUnionType extends AbstractBaseType<UnionTypeDefinition> implements UnionTypeDefinition {
    private final ImmutableList<TypeDefinition<?>> types;

    BaseUnionType(final QName qname, final Collection<? extends UnknownSchemaNode> unknownSchemaNodes,
            final Collection<TypeDefinition<?>> types) {
        super(qname, unknownSchemaNodes);
        this.types = ImmutableList.copyOf(types);
    }

    private BaseUnionType(final BaseUnionType original, final QName qname) {
        super(original, qname);
        this.types = original.types;
    }

    @Override
    BaseUnionType bindTo(final QName newQName) {
        return new BaseUnionType(this, newQName);
    }

    @Override
    public List<TypeDefinition<?>> getTypes() {
        return types;
    }

    @Override
    public int hashCode() {
        return UnionTypeDefinition.hashCode(this);
    }

    @Override
    public boolean equals(final Object obj) {
        return UnionTypeDefinition.equals(this, obj);
    }

    @Override
    public String toString() {
        return UnionTypeDefinition.toString(this);
    }
}