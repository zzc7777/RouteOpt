package org.zzc.bpcsolver.util;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.util.Arrays;
import java.util.List;

public final class ArrayOperation {

    private static final double DEFAULT_TOLERANCE = 1e-6;

    private ArrayOperation() {
    }

    public static int[] deepCopyIntArray(int[] original) {
        if (original == null) {
            return null;
        }
        int[] copy = new int[original.length];
        System.arraycopy(original, 0, copy, 0, original.length);
        return copy;
    }

    public static double[] deepCopyDoubleArray(double[] original) {
        if (original == null) {
            return null;
        }
        double[] copy = new double[original.length];
        System.arraycopy(original, 0, copy, 0, original.length);
        return copy;
    }

    public static double[][] deepCopyDouble2DArray(double[][] original) {
        if (original == null) {
            return null;
        }
        double[][] copy = new double[original.length][];
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) {
                copy[i] = new double[original[i].length];
                System.arraycopy(original[i], 0, copy[i], 0, original[i].length);
            }
        }
        return copy;
    }

    public static TIntSet intersection(TIntSet set, TIntArrayList list) {
        TIntSet result = new TIntHashSet(set);
        result.retainAll(list);
        return result;
    }

    public static TIntSet union(TIntSet set1, TIntSet set2) {
        TIntSet result = new TIntHashSet(set1);
        result.addAll(set2);
        return result;
    }

    public static boolean isIntegerValue(double value) {
        return Math.abs(value) <= DEFAULT_TOLERANCE
                || Math.abs(value - 1.0) <= DEFAULT_TOLERANCE;
    }

    public static boolean equalOne(double value) {
        return Math.abs(value - 1.0) <= DEFAULT_TOLERANCE;
    }

    public static boolean equalZero(double value) {
        return Math.abs(value) <= DEFAULT_TOLERANCE;
    }

    public static boolean containsArray(List<int[]> list, int[] array) {
        return list.stream().anyMatch(arr -> Arrays.equals(arr, array));
    }

    public static boolean isSetContained(TIntSet set1, TIntSet set2) {
        int[] set1Array = set1.toArray();
        for (int element : set1Array) {
            if (!set2.contains(element)) {
                return false;
            }
        }
        return true;
    }
}
