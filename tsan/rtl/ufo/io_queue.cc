

#include <unistd.h>
#include "../../../sanitizer_common/sanitizer_atomic.h"
#include "../../../sanitizer_common/sanitizer_common.h"
#include "../../../sanitizer_common/sanitizer_posix.h"

#include "../../../interception/interception.h"
#include "../tsan_defs.h"
#include "../tsan_mman.h"

#include "defs.h"
#include "ufo.h"
#include "tlbuffer.h"
#include "io_queue.h"
#include "snappy/snappy.h"

//JEFF
//DECLARE_REAL(int, pthread_join, pthread_t, const void**)

DECLARE_REAL(int, pthread_mutex_init, pthread_mutex_t*, const pthread_mutexattr_t*)
DECLARE_REAL(int, pthread_mutex_lock, pthread_mutex_t*)
DECLARE_REAL(int, pthread_mutex_unlock, pthread_mutex_t*)
DECLARE_REAL(int, pthread_mutex_destroy, pthread_mutex_t*)

DECLARE_REAL(int, pthread_cond_init, pthread_cond_t*, const pthread_condattr_t*)
DECLARE_REAL(int, pthread_cond_wait, pthread_cond_t*, pthread_mutex_t*)
DECLARE_REAL(int, pthread_cond_signal, pthread_cond_t*)
DECLARE_REAL(int, pthread_cond_destroy, pthread_cond_t*)

namespace aser {
namespace ufo {

using __sanitizer::internal_write;

// defined in ufo_rtl.cc
extern UFOContext *uctx;

static char* g_zip_buf = nullptr;
static size_t g_zipbuf_len = 0;
static struct snappy_env g_snappy_env;

// called by one thread (OutQueue::pt_worker_) at a time, COMPRESS_ON
void _do_write(int fd, Byte* data, u64 len) {
  if (uctx->use_compression) {
    size_t outlen;
    g_snappy_env.scratch = 0; // reuse env.hashtable
    g_snappy_env.scratch_output = 0;
    int err = snappy_compress(&g_snappy_env, (const char *) data, len, g_zip_buf, &outlen);
    DPrintf("SNAPPY>>>#%d err:%d   %llu  ->  %llu\r\n", __tsan::cur_thread()->tid, err, len, outlen);
    if (err != 0) {
      Printf("!!! #%d Error(%d) compress buffer, len:%d\r\n", __tsan::cur_thread()->tid, err, len);
      __sanitizer::Die();
    }
    u32 block_len = (u32) outlen;
    internal_write(fd, &block_len, 4);
    internal_write(fd, g_zip_buf, outlen);
  } else {
    internal_write(fd, data, len);
  }

#ifdef SYNC_AT_FLUSH
  fsync(fd);
#endif
}


void OutQueue::wait_non_full() {
  REAL(pthread_cond_wait)(&cond_not_full_, &mutex_);
}
void OutQueue::wait_non_empty() {
  REAL(pthread_cond_wait)(&cond_not_empty_, &mutex_);
}

void OutQueue::notify_non_empty() {
  REAL(pthread_cond_signal)(&cond_not_empty_);
}

void OutQueue::notify_non_full() {
  REAL(pthread_cond_signal)(&cond_not_full_);
}
void OutQueue::lock() {
  REAL(pthread_mutex_lock)(&mutex_);
}

void OutQueue::unlock() {
  REAL(pthread_mutex_unlock)(&mutex_);
}

bool OutQueue::is_started() const {
  return __sanitizer::atomic_load_relaxed(&continue_);
}


// for pthread
static void* _work_loop(void*);

void OutQueue::start(int len) {
  this->length_ = len;

  REAL(pthread_mutex_init)(&mutex_, NULL);
  REAL(pthread_cond_init)(&cond_not_full_, NULL);
  REAL(pthread_cond_init)(&cond_not_empty_, NULL);
  head_ = 0;
  tail_ = 0;
  task_count_ = 0;

  taskq_ = (WriteTask*)__tsan::internal_alloc(__tsan::MBlockScopedBuf, len * sizeof(WriteTask));

  for (int i = 0; i < length_; ++i) {
    u32 sz = uctx->get_buf_size();

    taskq_[i].size_ = 0;
    taskq_[i].fd_ = -1;
    taskq_[i].cap_ = sz;
    taskq_[i].data_ = (Byte*)__tsan::internal_alloc(__tsan::MBlockScopedBuf, sz);
    uctx->mem_acquired(sz);
  }

  if (uctx->use_compression) {
    snappy_init_env(&g_snappy_env);
    g_zipbuf_len = snappy_max_compressed_length(uctx->get_buf_size());
    g_zip_buf = (char *) __tsan::internal_alloc(__tsan::MBlockScopedBuf, g_zipbuf_len);
    uctx->mem_acquired(g_zipbuf_len);
  }

  __sanitizer::atomic_store_relaxed(&continue_, 1);
  __sanitizer::real_pthread_create(&pt_worker_, NULL, _work_loop, this);
}

void OutQueue::push(TLBuffer *buf) {
  lock();
  while (is_full()) {
    wait_non_full();
  }
  WriteTask *task = taskq_ + tail_;
  tail_ = (tail_ + 1) % length_;
  task_count_++;
  task->load_with(buf);// non-block
  notify_non_empty();
  unlock();
}


static void* _work_loop(void* p) {
  OutQueue* q = (OutQueue*)p;
  while (q->is_started()) {
    q->lock();
    while (q->is_empty()) {
      if (q->is_started()) {
        q->wait_non_empty();
      } else { // thread should quit
        q->unlock();
        return nullptr;
      }
    }

    WriteTask *task = q->pop();
    _do_write(task->fd_, task->data_, task->size_);

    task->size_ = 0;
    q->notify_non_full();
    q->unlock();
  } // while
  return nullptr;
}

// see comment in header
void OutQueue::release_mem() {
  for (int j = 0; j < length_; ++j) {
    Byte **pp = &(taskq_[j].data_);
    if (*pp != nullptr) {
      __tsan::internal_free(*pp);
      uctx->mem_released(taskq_[j].cap_);
    }
    *pp = nullptr;
  }
  if (taskq_ != nullptr)
    __tsan::internal_free(taskq_);
  taskq_ = nullptr;

  if (uctx->use_compression) {
    if (g_zip_buf != nullptr) {
      __tsan::internal_free(g_zip_buf);
      uctx->mem_released(g_zipbuf_len);
    }
    g_zip_buf = nullptr;
    snappy_free_env(&g_snappy_env);
  }

}

void OutQueue::stop() {
  __sanitizer::atomic_store_relaxed(&continue_, 0);
  notify_non_empty();// send signal to pt_worker_, pt_worker_ should finish
  //REAL(pthread_join)(pt_worker_, NULL);
    __sanitizer::real_pthread_join((void*)pt_worker_, NULL);
    lock();
  //finish rest task
  while (!is_empty()) {
    WriteTask* task = pop();
    if (task->size_ > 0) {
      _do_write(task->fd_, task->data_, task->size_);
    }
  }

  release_mem();

  REAL(pthread_cond_destroy)(&cond_not_empty_);
  REAL(pthread_cond_destroy)(&cond_not_full_);

  unlock();
  REAL(pthread_mutex_destroy)(&mutex_);
}


}
}
