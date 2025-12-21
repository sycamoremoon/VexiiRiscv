#include <sim.h>

void main(){
    for(int i=0;i<10;i++) {
        char *str = "hello world";
        while(*str) sim_putchar(*str++);
    }
}
