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
 *   EclipseSource - removed internal root node list from TSDAG (Bug 369907)
 *******************************************************************************/

package org.eclipse.virgo.util.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

import org.eclipse.virgo.util.common.GraphNode.DirectedAcyclicGraphVisitor;
import org.eclipse.virgo.util.common.GraphNode.ExceptionThrowingDirectedAcyclicGraphVisitor;
import org.junit.Test;

public class ThreadSafeGraphNodeTests {

    private static void add(List<String> l, String... s) {
        for (String string : s) {
            l.add(string);
        }
    }

    private static final List<String> EXPECTED_VISITS = new ArrayList<String>();

    static {
        add(EXPECTED_VISITS, "We", "Pa", "Cr", "B1", "Lo", "Fi", "Cu", "B2", "B3");
    }

    private GraphNode<String> buildTestGraphAndReturnRootNode() {
        return buildTestGraphAndReturnRootNode(new ThreadSafeDirectedAcyclicGraph<String>());
    }

    private GraphNode<String> buildTestGraphAndReturnRootNode(DirectedAcyclicGraph<String> graph) {
        return buildTestGraphAndReturnRootNode(graph, "We");
    }

    private GraphNode<String> buildTestGraphAndReturnRootNode(DirectedAcyclicGraph<String> graph, String rootValue) {
        GraphNode<String> top = graph.createNode(rootValue);

        // shared nodes
        GraphNode<String> lo = graph.createNode("Lo");
        GraphNode<String> fi = graph.createNode("Fi");

        // We.add(Pa)
        top.addChild(graph.createNode("Pa"));
        GraphNode<String> pa = top.getChildren().get(0);
        // Pa.add(Cr)
        pa.addChild(graph.createNode("Cr"));
        GraphNode<String> cr = pa.getChildren().get(0);
        cr.addChild(graph.createNode("B1"));
        cr.addChild(lo);
        cr.addChild(fi);

        // Pa.add(Cu)
        pa.addChild(graph.createNode("Cu"));
        GraphNode<String> cu = pa.getChildren().get(1);
        cu.addChild(graph.createNode("B2"));
        cu.addChild(lo);
        cu.addChild(fi);

        // Pa.add(B3)
        pa.addChild(graph.createNode("B3"));
        // Pa.add(Lo)
        pa.addChild(lo);

        return top;
    }

    private GraphNode<String> addFluffyToGraph(DirectedAcyclicGraph<String> graph) {
        GraphNode<String> body = graph.createNode("Fluffy's body");
        for (int i = 0; i < 3; i++) {
            GraphNode<String> head = graph.createNode("head " + i);
            head.addChild(body);
        }
        return body;
    }

    @Test
    public void testEmptyNode() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();

        GraphNode<String> nullGraph = graph.createNode(null);

