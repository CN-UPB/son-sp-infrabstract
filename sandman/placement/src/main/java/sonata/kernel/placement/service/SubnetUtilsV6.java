package sonata.kernel.placement.service;

import org.apache.commons.net.util.SubnetUtils;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Handles IPv6 Subnets and IP addresses.
 * Inspired by the SubnetUtils class from the org.apache.commons.net.
 * Not used since the emulator configures the network resources.
 */
public class SubnetUtilsV6 {

    /**
     * Utils for IPv4 Addresses used to handle IPv4 subnets.
     */
    SubnetUtils oldUtils;

    /**
     * Number of bits in the IPv6 address
     */
    private static final int NBITS = 128;

    /**
     * Stores an IPv6 netmask
     */
    private int[] netmask = new int[]{0,0,0,0};
    /**
     * Stores an IPv6 address
     */
    private int[] address = new int[]{0,0,0,0};
    /**
     * Stores an IPv6 network address
     */
    private int[] network = new int[]{0,0,0,0};
    /**
     * Stores an IPv6 broadcast address
     */
    private int[] broadcast = new int[]{0,0,0,0};
    /**
     * Stores the number of bits used for the net part
     */
    private int cidr = 0;

    /**
     * Whether the broadcast/network address are included in host count
     */
    private boolean inclusiveHostCount = false;

    /**
     * Creates a SubnetUtilsV6 object from a subnet as String
     * @param cidrNotation subnet
     * @return
     */
    public static SubnetUtilsV6 createSubnet(String cidrNotation){
        SubnetUtils oldUtils = null;
        boolean oldUtilsError = false;
        try {
            oldUtils = new SubnetUtils(cidrNotation);
        } catch (Exception e) {
            oldUtilsError = true;
        }
        if(oldUtilsError)
            return new SubnetUtilsV6(cidrNotation);
        else
            return new SubnetUtilsV6(oldUtils);
    }

    /**
     * Creates a SubnetUtilsV6 object from a address and a netmask as Strings
     * @param address
     * @param mask
     * @return
     */
    public static SubnetUtilsV6 createSubnet(String address, String mask){
        SubnetUtils oldUtils = null;
        boolean oldUtilsError = false;
        try {
            oldUtils = new SubnetUtils(address, mask);
        } catch (Exception e) {
            oldUtilsError = true;
        }
        if(oldUtilsError)
            return new SubnetUtilsV6(address, mask);
        else
            return new SubnetUtilsV6(oldUtils);
    }

    /**
     * Constructor for an IPv4 subnet using the SubnetUtils object
     * @param oldUtils
     */
    protected SubnetUtilsV6(SubnetUtils oldUtils){
        this.oldUtils = oldUtils;
    }

    /**
     * Constructor for an IPv6 subnet as String
     * @param cidrNotation
     */
    protected SubnetUtilsV6(String cidrNotation){
        calculate(cidrNotation);
    }

    /**
     * Constructor for an IPv6 subnet as address and netmask as String
     * @param address
     * @param mask
     */
    protected SubnetUtilsV6(String address, String mask){
        calculate(toCidrNotation(address, mask));
    }

    /**
     * If an IPv4 address is used with a SubnetUtils object
     * @return
     */
    public boolean isV6(){
        return oldUtils==null;
    }

    /**
     * Utility class to compute address and masks for the IPv6 subnet
     */
    public final class SubnetInfoV6 {

        /* Mask to convert unsigned int to a long (i.e. keep 32 bits) */
        private static final long UNSIGNED_INT_MASK = 0x0FFFFFFFFL;

        private SubnetInfoV6() {
            if(oldUtils!=null)
                oldInfo = oldUtils.getInfo();
        }

        /**
         * SubnetInfo for IPv4 subnet
         */
        private SubnetUtils.SubnetInfo oldInfo;

        /**
         * Returns the subnet's netmask
         * @return
         */
        private int[] netmask()       { return netmask; }

        /**
         * Returns the subnet's network address
         * @return
         */
        private int[] network()       { return network; }

        /**
         * Returns the IPv6 address
         * @return
         */
        private int[] address()       { return address; }

        /**
         * Returns the subnet's broadcast address
         * @return
         */
        private int[] broadcast()     { return broadcast; }

