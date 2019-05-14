

#ifndef UFO_UFO_H
#define UFO_UFO_H

#include "../../../sanitizer_common/sanitizer_atomic.h"
#include "../tsan_mutex.h"

#include "defs.h"
#include "ufo_interface.h"
#include "tlbuffer.h"
#include "ufo_stat.h"
#include "io_queue.h"

namespace aser {
namespace ufo {

using __sanitizer::u8;
using __sanitizer::u16;
using __sanitizer::u32;
using __sanitizer::u64;
using __sanitizer::s8;
using __sanitizer::s16;
using __sanitizer::s32;
using __sanitizer::s64;
using __sanitizer::uptr;
using __sanitizer::Printf;

using __sanitizer::atomic_uint8_t;
using __sanitizer::atomic_uint16_t;
using __sanitizer::atomic_uint32_t;
using __sanitizer::atomic_uint64_t;
using __sanitizer::atomic_load_relaxed;
using __sanitizer::atomic_store_relaxed;

typedef void* (*FPAlloc)(__tsan::ThreadState *thr, uptr pc, void *addr, uptr size);
typedef void (*FPDealloc)(__tsan::ThreadState *thr, uptr pc, void *addr, uptr size);
typedef void (*FPThr)(int tid_parent, int tid_kid, uptr pc);
typedef void (*FPThrStart)(__tsan::ThreadState *thr, uptr, uptr, uptr, uptr);
typedef void (*FPThrEnd)(__tsan::ThreadState *thr);

typedef void (*FPMtxLock)(__tsan::ThreadState *thr, uptr pc, u64 mutex_id);
typedef void (*FPCondWait)(__tsan::ThreadState* thr, uptr pc, u64 addr_cond, u64 addr_mtx);
typedef void (*FPCondSignal)(__tsan::ThreadState* thr, uptr pc, u64 addr_cond);

typedef void (*FPMemAcc)(__tsan::ThreadState *thr, uptr pc, uptr addr, int kAccessSizeLog, bool is_write);
typedef void (*FPMemRangeAcc)(__tsan::ThreadState *thr, uptr pc, uptr addr, uptr size, bool is_write);
typedef void (*FPFuncEnter)(__tsan::ThreadState *thr, uptr pc);
typedef void (*FPFuncExit)(__tsan::ThreadState *thr);
typedef void (*FPPtrProp)(__tsan::ThreadState *thr, uptr pc, uptr addr_src, uptr addr_dest);
typedef void (*FPPtrDeRef)(__tsan::ThreadState *thr, uptr pc, uptr addr_src);


class UFOContext {
  static s64 get_int_opt(const char *name, s64 default_val);
  void read_config();
  void open_trace_dir();
  bool do_print_stat_;
  // state
  u32 cur_pid_;
  u32 p_pid_;
  bool is_subproc;
  u32 mudule_length_;
  atomic_uint32_t tl_buf_size_;
  // track all buffer allocation
  // resize tl buffer size dynamically
  __tsan::Mutex lock;
  u64 mem_t1_;
  u64 mem_t2_;
  u16 ratio_mem_x2;
  u16 ratio_mem_x4;
  u64 total_mem_;
public:
  // config
  bool is_on;
  atomic_uint32_t stack_size;
  bool no_stack;
  bool no_data_value;// do not record the value of the read/write
  bool trace_func_call;
  bool trace_ptr_prop;

  char trace_dir[DIR_MAX_LEN];
  u64 time_started;

  TLBuffer *tlbufs;
  // logical time for mem_acc(r/w, range r/w), lock, alloc/dealloc
  // these 5 type of events are synced,
  volatile u64 e_count;

  ALWAYS_INLINE
  u32 get_buf_size() const {
    return atomic_load_relaxed(&tl_buf_size_);
  }

  bool use_io_q;
  bool use_compression;
  int out_queue_legth;

  TLStatistic *stat;

  OutQueue *out_queue;

  void save_module_info(int tid);
  void output_stat();
public:

  // init and start_trace
  void init_start();

  void destroy();

  void print_config();
  // after fork, executed by child process
  void child_after_fork();

  // race condition on tl_buf_size_, but it does not matter.
  void mem_acquired(u32 n_bytes);
  void mem_released(u32 n_bytes);

  static u64 get_time_ms();


  void start_trace();

  static void stop_trace();

  static FPAlloc          fn_alloc;
  static FPDealloc        fn_dealloc;
  static FPThr            fn_thread_created;
  static FPThrStart       fn_thread_started;
  static FPThr            fn_thread_join;
  static FPThrEnd         fn_thread_end;
    
  static FPMtxLock        fn_mtx_lock;
  static FPMtxLock        fn_mtx_unlock;

  static FPCondWait       fn_cond_wait;
  static FPCondSignal     fn_cond_signal;
  static FPCondSignal     fn_cond_bc;

  static FPMtxLock        fn_rd_lock;
  static FPMtxLock        fn_rd_unlock;
  static FPMtxLock        fn_rw_unlock;

  static FPMemAcc         fn_mem_acc;
  static FPMemRangeAcc    fn_mem_range_acc;
  static FPFuncEnter      fn_enter_func;
  static FPFuncExit       fn_exit_func;
  static FPPtrProp        fn_ptr_prop;
  static FPPtrDeRef       fn_ptr_deref;
};

//void (*fn_dealloc)(__tsan::ThreadState *thr, uptr pc, void *addr);
//void (*fn_thread_start)(int tid_parent, int tid_kid, uptr pc);
//void (*fn_thread_join)(int tid_main, int tid_joiner, uptr pc);
//
//void (*fn_mtx_lock)(__tsan::ThreadState *thr, uptr pc, u64 mutex_id);
//void (*fn_mtx_unlock)(__tsan::ThreadState *thr, uptr pc, u64 mutex_id);
//
//void (*fn_rd_lock)(__tsan::ThreadState *thr, uptr pc, u64 mutex_id);
//void (*fn_rd_unlock)(__tsan::ThreadState *thr, uptr pc, u64 mutex_id);
//void (*fn_rw_unlock)(__tsan::ThreadState *thr, uptr pc, u64 mutex_id);
//
//void (*fn_mem_acc)(__tsan::ThreadState *thr, uptr pc, uptr addr, int kAccessSizeLog, bool is_write);
//void (*fn_mem_range_acc)(__tsan::ThreadState *thr, uptr pc, uptr addr, uptr size, bool is_write);
//void (*fn_enter_func)(__tsan::ThreadState *thr, uptr pc);
//void (*fn_exit_func)(__tsan::ThreadState *thr);

}
}

#endif //UFO_UFO_H