        assertNull(nullGraph.getValue());
        assertEquals("null<>", nullGraph.toString());
    }

    @Test
    public void testDepthTwo() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> root = graph.createNode("root");

        root.addChild(graph.createNode("C1"));
        root.addChild(graph.createNode("C2"));

        assertEquals("root<C1<>, C2<>>", root.toString());
    }

    @Test
    public void testDepthThree() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> we = graph.createNode("We");
        GraphNode<String> pa = graph.createNode("Pa");
        GraphNode<String> cr = graph.createNode("Cr");

        we.addChild(pa);
        pa.addChild(cr);

        assertEquals("We<Pa<Cr<>>>", we.toString());
    }

    @Test
    public void testToString() {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();

        assertEquals("null<>", graph.createNode(null).toString());

        GraphNode<String> top = buildTestGraphAndReturnRootNode(graph);

        assertEquals("We<Pa<Cr<B1<>, Lo<>, Fi<>>, Cu<B2<>, Lo<>, Fi<>>, B3<>, Lo<>>>", top.toString());
    }

    @Test
    public void testHashCodeEquals() {
        DirectedAcyclicGraph<String> graphA = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> topA = graphA.createNode("root");

        assertFalse(topA.equals(null));
        assertFalse(topA.equals(new Object()));

        DirectedAcyclicGraph<String> graphB = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> topB = graphB.createNode("root");

        assertEquals(topA.hashCode(), topB.hashCode());
        assertEquals(topB.hashCode(), topA.hashCode());

        assertEquals(topA, topB);
        assertEquals(topB, topA);

        GraphNode<String> t1 = graphA.createNode("a");
        {
            GraphNode<String> a = t1;
            assertFalse(topA.equals(a));
            assertFalse(a.equals(topA));
        }

        {
            GraphNode<String> a = graphA.createNode(null);
            a.hashCode();
            assertFalse(topA.equals(a));
            assertFalse(a.equals(topA));
        }

        assertTrue(graphA.createNode(null).equals(graphA.createNode(null)));
        assertFalse(graphA.createNode(null).equals(graphA.createNode("a")));
        assertFalse(graphA.createNode("a").equals(graphA.createNode(null)));

        GraphNode<String> t2 = graphA.createNode("b");
        assertFalse(t1.equals(t2));
        assertFalse(t2.equals(t1));
    }

    @Test
    public void testSize() {
        GraphNode<String> top = buildTestGraphAndReturnRootNode();

        // (+1) Web shop application
        // (+1) Payment application
        // (+1) Credit card application
        // (+3) B1, Logging Bundle, Financial Utils (Lo and Fi first time)
        // (+1) Currency conversion application
        // (+1) B2, Logging Bundle, Financial Utils (B2 only)
        // (+1) B3
        // (+0) Logging Bundle
        assertEquals(9, top.size());
    }

    @Test
    public void testSizeWithNullNodes() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();

        GraphNode<String> graphWithNullNodes = graph.createNode("with null values");
        assertEquals(1, graphWithNullNodes.size());

        graphWithNullNodes.addChild(graph.createNode("first"));
        assertEquals(2, graphWithNullNodes.size());

        graphWithNullNodes.addChild(graph.createNode(null));
        assertEquals(3, graphWithNullNodes.size());

        graphWithNullNodes.addChild(graph.createNode("third"));
        assertEquals(4, graphWithNullNodes.size());
    }

    @Test
    public void testSizeWithMultipleRoots() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> fluffy = addFluffyToGraph(graph);

        assertEquals(1, fluffy.size());

        List<GraphNode<String>> parents = fluffy.getParents();
        for (GraphNode<String> parent : parents) {
            assertEquals(2, parent.size());
        }
    }

    @Test
    public void testChildren() {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> top = buildTestGraphAndReturnRootNode(graph);

        List<GraphNode<String>> children = top.getChildren();
        assertEquals(1, children.size());
        GraphNode<String> newChild = graph.createNode("newChild");

        top.addChild(newChild);
        assertEquals(2, children.size());
        assertEquals(newChild, children.get(1));
    }

    @Test
    public void testDAGWithNoChilds() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();

        GraphNode<String> solo = graph.createNode("solo");

        assertNotNull(solo.getChildren());
        assertTrue(solo.getChildren().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInstanceOfChild() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> child = new NoopGraphNode();

        graph.createNode("foo").addChild(child);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDuplicateInsertionOfAChild() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();

        GraphNode<String> rootNode = buildTestGraphAndReturnRootNode(graph);
        GraphNode<String> child = graph.createNode("child");

        rootNode.addChild(child);
        rootNode.addChild(child);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddChildFromOtherDAG() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> top = buildTestGraphAndReturnRootNode(graph);

        DirectedAcyclicGraph<String> secondGraph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> alienNode = secondGraph.createNode("alien");

        top.addChild(alienNode);
    }

    @Test
    public void testAddSharedChild() {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> cr = graph.createNode("cr");
        GraphNode<String> cu = graph.createNode("cu");
        GraphNode<String> lo = graph.createNode("lo");

        cr.addChild(lo);
        cu.addChild(lo);

        assertEquals(2, lo.getParents().size());
        assertTrue(lo.getParents().contains(cr));
        assertTrue(lo.getParents().contains(cu));

        // Ensure parents are correctly set up in both root nodes.
        checkParents(Collections.singletonList(cr));
        checkParents(Collections.singletonList(cu));
    }

    @Test
    public void testAddSharedChildWithMultipleThreads() throws Exception {

        final int THREAD_COUNT = 150;
        final CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT + 1);
        final DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        final GraphNode<String> sharedChild = graph.createNode("shared child");

        class AddChildThread extends Thread {

            private final int counter;

            public AddChildThread(int counter) {
                this.counter = counter;
            }

            @Override
            public void run() {
                try {
                    barrier.await();
                    GraphNode<String> root = graph.createNode("root" + this.counter);
                    root.addChild(sharedChild);
                    barrier.await();
                } catch (Exception e) {
                    fail();
                }
            }
        }

        for (int i = 0; i < THREAD_COUNT; i++) {
            new AddChildThread(i).start();
        }
        barrier.await(); // wait for all threads to be ready
        barrier.await(); // wait for all threads to be finish

        assertEquals(THREAD_COUNT, sharedChild.getParents().size());
    }

    private void checkParents(List<GraphNode<String>> parents) {
        for (GraphNode<String> parent : parents) {
            List<GraphNode<String>> children = parent.getChildren();
            for (GraphNode<String> child : children) {
                assertTrue(child.getParents().contains(parent));
                checkParents(Collections.singletonList(child));
            }
        }
    }

    @Test
    public void testChildrenListModification() {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> top = buildTestGraphAndReturnRootNode(graph);

        List<GraphNode<String>> children = top.getChildren();
        assertEquals(1, children.size());

        GraphNode<String> newChild = buildTestGraphAndReturnRootNode(graph, "newChild");
        children.add(newChild);
        assertEquals(2, children.size());
        assertEquals(newChild, children.get(1));
    }

    @Test
    public void testGetValue() {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> top = buildTestGraphAndReturnRootNode(graph);

        assertEquals("We", top.getValue());
    }

    @Test
    public void testRemoveChild() {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> top = buildTestGraphAndReturnRootNode(graph);

        List<GraphNode<String>> children = top.getChildren();
        assertTrue(top.removeChild(children.get(0)));

        assertEquals(1, top.size());
        assertFalse(top.removeChild(buildTestGraphAndReturnRootNode(graph, "unknownChild")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveWrongInstanceOfChild() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> child = new NoopGraphNode();

        graph.createNode("foo").removeChild(child);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveChildFromOtherDAG() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> top = buildTestGraphAndReturnRootNode(graph);

        DirectedAcyclicGraph<String> secondGraph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> alienNode = secondGraph.createNode("alien");

        top.removeChild(alienNode);
    }

    @Test
    public void testParent() {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> top = buildTestGraphAndReturnRootNode(graph);

        List<GraphNode<String>> children = top.getChildren();
        assertNotNull(top.getParents());
        assertTrue(top.getParents().isEmpty());

        GraphNode<String> child = children.get(0);
        assertEquals(Collections.singletonList(top), child.getParents());
        assertSame(top, child.getParents().get(0));

        top.removeChild(child);
        assertNotNull(child.getParents());
        assertTrue(child.getParents().isEmpty());
    }

    @Test
    public void testNormalVisit() {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> top = buildTestGraphAndReturnRootNode(graph);

        TestDirectedAcyclicGraphVisitor visitor = new TestDirectedAcyclicGraphVisitor();
        top.visit(visitor);
        assertTrue(EXPECTED_VISITS.equals(visitor.getVisited()));
    }

    @Test
    public void testSkippedVisit() {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();

        TestDirectedAcyclicGraphVisitor visitor = new TestDirectedAcyclicGraphVisitor();
        GraphNode<String> t = graph.createNode("-");
        t.visit(visitor);
        List<String> visited = visitor.getVisited();

        assertEquals(1, visited.size());
        assertEquals("-", visited.get(0));
    }

    @Test
    public void testPartiallySkippedVisit() {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> top = buildTestGraphAndReturnRootNode(graph);

        TestDirectedAcyclicGraphVisitor visitor = new TestDirectedAcyclicGraphVisitor();
        top.addChild(graph.createNode("-"));
        GraphNode<String> t = top.getChildren().get(1);
        t.addChild(graph.createNode("foo"));
        t.addChild(graph.createNode("bar"));
        assertEquals(12, top.size());

        top.visit(visitor);

        assertEquals(10, visitor.getVisited().size());
    }

    @Test
    public void testNormalExceptionVisit() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> top = buildTestGraphAndReturnRootNode(graph);

        TestExceptionThrowingDirectedAcyclicGraphVisitor visitor = new TestExceptionThrowingDirectedAcyclicGraphVisitor();
        top.visit(visitor);

        assertEquals(EXPECTED_VISITS, visitor.getVisited());
    }

    @Test
    public void testSkippedExceptionVisit() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();

        TestExceptionThrowingDirectedAcyclicGraphVisitor visitor = new TestExceptionThrowingDirectedAcyclicGraphVisitor();
        GraphNode<String> t = graph.createNode("-");
        t.visit(visitor);

        List<String> visited = visitor.getVisited();
        assertEquals(1, visited.size());
        assertEquals("-", visited.get(0));
    }

    @Test
    public void testPartiallySkippedDueToStopExceptionVisit() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> top = buildTestGraphAndReturnRootNode(graph);

        TestExceptionThrowingDirectedAcyclicGraphVisitor visitor = new TestExceptionThrowingDirectedAcyclicGraphVisitor();
        top.addChild(graph.createNode("-"));
        GraphNode<String> t = top.getChildren().get(1);
        t.addChild(graph.createNode("foo"));
        t.addChild(graph.createNode("bar"));
        assertEquals(12, top.size());

        top.visit(visitor);

        assertEquals(10, visitor.getVisited().size());
    }

    @Test(expected = Exception.class)
    public void testPartiallySkippedDueToExceptionExceptionVisit() throws Exception {
        DirectedAcyclicGraph<String> graph = new ThreadSafeDirectedAcyclicGraph<String>();
        GraphNode<String> top = buildTestGraphAndReturnRootNode(graph);

        TestExceptionThrowingDirectedAcyclicGraphVisitor visitor = new TestExceptionThrowingDirectedAcyclicGraphVisitor();
        top.addChild(graph.createNode("*"));
        GraphNode<String> t = top.getChildren().get(1);
        t.addChild(graph.createNode("foo"));
        t.addChild(graph.createNode("bar"));

        assertEquals(12, top.size());
        try {
            top.visit(visitor);
        } finally {
            assertEquals(10, visitor.getVisited().size());
        }
    }

    private static class TestDirectedAcyclicGraphVisitor implements DirectedAcyclicGraphVisitor<String> {

        private final List<String> visited = new ArrayList<String>();

        @Override
        public boolean visit(GraphNode<String> dag) {
            String value = dag.getValue();
            this.visited.add(value);
            return !value.startsWith("-");
        }

        public List<String> getVisited() {
            return this.visited;
        }
    }

    private static class TestExceptionThrowingDirectedAcyclicGraphVisitor implements ExceptionThrowingDirectedAcyclicGraphVisitor<String, Exception> {

        private final List<String> visited = new ArrayList<String>();

        @Override
        public boolean visit(GraphNode<String> dag) throws Exception {
            String value = dag.getValue();
            this.visited.add(value);
            if (value.startsWith("*")) {
                throw new Exception();
            }
            return !value.startsWith("-");
        }

        public List<String> getVisited() {
            return this.visited;
        }
    }

    protected static class NoopGraphNode implements GraphNode<String> {

        @Override
        public void visit(DirectedAcyclicGraphVisitor<String> visitor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <E extends Exception> void visit(ExceptionThrowingDirectedAcyclicGraphVisitor<String, E> visitor) throws E {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addChild(GraphNode<String> child) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeChild(GraphNode<String> child) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GraphNode<String>> getChildren() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GraphNode<String>> getParents() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getValue() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isRootNode() {
            throw new UnsupportedOperationException();
        }

    }

}