        /**
         * If SubnetInfo for an IPv4 address is used
         * @return
         */
        public boolean isV6(){
            return oldInfo==null;
        }

        // long versions of the values (as unsigned int) which are more suitable for range checking

        /**
         * Subnet's network address as long
         * @return
         */
        private long[] networkLong()  {
            long[] ret = new long[4];
            for(int i=0; i<4; i++)
                ret[i] = network[i] &  UNSIGNED_INT_MASK;
            return ret;
        }

        /**
         * Return the network address from an IPv6 address in this subnet.
         * @param x IPv6 address
         * @return
         */
        private long networkLong(int x)  { return network[x] &  UNSIGNED_INT_MASK; }

        /**
         * Subnet's broadcast address as long
         * @return
         */
        private long[] broadcastLong() {
            long[] ret = new long[4];
            for(int i=0; i<4; i++)
                ret[i] = broadcast[i] &  UNSIGNED_INT_MASK;
            return ret;
        }

        /**
         * Return the broadcast address from an IPv6 address in this subnet.
         * @param x IPv6 address
         * @return
         */
        private long broadcastLong(int x){ return broadcast[x] &  UNSIGNED_INT_MASK; }

        /**
         * Converts an IPv6 address from int[] to long[] representation
         * @param arr IPv6 address
         * @return
         */
        private long[] toLongArray(int[] arr){
            long[] ret = new long[4];
            for(int i=0; i<4; i++)
                ret[i] = arr[i] &  UNSIGNED_INT_MASK;
            return ret;
        }

        /**
         * Returns the lowest IPv6 address in this subnet
         * @return
         */
        private int[] low() {
            if(isInclusiveHostCount())
                return network();
            else {
                long[] res = new long[4];
                for(int i=0; i<4; i++)
                    res[i] = broadcastLong(i) - networkLong(i);
                if(res[0]>0 || res[1]>0 || res[2]>0 || res[3]>1) {
                    int[] ret = network();
                    ret[3] += 1;
                    return ret;
                } else {
                    int[] ret = new int[4];
                    return ret;
                }
            }
        }

        /**
         * Returns the highest IPv6 address in this subnet
         * @return
         */
        private int[] high() {
            if(isInclusiveHostCount())
                return broadcast();
            else {
                long[] res = new long[4];
                for(int i=0; i<4; i++)
                    res[i] = broadcastLong(i) - networkLong(i);
                if(res[0]>0 || res[1]>0 || res[2]>0 || res[3]>1) {
                    int[] ret = broadcast();
                    ret[3] -= 1;
                    return ret;
                } else {
                    int[] ret = new int[4];
                    return ret;
                }
            }
        }

        /**
         * Checks if the given IPv6 belongs to this subnet.
         * @param address IPv6 address
         * @return
         */
        public boolean isInRange(String address) {
            if(oldInfo==null)
                return isInRange(toInteger(address));
            else
                return oldInfo.isInRange(address);
        }

        /**
         * Checks if the given IPv6 belongs to this subnet.
         * @param address IPv6 address
         * @return
         */
        public boolean isInRange(int address){
            if(oldInfo==null)
                throw new IllegalArgumentException();
            else
                return oldInfo.isInRange(address);
        }

        /**
         * Checks if the given IPv6 belongs to this subnet.
         * @param address IPv6 address
         * @return
         */
        public boolean isInRange(int[] address) {
            long[] add = toLongArray(address);
            long[] low = toLongArray(low());
            long[] high = toLongArray(high());
            for(int i=0; i<4; i++) {
                if(add[i] < low[i] || add[i] > high[i])
                    return false;
            }
            return true;
        }

        /**
         * Returns this subnet's broadcast address as String
         * @return
         */
        public String getBroadcastAddress() {
            if(oldInfo==null)
                return format(toArray(broadcast()));
            else
                return oldInfo.getBroadcastAddress();
        }

        /**
         * Returns this subnet's network address as String
         * @return
         */
        public String getNetworkAddress() {
            if(oldInfo==null)
                return format(toArray(network()));
            else
                return oldInfo.getNetworkAddress();
        }

        /**
         * Returns this subnet's netmask as String
         * @return
         */
        public String getNetmask() {
            if(oldInfo==null)
                return format(toArray(netmask()));
            else
                return oldInfo.getNetmask();
        }

