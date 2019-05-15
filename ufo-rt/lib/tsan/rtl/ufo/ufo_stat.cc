

#include "ufo_stat.h"

namespace aser {
namespace ufo {

#define _P(f, ...)\
  Printf(__VA_ARGS__);\
  if (f != nullptr)\
    fprintf(f, __VA_ARGS__)

static void _print_state(FILE* f, int tid, const TLStatistic &stat) {
  _P(f, "#%d |Start %llu |Join %llu |Alloc %llu |Dealloc %llu "
              "|Lock %llu |Unlock %llu |Cond Wait %llu |Cond Signal %llu | Cond BC %llu"

        "\r\nR1 %llu |R2 %llu |R4 %llu |R8 %llu "
                 "|W1 %llu |W2 %llu |W4 %llu |W8 %llu"
        "\r\nRW %llu |RR %llu |Stack ACC %llu |Stack Range ACC %llu |Func Call %llu | Ptr Prop %llu"
        "\r\nTotal stored events: %llu, size: %llu\r\n",
         tid, stat.c_start, stat.c_join, stat.c_alloc, stat.c_dealloc,
             stat.c_lock, stat.c_unlock, stat.c_cond_wait, stat.c_cond_signal, stat.c_cond_bc,

         stat.c_read[0], stat.c_read[1], stat.c_read[2], stat.c_read[3],
           stat.c_write[0], stat.c_write[1], stat.c_write[2], stat.c_write[3],

         stat.c_range_w, stat.c_range_r, stat.cs_acc, stat.cs_range_acc, stat.c_func_call, stat.c_ptr_prop,

     stat.count(), stat.size());
}


void summary_stat(FILE* f, TLStatistic* arr_stat, int len, u32 this_pid, u32 p_pid) {
  TLStatistic total;
  total.init();
  _P(f, "\r\n >>>>>>>>>>>>>>>>>> Statistic (pid:%u from:%u) >>>>>>>>>>>>>>>>>>>>>>>>>>>>\r\n", this_pid, p_pid);

  for (int i = 0; i < len; ++i) {
    if (!arr_stat[i].used())
      continue;

    total.c_start += arr_stat[i].c_start;
    total.c_join += arr_stat[i].c_join;
    total.c_dealloc += arr_stat[i].c_dealloc;
    total.c_alloc += arr_stat[i].c_alloc;
    total.c_unlock += arr_stat[i].c_unlock;
    total.c_lock += arr_stat[i].c_lock;
    total.c_cond_wait += arr_stat[i].c_cond_wait;
    total.c_cond_signal += arr_stat[i].c_cond_signal;
    total.c_cond_bc += arr_stat[i].c_cond_bc;

    total.c_range_r += arr_stat[i].c_range_r;
    total.c_range_w += arr_stat[i].c_range_w;
    total.c_read[0] += arr_stat[i].c_read[0];
    total.c_read[1] += arr_stat[i].c_read[1];
    total.c_read[2] += arr_stat[i].c_read[2];
    total.c_read[3] += arr_stat[i].c_read[3];
    total.c_write[0] += arr_stat[i].c_write[0];
    total.c_write[1] += arr_stat[i].c_write[1];
    total.c_write[2] += arr_stat[i].c_write[2];
    total.c_write[3] += arr_stat[i].c_write[3];
    total.cs_acc += arr_stat[i].cs_acc;
    total.cs_range_acc += arr_stat[i].cs_range_acc;
    total.c_func_call += arr_stat[i].c_func_call;
    total.c_ptr_prop += arr_stat[i].c_ptr_prop;

    _print_state(f, i, arr_stat[i]);
  }

//  _P(f, "\r\n     ================= Total (pid:%u from:%u) =================\r\n", this_pid, p_pid);
//  _print_state(f, -1, total);
//  _P(f, "\r\n <<<<<<<<<<<<<<<<<<<< Statistic <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\r\n");
}



void print_csv(FILE* f, TLStatistic* arr_stat, int len, u32 this_pid, u32 p_pid) {
  fprintf(f, "%u -> %u\r\n", p_pid, this_pid);
  fprintf(f, "TID,Start,Join,Alloc,Dealloc,Lock,Unlock,Cond Wait,Cond Signal,Cond BC," // 9
      "R1,R2,R4,R8,W1,W2,W4,W8," // 8
      "RW,RR,Stack ACC,Stack Range ACC,Func Call, Ptr Prop" // 5
      "Total,size\r\n");

  for (int i = 0; i < len; ++i) {
    u64 sz = arr_stat[i].size();
    if (sz < 1)
      continue;
    const auto& s = arr_stat[i];
    fprintf(f,
            "%d,%llu,%llu,%llu,%llu,%llu,%llu,%llu,%llu,%llu,"
             "%llu,%llu,%llu,%llu,%llu,%llu,%llu,%llu,"
                "%llu,%llu,%llu,%llu,%llu,%llu,%llu, %llu\r\n",
            i, s.c_start, s.c_join, s.c_alloc, s.c_dealloc, s.c_lock, s.c_unlock,
                  s.c_cond_wait, s.c_cond_signal, s.c_cond_bc,
            s.c_read[0], s.c_read[1], s.c_read[2], s.c_read[3],
              s.c_write[0], s.c_write[1], s.c_write[2], s.c_write[3],
            s.c_range_w, s.c_range_r, s.cs_acc, s.cs_range_acc, s.c_func_call, s.c_ptr_prop,
            s.count(), sz);
  }
}



#undef _P

}
}
