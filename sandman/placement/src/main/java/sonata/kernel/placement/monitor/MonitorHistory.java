package sonata.kernel.placement.monitor;

/**
 * Stores monitoring data of one vnf in circular buffers.
 */
public class MonitorHistory {

    /**
     * Vnf's datacenter name
     */
    public final String datacenter;
    /**
     * Vnf's stack name
     */
    public final String stack;
    /**
     * Server name
     */
    public final String function;

    /**
     * Circular buffer containing cpu values
     */
    public final ValueHistoryDouble cpuHistory;
    /**
     * Circular buffer containing memory values
     */
    public final ValueHistoryLong memoryHistory;

    /**
     * Creates a new object storing the data of one vnf
     * @param datacenter Vnf's datacenter name
     * @param stack Vnf's stack name
     * @param function Server name
     * @param length Size of circular buffers
     */
    public MonitorHistory(String datacenter, String stack, String function, int length){
        this.datacenter = datacenter;
        this.stack = stack;
        this.function = function;
        this.cpuHistory = new ValueHistoryDouble(length);
        this.memoryHistory = new ValueHistoryLong(length);
    }

    /**
     * Ringbuffer like array structure for long values
     */
    public static class ValueHistoryLong{

        /**
         * Value timestamps
         */
        public final long[] time;
        /**
         * Long values
         */
        public final long[] data;
        /**
         * Index for a new value
         */
        public int nextIndex = 0;

        /**
         * Initializes the buffers
         * @param length Size of the buffers
         */
        public ValueHistoryLong(int length){
            data = new long[length];
            time = new long[length];
        }

        /**
         * Adds a new value and updates the nextIndex.
         * If the nextIndex points outside of the array sets it to the start.
         * @param time Timestamp
         * @param value Long value
         */
        public void addValue(long time, long value) {
            this.time[nextIndex] = time;
            this.data[nextIndex] = value;
            nextIndex++;
            if(nextIndex>=data.length)
                nextIndex = 0;
        }

        /**
         * Fills given arrays with the last @number values
         * @param number Number of elements to return
         * @param time Timestamp array
         * @param data Value array
         * @return The number of written values
         */
        public int getLast(int number, long[] time, long[] data){
            int newLength = Math.min(number, data.length);
            if(nextIndex >= newLength) {
                // copy in one go
                System.arraycopy(this.data, newLength-data.length, data, 0, newLength);
                System.arraycopy(this.time, newLength-data.length, time, 0, newLength);
            } else {
                // two copies since range is split
                System.arraycopy(this.data, 0, data, newLength-nextIndex, nextIndex);
                System.arraycopy(this.time, 0, time, newLength-nextIndex, nextIndex);

                System.arraycopy(this.data, this.data.length-(newLength-nextIndex), data, 0, newLength-nextIndex);
                System.arraycopy(this.time, this.time.length-(newLength-nextIndex), time, 0, newLength-nextIndex);
            }
            return newLength;
        }

        /**
         * Returns the size of buffers.
         * Does not return the number of written elements.
         * @return Size of the buffers
         */
        public int length(){
            return data.length;
        }
    }

    /**
     * Ringbuffer like array structure for double values
     */
    public static class ValueHistoryDouble{

        /**
         * Value timestamps
         */
        public final long[] time;
        /**
         * Double values
         */
        public final double[] data;
        /**
         * Index for a new value
         */
        public int nextIndex = 0;

        /**
         * Initializes the buffers
         * @param length Size of the buffers
         */
        public ValueHistoryDouble(int length){
            time = new long[length];
            data = new double[length];
        }

        /**
         * Adds a new value and updates the nextIndex.
         * If the nextIndex points outside of the array sets it to the start.
         * @param time Timestamp
         * @param value Long value
         */
        public void addValue(long time, double value) {
            this.time[nextIndex] = time;
            this.data[nextIndex] = value;
            nextIndex++;
            if(nextIndex>=data.length)
                nextIndex = 0;
        }

        /**
         * Fills given arrays with the last @number values
         * @param number Number of elements to return
         * @param time Timestamp array
         * @param data Value array
         * @return The number of written values
         */
        public int getLast(int number, long[] time, double[] data){
            int newLength = Math.min(number, data.length);
            if(nextIndex >= newLength) {
                // copy in one go
                System.arraycopy(this.data, newLength-data.length, data, 0, newLength);
                System.arraycopy(this.time, newLength-data.length, time, 0, newLength);
            } else {
                // two copies since range is split
                System.arraycopy(this.data, 0, data, newLength-nextIndex, nextIndex);
                System.arraycopy(this.time, 0, time, newLength-nextIndex, nextIndex);

                System.arraycopy(this.data, this.data.length-(newLength-nextIndex), data, 0, newLength-nextIndex);
                System.arraycopy(this.time, this.time.length-(newLength-nextIndex), time, 0, newLength-nextIndex);
            }
            return newLength;
        }

        /**
         * Returns the size of buffers.
         * Does not return the number of written elements.
         * @return Size of the buffers
         */
        public int length(){
            return data.length;
        }
    }
}
