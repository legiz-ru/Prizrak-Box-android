package main

//#include "bridge.h"
import "C"

import "cfa/native/config"

//export setZashboardController
func setZashboardController(addr C.c_string, secret C.c_string) {
	config.SetZashboardController(C.GoString(addr), C.GoString(secret))
}
