package simpledb;

import java.util.HashMap;

import simpledb.Predicate.Op;

/** A class to represent a fixed-width histogram over a single integer-based field.
 */
public class IntHistogram {

    /**
     * Create a new IntHistogram.
     *
     * This IntHistogram should maintain a histogram of integer values that it receives.
     * It should split the histogram into "buckets" buckets.
     *
     * The values that are being histogrammed will be provided one-at-a-time through the "addValue()" function.
     *
     * Your implementation should use space and have execution time that are both
     * constant with respect to the number of values being histogrammed.  For example, you shouldn't
     * simply store every value that you see in a sorted list.
     *
     * @param buckets The number of buckets to split the input value into.
     * @param min The minimum integer value that will ever be passed to this class for histogramming
     * @param max The maximum integer value that will ever be passed to this class for histogramming
     */

    private int numVals;
    private int min;
    private int max;
    private int numBuckets;
    private int bucketWidth;

    // the dictionary of number of elements per bucket
    private HashMap<Integer, Integer> elementCountMap;

    public IntHistogram(int buckets, int min, int max) {

        this.numVals = 0;
        this.min = min;
        this.max = max;

        this.numBuckets = buckets;
        this.bucketWidth = (int) Math.ceil(((double)((this.max - this.min) + 1)) / this.numBuckets); // there needs to be at least one element per bcuekrt
        this.elementCountMap = new HashMap<Integer, Integer>();

        for(int i = 0; i < this.numBuckets; i++) {
            this.elementCountMap.put(i, 0);
        }
    }

    /*
     * get the key of whatever bucket value b should be in
     */

    private int _getKey(int v) {
        // if (v < this.min || v > this.max) {
        //     return -1;
        // } ---> we actually can use the wrong values to determing comparisons
        return (int) Math.floor(((double) (v - this.min)) / this.bucketWidth); //i think key vals start at 0
    }

    /**
     * Add a value to the set of values that you are keeping a histogram of.
     * @param v Value to add to the histogram
     */
    public void addValue(int v) {
        int key = this._getKey(v);
        if (key == -1) return;
        this.elementCountMap.put(key, this.elementCountMap.get(key) + 1);

        //increase value count
        this.numVals++;
    }

    /**
     * Estimate the selectivity of a particular predicate and operand on this table.
     *
     * For example, if "op" is "GREATER_THAN" and "v" is 5,
     * return your estimate of the fraction of elements that are greater than 5.
     *
     * @param op Operator
     * @param v Value
     * @return Predicted selectivity of this particular operator and value
     */
    public double estimateSelectivity(Predicate.Op op, int v) {
        // op has EQUALS, GREATER_THAN, LESS_THAN, LESS_THAN_OR_EQ, GREATER_THAN_OR_EQ, LIKE, NOT_EQUALS;
        int key = this._getKey(v);

        if (op == Op.EQUALS) {
            if (key >= 0 && key < this.numBuckets) {
                return (((double) this.elementCountMap.get(key)) / (double) this.bucketWidth / (double) this.numVals); //only one really has to be a double but these rules always confuse me and we're not really tested on efficiency in this way
            }
            return 0; // if it isn't in our bucket map then there are none

        } else if (op == Op.GREATER_THAN) {
            // greater than but not equal to
            if (key >= 0 && key < this.numBuckets) {
                // get num in OWN BUCKET GREATER THAN
                // double percentageThroughBucket = ( (double) (v - this.min) /(double) this.bucketWidth) % 1; // dec rep'ing how far thru span in bucket v arises
                // double percentageInBucket = this.elementCountMap.get(key)
                // double percentageThroughHist =  (double) (this.numBuckets - key - 1) / (double) this.numBuckets;
                double b_fraction = (double) this.elementCountMap.get(key) / (double) this.numVals;
                int b_right = this.min + (this.bucketWidth * (key + 1)) - 1;
                double b_part = (double) (b_right - v) / (double) this.bucketWidth;
                double b_contributes = b_part * b_fraction; // all of this has just been following the instructions

                double selectivity = b_contributes;
                int selectKey = key + 1;
                while (selectKey < this.numBuckets) {
                    // get the selectivity of 100% of all of the elements greater than the bucket v was in
                    double k_part = (double) this.elementCountMap.get(selectKey) / (double) this.numVals;
                    selectivity += k_part;
                    selectKey++;
                }

                return selectivity;

            } else {
                return (key < 0) ? 1 : 0; // v is either smaller or larger than everything in our buckets
            }
        } else if (op == Op.LESS_THAN) {
            if (key >= 0 && key < this.numBuckets) {

                double b_fraction = (double) this.elementCountMap.get(key) / (double) this.numVals;
                int b_left = this.min + (this.bucketWidth * (key)); // get the starting value of the bucket now
                double b_part = (double) (v - b_left) / (double) this.bucketWidth; // get percent of bucket less than v (assuming even distrb)
                double b_contributes = b_part * b_fraction; // all of this has just been following the instructions

                double selectivity = b_contributes;
                int selectKey = key - 1;
                while (selectKey >= 0) {
                    // get the selectivity of 100% of all of the elements greater than the bucket v was in
                    double k_part = (double) this.elementCountMap.get(selectKey) / (double) this.numVals;
                    selectivity += k_part;
                    selectKey--;
                }

                return selectivity;

            } else {
                return (key < 0) ? 0 : 1; // v is either smaller or larger than everything in our buckets
            }

        } else if (op == Op.LESS_THAN_OR_EQ) {
            return this.estimateSelectivity(Predicate.Op.LESS_THAN, v) + this.estimateSelectivity(Predicate.Op.EQUALS, v); // return less than + equal to

        } else if (op == Op.GREATER_THAN_OR_EQ) {
            return this.estimateSelectivity(Predicate.Op.GREATER_THAN, v) + this.estimateSelectivity(Predicate.Op.EQUALS, v); // return greater than + equal to

        } else if (op == Op.LIKE) {
            return this.estimateSelectivity(Predicate.Op.EQUALS, v); // I THINK THIS IS HOW LIKE WORKS
        } else if (op == Op.NOT_EQUALS) { // could just be an else but idc i'm thorough
            return 1 - this.estimateSelectivity(Predicate.Op.EQUALS, v);
        }



        return 0;

    }

    /**
     * @return
     *     the average selectivity of this histogram.
     *
     *     This is not an indispensable method to implement the basic
     *     join optimization. It may be needed if you want to
     *     implement a more efficient optimization
     * */
    public double avgSelectivity()
    {
        // some code goes here
        return 1.0;
    }

    /**
     * @return A string describing this histogram, for debugging purposes
     */
    public String toString() {
        // some code goes here
        String response = this.elementCountMap.toString(); // lazy but i'll fix later
        return response;
    }
}