        /**
         * Returns the IPv6 this subnet is based on as String
         * @return
         */
        public String getAddress() {
            if(oldInfo==null)
                return format(toArray(address()));
            else
                return oldInfo.getAddress();
        }

        /**
         * Return the number of bits in the netmask
         * @return
         */
        public int getCidrBits(){
            if(oldInfo!=null) {
                return Integer.parseInt(oldInfo.getCidrSignature().split("/")[1]);
            }
            return cidr;
        }

        /**
         * Returns the lowest address of this subnet as String
         * @return
         */
        public String getLowAddress() {
            if(oldInfo==null)
                return format(toArray(low()));
            else
                return oldInfo.getLowAddress();
        }

        /**
         * Returns the highest address of this subnet as String
         * @return
         */
        public String getHighAddress() {
            if(oldInfo==null)
                return format(toArray(high()));
            else
                return oldInfo.getHighAddress();
        }

        /**
         * Returns the number of IP addresses in this subnet
         * @return
         */
        public long getAddressCountLong() {
            if(oldInfo!=null)
                return oldInfo.getAddressCountLong();
            return getAddressCountLong(64);
        }

        /**
         * Returns the number of sub subnets with a netmask of @subnetSize bits
         * @param subnetSize Number of bits the sub subnets' netmasks have
         * @return
         */
        public long getAddressCountLong(int subnetSize) {
            int netmaskcidr = pop(netmask());
            long count = (long)Math.pow(2, subnetSize-netmaskcidr);
            return count;
        }

        /**
         * Returns the Cidr Signature as String
         * @return
         */
        public String getCidrSignature() {
            if(oldInfo!=null)
                oldInfo.getCidrSignature();
            return toCidrNotation(
                    format(toArray(address())),
                    format(toArray(netmask()))
            );
        }

        /**
         * Returns all IP addresses as String array
         * @return
         */
        public String[] getAllAddresses(){
            if(oldInfo!=null)
                return oldInfo.getAllAddresses();
            else
                return getAllAddresses(64);
        }

        /**
         * Returns all sub subnets with a fixed netmask as String array
         * @param subnetSize Sub subnets' netmask bit length
         * @return
         */
        public String[] getAllAddresses(int subnetSize) {
            int ct = (int) getAddressCountLong(subnetSize);
            if(ct <= 0)
                return new String[0];
            String[] addresses = new String[(int)ct];
            if (ct == 0) {
                return addresses;
            }
            long[] add = toLongArray(low());
            long[] high = toLongArray(high());
            long[] one = new long[4];
            long partLimit = 0x100000000L;
            // Create subnet adding value
            int index = subnetSize;
            int indexPart = 0;
            while(index>32) {
                index -= 32;
                indexPart++;
            }
            one[indexPart] = (1 << 32 - index);

            // Iterate over addresses
            int j=0;
            while(add[0]<=high[0] && add[1]<=high[1] && add[2]<=high[2] && add[3]<=high[3]) {

                addresses[j] = format(toArray(add))+"/"+subnetSize;

                // Increase address
                for(int i=3; i>=0; i--) {
                    add[i] += one[i];
                    if(add[i] > partLimit && i>0) {
                        add[i] -= partLimit;
                        add[i-1] += 1;
                    }
                }

                j++;
            }
            return addresses;
        }

        /**
         * Returns information about the subnet
         * @return
         */
        @Override
        public String toString() {
            if(oldInfo!=null)
                return oldInfo.toString();
            final StringBuilder buf = new StringBuilder();
            buf.append("CIDR Signature:\t[").append(getCidrSignature()).append("]")
                    .append(" Netmask: [").append(getNetmask()).append("]\n")
                    .append("Network:\t[").append(getNetworkAddress()).append("]\n")
                    .append("Broadcast:\t[").append(getBroadcastAddress()).append("]\n")
                    .append("First Address:\t[").append(getLowAddress()).append("]\n")
                    .append("Last Address:\t[").append(getHighAddress()).append("]\n")
                    .append("# Addresses:\t[").append(getAddressCountLong()).append("]\n");
            return buf.toString();
        }

    }

