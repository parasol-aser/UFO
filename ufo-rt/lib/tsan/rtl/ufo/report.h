// report stack tracing in Sanitizer style

#ifndef UFO_REPORT_H
#define UFO_REPORT_H



#include "defs.h"
#include "ufo_interface.h"
#include "ufo.h"

//#define DPrintf Printf

namespace aser {
namespace ufo {

extern UFOContext *uctx;


void print_callstack(__tsan::ThreadState *thr, uptr pc);


}
}


#endif //UFO_REPORT_H
