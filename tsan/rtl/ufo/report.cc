#include "../tsan_defs.h"
#include "../tsan_flags.h"
#include "../tsan_mman.h"
#include "../tsan_symbolize.h"
#include "../tsan_stack_trace.h"
#include "../tsan_report.h"
#include "../../../sanitizer_common/sanitizer_stacktrace_printer.h"


#include "ufo.h"
#include "report.h"

namespace aser {
namespace ufo {

using __sanitizer::common_flags;
using __sanitizer::kStackTraceMax;
using __sanitizer::SymbolizedStack;
using __sanitizer::AddressInfo;
using __sanitizer::StripPathPrefix;
using __sanitizer::InternalScopedString;
using __sanitizer::GetPageSizeCached;

void print_callstack(__tsan::ThreadState *thr, uptr pc) {

//  Printf("\r\n>>>>print_callstack\r\n");
  __tsan::VarSizeStackTrace trace;
  u32 size = thr->shadow_stack_pos - thr->shadow_stack;
  trace.Init(thr->shadow_stack, size, pc);

  __tsan::ReportStack *rstk = SymbolizeStack(trace);

  if (rstk == nullptr || rstk->frames == nullptr) {
    Printf("Empty Call Stack\n");
    return;
  }

  SymbolizedStack *frame = rstk->frames;
  int depth = 0;
  while (frame != nullptr && frame->info.address > 0) {
//    for (int i = 0; i < depth; ++i) {
//      Printf(" ");
//    }
    InternalScopedString res(2 * GetPageSizeCached());
    __sanitizer::RenderFrame(&res, common_flags()->stack_trace_format, depth, frame->info,
                             false,
                             common_flags()->strip_path_prefix, "__interceptor_");
    Printf("%s\n", res.data());
    depth++;
    frame = frame->next;
  }
  Printf("\n");

}

}
}

