package com.apontaja.back.customer.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regroupe les profils qui partagent un email ou un téléphone (même clé, voir {@link ContactKeys}). Les groupes
 * sont transitifs : A~B par email et B~C par téléphone donnent {A, B, C}. Fonction pure, sans accès base.
 */
public final class DuplicateDetector {

    private static final Comparator<DuplicateCandidate> OLDEST_FIRST = Comparator
            .comparing(DuplicateCandidate::createdAt).thenComparing(DuplicateCandidate::profileId);

    private DuplicateDetector() {
    }

    /** Un groupe de doublons : membres du plus ancien au plus récent, et motifs de rapprochement. */
    public record Cluster(List<DuplicateCandidate> members, Set<DuplicateReason> reasons) {
    }

    /** Groupes d'au moins deux profils, le plus ancien d'abord. */
    public static List<Cluster> detect(List<DuplicateCandidate> candidates) {
        int size = candidates.size();
        int[] parent = new int[size];
        Map<String, List<Integer>> byEmail = new HashMap<>();
        Map<String, List<Integer>> byPhone = new HashMap<>();
        for (int i = 0; i < size; i++) {
            parent[i] = i;
            DuplicateCandidate candidate = candidates.get(i);
            index(byEmail, ContactKeys.emailKey(candidate.email()), i);
            index(byPhone, ContactKeys.phoneKey(candidate.phone()), i);
        }

        union(parent, byEmail.values());
        union(parent, byPhone.values());
        Map<Integer, Set<DuplicateReason>> reasonsByRoot = new HashMap<>();
        collectReasons(parent, byEmail.values(), DuplicateReason.EMAIL, reasonsByRoot);
        collectReasons(parent, byPhone.values(), DuplicateReason.PHONE, reasonsByRoot);

        Map<Integer, List<DuplicateCandidate>> membersByRoot = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int root = find(parent, i);
            if (reasonsByRoot.containsKey(root)) {
                membersByRoot.computeIfAbsent(root, key -> new ArrayList<>()).add(candidates.get(i));
            }
        }

        List<Cluster> clusters = new ArrayList<>();
        for (Map.Entry<Integer, List<DuplicateCandidate>> entry : membersByRoot.entrySet()) {
            List<DuplicateCandidate> members = new ArrayList<>(entry.getValue());
            members.sort(OLDEST_FIRST);
            clusters.add(new Cluster(List.copyOf(members), reasonsByRoot.get(entry.getKey())));
        }
        clusters.sort(Comparator.comparing((Cluster cluster) -> cluster.members().get(0), OLDEST_FIRST));
        return clusters;
    }

    private static void index(Map<String, List<Integer>> buckets, String key, int position) {
        if (key != null) {
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(position);
        }
    }

    private static void union(int[] parent, Collection<List<Integer>> buckets) {
        for (List<Integer> bucket : buckets) {
            for (int j = 1; j < bucket.size(); j++) {
                int rootA = find(parent, bucket.get(0));
                int rootB = find(parent, bucket.get(j));
                if (rootA != rootB) {
                    parent[rootB] = rootA;
                }
            }
        }
    }

    private static void collectReasons(int[] parent, Collection<List<Integer>> buckets, DuplicateReason reason,
            Map<Integer, Set<DuplicateReason>> reasonsByRoot) {
        for (List<Integer> bucket : buckets) {
            if (bucket.size() >= 2) {
                reasonsByRoot.computeIfAbsent(find(parent, bucket.get(0)),
                        key -> EnumSet.noneOf(DuplicateReason.class)).add(reason);
            }
        }
    }

    private static int find(int[] parent, int start) {
        int root = start;
        while (parent[root] != root) {
            root = parent[root];
        }
        int current = start;
        while (parent[current] != root) {
            int next = parent[current];
            parent[current] = root;
            current = next;
        }
        return root;
    }
}
