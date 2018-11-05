#include <time.h>
#define MAX_TEST_DURATION 300

// exit status code
#define EXIT_VULNERABLE 113

time_t start_timer(void);
int is_timer_expired(time_t timer_started);

time_t start_timer(){
  return time(NULL);
}

int is_timer_expired(time_t timer_started){
  return time(NULL) < (timer_started + MAX_TEST_DURATION);
}
