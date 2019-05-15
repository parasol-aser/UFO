

#ifndef UFO_IO_Q_H
#define UFO_IO_Q_H

#include <pthread.h>

#include "defs.h"
#include "tlbuffer.h"

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

struct WriteTask {
  int fd_;
  Byte *data_;
  u32 size_;
  u32 cap_;

  ALWAYS_INLINE
  void load_with(TLBuffer *buf) {

    this->fd_ = buf->trace_fd_;
    this->size_ = buf->size_;
    buf->size_ = 0;

    u32 old_cap = buf->capacity_;
    buf->capacity_ = this->cap_;
    this->cap_ = old_cap;

    Byte *data = buf->buf_;
    buf->buf_ = this->data_;
    this->data_ = data;
  }
};

// FIFO Queue
struct OutQueue {
private:
  int length_;
  WriteTask* taskq_;
  int head_;
  int tail_;
  int task_count_;
  __sanitizer::atomic_uint8_t continue_;

  pthread_mutex_t mutex_;
  pthread_cond_t cond_not_full_;
  pthread_cond_t cond_not_empty_;
  pthread_t pt_worker_;
public:

  void start(int len);

  // called by multiple thread
  void push(TLBuffer *buf);

  void stop();

  void lock();

  void unlock();

  void wait_non_full();

  void wait_non_empty();

  void notify_non_full();

  void notify_non_empty();

  bool is_started() const;

  // release all memory, do nothing with the cond or mutex or the worker thread.
  // called at stop,
  // or by the child process immediately after fork
  void release_mem();

  WriteTask* pop() {
    WriteTask* task = taskq_ + head_;
    head_ = (head_ + 1 ) % length_;
    task_count_--;
    return task;
  }

  bool is_empty() const {
    return head_ == tail_;
  }

  bool is_full() const {
    return tail_ - head_ == (length_ - 1)
           || head_ - tail_ == 1;
  }

  int size() const {
    return this->task_count_;
  }
};


}
}
#endif //UFO_IO_Q_H
