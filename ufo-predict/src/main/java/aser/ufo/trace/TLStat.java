package aser.ufo.trace;

import java.util.Arrays;

public class TLStat {

  public long c_tstart = 0;
  public long c_join = 0;
  public long c_lock = 0;
  public long c_unlock = 0;
  public long c_alloc = 0;
  public long c_dealloc = 0;
  public long[] c_read = new long[4];
  public long[] c_write = new long[4];
  public long c_range_w = 0;
  public long c_range_r = 0;
  
  public long c_total = 0;
  public long c_isync = 0;
public long c_notify = 0;
public long c_wait = 0;
public long c_notifyAll = 0;

  @Override
  public String toString() {
    return "TLStat{" +
        "c_tstart=" + c_tstart +
        ", c_join=" + c_join +
        ", c_lock=" + c_lock +
        ", c_unlock=" + c_unlock +
        ", c_alloc=" + c_alloc +
        ", c_dealloc=" + c_dealloc +
        ", c_read=" + Arrays.toString(c_read) +
        ", c_write=" + Arrays.toString(c_write) +
        ", c_range_w=" + c_range_w +
        ", c_range_r=" + c_range_r +
        '}';
  }

//  public long fsize() {
//    long c = 0;
//    c += c_tstart * 7;
//    c += c_join * 7;
//    c += c_alloc * 17;
//    c += c_dealloc * 13;
//    c += c_lock * 13;
//    c += c_unlock * 7;
//    c += c_range_w * sizeof(MemRangeWrite);
//    c += c_range_r * sizeof(MemRangeRead);
//    c += c_read[0] * (sizeof(MemAccEvent) + 1);
//    c += c_read[1] * (sizeof(MemAccEvent) + 2);
//    c += c_read[2] * (sizeof(MemAccEvent) + 4);
//    c += c_read[3] * (sizeof(MemAccEvent) + 8);
//    c += c_write[0] * (sizeof(MemAccEvent) + 1);
//    c += c_write[1] * (sizeof(MemAccEvent) + 2);
//    c += c_write[2] * (sizeof(MemAccEvent) + 4);
//    c += c_write[3] * (sizeof(MemAccEvent) + 8);
//    return c;
//  }
}
