/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.data.codec.binfmt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LithiumWriteObjectMappingTest {
    @Test
    public void testStringType() {
        assertEquals(LithiumValue.STRING_TYPE, AbstractLithiumDataOutput.getSerializableType("foobar"));
        final String largeString = largeString(LithiumValue.STRING_BYTES_LENGTH_THRESHOLD);
        assertEquals(LithiumValue.STRING_BYTES_TYPE, AbstractLithiumDataOutput.getSerializableType(largeString));
    }

    private static String largeString(final int minSize) {
        final int pow = (int) (Math.log(minSize * 2) / Math.log(2));
        StringBuilder sb = new StringBuilder("X");
        for (int i = 0; i < pow; i++) {
            sb.append(sb);
        }
        return sb.toString();
    }
}
