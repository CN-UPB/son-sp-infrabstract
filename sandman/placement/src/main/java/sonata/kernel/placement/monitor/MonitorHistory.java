package sonata.kernel.placement.monitor;


public class MonitorHistory {

    public final String datacenter;
    public final String stack;
    public final String function;

    public final ValueHistoryDouble cpuHistory;
    public final ValueHistoryLong memoryHistory;

    public MonitorHistory(String datacenter, String stack, String function, int length){
        this.datacenter = datacenter;
        this.stack = stack;
        this.function = function;
        this.cpuHistory = new ValueHistoryDouble(length);
        this.memoryHistory = new ValueHistoryLong(length);
    }

    /**
     * Ringbuffer like array structure
     */
    public static class ValueHistoryLong{

        public final long[] time;
        public final long[] data;
        public int nextIndex = 0;

        public ValueHistoryLong(int length){
            data = new long[length];
            time = new long[length];
        }

        public void addValue(long time, long value) {
            this.time[nextIndex] = time;
            this.data[nextIndex] = value;
            nextIndex++;
            if(nextIndex>=data.length)
                nextIndex = 0;
        }

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

        public int length(){
            return data.length;
        }
    }

    public static class ValueHistoryDouble{

        public final long[] time;
        public final double[] data;
        public int nextIndex = 0;

        public ValueHistoryDouble(int length){
            time = new long[length];
            data = new double[length];
        }

        public void addValue(long time, double value) {
            this.time[nextIndex] = time;
            this.data[nextIndex] = value;
            nextIndex++;
            if(nextIndex>=data.length)
                nextIndex = 0;
        }

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

        public int length(){
            return data.length;
        }
    }
}
