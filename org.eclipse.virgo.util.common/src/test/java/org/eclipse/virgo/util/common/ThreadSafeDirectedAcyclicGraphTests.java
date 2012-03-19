/*******************************************************************************
 * Copyright (c) 2011 VMware Inc. and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   VMware Inc. - initial contribution (ThreadSafeArrayListTreeTests.java)
 *   EclipseSource - reworked from generic tree to DAG (Bug 358697)
 *******************************************************************************/

package org.eclipse.virgo.util.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CyclicBarrier;

import org.junit.Before;
import org.junit.Test;

public class ThreadSafeDirectedAcyclicGraphTests {

    private DirectedAcyclicGraph<String> graph;

    private static DirectedAcyclicGraph<String> getDAG() {
        return getDAG("We");
    }

    private static DirectedAcyclicGraph<String> getDAG(String rootValue) {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();

        // shared nodes
        GraphNode<String> lo = graph.createNode("Lo");
        GraphNode<String> fi = graph.createNode("Fi");

        // We.add(Pa)
        GraphNode<String> we = graph.createNode("We");
        GraphNode<String> pa = graph.createNode("Pa");
        we.addChild(pa);
        // Pa.add(Cr)
        GraphNode<String> cr = graph.createNode("Cr");
        pa.addChild(cr);
        // Cr.add(B1,Lo,Fi)
        cr.addChild(graph.createNode("B1"));
        cr.addChild(lo);
        cr.addChild(fi);

        // Pa.add(Cu)
        GraphNode<String> cu = graph.createNode("Cu");
        pa.addChild(cu);
        cu.addChild(graph.createNode("B2"));
        cu.addChild(lo);
        cu.addChild(fi);

        // Pa.add(B3)
        pa.addChild(graph.createNode("B3"));
        // Pa.add(Lo)
        pa.addChild(lo);

        return graph;
    }

    @Before
    public void setUp() {
        this.graph = getDAG();
    }

    @Test
    public void testEmptyGraph() throws Exception {
        DirectedAcyclicGraph<String> emptyGraph = new ThreadSafeDirectedAcyclicGraph<String>();

        assertNotNull(emptyGraph);
    }

    @Test
    public void testGraphWithSingleRootNode() throws Exception {
        DirectedAcyclicGraph<String> smallGraph = new ThreadSafeDirectedAcyclicGraph<String>();

        GraphNode<String> rootNode = smallGraph.createNode("root");

        assertNotNull(rootNode);
    }

    @Test
    public void testGraphWithSingleNullRootNode() throws Exception {
        DirectedAcyclicGraph<String> smallGraph = new ThreadSafeDirectedAcyclicGraph<String>();

        GraphNode<String> rootNode = smallGraph.createNode(null);

        assertNotNull(rootNode);
    }

