

#ifndef UFO_RTL_IMPL_H
#define UFO_RTL_IMPL_H


#include "defs.h"
#include "ufo_interface.h"
#include "ufo.h"
#include "report.h"

//#define DPrintf Printf

namespace aser {
namespace ufo {

extern UFOContext* uctx;


#ifdef STAT_ON
#define MC_STAT(thr, field)\
  uctx->stat[tid].field++;
#else
#define MC_STAT(thr, field)
#endif


// read one byte before lock,unlock
ALWAYS_INLINE
static void _reset_read(int tid, u64 mtx_id) {
  auto& buf = uctx->tlbufs[tid];
  // read 1 byte before lock, drop this read
  const u64 esz = sizeof(MemAccEvent) + 1;
  if (LIKELY(buf.size_ >= esz)) {
    if (LIKELY(buf.buf_[buf.size_ - esz] == 8)) { // 8 -> read 1 byte
      u64 addr = *((u64*)(buf.buf_ + buf.size_ - esz + 6));
      addr &= 0x0000ffffffffffff;
      if (LIKELY(addr == mtx_id)) {
        buf.size_ -= esz;
#ifdef STAT_ON
        uctx->stat[tid].c_read[0]--;
#endif
        DPrintf("read 1 byte reset\r\n");
      }
    }
  }
}

void impl_mtx_lock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {
  const int tid = thr->tid;
  MC_STAT(thr, c_lock)
  DPrintf3("UFO>>> #%d lock  mutex id:%llu    pc:%p\r\n", tid, mutex_id, pc);
  _reset_read(tid, mutex_id);
//  u64 _idx = __sync_add_and_fetch(&uctx->e_count, 1);
  uctx->tlbufs[tid].put_event(LockEvent((u64)mutex_id, pc));
}

void impl_mtx_unlock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {
  const int tid = thr->tid;
  MC_STAT(thr, c_unlock)
  DPrintf3("UFO>>> #%d unlock mutex %llu    pc:%p\r\n", tid, mutex_id, pc);
  auto& buf = uctx->tlbufs[tid];
  _reset_read(tid, mutex_id);
  buf.put_event(UnlockEvent((u64)mutex_id, (u64)pc));
}

void impl_rd_lock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {
  DPrintf3("UFO>>> #%d rd lock :%llu    pc:%p\r\n", thr->tid, mutex_id, pc);
}

void impl_rd_unlock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {
  DPrintf3("UFO>>> #%d !!!!!!!!!!!!11 rd unlock :%llu    pc:%p\r\n", thr->tid, mutex_id, pc);
}

void impl_rw_unlock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {
  DPrintf3("UFO>>> #%d rw unlock :%llu    pc:%p\r\n", thr->tid, mutex_id, pc);
}


void impl_cond_wait(__tsan::ThreadState* thr, uptr pc, u64 addr_cond, u64 addr_mtx) {
  DPrintf3("UFO>>> #%d cond wait       cond: %p  mutex: %p    pc:%p\r\n", thr->tid, addr_cond, addr_mtx, pc);
  const int tid = thr->tid;
  MC_STAT(thr, c_cond_wait)
    
      u64 _idx = __sync_add_and_fetch(&uctx->e_count, 1);

    uctx->tlbufs[tid].put_event(ThrCondWaitEvent(_idx,addr_cond,addr_mtx, pc));//JEFF wait
 
}

void impl_cond_signal(__tsan::ThreadState* thr, uptr pc, u64 addr_cond) {
  DPrintf3("UFO>>> #%d cond signal     cond: %p   pc:%p\r\n", thr->tid, addr_cond, pc);
  const int tid = thr->tid;
  MC_STAT(thr, c_cond_signal)
    
    u64 _idx = __sync_add_and_fetch(&uctx->e_count, 1);

    uctx->tlbufs[tid].put_event(ThrCondSignalEvent(_idx,addr_cond,pc));//JEFF signal

}

void impl_cond_broadcast(__tsan::ThreadState* thr, uptr pc, u64 addr_cond) {
  DPrintf3("UFO>>> #%d cond broadcast  cond: %p   pc:%p\r\n", thr->tid, addr_cond, pc);
  const int tid = thr->tid;
  MC_STAT(thr, c_cond_bc)
    
    u64 _idx = __sync_add_and_fetch(&uctx->e_count, 1);

    uctx->tlbufs[tid].put_event(ThrCondBCEvent(_idx,addr_cond,pc));//JEFF signal

}


///////////////////////////////////////////////////////////////////////////////////////////////

//ALWAYS_INLINE
//static void _reset_alloc_dealloc(TLBuffer& buf) {
//
//}
void *impl_alloc(ThreadState *thr, uptr pc, void *addr_left, uptr size) {
  DPrintf3("UFO>>> #%d alloc %llu bytes from %p    pc:%p\r\n", thr->tid, size, addr_left, pc);
  //DPrintf3("UFO>>> #%d allocate %p bytes from %p    pc:%p\r\n", thr->tid, size, addr_left, pc);
  const int tid = thr->tid;
  MC_STAT(thr, c_alloc)
  auto& buf = uctx->tlbufs[tid];
//  u64 _idx = __sync_add_and_fetch(&uctx->e_count, 1);
  buf.put_event(AllocEvent((u64)addr_left, (u64)pc, (u32)size));
  return addr_left;
}

void impl_dealloc(ThreadState *thr, uptr pc, void *addr, uptr size) {
  DPrintf3("UFO>>> #%d free at %p  size:%llu   pc:%p  \r\n", thr->tid, addr, size, pc);
  const int tid = thr->tid;
  MC_STAT(thr, c_dealloc)
  TLBuffer& buf = uctx->tlbufs[tid];
//  const u64 esz = sizeof(AllocEvent);
//  if (LIKELY(buf.size_ >= esz)) {
//    if (UNLIKELY(buf.buf_[buf.size_ - esz] == AllocEvent::TYPE_INDEX)) {
//      u64 alloc_addr = *((u64*)(buf.buf_ + buf.size_ - esz + 5));
//      alloc_addr &= 0x0000ffffffffffff;
//      u64 dealloc_addr = (u64)addr;
//      if (LIKELY(alloc_addr == dealloc_addr)) {
//        buf.size_ -= esz;
//#ifdef STAT_ON
//        uctx->stat[tid].c_alloc--;
//#endif
//        DPrintf3("Continous alloc dealloc reset.\r\n");
//        return;
//      }
//    }
//  }
//  u64 _idx = __sync_add_and_fetch(&uctx->e_count, 1);
  buf.put_event(DeallocEvent((u64)addr, (u64)pc, (u32)size));
}

#pragma GCC diagnostic ignored "-Wunused-function"
static u64 __read_addr(uptr addr, int kAccessSizeLog) {
switch(kAccessSizeLog) {
case 0:return *((Byte*)addr);
case 1:return *((u16*)addr);
case 2:return *((u32*)addr);
case 3:return *((u64*)addr);
default: return (u64)-1;
}
}
#pragma GCC diagnostic warning "-Wunused-function"

void impl_thread_created(int tid_parent, int tid_kid, uptr pc) {
  DPrintf3("UFO>>> (this tid %d) #%d -> #%d   pc:%p\r\n", cur_thread()->tid, tid_parent, tid_kid, pc);
#ifdef STAT_ON
  uctx->stat[tid_parent].c_start++;
#endif

  u32 et = (u32)(uctx->get_time_ms() - uctx->time_started);
  auto& buf_pa = uctx->tlbufs[tid_parent];
  // alloc 288 bytes on first pthread creation, drop this event
  if (LIKELY(buf_pa.size_ >= sizeof(AllocEvent))) {
    if (LIKELY(buf_pa.buf_[buf_pa.size_ - sizeof(AllocEvent)] == EventType::MemAlloc)) {
      u32 size = *((u32*)(buf_pa.buf_ + buf_pa.size_ - 4));
      if (LIKELY(size == 288)) {// just observed this number 288
        buf_pa.size_ -= sizeof(AllocEvent);
#ifdef STAT_ON
        uctx->stat[tid_parent].c_alloc--;
#endif
        DPrintf("alloc 288 reset\r\n");
      }
    }
  }
    
    u64 _idx = __sync_add_and_fetch(&uctx->e_count, 1);

  buf_pa.put_event(CreateThreadEvent(_idx,(TidType) tid_kid, et, (u64)pc));

  auto &buf_kid = uctx->tlbufs[tid_kid];
  if (UNLIKELY(buf_kid.buf_ != nullptr && buf_kid.size_ > 0)) {
    buf_kid.flush();
  } else buf_kid.open_buf();

  if (LIKELY(!buf_kid.is_file_open())) {
    buf_kid.open_file(tid_kid);
  }

  buf_kid.put_event(ThreadBeginEvent((TidType) tid_parent, (u64)pc, et));
}

// rewrite begin event
void impl_thread_started(__tsan::ThreadState* thr, uptr stk_addr, uptr stk_size, uptr tls_addr, uptr tls_size) {
  DPrintf3("UFO>>>thread_started (this tid %d) #%d stack #%llu %d   tls:%llu %d\r\n",
         cur_thread()->tid, thr->tid, stk_addr, stk_size, tls_addr, tls_size);

  const int tid = thr->tid;
  auto& buf = uctx->tlbufs[tid];
  if (LIKELY(buf.size_ >= sizeof(ThreadBeginEvent))) {
    if (LIKELY(buf.buf_[buf.size_ - sizeof(ThreadBeginEvent)] == EventType::ThreadBegin)) {
      ThreadBeginEvent* e = (ThreadBeginEvent*)(buf.buf_ + buf.size_ - sizeof(ThreadBeginEvent));
      e->stk_addr = (u64)stk_addr;
      e->stk_size = (u32)stk_size;
      e->tls_addr = (u64)tls_addr;
      e->tls_size = (u32)tls_size;
    }
  }
    
    //JEFF: save module info at every new thread start??
    uctx->save_module_info(tid);
}

void impl_thread_join(int tid_main, int tid_joiner, uptr pc) {
  DPrintf3("UFO>>> (this tid %d) #%d <- #%d   pc:%p\r\n", cur_thread()->tid, tid_main, tid_joiner, pc);
#ifdef STAT_ON
  uctx->stat[tid_main].c_join++;
#endif

  u32 et = (u32)(uctx->get_time_ms() - uctx->time_started);
    
      u64 _idx = __sync_add_and_fetch(&uctx->e_count, 1);

  uctx->tlbufs[tid_main].put_event(JoinThreadEvent(_idx,(TidType) tid_joiner, et, (u64)pc));

//  auto &buf_kid = uctx->tlbufs[tid_joiner];
//  buf_kid.put_event(ThreadEndEvent((TidType) tid_main, et));
//  buf_kid.finish();
}

