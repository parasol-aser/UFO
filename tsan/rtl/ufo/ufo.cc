

#include <stdio.h>
#include <sys/stat.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/time.h>

#include "../../../sanitizer_common/sanitizer_common.h"
//#include "../../../sanitizer_common/sanitizer_symbolizer.h"
#include <stdio.h>

#include "../../../sanitizer_common/sanitizer_posix.h"
#include "../../../sanitizer_common/sanitizer_libc.h"

#include "../tsan_defs.h"
#include "../tsan_flags.h"
#include "../tsan_mman.h"
#include "../tsan_symbolize.h"

#include "defs.h"
#include "ufo.h"

#include "dummy_rtl.h"
#include "rtl_impl.h"

namespace aser {
namespace ufo {

//static
u64 UFOContext::get_time_ms() {
  timeval tv;
  gettimeofday(&tv, 0);
  u64 ms = (u64) (tv.tv_sec) * 1000 + (u64) (tv.tv_usec) / 1000;
  return ms;
}
//void* (*fn_alloc)(__tsan::ThreadState *thr, uptr pc, void *addr, uptr size);
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

FPAlloc UFOContext::fn_alloc = &nop_alloc;

FPDealloc UFOContext::fn_dealloc = &nop_dealloc;
FPThr UFOContext::fn_thread_created = &nop_thread_created;
FPThrStart UFOContext::fn_thread_started = &nop_thread_start;
FPThr UFOContext::fn_thread_join = &nop_thread_join;
FPThrEnd UFOContext::fn_thread_end = &nop_thread_end;
    
FPMtxLock UFOContext::fn_mtx_lock = &nop_mtx_lock;
FPMtxLock UFOContext::fn_mtx_unlock = &nop_mtx_unlock;

FPMtxLock UFOContext::fn_rd_lock = &nop_rd_lock;
FPMtxLock UFOContext::fn_rd_unlock = &nop_rd_unlock;
FPMtxLock UFOContext::fn_rw_unlock = &nop_rw_unlock;


FPCondWait UFOContext::fn_cond_wait = &nop_cond_wait;
FPCondSignal UFOContext::fn_cond_signal = &nop_cond_signal;
FPCondSignal UFOContext::fn_cond_bc = &nop_cond_broadcast;

FPMemAcc UFOContext::fn_mem_acc = &nop_mem_acc;
FPMemRangeAcc UFOContext::fn_mem_range_acc = &nop_mem_range_acc;

FPFuncEnter UFOContext::fn_enter_func = &nop_enter_func;
FPFuncExit UFOContext::fn_exit_func = &nop_exit_func;
FPPtrProp UFOContext::fn_ptr_prop = &nop_ptr_prop;
FPPtrDeRef UFOContext::fn_ptr_deref = &nop_ptr_deref;

// static
s64 UFOContext::get_int_opt(const char *name, s64 default_val) {
  const char *str_int = __sanitizer::GetEnv(name);
  if (str_int == nullptr
      || internal_strnlen(str_int, 40) < 1) {
    return default_val;
  } else {
    return internal_atoll(str_int);
  }
}


void UFOContext::start_trace() {
  if ( !is_on) {
    return;
  }
  fn_alloc = &impl_alloc;
  fn_dealloc = &impl_dealloc;
  fn_thread_created = &impl_thread_created;
  fn_thread_started = &impl_thread_started;
  fn_thread_join = &impl_thread_join;
    fn_thread_end = &impl_thread_end;
//
  fn_mtx_lock = &impl_mtx_lock;
  fn_mtx_unlock = &impl_mtx_unlock;
  fn_rd_lock = &impl_rd_lock;
  fn_rd_unlock = &impl_rd_unlock;
  fn_rw_unlock = &impl_rw_unlock;
//
  fn_cond_wait = &impl_cond_wait;
  fn_cond_signal = &impl_cond_signal;
  fn_cond_bc = &impl_cond_broadcast;


  if (this->trace_func_call) {
    fn_enter_func = &impl_enter_func;
    fn_exit_func = &impl_exit_func;
  }
//  if (this->trace_ptr_prop) {
//    fn_ptr_prop = &impl_ptr_prop;
//    fn_ptr_deref = &impl_ptr_deref;
//  }

  s64 set_no_stack = get_int_opt(ENV_NO_STACK_ACC, NO_STACK_ACC);
  s64 set_no_value = get_int_opt(ENV_NO_VALUE, 0);
  this->no_data_value = set_no_value != 0;
  if (set_no_stack == 0) {
    this->no_stack = false;
    fn_mem_range_acc = &impl_mem_range_acc;
    if (no_data_value) {
      fn_mem_acc = &impl_mem_acc_nv;
    } else {
      fn_mem_acc = &impl_mem_acc;
    }
  } else {
    this->no_stack = true;
    fn_mem_range_acc = &ns_mem_range_acc;
    if (no_data_value) {
      fn_mem_acc = &ns_mem_acc_nv;
    } else {
      fn_mem_acc = &ns_mem_acc;
    }
  }
  DPrintf("UFO>>>start tracing\r\n");
}

void UFOContext::stop_trace() {
  fn_alloc = &nop_alloc;
  fn_dealloc = &nop_dealloc;
  fn_thread_created = &nop_thread_created;
  fn_thread_started = &nop_thread_start;
  fn_thread_join = &nop_thread_join;
  fn_thread_end = &nop_thread_end;
    
  fn_mtx_lock = &nop_mtx_lock;
  fn_mtx_unlock = &nop_mtx_unlock;
  fn_rd_lock = &nop_rd_lock;
  fn_rd_unlock = &nop_rd_unlock;
  fn_rw_unlock = &nop_rw_unlock;

  fn_cond_wait = &nop_cond_wait;
  fn_cond_signal = &nop_cond_signal;
  fn_cond_bc = &nop_cond_broadcast;

  fn_mem_acc = &nop_mem_acc;
  fn_mem_range_acc = &nop_mem_range_acc;
  fn_enter_func = &nop_enter_func;
  fn_exit_func = &nop_exit_func;
  fn_ptr_prop = &nop_ptr_prop;
  fn_ptr_deref = &nop_ptr_deref;
}
void UFOContext::read_config() {

  this->tl_buf_size_ = {0};
  this->use_compression = 0;
  this->use_io_q = 0;
  this->out_queue_legth = -1;

  this->do_print_stat_ = get_int_opt(ENV_PRINT_STAT, 1);

  // buffer size
  u64 bufsz = (u64)get_int_opt(ENV_TL_BUF_SIZE, DEFAULT_BUF_PRE_THR);
  if (0 < bufsz && bufsz < 4096) { // 4G
//    this->tl_buf_size = (u64) bufsz * 1024 * 1024; // to MB
    u32 sz = bufsz * 1024 * 1024;
    atomic_store_relaxed(&this->tl_buf_size_, sz);
  } else {
    DPrintf3("!!! Could not read thread local buffer size or size illegal:  %ld\r\n", bufsz);
    Die();
  }

  bufsz = get_int_opt(ENV_UFO_MEM_T1, DEFAULT_MEM_THRESHOLD_1);
  if (0 < bufsz && bufsz < DEFAULT_MEM_THRESHOLD_1 * 100)
    this->mem_t1_ = bufsz * 1024 * 1024;
  else {
    DPrintf3("!!! Invalid level 1 memory threshold: %uMB, reset to default: %uMB.\r\n"
        , bufsz, DEFAULT_MEM_THRESHOLD_1);
    this->mem_t1_ = DEFAULT_MEM_THRESHOLD_1 * 1024 * 1024;
  }
  bufsz = get_int_opt(ENV_UFO_MEM_T2, DEFAULT_MEM_THRESHOLD_2);
  if (mem_t1_ < (bufsz * 1024 * 1024) && bufsz < DEFAULT_MEM_THRESHOLD_2 * 100)
    this->mem_t2_ = bufsz * 1024 * 1024;
  else {
    this->mem_t2_ = mem_t1_ * 3 / 2;
    DPrintf3("!!! Invalid level 2 memory threshold: %uMB, reset to 1.5 * level1 (%u MB).\r\n"
        , bufsz, mem_t2_ / 1024 / 1024);
  }

  ratio_mem_x2 = 0;
  ratio_mem_x4 = 0;

  // use snappy compression
  s64 use_comp = get_int_opt(ENV_USE_COMPRESS, COMPRESS_ON);
  this->use_compression = use_comp;

  // use async io queue
  s64 do_use_q = get_int_opt(ENV_USE_IO_Q, ASYNC_IO_ON);
  this->use_io_q = do_use_q;
  if (use_io_q) {
    // queue size
    s64 io_q_sz = get_int_opt(ENV_IO_Q_SIZE, DEFAULT_IO_Q_SIZE);
    if (0 < io_q_sz && io_q_sz < 1024) {
      this->out_queue_legth = io_q_sz;
    } else {
      Printf("!!! Could not read out queue length or length is illegal:  %d\r\n", io_q_sz);
      Die();
    }
  }

  s64 do_trace_call = get_int_opt(ENV_TRACE_FUNC, 0);
  this->trace_func_call = do_trace_call;

  s64 do_trace_ptr_prop = get_int_opt(ENV_PTR_PROP, 0);
  this->trace_ptr_prop = do_trace_ptr_prop;

  // trace dir
  __sanitizer::internal_memset(trace_dir, '\0', DIR_MAX_LEN);
  const char *env_dir = __sanitizer::GetEnv(ENV_TRACE_DIR);
  u64 dir_len;
  if (env_dir == nullptr
      || (dir_len = internal_strnlen(env_dir, DIR_MAX_LEN)) < 1) {
    internal_strncpy(trace_dir, DEFAULT_TRACE_DIR, 100);
  } else {
    internal_strncpy(trace_dir, env_dir, DIR_MAX_LEN - 45);
  }
  // append '_' to dir name
  dir_len = internal_strnlen(trace_dir, DIR_MAX_LEN - 1);
  if (0 < dir_len && dir_len < DIR_MAX_LEN - 45) {
    if (trace_dir[dir_len - 1] == '/') {
      trace_dir[dir_len - 1] = '_';
    } else {
      trace_dir[dir_len] = '_';
      trace_dir[dir_len + 1] = '\0';
      dir_len++;
    }
  }
  // append process id to the dir name
//  pre_len += __sanitizer::internal_snprintf(file_name + pre_len, name_len, "%d", tid);
  char str_pid[50];
  __sanitizer::internal_memset(str_pid, '\0', 50);
  __sanitizer::internal_snprintf(str_pid, 45, "%u", this->cur_pid_);
  __sanitizer::internal_strncat(trace_dir, str_pid, 45);

}

void UFOContext::open_trace_dir() {
  // open or create
  struct stat _st = {0};
  __sanitizer::internal_stat(trace_dir, &_st);
  if (!S_ISDIR(_st.st_mode)) {
    if (0 != mkdir(trace_dir, 0700)) {
      fprintf(stderr, "UFO>>> Could not create directory for trace files '%s': ", trace_dir);
      perror("");
      Die();
    }
  } else {
    // These are data types defined in the "dirent" header
    DIR *folder = opendir(trace_dir);
    struct dirent *next_file;
    char filepath[320];

    while ((next_file = readdir(folder)) != nullptr) {
      // build the path for each file in the folder
      sprintf(filepath, "%s/%s", trace_dir, next_file->d_name);
      remove(filepath);
    }
    closedir(folder);
  }
}

void UFOContext::print_config() {

  DPrintf3("UFO>>> (curpid:%u parentpid:%u) enabled: ", this->cur_pid_, this->p_pid_);
  if (this->no_stack) {
    DPrintf3("do not record stack access; ");
  } else {
    DPrintf3("record stack access; ");
  }
  if (this->no_data_value) {
    DPrintf3("do not record read/write value; ");
  } else {
    DPrintf3("record value; ");
  }
  if (this->trace_func_call) {
    DPrintf3("trace func call; ");
  } else DPrintf3("do not trace func call; ");

  if (this->trace_ptr_prop) {
    DPrintf3("trace ptr prop; ");
  } else DPrintf3("do not trace ptr prop; ");


#ifdef STAT_ON
  DPrintf3("statistic set on; ");
#endif
  u32 sz = get_buf_size();
  DPrintf3("initial per thread buffer size:%u (%u MB), ", sz, (sz / 1024 / 1024));
//  Printf("level 1 memory threshold:%uMB, level 2: %uMB; ", mem_t1_ / 1024 / 1024, mem_t2_ / 1024 / 1024);

  if (this->use_compression) {
    DPrintf3("compress buffer; ");
  } else {
    DPrintf3("do not compress; ");
  }
  if (this->use_io_q) {
    DPrintf3("async IO queue enabled, length: %d; ", out_queue_legth);
  } else {
    DPrintf3("async IO queue disabled; ");
  }
  if (this->do_print_stat_) {
    DPrintf3("print stat; ");
  } else {
    DPrintf3("do not print stat; ");
  }

  DPrintf3("trace dir is '%s'.\r\n", this->trace_dir);
}

void UFOContext::init_start() {
    
  // this process id
  this->cur_pid_ = (u32)__sanitizer::internal_getpid();
  this->p_pid_ = (u32)__sanitizer::internal_getppid();
  this->is_subproc = false;
  this->mudule_length_ = 0;
  this->e_count = 0;

  s64 set_on = get_int_opt(ENV_UFO_ON, 0);
  this->is_on = (set_on != 0);

  // step 0: prepare statistic
#ifdef STAT_ON
  this->stat = (TLStatistic *) __tsan::internal_alloc(__tsan::MBlockScopedBuf, MAX_THREAD * sizeof(TLStatistic));
  for (u32 i = 0; i < MAX_THREAD; ++i) {
    stat[i].init();
  }
#endif

  // read before return (if not on), stat config
  read_config(); // before openbuf

//    DPrintf3("IN UFO trace dir is '%s'.\r\n", this->trace_dir);

  if (!this->is_on) {
    DPrintf3("UFO>>> UFO (pid:%u from:%u) disabled\r\n", this->cur_pid_);
    return;
  }

  time_started = get_time_ms();
  print_config();

  // step1: prepare tl buffer
  tlbufs = (TLBuffer *) __tsan::internal_alloc(__tsan::MBlockScopedBuf, MAX_THREAD * sizeof(TLBuffer));
  for (u32 i = 0; i < MAX_THREAD; ++i) {
    tlbufs[i].init();
  }
  G_BUF_BASE = tlbufs;

  // step 3: prepare async io queue
  if (use_io_q) {
    this->out_queue = (OutQueue *) __tsan::internal_alloc(__tsan::MBlockScopedBuf, sizeof(OutQueue));
    out_queue->start(this->out_queue_legth);
  }

  // step 4: create trace dir
  open_trace_dir();
    
    
    //JEFF, we do this at the end ??
  // step 5: save loaded module info
//  save_module_info(); //save module info at a later point

  // step create buffer for main thread
#ifdef BUF_EVENT_ON
  tlbufs[0].open_buf();
#endif
  tlbufs[0].open_file(0);

  { // put ThreadStarted event to main thread trace
    uptr stk_addr;
    uptr stk_size;
    uptr tls_addr;
    uptr tls_size;

    __sanitizer::GetThreadStackAndTls(false,
                                      (uptr *) &stk_addr, &stk_size,
                                      (uptr *) &tls_addr, &tls_size);
    ThreadBeginEvent be(u16(0), u64(0), u32(0));
    be.stk_addr = (u64) stk_addr;
    be.stk_size = (u32) stk_size;
    be.tls_addr = (u64) tls_addr;
    be.tls_size = (u32) tls_size;

    tlbufs[0].put_event(be);
  }
  //this->start_trace();//JEFF: start tracing after the first new thread is created
}

void UFOContext::destroy() {
  stop_trace();

#ifdef STAT_ON
  if (this->do_print_stat_ && this->is_on) {
    output_stat();
  }
  __tsan::internal_free(stat);
#endif
  stat = nullptr;

  if (!this->is_on)
    return;

  // first flush io queue
  if (use_io_q) {
    out_queue->stop();
    __tsan::internal_free(out_queue);
  }
  // then flush remaining buffer & close file
  for (u32 i = 0; i < MAX_THREAD; ++i) {
    tlbufs[i].finish();
  }

  __tsan::internal_free(tlbufs);
  tlbufs = nullptr;

}