    @Test
    public void testGraphWithOnlyChildren() throws Exception {
        DirectedAcyclicGraph<String> onlyChildGraph = new ThreadSafeDirectedAcyclicGraph<String>();

        GraphNode<String> rootNode = onlyChildGraph.createNode("root");

        GraphNode<String> child = onlyChildGraph.createNode("child");
        rootNode.addChild(child);

        GraphNode<String> grandchild = onlyChildGraph.createNode("grandchild");
        child.addChild(grandchild);

        assertTrue(child.removeChild(grandchild));
        assertTrue(rootNode.removeChild(child));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDirectCycle() throws Exception {
        DirectedAcyclicGraph<Integer> smallGraph = new ThreadSafeDirectedAcyclicGraph<Integer>();
        GraphNode<Integer> root = smallGraph.createNode(Integer.valueOf(42));

        root.addChild(root);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParentNodeIsNotADescendantOfTheNewChild() throws Exception {
        DirectedAcyclicGraph<String> dag = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> rootNode = dag.createNode("root");
        GraphNode<String> child = dag.createNode("child");

        rootNode.addChild(child);
        child.addChild(rootNode);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParentIsNotADescendantOfTheNewChildDistanceTwo() throws Exception {
        DirectedAcyclicGraph<String> dag = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> rootNode = dag.createNode("root");

        GraphNode<String> child = dag.createNode("child");
        rootNode.addChild(child);
        GraphNode<String> child2 = dag.createNode("child2");
        rootNode.addChild(child2);
        GraphNode<String> grandchild = dag.createNode("grandchild");
        child.addChild(grandchild);

        grandchild.addChild(rootNode);
    }

    @Test
    public void testAddSharedChildWithMultipleThreads() throws Exception {

        final DirectedAcyclicGraph<String> sharedChildGraph = new ThreadSafeDirectedAcyclicGraph<String>();
        final int THREAD_COUNT = 150;
        final CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT + 1);
        final GraphNode<String> sharedChild = sharedChildGraph.createNode("shared child");
        assertEquals(0, sharedChild.getParents().size());

        class AddChildThread extends Thread {

            private final int counter;

            public AddChildThread(int counter) {
                this.counter = counter;
            }

            @Override
            public void run() {
                try {
                    barrier.await(); // 1
                    GraphNode<String> root = sharedChildGraph.createNode("root" + this.counter);
                    root.addChild(sharedChild);
                    barrier.await(); // 2
                    barrier.await();
                    assertTrue(root.removeChild(sharedChild));
                    barrier.await(); // 3
                    barrier.await();
                } catch (Exception e) {
                    fail();
                }
            }
        }

        for (int i = 0; i < THREAD_COUNT; i++) {
            new AddChildThread(i).start();
        }
        barrier.await(); // 1 - wait for all threads to be ready
        barrier.await(); // 2 - wait for all threads to create and add the nodes
        assertEquals(THREAD_COUNT, sharedChild.getParents().size());
        barrier.await(); //
        barrier.await(); // 3 - wait for all threads to be finish
        assertEquals(0, sharedChild.getParents().size());
        barrier.await(); // wait for all threads to be finish
        assertEquals(0, sharedChild.getParents().size());
    }

    @Test
    public void testGraphWithSharedNodes() throws Exception {
        DirectedAcyclicGraph<String> smallGraph = new ThreadSafeDirectedAcyclicGraph<String>();

        GraphNode<String> r1 = smallGraph.createNode("R1");
        GraphNode<String> c1 = smallGraph.createNode("C1");
        GraphNode<String> c2 = smallGraph.createNode("C2");
        smallGraph.createNode("C3");

        r1.addChild(c1);
        r1.addChild(c2);
        assertEquals("R1<C1<>, C2<>>", r1.toString());

        GraphNode<String> r2 = smallGraph.createNode("R2");

        r2.addChild(c1);
        assertEquals("R2<C1<>>", r2.toString());
    }

    @Test
    public void testDisassembleGraphWithSharedNodes() throws Exception {
        DirectedAcyclicGraph<String> smallGraph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> r1 = smallGraph.createNode("R1");
        GraphNode<String> c1 = smallGraph.createNode("C1");
        GraphNode<String> c2 = smallGraph.createNode("C2");
        r1.addChild(c1);
        r1.addChild(c2);
        GraphNode<String> r2 = smallGraph.createNode("R2");
        r2.addChild(c1);
        assertEquals("R1<C1<>, C2<>>", r1.toString());
        assertEquals("R2<C1<>>", r2.toString());

        // remove shared node
        assertTrue(r2.removeChild(c1));
        assertEquals("R1<C1<>, C2<>>", r1.toString());
        assertTrue(r1.removeChild(c2));
        assertTrue(r1.removeChild(c1));
    }

    @Test
    public void testHashCodeEquals() {
        assertFalse(this.graph.equals(null));

        assertFalse(this.graph.equals(new Object()));

        assertFalse(new ThreadSafeDirectedAcyclicGraph<String>().equals(new ThreadSafeDirectedAcyclicGraph<String>()));
    }

}