    void impl_thread_end(ThreadState *thr) {
        DPrintf("UFO>>> thread end %d \r\n", thr->tid);
        
        u32 et = (u32)(uctx->get_time_ms() - uctx->time_started);
        
        auto &buf_kid = uctx->tlbufs[thr->tid];
        buf_kid.put_event(ThreadEndEvent((TidType) 0, et));
          buf_kid.finish(); //JEFF
        //buf_kid.release_all();
    }

    
void impl_enter_func(ThreadState *thr, uptr pc) {
  DPrintf3("UFO>>> #%d call  pc:%p  \r\n", thr->tid, pc);
  int tid = thr->tid;
  MC_STAT(thr, c_func_call)
  auto& buf = uctx->tlbufs[tid];
  buf.put_event(FuncEntryEvent((u64)pc));
  buf.last_fe = buf.e_counter_;
    
    
    

    
}

void impl_exit_func(ThreadState *thr) {
  DPrintf3("UFO>>> #%d exit call  pc:%p  \r\n", thr->tid);
  int tid = thr->tid;
  TLBuffer& buf = uctx->tlbufs[tid];

  u64 esz = sizeof(FuncEntryEvent);
  if (LIKELY(buf.size_ >= esz)) {
    if (UNLIKELY((buf.e_counter_ == buf.last_fe)
                 && (buf.buf_[buf.size_ - esz] == FuncEntryEvent::TYPE_INDEX))) {
      buf.size_ -= esz;
      return;
    }
  }
  buf.put_event(FuncExitEvent());
}


void impl_ptr_prop(__tsan::ThreadState *thr, uptr pc, uptr addr_src, uptr addr_dest) {
  DPrintf3("UFO>>>#%d  ptr prop  pc:%p  %p ==> %p \r\n", thr->tid, pc, addr_src, addr_dest);
  int tid = thr->tid;
  MC_STAT(thr, c_ptr_prop)
  uctx->tlbufs[tid].put_event(PtrAssignEvent((u64)addr_src, (u64)addr_dest));
}


void impl_ptr_deref(__tsan::ThreadState *thr, uptr pc, uptr addr_ptr) {
  DPrintf3("UFO>>>#%d  ptr de-ref  pc:%p  %p \r\n", thr->tid, pc, addr_ptr);
  int tid = thr->tid;
//  MC_STAT(thr, c_ptr_prop)
  uctx->tlbufs[tid].put_event(PtrDeRefEvent((u64)addr_ptr));
}

#include "impl_mem_acc.h"

} // ns ufo_bench
} // ns bw







#endif //UFO_RTL_IMPL_H
