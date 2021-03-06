/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.parser.rfc7950.stmt.key;

import java.util.Set;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.stmt.KeyEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.KeyStatement;
import org.opendaylight.yangtools.yang.model.spi.meta.AbstractDeclaredEffectiveStatement;

abstract class AbstractKeyEffectiveStatement
        extends AbstractDeclaredEffectiveStatement.Default<Set<QName>, KeyStatement>
        implements KeyEffectiveStatement {
    abstract static class Foreign extends AbstractKeyEffectiveStatement {
        // Polymorphic, with single value or a collection
        private final Object argument;

        Foreign(final KeyStatement declared, final Set<QName> argument) {
            super(declared);
            this.argument = KeyStatementSupport.maskSet(argument);
        }

        @Override
        public final Set<QName> argument() {
            return KeyStatementSupport.unmaskSet(argument);
        }
    }

    abstract static class Local extends AbstractKeyEffectiveStatement {
        Local(final KeyStatement declared) {
            super(declared);
        }

        @Override
        public final Set<QName> argument() {
            return getDeclared().argument();
        }
    }

    AbstractKeyEffectiveStatement(final KeyStatement declared) {
        super(declared);
    }
}