    static inline bool CompareBaseAddressJEFF(const LoadedModule &a,
                                          const LoadedModule &b) {
        return a.base_address() < b.base_address();
    }

void UFOContext::save_module_info(int tid) {

    MemoryMappingLayout memory_mapping(false);
    InternalMmapVector<LoadedModule> modules(/*initial_capacity*/ 128);
    memory_mapping.DumpListOfModules(&modules);
    InternalSort(&modules, modules.size(), CompareBaseAddressJEFF);
    
//  __sanitizer::ListOfModules modules;
//  modules.init();
//  const __sanitizer::ListOfModules& modules = __sanitizer::Symbolizer::GetOrInit()->modules_;
  const u32 cur_len = (u32)modules.size();

  if (this->mudule_length_ == cur_len)
    return;

    this->mudule_length_ = cur_len;//set module_length and write to file


    DPrintf3("UFO>>> new thread #%d in pid #%d loaded %d modules\r\n", tid, this->cur_pid_, modules.size());

  char path[DIR_MAX_LEN];
  internal_strncpy(path, trace_dir, 200);
  internal_strncat(path, NAME_MODULE_INFO, 50);

  FILE *cfp = fopen(path, "w+");
    
    
for (uptr i = 0; i < modules.size(); ++i) {//      fprintf(fp, "%s %s %s %d", "We", "are", "in", 2012);
    fprintf(cfp, "%s|", modules[i].full_name());
    uptr base = modules[i].base_address();
      uptr max_addr = modules[i].max_executable_address();
          fprintf(cfp, "%lx|", base);
            fprintf(cfp, "%lx|", max_addr);
//    fprintf(cfp, "%llx|", base);
//      fprintf(cfp, "%llx|", max_addr);
//      
//    for (const auto &range : module.ranges()) {
////      if (range.executable) {
//      uptr start = range.beg;
//      uptr end = range.end;
//      fprintf(cfp, "%d|%llx|%llx|", range.executable, start, end);
////      }
//    }
    fprintf(cfp, "\r\n");
    
    //PRINT MODULE MAP
    DPrintf3("0x%zx-0x%zx %s (%s)\n", modules[i].base_address(),
           modules[i].max_executable_address(), modules[i].full_name(),
           ModuleArchToString(modules[i].arch()));
    
  }
  fclose(cfp);
}


void UFOContext::output_stat() {
  char path[DIR_MAX_LEN];

  internal_strncpy(path, this->trace_dir, 200);
  internal_strncat(path, NAME_STAT_FILE, 50);
  FILE *f_pp = fopen(path, "w+");
  summary_stat(f_pp, this->stat, MAX_THREAD, cur_pid_, p_pid_);
  fclose(f_pp);

  internal_memset(path, '\0', DIR_MAX_LEN);

  internal_strncpy(path, trace_dir, 200);
  internal_strncat(path, NAME_STAT_CSV, 50);
  FILE* f_csv = fopen(path, "w+");
  print_csv(f_csv, this->stat, MAX_THREAD, cur_pid_, p_pid_);
  fclose(f_csv);
}

/**
 * if is on, re-init:
 * update this pid and parent pid;
 * update trace dir str and create new folder for child process;
 * release all memory in the old out-queue, then start a new queue.
 * reset each buffer and stat.
 *
 *
 * what happens to resources?
 * for files:
 * simply reset all file descriptors, when flush is called, new file will be created.
 * for memory:
 * do nothing and keep the addresses in the pointers, memory will be re-mapped if accessed later.
 * reset all 'size' to zero.
 * for pthread stuff:
 * do nothing, discard the whole out-queue and create a new one.
 *
 */
void UFOContext::child_after_fork() {
  if (!is_on)
    return;

  this->cur_pid_ = (u32)__sanitizer::internal_getpid();
  this->p_pid_ = (u32)__sanitizer::internal_getppid();
  this->is_subproc = true;
  this->mudule_length_ = 0;

  read_config();
  time_started = get_time_ms();
  print_config();
  open_trace_dir();
    
//  save_module_info();//JEFF save module info at a later point!

  if (use_io_q) {
    this->out_queue->release_mem();
    __tsan::internal_free(out_queue);
    this->out_queue = (OutQueue *) __tsan::internal_alloc(__tsan::MBlockScopedBuf, sizeof(OutQueue));
    out_queue->start(this->out_queue_legth);
  }

  for (u32 i = 0; i < MAX_THREAD; ++i) {
    this->tlbufs[i].reset();

#ifdef STAT_ON
    this->stat[i].init();
#endif
  }

//  this->start_trace();//JEFF: start tracing after the first new thread has been created
}

void UFOContext::mem_acquired(u32 n_bytes) {
//  this->lock.Lock();
//  this->total_mem_ += n_bytes;
//  if (total_mem_ >= this->mem_t2_) {
//    mem_t2_ = mem_t2_ * 4 / 3;
//
//    u32 buf_sz = get_buf_size();
//    buf_sz /= 4;
//    if (buf_sz <= MIN_BUF_SZ)
//      buf_sz = MIN_BUF_SZ;
//    atomic_store_relaxed(&this->tl_buf_size_, buf_sz);
//    Printf(">>>>>>>>>> tl buf  / 4\r\n");
//
//    ratio_mem_x4 += 1;
//  } else if (total_mem_ > this->mem_t1_) {
//    mem_t1_ = mem_t1_ * 3 / 2;
//
//    u32 buf_sz = get_buf_size();
//    buf_sz /= 2;
//    if (buf_sz <= MIN_BUF_SZ)
//      buf_sz = MIN_BUF_SZ;
//    atomic_store_relaxed(&this->tl_buf_size_, buf_sz);
//    Printf(">>>>>>>>>> tl buf  / 2\r\n");
//
//    ratio_mem_x2 += 1;
//  }
//  lock.Unlock();
}

void UFOContext::mem_released(u32 n_bytes) {
//  lock.Lock();
//  this->total_mem_ += n_bytes;
//
//  if (ratio_mem_x4 > 1 && total_mem_ < mem_t2_) {
//    ratio_mem_x4 -= 1;
//
//    u32 buf_sz = get_buf_size();
//    buf_sz *= 4;
//    atomic_store_relaxed(&this->tl_buf_size_, buf_sz);
//    Printf(">>>>>>>>>> tl buf X4\r\n");
//
//  } else if (ratio_mem_x2 > 1 && total_mem_ < mem_t1_) {
//
//    ratio_mem_x2 -= 1;
//
//    u32 buf_sz = get_buf_size();
//    buf_sz *= 2;
//    atomic_store_relaxed(&this->tl_buf_size_, buf_sz);
//    Printf(">>>>>>>>>> tl buf X2\r\n");
//  }
//  lock.Unlock();
}

}
}

