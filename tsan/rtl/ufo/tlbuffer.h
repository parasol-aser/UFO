

#ifndef UFO_TLBUFFER_H
#define UFO_TLBUFFER_H


#ifndef BUF_EVENT_ON
#include "../../../sanitizer_common/sanitizer_common.h"
#include "../../../sanitizer_common/sanitizer_posix.h"
#endif

#include "../tsan_defs.h"
#include "defs.h"


namespace aser {
namespace ufo {

struct TLBuffer;
extern TLBuffer *G_BUF_BASE;


// thread safe, COMPRESS_ON
void write_file(int fd, Byte* data, u64 len);

struct TLBuffer {

  bool stopped;

  Byte *buf_;
  u32 size_;
  u32 capacity_;
  int trace_fd_;
  u64 last_fe; // last e_count_ value at function entry, used to eliminate empty calls

  // useless, info saved to thrbegin
  u32 tls_height;
  u64 tls_bottom;// lower address
  u32 stack_height;
  u64 stack_bottom;// lower address

  u64 e_counter_;

  void init();

  void open_buf();

  void open_file(int tid);

  void flush();

  void finish();

  void reset();

  bool is_file_open() const;


#pragma GCC diagnostic ignored "-Wcast-qual"
  template<typename E, u32 SZ = sizeof(E)>
  __HOT_CODE
  ALWAYS_INLINE
  void put_event(const E &event) {

      if(trace_fd_<0) return;//JEFF
      
#ifndef BUF_EVENT_ON
    if (internal_write(trace_fd_, &event, SZ) < 0) {
    fprintf(stderr, "size:%llu  this %p  error: unable to put_event to file fd: %d.\n", sizeof(E), this, trace_fd_);
  }
  return;
#else
    if (UNLIKELY(buf_ == nullptr)) {
      open_buf();
      int tid = this - G_BUF_BASE;
      open_file(tid);
    } else if (UNLIKELY(size_ + SZ >= capacity_)) {
      flush();
    }
    Byte *pdata = (Byte *) (&event);
    Byte *pbuf = buf_ + size_;

    // write index (8 byte)
    *pbuf = *pdata;
    u32 offset = 1;

    while (offset < SZ) {
      *((u16 *) (pbuf + offset)) = *((u16 *) (pdata + offset));
      offset += 2;
    }
    size_ += offset;
#endif

    this->e_counter_++;
  }
#pragma GCC diagnostic warning "-Wcast-qual"
};


} // ns ufo_bench
} // ns bw

#endif //UFO_TLBUFFER_H
