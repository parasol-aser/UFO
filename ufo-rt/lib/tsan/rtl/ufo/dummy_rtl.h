
#include "ufo_interface.h"

using namespace __tsan;

namespace aser {
namespace ufo {


void nop_mtx_lock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {}

void nop_mtx_unlock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {}

void nop_rd_lock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {}

void nop_rd_unlock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id){}

void nop_rw_unlock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {}

void nop_cond_wait(__tsan::ThreadState* thr, uptr pc, u64 addr_cond, u64 addr_mtx){}
void nop_cond_signal(__tsan::ThreadState* thr, uptr pc, u64 addr_cond){}
void nop_cond_broadcast(__tsan::ThreadState* thr, uptr pc, u64 addr_cond){}


///////////////////////////////////////////////////////////////////////////////////////////////

void *nop_alloc(ThreadState *thr, uptr pc, void *addr_left, uptr size) {
  return addr_left;
}

void nop_dealloc(ThreadState *thr, uptr pc, void *addr, uptr size) {
DPrintf3("#%d: in UFO nop_dealloc doing nothing\n", thr->tid);
}

void nop_mem_acc(ThreadState *thr, uptr pc, uptr addr, int kAccessSizeLog, bool is_write) {}

// 7
// 8 + (13 + 48) + 32 = 104
void nop_mem_range_acc(__tsan::ThreadState *thr, uptr pc, uptr addr, uptr size, bool is_write) {}


void nop_thread_created(int tid_parent, int tid_kid, uptr pc) {}

void nop_thread_start(__tsan::ThreadState* thr, uptr stk_addr, uptr stk_size, uptr tls_addr, uptr tls_size){}

void nop_thread_join(int tid_main, int tid_joiner, uptr pc) {}
void nop_thread_end(ThreadState *thr) {}

void nop_enter_func(ThreadState *thr, uptr pc) {}

void nop_exit_func(ThreadState *thr){}


void nop_ptr_deref(__tsan::ThreadState *thr, uptr pc, uptr addr_src){}
void nop_ptr_prop(__tsan::ThreadState *thr, uptr pc, uptr addr_src, uptr addr_dest){}

} // ns ufo_bench
} // ns bw
















