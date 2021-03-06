/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.stmt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.spi.meta.SomeModifiersUnresolvedException;

public class SubstatementValidatorTest {
    @SuppressWarnings("checkstyle:regexpSinglelineJava")
    private final PrintStream stdout = System.out;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    @Before
    public void setUp() throws UnsupportedEncodingException {
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
    }

    @After
    public void cleanUp() {
        System.setOut(stdout);
    }

    @Test
    public void noException() throws Exception {
        assertNotNull(TestUtils.loadModules(getClass().getResource("/augment-test/augment-in-augment").toURI()));
    }

    @Test
    public void undesirableElementException() throws Exception {
        try {
            TestUtils.loadModules(getClass().getResource("/substatement-validator/undesirable-element").toURI());
            fail("Unexpected success");
        } catch (ReactorException ex) {
            assertNotNull(ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("TYPE is not valid for REVISION"));
        }
    }

    @Test
    public void maximalElementCountException() throws Exception {
        try {
            TestUtils.loadModules(getClass().getResource("/substatement-validator/maximal-element").toURI());
            fail("Unexpected success");
        } catch (ReactorException ex) {
            assertNotNull(ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("Maximal count of DESCRIPTION for AUGMENT is 1"));
        }
    }

    @Test
    public void missingElementException() {
        assertThrows(SomeModifiersUnresolvedException.class, () -> TestUtils.loadModules(
            SubstatementValidatorTest.class.getResource("/substatement-validator/missing-element").toURI()));
    }

    @Test
    public void bug6173Test() throws Exception {
        final Collection<? extends Module> loadModules = TestUtils.loadModules(getClass()
            .getResource("/substatement-validator/empty-element").toURI()).getModules();
        assertEquals(1, loadModules.size());
    }

    @Test
    public void bug4310test() throws Exception {
        assertThrows(SomeModifiersUnresolvedException.class, () -> TestUtils.loadModules(
            SubstatementValidatorTest.class.getResource("/substatement-validator/bug-4310").toURI()));
    }
}