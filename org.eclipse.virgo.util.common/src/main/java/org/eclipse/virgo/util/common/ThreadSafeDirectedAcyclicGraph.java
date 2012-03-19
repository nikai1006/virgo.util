/*******************************************************************************
 * Copyright (c) 2011 VMware Inc. and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   VMware Inc. - initial contribution (ThreadSafeArrayListTree.java)
 *   EclipseSource - reworked from generic tree to DAG (Bug 358697)
 *   EclipseSource - removed internal root node list (Bug 369907)
 *******************************************************************************/

package org.eclipse.virgo.util.common;


/**
 * {@link DirectedAcyclicGraph} is a set of {@link GraphNode}s with a parent child relationship. The nodes are connected
 * to each other in a way that it is impossible to start at a node n and follow a child relationship that loops back to
 * n. The DAG may have multiple root nodes (nodes with no parents) and nodes may share children.
 * <p />
 * Once created a root node can become a non-root node by adding the node as a child to another node. This can be done
 * by calling the method addChild on a node. All nodes of a DAG are reachable from its root nodes.
 * 
 * <strong>Concurrent Semantics</strong><br />
 * 
 * This class is thread safe.
 * 
 * @param <V> type of values in the graph
 */
public class ThreadSafeDirectedAcyclicGraph<V> implements DirectedAcyclicGraph<V> {

    private final Object monitor = new Object();

    /**
     * {@inheritDoc}
     */
    @Override
    public ThreadSafeGraphNode<V> createNode(V value) {
        return new ThreadSafeGraphNode<V>(value, this.monitor);
    }

}
