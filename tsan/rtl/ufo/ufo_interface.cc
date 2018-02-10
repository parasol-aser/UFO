
#include <stdio.h>
#include <stdlib.h>

#include "../tsan_interface.h"
#include "defs.h"
#include "ufo_interface.h"
#include "ufo.h"


namespace aser {
namespace ufo {
    // this is just a flag indicating ufo status
    // ufo is enable/disabled by UFOContext::start_trace() stop_trace()
    static volatile bool g_started = false;
    
    UFOContext* uctx;
    
    char *global_trace_dir;
    
using namespace __tsan;

    static inline bool CompareBaseAddressJEFF2(const LoadedModule &a,
                                              const LoadedModule &b) {
        return a.base_address() < b.base_address();
    }
    
static void exit_save_module_info_jeff()
    {
        Printf("UFO>>> GREAT! I'M calling exit_save_module_info_jeff!\r\n");
      
        
        MemoryMappingLayout memory_mapping(false);
        InternalMmapVector<LoadedModule> modules(/*initial_capacity*/ 128);
        memory_mapping.DumpListOfModules(&modules);
        InternalSort(&modules, modules.size(), CompareBaseAddressJEFF2);
        
        //  __sanitizer::ListOfModules modules;
        //  modules.init();
        //  const __sanitizer::ListOfModules& modules = __sanitizer::Symbolizer::GetOrInit()->modules_;
        DPrintf3("UFO>>> pid #%d loaded %d modules\r\n", this->cur_pid_, modules.size());
        
        
        //  DPrintf3("UFO>>>Proc %d: Saving info of %d modules\r\n", this->cur_pid_, cur_len);
        
        char path[DIR_MAX_LEN];
        internal_strncpy(path, global_trace_dir, 200);
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
            Printf("0x%zx-0x%zx %s (%s)\n", modules[i].base_address(),
                   modules[i].max_executable_address(), modules[i].full_name(),
                   ModuleArchToString(modules[i].arch()));
            
        }
        fclose(cfp);
    }
    
    
    
bool init_ufo() {
  if (g_started)
    return true;


    
  __tsan::flags()->history_size = 0;

  uctx = (UFOContext*)internal_alloc(MBlockScopedBuf, sizeof(UFOContext));
  uctx->init_start();

    //JEFF: BAD -- this only work for normal termination
    //setup an exit handler to save module info
//    global_trace_dir = uctx->trace_dir;
//    atexit(exit_save_module_info_jeff);//JEFF
    
  g_started = true;
  return true;
}

using namespace __sanitizer;

bool finish_ufo() {
  if (!g_started)
    return false;

  uctx->destroy();
  // when finish ufo, set g_started to false firstly.
  g_started = false;

  internal_free(uctx);
  uctx = nullptr;
  return false;
}


// might loss some events
void before_fork() {
  uctx->stop_trace();
  g_started = false;
}

void parent_after_fork() {
  //uctx->start_trace();//JEFF
  g_started = true;
}
void child_after_fork() {
  uctx->child_after_fork();
  g_started = true;
}


///////////////////////////////////////////////////////////////////////////////////////////////

void on_mtx_lock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {
  (*UFOContext::fn_mtx_lock)(thr, pc, mutex_id);
}

void on_mtx_unlock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {
  (*UFOContext::fn_mtx_unlock)(thr, pc, mutex_id);
}

void on_cond_wait(__tsan::ThreadState* thr, uptr pc, u64 addr_cond, u64 addr_mtx) {
  (*UFOContext::fn_cond_wait)(thr, pc, addr_cond, addr_mtx);
}

void on_cond_signal(__tsan::ThreadState* thr, uptr pc, u64 addr_cond) {
  (*UFOContext::fn_cond_signal)(thr, pc, addr_cond);
}

void on_cond_broadcast(__tsan::ThreadState* thr, uptr pc, u64 addr_cond) {
  (*UFOContext::fn_cond_bc)(thr, pc, addr_cond);
}

//void on_rd_lock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {
//  MC_STAT(thr, c_rd_lock)
//  (*UFOContext::fn_rd_lock)(thr, pc, mutex_id);
//}
//
//void on_rd_unlock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {
//  MC_STAT(thr, c_rd_unlock)
//  (*UFOContext::fn_rd_unlock)(thr, pc, mutex_id);
//}
//
//void on_rw_unlock(__tsan::ThreadState *thr, uptr pc, u64 mutex_id) {
//  MC_STAT(thr, c_rw_unlock)
//  (*UFOContext::fn_rw_unlock)(thr, pc, mutex_id);
//}


void *on_alloc(ThreadState *thr, uptr pc, void *addr_left, uptr size) {
  return (*UFOContext::fn_alloc)(thr, pc, addr_left, size);
}

void on_dealloc(ThreadState *thr, uptr pc, void *addr, uptr size) {
  (*UFOContext::fn_dealloc)(thr, pc, addr, size);
}


__HOT_CODE
void on_mem_acc(ThreadState *thr, uptr pc, uptr addr, int kAccessSizeLog, volatile bool is_write) {
  (*UFOContext::fn_mem_acc)(thr, pc, addr, kAccessSizeLog, is_write);
}

__HOT_CODE
void on_mem_range_acc(__tsan::ThreadState *thr, uptr pc, uptr addr, uptr size, volatile bool is_write) {
  (*UFOContext::fn_mem_range_acc)(thr, pc, addr, size, is_write);
}


void on_thread_created(int tid_parent, int tid_kid, uptr pc) {
    
    uctx->start_trace();//JEFF: start tracing after the first new thread is created

  (*UFOContext::fn_thread_created)(tid_parent, tid_kid, pc);
}
void on_thread_start(__tsan::ThreadState* thr, uptr stk_addr, uptr stk_size, uptr tls_addr, uptr tls_size) {
  (*UFOContext::fn_thread_started)(thr, stk_addr, stk_size, tls_addr, tls_size);
}

void on_thread_join(int tid_main, int tid_joiner, uptr pc) {
  (*UFOContext::fn_thread_join)(tid_main, tid_joiner, pc);
}
    
    void on_thread_end(__tsan::ThreadState* thr) {
        (*UFOContext::fn_thread_end)(thr);
    }

void enter_func(__tsan::ThreadState *thr, uptr pc) {
  (*UFOContext::fn_enter_func)(thr, pc);
}

void exit_func(__tsan::ThreadState *thr) {
  (*UFOContext::fn_exit_func)(thr);
}

void on_ptr_prop(__tsan::ThreadState *thr, uptr pc, uptr addr_src, uptr addr_dest) {
  (*UFOContext::fn_ptr_prop)(thr, pc, addr_src, addr_dest);
}
void on_ptr_deref(__tsan::ThreadState *thr, uptr pc, uptr addr_ptr) {
  (*UFOContext::fn_ptr_deref)(thr, pc, addr_ptr);
}

} // ns ufo_bench
} // ns bw
















