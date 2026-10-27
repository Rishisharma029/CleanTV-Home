package com.gathin.tvhome.ai;

/**
 * High-performance, zero-allocation-in-loop Levenshtein distance algorithm in pure Java.
 * Features:
 * - Two-row array space optimization (O(min(M, N)) space).
 * - Length-difference early exit (O(1) pre-check).
 * - Threshold-bounded calculation with early termination when row minimum exceeds maxDistance.
 * - Zero external dependencies, 100% pure Java 17.
 */
public final class LevenshteinDistance {

    private LevenshteinDistance() {}

    /**
     * Computes the Levenshtein distance between s1 and s2, capped at maxDistance.
     * If distance is strictly greater than maxDistance, returns maxDistance + 1.
     */
    public static int compute(CharSequence s1, CharSequence s2, int maxDistance) {
        if (s1 == null || s2 == null) {
            return maxDistance + 1;
        }

        int m = s1.length();
        int n = s2.length();

        // Length difference filter: if lengths differ by more than maxDistance,
        // it is mathematically impossible for edit distance to be <= maxDistance.
        if (Math.abs(m - n) > maxDistance) {
            return maxDistance + 1;
        }

        // Trivial identity checks
        if (s1.equals(s2)) {
            return 0;
        }
        if (m == 0) return (n <= maxDistance) ? n : maxDistance + 1;
        if (n == 0) return (m <= maxDistance) ? m : maxDistance + 1;

        // Ensure s1 is shorter to minimize row buffer allocations
        if (m > n) {
            CharSequence tmp = s1;
            s1 = s2;
            s2 = tmp;
            int tmpLen = m;
            m = n;
            n = tmpLen;
        }

        // Two-row buffers
        int[] v0 = new int[m + 1];
        int[] v1 = new int[m + 1];

        for (int i = 0; i <= m; i++) {
            v0[i] = i;
        }

        for (int j = 1; j <= n; j++) {
            v1[0] = j;
            int rowMin = v1[0];
            char c2 = s2.charAt(j - 1);

            for (int i = 1; i <= m; i++) {
                char c1 = s1.charAt(i - 1);
                int cost = (c1 == c2) ? 0 : 1;

                int insertion = v1[i - 1] + 1;
                int deletion = v0[i] + 1;
                int substitution = v0[i - 1] + cost;

                int min = insertion < deletion ? insertion : deletion;
                if (substitution < min) {
                    min = substitution;
                }
                v1[i] = min;

                if (min < rowMin) {
                    rowMin = min;
                }
            }

            // Early row-minimum termination
            if (rowMin > maxDistance) {
                return maxDistance + 1;
            }

            // Swap buffers
            int[] tmp = v0;
            v0 = v1;
            v1 = tmp;
        }

        int finalDist = v0[m];
        return (finalDist <= maxDistance) ? finalDist : maxDistance + 1;
    }
}