    /**
     * Whether the broadcast/network address are included in host count
     * @return
     */
    public boolean isInclusiveHostCount() {
        return inclusiveHostCount;
    }

    /**
     * Sets whether the broadcast/network address are included in host count
     * @param inclusiveHostCount
     */
    public void setInclusiveHostCount(boolean inclusiveHostCount) {
        this.inclusiveHostCount = inclusiveHostCount;
    }

    /**
     * Returns the underlying SubnetInfoV6 object
     * @return
     */
    public SubnetInfoV6 getInfo() { return new SubnetInfoV6(); }


    /**
    * Initialize the internal fields from the supplied CIDR mask
    * @param mask CIDR mask
    */
    private void calculate(String mask) {

        String[] cidrParts = mask.split("/");

        // Set address
        address = toInteger(cidrParts[0]);

        // Set netmask
        int cidrPart = Integer.parseInt(cidrParts[1]);
        if(cidrPart<0 || cidrPart >NBITS)
            throw new IllegalArgumentException();
        int cidrPartRest = cidrPart;
        for(int i=0; i<4; i++) {
            int bits = cidrPartRest;
            if(cidrPartRest > 32)
                bits = 32;
            netmask[i] = 0;
            for (int j = 0; j < bits; j++) {
                netmask[i] |= (1 << 31 - j);
            }
             cidrPartRest -= 32;
        }

        cidr = cidrPart;

        // Set network address and broadcast address
        for(int i=0; i<4; i++) {
            network[i] = (address[i] & netmask[i]);
            broadcast[i] = network[i] | ~(netmask[i]);
        }
    }

    /**
    * Convert a packed integer address into a 8-element array
    */
    private int[] toArray(int[] val) {
        int ret[] = new int[8];

        for(int i=0; i<4; i++){
            ret[i*2] = (val[i] >> 16) & (0xffff);
            ret[i*2 +1] = val[i] & (0xffff);
        }
        return ret;
    }

    /**
     * Convert a the address from long[] to int[] representation
     * @param val
     * @return
     */
    private int[] toArray(long[] val) {
        int ret[] = new int[8];

        for(int i=0; i<4; i++){
            ret[i*2] = (int)(val[i] >> 16);
            ret[i*2 +1] = (int)(val[i] & 0xffff);
        }
        return ret;
    }

    /*
     * Convert a 8-element array into dotted decimal format
     */
    private String format(int[] hextets) {
        StringBuilder str = new StringBuilder();
        for (int i =0; i < hextets.length; ++i){
            str.append(Integer.toHexString(hextets[i]));
            if (i != hextets.length - 1) {
                str.append(":");
            }
        }
        return str.toString();
    }

    /*
    * Converts an IPv6 address to an integer array
    */
    private int[] toInteger(String address) {

        try {
            int[] result = new int[4];
            InetAddress ipAddress = InetAddress.getByName(address);
            byte[] raw = ipAddress.getAddress();
            for(int i=0; i<4; i++)
                result[i] = (raw[i*4] & 0x000000ff)<<24 | (raw[i*4+1] & 0x000000ff)<<16 | (raw[i*4+2] & 0x000000ff)<<8 | (raw[i*4+3] & 0x000000ff);
            return result;
        } catch (UnknownHostException e) {
            return null;
        }

    }

    /**
     * Counts the number of 1-bits in the int[] array
     * @param x
     * @return
     */
    protected int pop(int[] x){
        return pop(x[0]) + pop(x[1]) + pop(x[2]) + pop(x[3]);
    }

    /**
    * Count the number of 1-bits in a 32-bit integer using a divide-and-conquer strategy
    * see Hacker's Delight section 5.1
    */
    protected int pop(int x) {
        x = x - ((x >>> 1) & 0x55555555);
        x = (x & 0x33333333) + ((x >>> 2) & 0x33333333);
        x = (x + (x >>> 4)) & 0x0F0F0F0F;
        x = x + (x >>> 8);
        x = x + (x >>> 16);
        return x & 0x0000003F;
    }

    /**
     * Converts an address and netmask as String to the CIDR notation
     * @param addr
     * @param mask
     * @return
     */
    protected String toCidrNotation(String addr, String mask) {
        return addr + "/" + pop(toInteger(mask));
    }
}
