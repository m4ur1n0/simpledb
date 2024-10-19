package simpledb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import simpledb.Predicate.Op;

/**
 * TableStats represents statistics (e.g., histograms) about base tables in a
 * query.
 *
 * This class is not needed in implementing lab1 and lab2.
 */
public class TableStats {

    private static final ConcurrentHashMap<String, TableStats> statsMap = new ConcurrentHashMap<String, TableStats>();

    static final int IOCOSTPERPAGE = 1000;

    public static TableStats getTableStats(String tablename) {
        return statsMap.get(tablename);
    }

    public static void setTableStats(String tablename, TableStats stats) {
        statsMap.put(tablename, stats);
    }

    public static void setStatsMap(HashMap<String,TableStats> s)
    {
        try {
            java.lang.reflect.Field statsMapF = TableStats.class.getDeclaredField("statsMap");
            statsMapF.setAccessible(true);
            statsMapF.set(null, s);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    public static Map<String, TableStats> getStatsMap() {
        return statsMap;
    }

    public static void computeStatistics() {
        Iterator<Integer> tableIt = Database.getCatalog().tableIdIterator();

        System.out.println("Computing table stats.");
        while (tableIt.hasNext()) {
            int tableid = tableIt.next();
            TableStats s = new TableStats(tableid, IOCOSTPERPAGE);
            setTableStats(Database.getCatalog().getTableName(tableid), s);
        }
        System.out.println("Done.");
    }

    /**
     * Number of bins for the histogram. Feel free to increase this value over
     * 100, though our tests assume that you have at least 100 bins in your
     * histograms.
     */
    static final int NUM_HIST_BINS = 100;

    private TupleDesc td;
    private int ioCostPerPage;
    private int tableid;
    private int numTups;

    private HashMap<Integer, Integer> mins;
    private HashMap<Integer, Integer> maxes;
    private HashMap<Integer, IntHistogram> intHistograms;
    private HashMap<Integer, StringHistogram> stringHistograms;

    private HeapFile heapfile; // the actual table file
    private int numPages;





    /**
     * Create a new TableStats object, that keeps track of statistics on each
     * column of a table
     *
     * @param tableid
     *            The table over which to compute statistics
     * @param ioCostPerPage
     *            The cost per page of IO. This doesn't differentiate between
     *            sequential-scan IO and disk seeks.
     */
    public TableStats(int tableid, int ioCostPerPage) {
        // For this function, you'll have to get the
        // DbFile for the table in question,
        // then scan through its tuples and calculate
        // the values that you need.
        // You should try to do this reasonably efficiently, but you don't
        // necessarily have to (for example) do everything
        // in a single scan of the table.
        // some code goes here
        this.tableid = tableid;
        this.ioCostPerPage = ioCostPerPage; // there is a static var that feels like should be this, but this is a required variable.......

        /*
         * field maxes and mins are mapped from field index to the maximum (or minimum) field in that column
         */
        this.mins = new HashMap<Integer, Integer>();
        this.maxes = new HashMap<Integer, Integer>();

        this.intHistograms = new HashMap<>();
        this.stringHistograms = new HashMap<>();

        this.heapfile = (HeapFile) Database.getCatalog().getDatabaseFile(tableid); //if this fails just let it throw its own error -- shouldn't fail
        this.td = this.heapfile.getTupleDesc();

        this.numTups = 0;

        // THIS IS HOW THEO MAURINO WOULD HAVE GOTTEN MINS, MAXES, AND HISTOGRAMS FOR THE TABLE
//        this._scanMinAndMax();
//        this._initHistograms();
//        this._fillHistograms();



        // THE FOLLOWING CODE WAS TAKEN FROM GITHUB USER jacksonatkins
        // AND EDITED SLIGHTLY BY THEO MAURINO. NO CREDIT IS CLAIMED
        DbFileIterator itr = this.heapfile.iterator(new TransactionId());
        try {
            itr.open();
            while (itr.hasNext()) {
                Tuple tup = itr.next();
                this.numTups++;
                TupleDesc td = tup.getTupleDesc();
                for (int i = 0; i < td.numFields(); i++) {
                    if (td.getFieldType(i).equals(Type.INT_TYPE)) {
                        int value = ((IntField) tup.getField(i)).getValue();
                        if (!this.maxes.containsKey(i)) {
                            this.maxes.put(i, -1);
                        }
                        if (!this.mins.containsKey(i)) {
                            this.mins.put(i, Integer.MAX_VALUE);
                        }
                        if (value > this.maxes.get(i)) {
                            this.maxes.put(i, value);
                        }
                        if (value < this.mins.get(i)) {
                            this.mins.put(i, value);
                        }
                    } else {
                        if (!this.stringHistograms.containsKey(i)) {
                            this.stringHistograms.put(i, new StringHistogram(NUM_HIST_BINS));
                        }
                    }
                }
            }
        } catch (DbException | TransactionAbortedException d) {
            d.printStackTrace();
        }
        this.numPages = this.heapfile.numPages();
        itr.close();
        for (int field : this.maxes.keySet()) {
            int max = this.maxes.get(field);
            int min = this.mins.get(field);
            this.intHistograms.put(field, new IntHistogram(NUM_HIST_BINS, min, max));
        }
        try {
            itr.open();
            while (itr.hasNext()) {
                Tuple tup = itr.next();
                TupleDesc td = tup.getTupleDesc();
                for (int i = 0; i < td.numFields(); i++) {
                    if (td.getFieldType(i).equals(Type.INT_TYPE)) {
                        int value = ((IntField) tup.getField(i)).getValue();
                        this.intHistograms.get(i).addValue(value);
                    } else {
                        String value = ((StringField) tup.getField(i)).getValue();
                        this.stringHistograms.get(i).addValue(value);
                    }
                }
            }
        } catch (DbException | TransactionAbortedException d) {
            d.printStackTrace();
        }
        itr.close();

    // END OF CODE TAKEN FROM GITHUB USER jacksonatkins

    }

    /*
     * need to get those histograms we love so much
     */
    private void _initHistograms() {
        // initial scan --> CREATING the hists
        for (int i = 0; i < this.td.numFields(); i++) { // one for each field in table
            Type t = this.td.getFieldType(i);

            if (t.equals(Type.INT_TYPE)) {
                // need an int hist\
                int min = this.mins.get(i); // casting should be fine because all tds should be same --> IDK WHAT HAPPENS IF NONE OF THEM HAVE A VAL
                int max = this.maxes.get(i);
                IntHistogram inthist = new IntHistogram(NUM_HIST_BINS, min, max);
                this.intHistograms.put(i, inthist);

            } else if (t.equals(Type.STRING_TYPE)) {
                // need a string hist
                this.stringHistograms.put(i, new StringHistogram(NUM_HIST_BINS));
            }

        }
    }

    private void _fillHistograms() {

        try {
            TransactionId tID = new TransactionId();
            DbFileIterator dbIt = this.heapfile.iterator(tID);
            dbIt.open();

            while (dbIt.hasNext()) {
                Tuple t = dbIt.next();
                this.numTups++; // needs to go as early as possible in case something fails
//                int numFields = t.getTupleDesc().numFields();
                int numFields = this.td.numFields();
//                TupleDesc descriptor = t.getTupleDesc();


                for (int i = 0; i < numFields; i++) {
//                    Type fType = this.td.getFieldType(i); // don't wanna run getTupleDesc many times (doesn't rly matter)
                    Field f = t.getField(i);
                    Type fType = f.getType();

                    switch (fType) {
                        case INT_TYPE:
                            IntHistogram inthist = (IntHistogram) this.intHistograms.get(i);
                            inthist.addValue(((IntField) f).getValue());
                            break;

                        case STRING_TYPE:
                            StringHistogram strhist = (StringHistogram) this.stringHistograms.get(i);
                            strhist.addValue(((StringField) f).getValue());
                            break;
                    }
                }
            }
            dbIt.close();
            Database.getBufferPool().transactionComplete(tID);

        } catch (Exception e) {
            System.out.println("An error occurred during the scan to fill the histograms");
            e.printStackTrace();
            System.exit(1);
        }

    }


    /*
     * need to get the tables min and max values per column with a good ol scan
     */
    private void _scanMinAndMax() {

        try {
            TransactionId tID = new TransactionId();
            DbFileIterator dbIt = this.heapfile.iterator(tID);

            dbIt.open(); // make iterator active -- initialize everything else needed

            while (dbIt.hasNext()) {
                Tuple t = dbIt.next();
                /*
                 * this SHOULD be identical to this.td but I'm not certain if there are any situations
                 * where not -- so I feel I should get from each tuple vs. assuming -- though truly I
                 * believe it would actually be kinda catastrophic if they weren't the same. I'm just very confused as a general principle
                 */
                TupleDesc tupleTd = t.getTupleDesc();
                int fields = tupleTd.numFields();

                for (int i = 0; i < fields; i++) {
                    Field field = t.getField(i);


                    if (field.getType().equals(Type.INT_TYPE)) {
                        // if key isn't in maxes yet, or if current val in maxes is lower than field
                        int value = ((IntField) field).getValue();
                        if (!this.maxes.containsKey(i) || this.maxes.get(i) < value) { // assuming that if the first predicate of OR evals to TRUE it doesn't run the second
                            this.maxes.put(i, value); // replaces if exists
                        }
                        if (!this.mins.containsKey(i) || this.maxes.get(i) > value) {
                            this.mins.put(i, value);
                        }
                    }

                }
            }

            Database.getBufferPool().transactionComplete(tID); // finish (and commit?) transaction
            dbIt.close();
        }
        catch (Exception e) {
            System.out.println("An error occurred while trying to do the first scan of the table\n");
            e.printStackTrace();
            System.exit(1); // if anything fails here we need to stop everything
        }
    }

    /**
     * Estimates the cost of sequentially scanning the file, given that the cost
     * to read a page is costPerPageIO. You can assume that there are no seeks
     * and that no pages are in the buffer pool.
     *
     * Also, assume that your hard drive can only read entire pages at once, so
     * if the last page of the table only has one tuple on it, it's just as
     * expensive to read as a full page. (Most real hard drives can't
     * efficiently address regions smaller than a page at a time.)
     *
     * @return The estimated cost of scanning the table.
     */
    public double estimateScanCost() {

        return (double) (this.heapfile.numPages() * this.ioCostPerPage);
    }

    /**
     * This method returns the number of tuples in the relation, given that a
     * predicate with selectivity selectivityFactor is applied.
     *
     * @param selectivityFactor
     *            The selectivity of any predicates over the table
     * @return The estimated cardinality of the scan with the specified
     *         selectivityFactor
     */
    public int estimateTableCardinality(double selectivityFactor) {
        // some code goes here
        return (int) (this.numTups * selectivityFactor);
    }

    /**
     * The average selectivity of the field under op.
     * @param field
     *        the index of the field
     * @param op
     *        the operator in the predicate
     * The semantic of the method is that, given the table, and then given a
     * tuple, of which we do not know the value of the field, return the
     * expected selectivity. You may estimate this value from the histograms.
     * */
    public double avgSelectivity(int field, Predicate.Op op) {
        Type fType = this.td.getFieldType(field);
        if (fType.equals(Type.INT_TYPE)) {
            IntHistogram ih = this.intHistograms.get(field);
            return ih.avgSelectivity();
        } else if (fType.equals(Type.STRING_TYPE)) {
            StringHistogram ih = this.stringHistograms.get(field);
            return ih.avgSelectivity(); // i actually didn't implement this so whatever
        }
        return -1; //idk what to put here -- wouldn't exist ig?
    }

    /**
     * Estimate the selectivity of predicate <tt>field op constant</tt> on the
     * table.
     *
     * @param field
     *            The field over which the predicate ranges
     * @param op
     *            The logical operation in the predicate
     * @param constant
     *            The value against which the field is compared
     * @return The estimated selectivity (fraction of tuples that satisfy) the
     *         predicate
     */
    public double estimateSelectivity(int field, Predicate.Op op, Field constant) {
        Type fType = this.td.getFieldType(field);
        if (fType.equals(Type.INT_TYPE)) {
            int constVal = ((IntField) constant).getValue();
            IntHistogram ih = this.intHistograms.get(field);
            return ih.estimateSelectivity(op, constVal);
        } else if (fType.equals(Type.STRING_TYPE)) {
            String constVal = ((StringField) constant).getValue();
            StringHistogram ih = this.stringHistograms.get(field);
            return ih.estimateSelectivity(op, constVal);
        }
        return 0; // if file doesn't exist in table it won't ever be found?
    }

    /**
     * return the total number of tuples in this table
     * */
    public int totalTuples() {
        // some code goes here
        return this.numTups;
    }

}
