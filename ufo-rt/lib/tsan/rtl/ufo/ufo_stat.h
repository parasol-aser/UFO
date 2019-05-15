
#ifndef UFO_STAT_H
#define UFO_STAT_H

#include <stdio.h>
#include "defs.h"
#include "ufo_interface.h"

namespace aser {
namespace ufo {

struct TLStatistic {
  u64 c_start;
  u64 c_join;
  u64 c_alloc;
  u64 c_dealloc;

  u64 c_lock;
  u64 c_rd_lock;
  u64 c_unlock;
  u64 c_rd_unlock;
  u64 c_rw_unlock;

  u64 c_cond_wait;
  u64 c_cond_signal;
  u64 c_cond_bc;

  u64 c_read[4];
  u64 c_write[4];
  u64 c_range_w;
  u64 c_range_r;
  // stack acc
  u64 cs_acc;
  u64 cs_range_acc;
  u64 c_func_call;

  u64 c_ptr_prop;

  void init() {
    c_start = 0;
    c_join = 0;
    c_alloc = 0;
    c_dealloc = 0;

    c_lock = 0;
    c_rd_lock = 0;
    c_unlock = 0;
    c_rd_unlock = 0;
    c_rw_unlock = 0;
    c_cond_wait = 0;
    c_cond_signal = 0;
    c_cond_bc = 0;

    c_read[0] = 0;
    c_read[1] = 0;
    c_read[2] = 0;
    c_read[3] = 0;
    c_write[0] = 0;
    c_write[1] = 0;
    c_write[2] = 0;
    c_write[3] = 0;
    c_range_r = 0;
    c_range_w = 0;
    cs_acc = 0;
    cs_range_acc = 0;
    c_func_call = 0;

    c_ptr_prop = 0;
  }

  u64 count() const noexcept {
    u64 c = 0;
    c += c_start;
    c += c_join;
    c += c_alloc;
    c += c_dealloc;
    c += c_lock;
    c += c_rd_lock;
    c += c_unlock;
    c += c_rd_unlock;
    c += c_rw_unlock;

    c += c_cond_wait;
    c += c_cond_signal;
    c += c_cond_bc;

    c += c_range_w;
    c += c_range_r;
    c += c_read[0];
    c += c_read[1];
    c += c_read[2];
    c += c_read[3];
    c += c_write[0];
    c += c_write[1];
    c += c_write[2];
    c += c_write[3];
    c += c_func_call;
    c += c_ptr_prop;
    return c;
  }
  u64 size() const noexcept {
    u64 c = 0;
    c += c_start * sizeof(aser::ufo::CreateThreadEvent);
    c += c_join * sizeof(JoinThreadEvent);
    c += c_alloc * sizeof(AllocEvent);
    c += c_dealloc * sizeof(DeallocEvent);

    c += c_lock * sizeof(LockEvent);

//    c += c_rd_lock * sizeof(LockEvent);
    c += c_unlock * sizeof(UnlockEvent);
//    u64 c_rd_unlock;
//    u64 c_rw_unlock;
    c += c_cond_wait * sizeof(ThrCondWaitEvent);
    c += c_cond_signal * sizeof(ThrCondSignalEvent);
    c += c_cond_bc * sizeof(ThrCondBCEvent);

    c += c_range_w * sizeof(MemRangeWrite);
    c += c_range_r * sizeof(MemRangeRead);
    c += c_read[0] * (sizeof(MemAccEvent) + 1);
    c += c_read[1] * (sizeof(MemAccEvent) + 2);
    c += c_read[2] * (sizeof(MemAccEvent) + 4);
    c += c_read[3] * (sizeof(MemAccEvent) + 8);
    c += c_write[0] * (sizeof(MemAccEvent) + 1);
    c += c_write[1] * (sizeof(MemAccEvent) + 2);
    c += c_write[2] * (sizeof(MemAccEvent) + 4);
    c += c_write[3] * (sizeof(MemAccEvent) + 8);
    c += c_func_call * (sizeof(FuncEntryEvent) + sizeof(FuncExitEvent));
    c += c_ptr_prop * (sizeof(PtrAssignEvent));
    return c;
  }

  bool used() const noexcept {
    return size() != 0;
  }

};


void summary_stat(FILE* f, TLStatistic* arr_stat, int len, u32 this_pid, u32 p_pid);

void print_csv(FILE* f, TLStatistic* arr_stat, int len, u32 this_pid, u32 p_pid);

}
}
#endif //UFO_STAT_H
