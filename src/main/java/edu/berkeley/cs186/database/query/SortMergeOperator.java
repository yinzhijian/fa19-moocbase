package edu.berkeley.cs186.database.query;

import java.util.*;

import edu.berkeley.cs186.database.TransactionContext;
import edu.berkeley.cs186.database.common.iterator.BacktrackingIterator;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.table.Record;

class SortMergeOperator extends JoinOperator {
    SortMergeOperator(QueryOperator leftSource,
                      QueryOperator rightSource,
                      String leftColumnName,
                      String rightColumnName,
                      TransactionContext transaction) {
        super(leftSource, rightSource, leftColumnName, rightColumnName, transaction, JoinType.SORTMERGE);

        this.stats = this.estimateStats();
        this.cost = this.estimateIOCost();
    }

    @Override
    public Iterator<Record> iterator() {
        return new SortMergeIterator();
    }

    @Override
    public int estimateIOCost() {
        //does nothing
        return 0;
    }

    /**
     * An implementation of Iterator that provides an iterator interface for this operator.
     *
     * Before proceeding, you should read and understand SNLJOperator.java
     *    You can find it in the same directory as this file.
     *
     * Word of advice: try to decompose the problem into distinguishable sub-problems.
     *    This means you'll probably want to add more methods than those given (Once again,
     *    SNLJOperator.java might be a useful reference).
     *
     */
    private class SortMergeIterator extends JoinIterator {
        /**
        * Some member variables are provided for guidance, but there are many possible solutions.
        * You should implement the solution that's best for you, using any member variables you need.
        * You're free to use these member variables, but you're not obligated to.
        */
        private BacktrackingIterator<Record> leftIterator;
        private BacktrackingIterator<Record> rightIterator;
        private Record leftRecord;
        private Record nextRecord;
        private Record rightRecord;
        private boolean marked;

        private SortMergeIterator() {
            super();
            // TODO(hw3_part1): implement
            SortOperator s = new SortOperator(SortMergeOperator.this.getTransaction(), this.getLeftTableName(),
                    new LeftRecordComparator());
            String leftTmpTable = s.sort();
            this.leftIterator = SortMergeOperator.this.getRecordIterator(leftTmpTable);
            s = new SortOperator(SortMergeOperator.this.getTransaction(), this.getRightTableName(),
                    new RightRecordComparator());
            String rightTmpTable = s.sort();
            this.rightIterator = SortMergeOperator.this.getRecordIterator(rightTmpTable);

            this.nextRecord = null;

            this.leftRecord = leftIterator.hasNext() ? leftIterator.next() : null;
            this.rightRecord = rightIterator.hasNext() ? rightIterator.next() : null;

            // We mark the first record so we can reset to it when we advance the left record.
            if (rightRecord != null) {
                rightIterator.markPrev();
            } else {
                return;
            }

            try {
                fetchNextRecord();
            } catch (NoSuchElementException e) {
                this.nextRecord = null;
            }
        }
        private void resetRightRecord() {
            this.rightIterator.reset();
            assert(rightIterator.hasNext());
            rightRecord = rightIterator.next();
        }
        private void nextLeftRecord() {
            if (!leftIterator.hasNext()) { throw new NoSuchElementException("All Done!"); }
            leftRecord = leftIterator.next();
        }
        private void nextRightRecord() {
            if (!rightIterator.hasNext()) { throw new NoSuchElementException("All Done!"); }
            rightRecord = rightIterator.next();
        }
        private void fetchNextRecord() {
            if (this.leftRecord == null) { throw new NoSuchElementException("No new record to fetch"); }
            this.nextRecord = null;
            do {
                DataBox leftJoinValue = this.leftRecord.getValues().get(SortMergeOperator.this.getLeftColumnIndex());
                DataBox rightJoinValue = rightRecord.getValues().get(SortMergeOperator.this.getRightColumnIndex());
                if (!marked) {
                    while (leftJoinValue.compareTo(rightJoinValue) < 0) {
                        nextLeftRecord();
                        leftJoinValue = this.leftRecord.getValues().get(SortMergeOperator.this.getLeftColumnIndex());
                    }
                    while (leftJoinValue.compareTo(rightJoinValue) > 0) {
                        nextRightRecord();
                        rightJoinValue = rightRecord.getValues().get(SortMergeOperator.this.getRightColumnIndex());
                    }
                    rightIterator.markPrev();
                    marked = true;
                }
                if (leftJoinValue.equals(rightJoinValue)) {
                    List<DataBox> leftValues = new ArrayList<>(this.leftRecord.getValues());
                    List<DataBox> rightValues = new ArrayList<>(rightRecord.getValues());
                    leftValues.addAll(rightValues);
                    this.nextRecord = new Record(leftValues);
                    if (rightIterator.hasNext()) {
                        rightRecord = rightIterator.next();
                    } else {
                        resetRightRecord();
                        try {
                            nextLeftRecord();
                        }catch (NoSuchElementException e) {
                            leftRecord = null;
                        }
                    }
                } else {
                    marked = false;
                    nextLeftRecord();
                    resetRightRecord();
                }
            } while (!hasNext());
        }

        /**
         * Checks if there are more record(s) to yield
         *
         * @return true if this iterator has another record to yield, otherwise false
         */
        @Override
        public boolean hasNext() {
            // TODO(hw3_part1): implement
            return this.nextRecord != null;
        }

        /**
         * Yields the next record of this iterator.
         *
         * @return the next Record
         * @throws NoSuchElementException if there are no more Records to yield
         */
        @Override
        public Record next() {
            // TODO(hw3_part1): implement
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            Record nextRecord = this.nextRecord;
            try {
                this.fetchNextRecord();
            } catch (NoSuchElementException e) {
                this.nextRecord = null;
            }
            return nextRecord;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private class LeftRecordComparator implements Comparator<Record> {
            @Override
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getLeftColumnIndex()).compareTo(
                           o2.getValues().get(SortMergeOperator.this.getLeftColumnIndex()));
            }
        }

        private class RightRecordComparator implements Comparator<Record> {
            @Override
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getRightColumnIndex()).compareTo(
                           o2.getValues().get(SortMergeOperator.this.getRightColumnIndex()));
            }
        }
    }
}
