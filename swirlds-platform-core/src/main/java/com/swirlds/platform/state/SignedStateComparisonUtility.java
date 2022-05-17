/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform.state;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.internal.ApplicationDefinition;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.MerkleRouteException;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.utility.EmptyIterator;
import com.swirlds.platform.Browser;
import com.swirlds.platform.SignedStateFileManager;
import com.swirlds.platform.swirldapp.AppLoaderException;
import com.swirlds.platform.swirldapp.SwirldAppLoader;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static com.swirlds.common.merkle.iterators.MerkleIterationOrder.PRE_ORDERED_DEPTH_FIRST;

/**
 * A tool for loading and comparing signed states.
 */
public final class SignedStateComparisonUtility {

	private static int DEFAULT_LIMIT = 1_000;

	private SignedStateComparisonUtility() {

	}

	/**
	 * Load classes into the constructable registry which are required to load the state.
	 */
	public static void loadClasses()
			throws SocketException, UnknownHostException, AppLoaderException, ConstructableRegistryException {

		final ApplicationDefinition appDefinition = Browser.loadConfigFile(Set.of());
		final SwirldAppLoader appLoader = SwirldAppLoader.loadSwirldApp(
				appDefinition.getMainClassName(),
				appDefinition.getAppJarPath());
		ConstructableRegistry.registerConstructables("com.swirlds", appLoader.getClassLoader());
	}

	/**
	 * Load a signed state from file and hashes it.
	 *
	 * @param path
	 * 		the path to the file
	 * @return the signed state
	 */
	public static SignedState loadSignedState(final String path)
			throws IOException, ExecutionException, InterruptedException {

		final File file = new File(path);
		final Pair<Hash, SignedState> signedStatePair = SignedStateFileManager.readSignedStateFromFile(file);
		final SignedState signedState = signedStatePair.getValue();

		CryptoFactory.getInstance().digestTreeAsync(signedState.getState()).get();

		return signedState;
	}

	/**
	 * A pair of mismatched nodes. Nodes may be null.
	 *
	 * @param nodeA
	 * 		the node from tree A
	 * @param nodeB
	 * 		the node from tree B
	 */
	public record MismatchedNodes(MerkleNode nodeA, MerkleNode nodeB) {

	}

	/**
	 * Create a filter for the iterator that compares states.
	 */
	private static BiPredicate<MerkleNode, MerkleRoute> buildFilter(final MerkleNode rootB) {
		return (final MerkleNode nodeA, final MerkleRoute routeA) -> {

			MerkleNode nodeB;
			try {
				nodeB = rootB.getNodeAtRoute(routeA);
			} catch (final MerkleRouteException e) {
				nodeB = null;
			}

			if (nodeA == null && nodeB == null) {
				return false;
			}

			final Hash hashA = nodeA == null ? CryptoFactory.getInstance().getNullHash() : nodeA.getHash();
			final Hash hashB = nodeB == null ? CryptoFactory.getInstance().getNullHash() : nodeB.getHash();

			if (hashA == null) {
				throw new IllegalStateException("Node " + nodeA.getClass().getName() +
						" at position " + nodeA.getRoute() + " is unhashed");
			}

			if (hashB == null) {
				throw new IllegalStateException("Node " + nodeB.getClass().getName() +
						" at position " + nodeB.getRoute() + " is unhashed");
			}

			return !hashA.equals(hashB);
		};
	}

	/**
	 * Create a descendant filter for the iterator that compares states.
	 */
	private static Predicate<MerkleInternal> buildDescendantFilter(final MerkleNode rootB) {
		return (final MerkleInternal nodeA) -> {

			MerkleNode nodeB;
			try {
				nodeB = rootB.getNodeAtRoute(nodeA.getRoute());
			} catch (final MerkleRouteException e) {
				nodeB = null;
			}

			if (nodeB == null || nodeB.isLeaf()) {
				// There is no point in iterating over descendants if nodeB is incapable of having them
				return false;
			}

			final Hash hashA = nodeA.getHash();
			final Hash hashB = nodeB.getHash();

			if (hashA == null) {
				throw new IllegalStateException("Node " + nodeA.getClass().getName() +
						" at position " + nodeA.getRoute() + " is unhashed");
			}

			if (hashB == null) {
				throw new IllegalStateException("Node " + nodeB.getClass().getName() +
						" at position " + nodeB.getRoute() + " is unhashed");
			}

			return !hashA.equals(hashB);
		};
	}

	/**
	 * Create a lambda that will transform the comparison iterator into an iterator that returns pairs of nodes that
	 * are different.
	 */
	private static BiFunction<MerkleNode, MerkleRoute, MismatchedNodes> buildTransformer(final MerkleNode rootB) {
		return (final MerkleNode nodeA, final MerkleRoute routeA) -> {
			MerkleNode nodeB;
			try {
				nodeB = rootB.getNodeAtRoute(routeA);
			} catch (final MerkleRouteException e) {
				nodeB = null;
			}
			return new MismatchedNodes(nodeA, nodeB);
		};
	}

