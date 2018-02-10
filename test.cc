#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>

int x=0;
int *p;
int *q;        
pthread_t new_thread;
pthread_mutex_t l;
pthread_cond_t cond;

void *thread_start(void*){
        pthread_mutex_lock(&l);
            *q=0;
            if(x==0)
               *p=0;
        pthread_mutex_unlock(&l);
        usleep(10);     

        pthread_cond_signal(&cond);
    return 0; 
}

void deallocate(void *z)
{
    free(z);
}

int main() {
	p = (int*)malloc(sizeof(int));
        q = (int*)malloc(sizeof(int));
        pthread_mutex_init(&l,NULL);
        pthread_cond_init(&cond,NULL);

        printf("pointer p: %p %p\n", &p, p);
        printf("pointer q: %p %p\n", &q, q);
        printf("new thread: %p %p\n", &new_thread, new_thread);
        printf("lock l: %p %p\n", &l, l);

        pthread_create(&new_thread,NULL,&thread_start,NULL);

        //pthread_join(new_thread,NULL);
//        usleep(10);     
        //if(x==0)return 0;
        pthread_mutex_lock(&l);

           deallocate(q);
        pthread_mutex_unlock(&l);

        pthread_mutex_lock(&l);
           x =1;
           pthread_cond_wait(&cond, &l);
        pthread_mutex_unlock(&l);
           deallocate(p);
        pthread_join(new_thread,NULL);

	return 0;
}