	/**
	 * Builds an iterator that walks over the parts of the merkle trees that do not match each other.
	 * Nodes are visited in pre-ordered depth first order.
	 *
	 * @param rootA
	 * 		the root of tree A, must be hashed
	 * @param rootB
	 * 		the root of tree B, must be hashed
	 * @return an iterator that contains differences between tree A and tree B
	 */
	public static Iterator<MismatchedNodes> mismatchedNodeIterator(final MerkleNode rootA, final MerkleNode rootB) {

		if (rootA == null && rootB == null) {
			return new EmptyIterator<>();
		}

		if (rootA == null) {
			return List.of(new MismatchedNodes(null, rootB)).iterator();
		}

		if (rootB == null) {
			return List.of(new MismatchedNodes(rootA, null)).iterator();
		}

		// This iterator could be made more efficient, but since the differences in a state are likely to be
		// small compared to the size of the state, such an optimization may not be worth the effort.
		return rootA.treeIterator()
				.setOrder(PRE_ORDERED_DEPTH_FIRST)
				.ignoreNull(false)
				.setFilter(buildFilter(rootB))
				.setDescendantFilter(buildDescendantFilter(rootB))
				.transform(buildTransformer(rootB));
	}

	/**
	 * Print to standard out all differences between two merkle trees.
	 *
	 * @param rootA
	 * 		a merkle tree, must be hashed
	 * @param rootB
	 * 		a merkle tree, must be hashed
	 * @param limit
	 * 		the maximum number of nodes to print
	 */
	public static void printMismatchedNodes(final MerkleNode rootA, final MerkleNode rootB, final int limit) {
		final Iterator<MismatchedNodes> iterator = mismatchedNodeIterator(rootA, rootB);

		if (iterator.hasNext()) {
			System.out.println("States do not match.\n");
		} else {
			System.out.println("States are identical.");
			return;
		}

		final StringBuilder sb = new StringBuilder();

		int count = 0;

		while (iterator.hasNext()) {
			count++;
			if (count > limit) {
				break;
			}

			final MismatchedNodes nodes = iterator.next();

			final MerkleNode nodeA = nodes.nodeA;
			final MerkleNode nodeB = nodes.nodeB;
			final MerkleRoute route = nodeA == null ? nodeB.getRoute() : nodeA.getRoute();

			sb.append("  ".repeat(route.size()));
			if (route.isEmpty()) {
				sb.append("(root)");
			} else {
				sb.append(route.getStep(-1));
			}
			sb.append(" ");

			if (nodeA == null) {
				sb.append("Node from tree A is NULL, node from tree B is a ").append(nodeB.getClass().getSimpleName());
			} else if (nodeB == null) {
				sb.append("Node from tree A is is a ").append(nodeA.getClass().getSimpleName())
						.append(", node from tree B is NULL");
			} else if (nodeA.getClassId() != nodeB.getClassId()) {
				sb.append("Node from tree A is a ").append(nodeA.getClass().getSimpleName())
						.append(", node from tree B is a ").append(nodeB.getClass().getSimpleName());
			} else {
				sb.append(nodeA.getClass().getSimpleName()).append(" A = ").append(nodeA.getHash())
						.append(", B = ").append(nodeB.getHash());
			}

			sb.append("\n");
		}

		System.out.println(sb);

		if (iterator.hasNext()) {
			int leftoverNodes = 0;
			while (iterator.hasNext()) {
				iterator.next();
				leftoverNodes++;
			}

			System.out.println("Maximum number of differences printed. " +
					leftoverNodes + " difference" + (leftoverNodes == 1 ? "" : "s") + " remain" +
					(leftoverNodes == 1 ? "s" : "") + ".");
		}
	}

	/**
	 * Must be run from SDK directory. Compares two states and writes the differences to standard out.
	 *
	 * @param args
	 * 		program arguments
	 */
	public static void main(String[] args) throws Exception {
		loadClasses();

		if (args.length < 2) {
			System.out.println("Usage: SignedStateComparisonUtility <path_to_stateA> <path_to_stateB>\n");
			System.exit(1);
		}

		final String stateAPath = args[0];
		final String stateBPath = args[1];

		System.out.println("Loading state A from " + stateAPath);
		final SignedState stateA = loadSignedState(stateAPath);
		System.out.println("State A hash = " + stateA.getState().getHash() +
				", round = " + stateA.getState().getPlatformState().getRound());

		System.out.println("Loading state B from " + stateBPath);
		final SignedState stateB = loadSignedState(stateBPath);
		System.out.println("State B hash = " + stateB.getState().getHash() +
				", round = " + stateB.getState().getPlatformState().getRound());

		printMismatchedNodes(stateA.getState(), stateB.getState(), DEFAULT_LIMIT);
	}
}
